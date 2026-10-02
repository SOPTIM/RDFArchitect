/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.database.inmemory;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.rdf.TestRDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The transaction behaviour of a workspace, exercised through its graphs. The rules themselves are
 * pinned in {@code WorkspaceTransactionContextTest} and {@code WorkspaceChangeLogTest}; what is
 * checked here is that the workspace wires them to real graphs.
 */
class WorkspaceTransactionTest {

    private static final String WORKSPACE = "workspace";
    private static final String GRAPH_A = "http://example.org/a";
    private static final String GRAPH_B = "http://example.org/b";

    private final GraphCompressionConfig graphConfig = new GraphCompressionConfig();

    private Workspace workspace;
    private Triple triple;
    private Triple triple2;
    private int originalTimeout;

    @BeforeEach
    void setUp() {
        originalTimeout = GraphCompressionConfig.getLockTimeoutSeconds();
        workspace = new Workspace(WORKSPACE);
        createGraph(workspace, GRAPH_A, GraphFactory.createDefaultGraph());
        createGraph(workspace, GRAPH_B, GraphFactory.createDefaultGraph());
        triple = TestRDFUtils.triple("s p o");
        triple2 = TestRDFUtils.triple("s2 p2 o2");
    }

    @AfterEach
    void tearDown() {
        graphConfig.setLockTimeoutSeconds(originalTimeout);
    }

    // -------------------------------------------------------------------------
    // Locking
    // -------------------------------------------------------------------------

    @Test
    void begin_whenWriteLockHeldByAnotherThread_timesOut() throws InterruptedException {
        graphConfig.setLockTimeoutSeconds(1);
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writer =
                new Thread(
                        () -> {
                            try (var _ = workspace.begin(ReadWrite.WRITE)) {
                                holding.countDown();
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                            }
                        });
        writer.start();
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> workspace.begin(ReadWrite.WRITE))
                    .isInstanceOf(GraphTransactionException.class)
                    .hasMessageContaining("Timeout");
        } finally {
            release.countDown();
            writer.join(5000);
        }
    }

    @Test
    void abandonCurrentTransaction_rollsBackWhatTheAbandonedTransactionChanged() {
        var transaction = workspace.begin(ReadWrite.WRITE);
        transaction.graph(GRAPH_A).getRdfGraph().add(triple);
        // The caller never closes it — the thread would otherwise go back into the pool holding it.

        var abandoned = WorkspaceTransactionContext.abandonCurrentTransaction();

        assertThat(abandoned).isEqualTo(WORKSPACE);
        try (var next = workspace.begin(ReadWrite.READ)) {
            assertThat(next.graph(GRAPH_A).getRdfGraph().contains(triple)).isFalse();
        }
    }

    @Test
    void abandonCurrentTransaction_givesTheWorkspaceLockBackToOtherThreads()
            throws InterruptedException {
        graphConfig.setLockTimeoutSeconds(5);
        workspace.begin(ReadWrite.WRITE);

        WorkspaceTransactionContext.abandonCurrentTransaction();

        var acquired = new AtomicBoolean();
        var writer =
                new Thread(
                        () -> {
                            try (var _ = workspace.begin(ReadWrite.WRITE)) {
                                acquired.set(true);
                            }
                        });
        writer.start();
        writer.join(10_000);

        assertThat(acquired).isTrue();
    }

    @Test
    void abandonCurrentTransaction_withoutATransaction_doesNothing() {
        assertThat(WorkspaceTransactionContext.abandonCurrentTransaction()).isNull();
    }

    @Test
    void begin_read_whenReadLockHeldByAnotherThread_doesNotBlock() throws InterruptedException {
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var succeeded = new AtomicBoolean();
        var reader =
                new Thread(
                        () -> {
                            try (var _ = workspace.begin(ReadWrite.READ)) {
                                holding.countDown();
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                            }
                        });
        reader.start();
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        try (var _ = workspace.begin(ReadWrite.READ)) {
            succeeded.set(true);
        } finally {
            release.countDown();
            reader.join(5000);
        }

        assertThat(succeeded).isTrue();
    }

    @Test
    void begin_afterAReadTransactionEnded_allowsAWrite() {
        try (var _ = workspace.begin(ReadWrite.READ)) {
            // just holding the read lock
        }

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.commit("added a triple");
        }

        assertThat(triplesIn(GRAPH_A)).contains(triple);
    }

    // -------------------------------------------------------------------------
    // Commit and abort
    // -------------------------------------------------------------------------

    @Test
    void commit_writtenTripleIsVisibleInSubsequentReadTransaction() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.commit("added a triple");
        }

        assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
    }

    @Test
    void commit_duringReadTransaction_throwsException() {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThatThrownBy(() -> transaction.commit("nope"))
                    .isInstanceOf(GraphTransactionException.class);
        }
    }

    @Test
    void abort_discardsTheWrittenTriple() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.abort();
        }

        assertThat(triplesIn(GRAPH_A)).isEmpty();
    }

    @Test
    void abort_makesFurtherWritesFail() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.abort();

            var graph = transaction.graph(GRAPH_A).getRdfGraph();
            assertThatThrownBy(() -> graph.add(triple2))
                    .isInstanceOf(GraphTransactionException.class)
                    .hasMessageContaining("has been aborted");
        }
    }

    @Test
    void close_withUncommittedChanges_rollsThemBack() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
        }

        assertThat(triplesIn(GRAPH_A)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // A change spanning two graphs is one step
    // -------------------------------------------------------------------------

    @Test
    void commit_acrossTwoGraphs_isRecordedAsOneChange() {
        writeToBothGraphs();

        assertThat(workspace.getChangeHistory())
                .first()
                .satisfies(
                        entry ->
                                assertThat(entry.affectedGraphUris())
                                        .containsExactlyInAnyOrder(GRAPH_A, GRAPH_B));
    }

    @Test
    void undo_acrossTwoGraphs_revertsBoth() {
        writeToBothGraphs();

        workspace.undo();

        assertThat(triplesIn(GRAPH_A)).isEmpty();
        assertThat(triplesIn(GRAPH_B)).isEmpty();
    }

    @Test
    void abort_acrossTwoGraphs_leavesNoPartialChange() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.graph(GRAPH_B).getRdfGraph().add(triple2);
            transaction.abort();
        }

        assertThat(triplesIn(GRAPH_A)).isEmpty();
        assertThat(triplesIn(GRAPH_B)).isEmpty();
    }

    @Test
    void commit_leavesGraphsThatDidNotChangeOutOfTheEntry() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.commit("touched only A");
        }

        assertThat(workspace.getChangeHistory())
                .first()
                .satisfies(entry -> assertThat(entry.affectedGraphUris()).containsExactly(GRAPH_A));
    }

    @Test
    void commit_layoutChange_isAttributedToItsOwnGraph() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var layout = transaction.graph(GRAPH_A).getDiagramLayout().getDiagramLayoutModel();
            layout.add(
                    layout.createResource("urn:diagram"),
                    layout.createProperty("urn:name"),
                    "a diagram");
            transaction.commit("moved a class");
        }

        assertThat(workspace.getChangeHistory())
                .first()
                .satisfies(entry -> assertThat(entry.affectedGraphUris()).containsExactly(GRAPH_A));
    }

    @Test
    void commit_namesTheChangeItRecorded() {
        commitTriple(GRAPH_A, triple, "renamed a class");

        assertThat(workspace.getChangeHistory())
                .first()
                .extracting(WorkspaceChangeLogEntry::message)
                .isEqualTo("renamed a class");
    }

    @Test
    void commit_nestedCommits_areNamedByTheEnclosingOne() {
        try (var outer = workspace.begin(ReadWrite.WRITE)) {
            try (var inner = workspace.begin(ReadWrite.WRITE)) {
                inner.graph(GRAPH_A).getRdfGraph().add(triple);
                inner.commit("inserted a stub");
            }
            outer.graph(GRAPH_B).getRdfGraph().add(triple2);
            outer.commit("extended a class");
        }

        assertThat(workspace.getChangeHistory())
                .first()
                .extracting(WorkspaceChangeLogEntry::message)
                .isEqualTo("extended a class");
    }

    @Test
    void abort_rollsBackAChangeToAWorkspaceDiagram() {
        var diagramId = UUID.randomUUID();
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.diagrams().put(diagramId, new CustomDiagram(diagramId, "kept", List.of()));
            transaction.commit("added a diagram");
        }

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.diagrams().get(diagramId).setName("discarded");
            transaction.diagrams().remove(diagramId);
            transaction.abort();
        }

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(transaction.diagrams()).containsOnlyKeys(diagramId);
            assertThat(transaction.diagrams().get(diagramId).getName()).isEqualTo("kept");
        }
    }

    @Test
    void abort_rollsBackAChangeToAGraphDiagram() {
        var diagramId = UUID.randomUUID();
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction
                    .graph(GRAPH_A)
                    .getCustomDiagrams()
                    .put(diagramId, new CustomDiagram(diagramId, "kept", List.of()));
            transaction.commit("added a diagram");
        }

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getCustomDiagrams().remove(diagramId);
            transaction.abort();
        }

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(transaction.graph(GRAPH_A).getCustomDiagrams()).containsOnlyKeys(diagramId);
        }
    }

    // -------------------------------------------------------------------------
    // Nesting
    // -------------------------------------------------------------------------

    @Test
    void nestedTransaction_commitsOnceWithTheEnclosingOne() {
        var entriesBefore = workspace.getChangeHistory().size();

        try (var outer = workspace.begin(ReadWrite.WRITE)) {
            outer.graph(GRAPH_A).getRdfGraph().add(triple);
            try (var inner = workspace.begin(ReadWrite.WRITE)) {
                inner.graph(GRAPH_B).getRdfGraph().add(triple2);
                inner.commit("inner change");
            }
            outer.commit("outer change");
        }

        assertThat(workspace.getChangeHistory()).hasSize(entriesBefore + 1);
        assertThat(workspace.getChangeHistory().getFirst().message()).isEqualTo("outer change");
        assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
        assertThat(triplesIn(GRAPH_B)).containsExactly(triple2);
    }

    @Test
    void nestedRead_insideAWrite_joinsAsWrite() {
        try (var outer = workspace.begin(ReadWrite.WRITE)) {
            try (var inner = workspace.begin(ReadWrite.READ)) {
                inner.graph(GRAPH_A).getRdfGraph().add(triple);
            }
            outer.commit("added inside a nested read");
        }

        assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
    }

    @Test
    void nestedAbort_rollsBackTheEnclosingTransactionToo() {
        try (var outer = workspace.begin(ReadWrite.WRITE)) {
            outer.graph(GRAPH_A).getRdfGraph().add(triple);
            try (var inner = workspace.begin(ReadWrite.WRITE)) {
                inner.abort();
            }
            assertThatThrownBy(() -> outer.commit("should not get through"))
                    .isInstanceOf(GraphTransactionException.class);
        }

        assertThat(triplesIn(GRAPH_A)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Undo and redo
    // -------------------------------------------------------------------------

    @Test
    void freshWorkspace_hasNothingToUndo() {
        var fresh = new Workspace("fresh");

        assertThat(fresh.canUndo()).isFalse();
        assertThat(fresh.canRedo()).isFalse();
    }

    @Test
    void undo_revertsTheLastCommitAndEnablesRedo() {
        commitTriple(GRAPH_A, triple, "first");

        workspace.undo();

        assertThat(triplesIn(GRAPH_A)).isEmpty();
        assertThat(workspace.canRedo()).isTrue();
    }

    @Test
    void redo_reappliesTheUndoneCommit() {
        commitTriple(GRAPH_A, triple, "first");
        workspace.undo();

        workspace.redo();

        assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
        assertThat(workspace.canRedo()).isFalse();
    }

    @Test
    void undo_withNothingToUndo_throwsException() {
        var fresh = new Workspace("fresh");

        assertThatThrownBy(fresh::undo).isInstanceOf(GraphVersionControlException.class);
    }

    @Test
    void redo_withNothingToRedo_throwsException() {
        assertThatThrownBy(() -> workspace.redo()).isInstanceOf(GraphVersionControlException.class);
    }

    @Test
    void undo_twice_revertsInReverseOrder() {
        commitTriple(GRAPH_A, triple, "first");
        commitTriple(GRAPH_A, triple2, "second");

        assertThat(workspace.undo().change().message()).isEqualTo("second");
        assertThat(workspace.undo().change().message()).isEqualTo("first");
    }

    @Test
    void undo_reportsWhatIsLeftToUndoAndRedo() {
        // Answered while the step still holds the lock, so the editor does not
        // have to ask twice more and cannot be told about a workspace that has
        // meanwhile moved on.
        commitTriple(GRAPH_A, triple, "first");
        commitTriple(GRAPH_A, triple2, "second");

        var step = workspace.undo();

        assertThat(step.change().message()).isEqualTo("second");
        assertThat(step.canUndo()).isTrue();
        assertThat(step.canRedo()).isTrue();

        // The two graphs of the fixture are entries of their own, so the last
        // step is the one that reports there is nothing left.
        var last = step;
        while (last.canUndo()) {
            last = workspace.undo();
        }
        assertThat(last.canUndo()).isFalse();
        assertThat(last.canRedo()).isTrue();
    }

    @Test
    void restoreToVersion_undoesEverythingAfterTheTargetVersion() {
        commitTriple(GRAPH_A, triple, "first");
        var target = workspace.getChangeHistory().getFirst().changeId();
        commitTriple(GRAPH_A, triple2, "second");

        workspace.restoreToVersion(target);

        assertThat(triplesIn(GRAPH_A)).containsExactly(triple);
        assertThat(workspace.canRedo()).isTrue();
    }

    @Test
    void restoreToVersion_unknownVersion_throwsException() {
        assertThatThrownBy(() -> workspace.restoreToVersion(java.util.UUID.randomUUID()))
                .isInstanceOf(GraphVersionControlException.class);
    }

    // -------------------------------------------------------------------------
    // Isolation between workspaces
    // -------------------------------------------------------------------------

    @Test
    void undo_inOneWorkspace_leavesTheOtherUntouched() {
        var other = new Workspace("other");
        createGraph(other, GRAPH_A, GraphFactory.createDefaultGraph());
        commitTriple(GRAPH_A, triple, "change in the first workspace");
        try (var transaction = other.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple2);
            transaction.commit("change in the other workspace");
        }

        workspace.undo();

        try (var transaction = other.begin(ReadWrite.READ)) {
            assertThat(transaction.graph(GRAPH_A).getRdfGraph().find().toList())
                    .containsExactly(triple2);
        }
    }

    @Test
    void begin_onASecondWorkspace_whileOneIsOpen_throwsException() {
        var other = new Workspace("other");

        try (var _ = workspace.begin(ReadWrite.READ)) {
            assertThatThrownBy(() -> other.begin(ReadWrite.READ))
                    .isInstanceOf(GraphTransactionException.class)
                    .hasMessageContaining("already inside workspace");
        }
    }

    @Test
    void transactionState_isNotSharedBetweenThreads() throws InterruptedException {
        var seen = new AtomicReference<Boolean>();
        var done = new CountDownLatch(1);

        try (var _ = workspace.begin(ReadWrite.READ)) {
            var other =
                    new Thread(
                            () -> {
                                try (var _ = workspace.begin(ReadWrite.READ)) {
                                    seen.set(true);
                                }
                                done.countDown();
                            });
            other.start();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(seen.get()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void writeToBothGraphs() {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(GRAPH_A).getRdfGraph().add(triple);
            transaction.graph(GRAPH_B).getRdfGraph().add(triple2);
            transaction.commit("copied a class from A to B");
        }
    }

    private void commitTriple(String graphUri, Triple value, String message) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.graph(graphUri).getRdfGraph().add(value);
            transaction.commit(message);
        }
    }

    private java.util.List<Triple> triplesIn(String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return transaction.graph(graphUri).getRdfGraph().find().toList();
        }
    }

    private static void createGraph(Workspace workspace, String graphUri, Graph graph) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.createGraph(graphUri, graph);
            transaction.commit("created graph %s".formatted(graphUri));
        }
    }

    private static void renameGraph(Workspace workspace, String oldGraphUri, String newGraphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.renameGraph(oldGraphUri, newGraphUri);
            transaction.commit("renamed graph %s".formatted(oldGraphUri));
        }
    }

    private static void deleteGraph(Workspace workspace, String graphUri) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(graphUri);
            transaction.commit("deleted graph %s".formatted(graphUri));
        }
    }

    private static void setPrefixes(Workspace workspace, PrefixMapping prefixMapping) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.setPrefixes(prefixMapping);
            transaction.commit("changed the namespace prefixes");
        }
    }
}
