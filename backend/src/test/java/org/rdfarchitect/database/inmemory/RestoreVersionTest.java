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
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.GRAPH_A;
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.GRAPH_B;
import static org.rdfarchitect.database.inmemory.WorkspaceFixtures.WORKSPACE;
import static org.rdfarchitect.rdf.TestRDFUtils.triple;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.UUID;

/**
 * Covers restoring a version as a commit of its own: what it puts back, what a restricted scope
 * leaves alone, and that the history keeps growing in one direction either way.
 */
class RestoreVersionTest {

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(WORKSPACE);
        createGraph(GRAPH_A);
    }

    // -------------------------------------------------------------------------
    // Restoring the whole workspace
    // -------------------------------------------------------------------------

    @Nested
    class WholeWorkspace {

        @Test
        void putsBackTheSchemaAsTheTargetVersionLeftIt() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o2"));

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p o1"));
        }

        @Test
        void putsBackWhatWasDeletedAfterTheTargetVersion() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var target = newestChangeId();
            deleteFromSchema(GRAPH_A, triple("s p o1"));

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p o1"));
        }

        @Test
        void reachesEveryGraphAChangeSpanned() {
            createGraph(GRAPH_B);
            var target = newestChangeId();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.graph(GRAPH_A).getRdfGraph().add(triple("s p a"));
                transaction.graph(GRAPH_B).getRdfGraph().add(triple("s p b"));
                transaction.commit("changed both graphs");
            }

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(schemaOf(GRAPH_A)).isEmpty();
            assertThat(schemaOf(GRAPH_B)).isEmpty();
        }

        @Test
        void bringsBackAGraphThatWasDeletedSince() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var target = newestChangeId();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_A);
                transaction.commit("deleted graph a");
            }

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p o1"));
        }

        @Test
        void takesAwayAGraphThatWasCreatedSince() {
            var target = newestChangeId();
            createGraph(GRAPH_B);

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void countsEveryVersionAnEntryGaveAParticipant_notEveryEntry() {
            // An inner commit contributes its version to the entry the outer one writes, so one
            // entry can give the same participant two versions. How far back its old state lies is
            // counted in versions, not in entries.
            var target = newestChangeId();
            try (var outer = workspace.begin(ReadWrite.WRITE)) {
                try (var inner = workspace.begin(ReadWrite.WRITE)) {
                    inner.graph(GRAPH_A).getRdfGraph().add(triple("s p inner"));
                    inner.commit("inner");
                }
                outer.graph(GRAPH_A).getRdfGraph().add(triple("s p outer"));
                outer.commit("outer");
            }
            assertThat(workspace.getChangeHistory().getFirst().participants()).hasSize(2);

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(schemaOf(GRAPH_A)).isEmpty();
        }

        @Test
        void putsBackTheNameAGraphHadAtTheTargetVersion() {
            var target = newestChangeId();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.renameGraph(GRAPH_A, GRAPH_B);
                transaction.commit("renamed graph a");
            }

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
        }
    }

    // -------------------------------------------------------------------------
    // Restoring part of the workspace
    // -------------------------------------------------------------------------

    @Nested
    class RestrictedScope {

        @Test
        void restoringOneGraphLeavesTheOthersWhereTheyAre() {
            createGraph(GRAPH_B);
            var target = newestChangeId();
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.graph(GRAPH_A).getRdfGraph().add(triple("s p a"));
                transaction.graph(GRAPH_B).getRdfGraph().add(triple("s p b"));
                transaction.commit("changed both graphs");
            }

            workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_A));

            assertThat(schemaOf(GRAPH_A)).isEmpty();
            assertThat(schemaOf(GRAPH_B)).containsExactly(triple("s p b"));
        }

        @Test
        void restoringOneGraph_namesItTheWayTheChangelogDoes() {
            // The changelog shows a renamed graph's earlier changes under the name it has now, so
            // that is the name the user picks a restore with.
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p a"));
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.renameGraph(GRAPH_A, GRAPH_B);
                transaction.commit("renamed graph a");
            }

            workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_B));

            assertThat(schemaOf(GRAPH_B)).isEmpty();
        }

        @Test
        void restoringOneGraphLeavesTheSetOfGraphsAlone() {
            // Restoring the set of graphs to bring one back would take every other graph created
            // since with it, so what became of a graph is a workspace-level decision.
            var target = newestChangeId();
            createGraph(GRAPH_B);

            workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_B));

            assertThat(workspace.listGraphUris()).containsExactlyInAnyOrder(GRAPH_A, GRAPH_B);
        }

        @Test
        void aSchemaDeletedSince_isNotWrittenIntoOnTheWayPast() {
            // Its contents are still captured, because the change that touched it still names it,
            // but the workspace no longer holds the schema: writing into it would be a change
            // nothing could name.
            createGraph(GRAPH_B);
            var target = newestChangeId();
            commitSchema(GRAPH_B, triple("s p b"));
            try (var transaction = workspace.begin(ReadWrite.WRITE)) {
                transaction.deleteGraph(GRAPH_B);
                transaction.commit("deleted graph b");
            }

            var step = workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_B));

            assertThat(step.change()).isNull();
            assertThat(workspace.listGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void aScopeThatCoversNothingChangesNothingAndSaysSo() {
            createGraph(GRAPH_B);
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o"));

            var step = workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_B));

            assertThat(step.change()).isNull();
            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p o"));
            assertThat(newestChange().message()).isEqualTo("added a triple");
        }

        @Test
        void anUnknownVersion_throwsException() {
            assertThatThrownBy(
                            () ->
                                    workspace.restoreToVersion(
                                            UUID.randomUUID(), RevertScope.everything()))
                    .isInstanceOf(GraphVersionControlException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void restoringToTheNewestVersionChangesNothing() {
            commitSchema(GRAPH_A, triple("s p o"));

            var step = workspace.restoreToVersion(newestChangeId(), RevertScope.everything());

            assertThat(step.change()).isNull();
            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p o"));
        }
    }

    // -------------------------------------------------------------------------
    // How far back a restore reaches
    // -------------------------------------------------------------------------

    @Nested
    class RetentionBound {

        @Test
        void theOldestRetainedVersion_canStillBeRestoredTo() {
            // Dropping an entry folds the version below it into its base, which lets go of the
            // triples that entry's delta showed. The version itself is still there and still
            // composes to the state it left, so it is still somewhere a restore can go.
            var config = new GraphCompressionConfig();
            var original = GraphCompressionConfig.getMaxVersions();
            try {
                config.setMaxVersions(4);
                var bounded = new Workspace("bounded");
                WorkspaceFixtures.createGraph(bounded, GRAPH_A);
                for (int i = 0; i < 10; i++) {
                    commitSchema(bounded, GRAPH_A, triple("s p o" + i));
                }
                var oldest = bounded.getChangeHistory().getLast();

                bounded.restoreToVersion(oldest.changeId(), RevertScope.everything());

                assertThat(WorkspaceFixtures.triplesIn(bounded, GRAPH_A))
                        .contains(triple("s p o6"))
                        .doesNotContain(triple("s p o7"), triple("s p o8"), triple("s p o9"));
            } finally {
                config.setMaxVersions(original);
            }
        }
    }

    // -------------------------------------------------------------------------
    // What the restore leaves in the history
    // -------------------------------------------------------------------------

    @Nested
    class History {

        @Test
        void aWholeWorkspaceMove_stepsThroughTheHistoryRatherThanWritingToIt() {
            // The changes it steps over stay where they are, so the walk can go back the other
            // way. Writing a commit instead would discard them.
            commitSchema(GRAPH_A, triple("s p o1"));
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o2"));
            var stepped = newestChangeId();

            workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(currentChange().changeId()).isEqualTo(target);
            assertThat(workspace.getChangeHistory())
                    .extracting(WorkspaceChangeLogEntry::changeId)
                    .startsWith(stepped, target);
            assertThat(workspace.canRedo()).isTrue();
        }

        @Test
        void aWholeWorkspaceMove_canBeWalkedBackTheOtherWay() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o2"));
            var stepped = newestChangeId();
            workspace.restoreToVersion(target, RevertScope.everything());

            workspace.restoreToVersion(stepped, RevertScope.everything());

            assertThat(currentChange().changeId()).isEqualTo(stepped);
            assertThat(schemaOf(GRAPH_A))
                    .containsExactlyInAnyOrder(triple("s p o1"), triple("s p o2"));
        }

        @Test
        void aSchemaMove_isRecordedAsAChangeOfItsOwn() {
            // It cannot step, because stepping would take the other schemas with it.
            createGraph(GRAPH_B);
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p a"));

            var step = workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_A));

            assertThat(step.change().message()).startsWith("restored " + GRAPH_A + " to ");
            assertThat(currentChange().changeId()).isEqualTo(step.change().changeId());
            assertThat(workspace.canRedo()).isFalse();
        }

        @Test
        void aSchemaMove_canItselfBeUndone() {
            createGraph(GRAPH_B);
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p a"));
            workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH_A));

            workspace.undo();

            assertThat(schemaOf(GRAPH_A)).containsExactly(triple("s p a"));
        }

        @Test
        void namesTheGraphItLandedIn() {
            var target = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o"));

            var step = workspace.restoreToVersion(target, RevertScope.everything());

            assertThat(step.change().affectedGraphUris()).containsExactly(GRAPH_A);
        }

        @Test
        void restoringToAnUndoneChange_reachesItAgain() {
            commitSchema(GRAPH_A, triple("s p o1"));
            commitSchema(GRAPH_A, triple("s p o2"));
            var ahead = newestChangeId();
            workspace.undo();
            workspace.undo();

            workspace.restoreToVersion(ahead, RevertScope.everything());

            assertThat(schemaOf(GRAPH_A))
                    .containsExactlyInAnyOrder(triple("s p o1"), triple("s p o2"));
            assertThat(currentChange().changeId()).isEqualTo(ahead);
        }

        @Test
        void restoringToTheNearerOfTwoUndoneChanges_leavesTheFurtherOneAhead() {
            // Reaching one again is not a change of its own, so what lies beyond it is untouched
            // and can still be reached.
            commitSchema(GRAPH_A, triple("s p o1"));
            var nearer = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o2"));
            var further = newestChangeId();
            workspace.undo();
            workspace.undo();

            workspace.restoreToVersion(nearer, RevertScope.everything());

            assertThat(currentChange().changeId()).isEqualTo(nearer);
            assertThat(workspace.getChangeHistory().getFirst().changeId()).isEqualTo(further);
            assertThat(workspace.canRedo()).isTrue();
        }

        @Test
        void restoringPartOfTheWorkspaceToAnUndoneChange_isRefused() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var ahead = newestChangeId();
            workspace.undo();

            assertThatThrownBy(
                            () -> workspace.restoreToVersion(ahead, RevertScope.ofGraphs(GRAPH_A)))
                    .isInstanceOf(GraphVersionControlException.class)
                    .hasMessageContaining("reapplies the whole change");
        }

        @Test
        void anUndoneChangeStaysInTheChangelog_aheadOfTheWorkspace() {
            // It still happened, and a redo brings it back; dropping it would leave someone who
            // pressed Ctrl+Z watching their work disappear from the record of it.
            commitSchema(GRAPH_A, triple("s p o1"));
            commitSchema(GRAPH_A, triple("s p o2"));
            var undone = newestChangeId();

            workspace.undo();

            var history = workspace.getChangeHistory();
            assertThat(history.getFirst().changeId()).isEqualTo(undone);
            assertThat(history.getFirst().undone()).isTrue();
            assertThat(history.get(1).undone()).isFalse();
        }

        @Test
        void undoneChangesAreListedFurthestAheadFirst() {
            commitSchema(GRAPH_A, triple("s p o1"));
            var older = newestChangeId();
            commitSchema(GRAPH_A, triple("s p o2"));
            var newer = newestChangeId();
            workspace.undo();
            workspace.undo();

            assertThat(workspace.getChangeHistory())
                    .extracting(WorkspaceChangeLogEntry::changeId)
                    .startsWith(newer, older);
        }

        @Test
        void redoingAChange_putsItBackBehindTheWorkspace() {
            commitSchema(GRAPH_A, triple("s p o1"));
            workspace.undo();

            workspace.redo();

            assertThat(workspace.getChangeHistory()).noneMatch(WorkspaceChangeLogEntry::undone);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkspaceChangeLogEntry newestChange() {
        return WorkspaceFixtures.newestChange(workspace);
    }

    /** The change the workspace stands on, which an undone one may now sit above. */
    private WorkspaceChangeLogEntry currentChange() {
        return workspace.getChangeHistory().stream()
                .filter(change -> !change.undone())
                .findFirst()
                .orElseThrow();
    }

    private UUID newestChangeId() {
        return newestChange().changeId();
    }

    private void createGraph(String graphUri) {
        WorkspaceFixtures.createGraph(workspace, graphUri);
    }

    private void commitSchema(String graphUri, Triple triple) {
        commitSchema(workspace, graphUri, triple);
    }

    private static void commitSchema(Workspace target, String graphUri, Triple triple) {
        WorkspaceFixtures.commitTriple(target, graphUri, triple, "added a triple");
    }

    private void deleteFromSchema(String graphUri, Triple triple) {
        WorkspaceFixtures.deleteTriple(workspace, graphUri, triple, "deleted a triple");
    }

    private List<Triple> schemaOf(String graphUri) {
        return WorkspaceFixtures.triplesIn(workspace, graphUri);
    }
}
