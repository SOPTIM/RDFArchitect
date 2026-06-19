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

import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class GraphWithContextTransactionalTest {

    private final GraphCompressionConfig graphConfig = new GraphCompressionConfig();

    private GraphWithContextTransactional ctx;
    private Triple triple;
    private Triple triple2;
    private int originalTimeout;

    @BeforeEach
    void setUp() {
        originalTimeout = GraphCompressionConfig.getLockTimeoutSeconds();
        ctx = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        triple = TestRDFUtils.triple("s p o");
        triple2 = TestRDFUtils.triple("s2 p2 o2");
    }

    @AfterEach
    void tearDown() {
        graphConfig.setLockTimeoutSeconds(originalTimeout);
        if (ctx.isInTransaction()) {
            ctx.end();
        }
    }

    // -------------------------------------------------------------------------
    // begin
    // -------------------------------------------------------------------------

    @Test
    void begin_read_isInReadTransaction() {
        ctx.begin(ReadWrite.READ);

        assertThat(ctx.isInTransaction()).isTrue();
        assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.READ);
    }

    @Test
    void begin_write_isInWriteTransaction() {
        ctx.begin(ReadWrite.WRITE);

        assertThat(ctx.isInTransaction()).isTrue();
        assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.WRITE);
    }

    @Test
    void begin_returnsThis() {
        var result = ctx.begin(ReadWrite.WRITE);

        assertThat(result).isSameAs(ctx);
    }

    @Test
    void begin_whileAlreadyInTransaction_throwsException() {
        ctx.begin(ReadWrite.WRITE);

        assertThatExceptionOfType(GraphTransactionException.class)
                .isThrownBy(() -> ctx.begin(ReadWrite.WRITE));
    }

    // -------------------------------------------------------------------------
    // begin — lock timeout
    // -------------------------------------------------------------------------

    @Test
    void begin_whenWriteLockHeldByAnotherThread_timesOut() throws InterruptedException {
        graphConfig.setLockTimeoutSeconds(1);

        var lockAcquired = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var holder = startLockHolder(ReadWrite.WRITE, lockAcquired, releaseLock);

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatExceptionOfType(GraphTransactionException.class)
                    .isThrownBy(() -> ctx.begin(ReadWrite.WRITE))
                    .withMessageContaining("Timeout");
        } finally {
            releaseLock.countDown();
            holder.join();
        }
    }

    @Test
    void begin_read_whenReadLockHeldByAnotherThread_doesNotBlock() throws InterruptedException {
        var lockAcquired = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var holder = startLockHolder(ReadWrite.READ, lockAcquired, releaseLock);

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            // read locks are shared — this must not block
            assertThatNoException().isThrownBy(() -> ctx.begin(ReadWrite.READ));
            ctx.end();
        } finally {
            releaseLock.countDown();
            holder.join();
        }
    }

    @Test
    void begin_whenInterruptedWhileWaiting_throwsAndRestoresInterruptFlag()
            throws InterruptedException {
        graphConfig.setLockTimeoutSeconds(2);

        var lockAcquired = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var holder = startLockHolder(ReadWrite.WRITE, lockAcquired, releaseLock);
        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        var caught = new AtomicReference<Throwable>();
        var interruptFlagSet = new AtomicBoolean(false);
        var waiter =
                new Thread(
                        () -> {
                            try {
                                ctx.begin(ReadWrite.WRITE);
                            } catch (Throwable t) {
                                caught.set(t);
                                interruptFlagSet.set(Thread.currentThread().isInterrupted());
                            }
                        });
        waiter.start();
        Thread.sleep(200); // give the waiter a moment to block on the lock
        waiter.interrupt();
        waiter.join();

        releaseLock.countDown();
        holder.join();

        assertThat(caught.get()).isInstanceOf(GraphTransactionException.class);
        assertThat(interruptFlagSet.get()).isTrue();
    }

    // -------------------------------------------------------------------------
    // isInTransaction / transactionMode
    // -------------------------------------------------------------------------

    @Test
    void isInTransaction_initialState_returnsFalse() {
        assertThat(ctx.isInTransaction()).isFalse();
    }

    @Test
    void transactionMode_whenNotInTransaction_throwsException() {
        assertThatExceptionOfType(GraphNotInATransactionException.class)
                .isThrownBy(() -> ctx.transactionMode());
    }

    // -------------------------------------------------------------------------
    // end / close
    // -------------------------------------------------------------------------

    @Test
    void end_afterReadTransaction_isNoLongerInTransaction() {
        ctx.begin(ReadWrite.READ);

        ctx.end();

        assertThat(ctx.isInTransaction()).isFalse();
    }

    @Test
    void end_afterWriteCommit_isNoLongerInTransaction() {
        ctx.begin(ReadWrite.WRITE);
        ctx.commit();

        ctx.end();

        assertThat(ctx.isInTransaction()).isFalse();
    }

    @Test
    void end_whenNotInTransaction_throwsException() {
        assertThatExceptionOfType(GraphNotInATransactionException.class)
                .isThrownBy(() -> ctx.end());
    }

    @Test
    void end_withUncommittedWriteChanges_abortsAutomatically() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            // no commit — close() via try-with-resources triggers auto-abort
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isFalse();
        }
    }

    @Test
    void close_whenInTransaction_endsTransaction() {
        ctx.begin(ReadWrite.READ);

        ctx.close();

        assertThat(ctx.isInTransaction()).isFalse();
    }

    @Test
    void close_whenNotInTransaction_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> ctx.close());
    }

    // -------------------------------------------------------------------------
    // commit
    // -------------------------------------------------------------------------

    @Test
    void commit_whenNotInTransaction_throwsException() {
        assertThatExceptionOfType(GraphNotInATransactionException.class)
                .isThrownBy(() -> ctx.commit());
    }

    @Test
    void commit_duringReadTransaction_throwsException() {
        ctx.begin(ReadWrite.READ);

        assertThatExceptionOfType(GraphTransactionException.class).isThrownBy(() -> ctx.commit());
    }

    @Test
    void commit_writtenTripleIsVisibleInSubsequentReadTransaction() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit();
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isTrue();
        }
    }

    @Test
    void commit_withoutMessage_doesNotAddToChangeLog() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit();
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getChangeLog().getUndoHistory()).size().isEqualTo(1);
            assertThat(ctx.canUndo()).isFalse();
        }
    }

    @Test
    void commitWithMessage_addsEntryToChangeLog() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("my change");
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            var history = ctx.getChangeLog().getUndoHistory();
            assertThat(history).hasSize(2);
            assertThat(history.getFirst().getMessage()).isEqualTo("my change");
            assertThat(history.get(1).getMessage()).isEqualTo("imported graph");
        }
    }

    @Test
    void commitWithMessage_enablesUndo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isTrue();
        }
    }

    @Test
    void commitWithMessage_multipleCommits_historyIsNewestFirst() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        try (var _ = ctx.begin(ReadWrite.READ)) {
            var history = ctx.getChangeLog().getUndoHistory();
            assertThat(history).hasSize(3);
            assertThat(history.getFirst().getMessage()).isEqualTo("second");
            assertThat(history.get(1).getMessage()).isEqualTo("first");
            assertThat(history.get(2).getMessage()).isEqualTo("imported graph");
        }
    }

    // -------------------------------------------------------------------------
    // abort
    // -------------------------------------------------------------------------

    @Test
    void abort_whenNotInTransaction_throwsException() {
        assertThatExceptionOfType(GraphNotInATransactionException.class)
                .isThrownBy(() -> ctx.abort());
    }

    @Test
    void abort_duringReadTransaction_throwsException() {
        ctx.begin(ReadWrite.READ);

        assertThatExceptionOfType(GraphTransactionException.class).isThrownBy(() -> ctx.abort());
    }

    @Test
    void abort_writtenTripleIsNotVisibleAfterAbort() {
        ctx.begin(ReadWrite.WRITE);
        ctx.getRdfGraph().add(triple);
        ctx.abort();
        ctx.end();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // canUndo / canRedo
    // -------------------------------------------------------------------------

    @Test
    void canUndo_initialState_returnsFalse() {
        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isFalse();
        }
    }

    @Test
    void canRedo_initialState_returnsFalse() {
        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canRedo()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // undo
    // -------------------------------------------------------------------------

    @Test
    void undo_whenNothingToUndo_throwsException() {
        assertThatExceptionOfType(GraphVersionControlException.class).isThrownBy(() -> ctx.undo());
    }

    @Test
    void undo_whileInTransaction_throwsException() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }
        ctx.begin(ReadWrite.READ);

        assertThatExceptionOfType(GraphTransactionException.class).isThrownBy(() -> ctx.undo());
    }

    @Test
    void undo_revertsLastCommit() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("added triple");
        }

        ctx.undo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isFalse();
        }
    }

    @Test
    void undo_enablesRedo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }

        ctx.undo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canRedo()).isTrue();
        }
    }

    @Test
    void undo_singleCommit_disablesUndo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }

        ctx.undo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // redo
    // -------------------------------------------------------------------------

    @Test
    void redo_whenNothingToRedo_throwsException() {
        assertThatExceptionOfType(GraphVersionControlException.class).isThrownBy(() -> ctx.redo());
    }

    @Test
    void redo_whileInTransaction_throwsException() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }
        ctx.undo();
        ctx.begin(ReadWrite.READ);

        assertThatExceptionOfType(GraphTransactionException.class).isThrownBy(() -> ctx.redo());
    }

    @Test
    void redo_reappliesUndoneCommit() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("added triple");
        }
        ctx.undo();

        ctx.redo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isTrue();
        }
    }

    @Test
    void redo_enablesUndo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }
        ctx.undo();

        ctx.redo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canUndo()).isTrue();
        }
    }

    @Test
    void redo_disablesRedo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }
        ctx.undo();

        ctx.redo();

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canRedo()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // undo return value
    // -------------------------------------------------------------------------

    @Test
    void undo_returnsUndoneChangeLogEntry() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("added triple");
        }

        var entry = ctx.undo();

        assertThat(entry).isNotNull();
        assertThat(entry.getMessage()).isEqualTo("added triple");
    }

    @Test
    void undo_multipleCommits_returnsNewestEntry() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        var entry = ctx.undo();

        assertThat(entry.getMessage()).isEqualTo("second");
    }

    @Test
    void undo_twice_returnsEntriesInReverseOrder() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        var second = ctx.undo();
        var first = ctx.undo();

        assertThat(second.getMessage()).isEqualTo("second");
        assertThat(first.getMessage()).isEqualTo("first");
    }

    // -------------------------------------------------------------------------
    // redo return value
    // -------------------------------------------------------------------------

    @Test
    void redo_returnsRedoneChangeLogEntry() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("added triple");
        }
        ctx.undo();

        var entry = ctx.redo();

        assertThat(entry).isNotNull();
        assertThat(entry.getMessage()).isEqualTo("added triple");
    }

    @Test
    void redo_afterMultipleUndos_returnsEntriesInCorrectOrder() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }
        ctx.undo();
        ctx.undo();

        var first = ctx.redo();
        var second = ctx.redo();

        assertThat(first.getMessage()).isEqualTo("first");
        assertThat(second.getMessage()).isEqualTo("second");
    }

    @Test
    void undo_thenRedo_returnsSameEntry() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("my change");
        }

        var undone = ctx.undo();
        var redone = ctx.redo();

        assertThat(undone.getChangeId()).isEqualTo(redone.getChangeId());
    }

    // -------------------------------------------------------------------------
    // restoreToVersion
    // -------------------------------------------------------------------------

    @Test
    void restoreToVersion_whileInTransaction_throwsException() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }
        ctx.begin(ReadWrite.READ);

        assertThatExceptionOfType(GraphTransactionException.class)
                .isThrownBy(() -> ctx.restoreToVersion(UUID.randomUUID()));
    }

    @Test
    void restoreToVersion_unknownVersion_throwsException() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("step 1");
        }

        assertThatExceptionOfType(GraphVersionControlException.class)
                .isThrownBy(() -> ctx.restoreToVersion(UUID.randomUUID()));
    }

    @Test
    void restoreToVersion_restoresToTargetVersion() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }

        UUID targetVersionId;
        try (var _ = ctx.begin(ReadWrite.READ)) {
            targetVersionId = ctx.getChangeLog().getUndoHistory().getFirst().getChangeId();
        }

        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        ctx.restoreToVersion(targetVersionId);

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isTrue();
            assertThat(ctx.getRdfGraph().contains(triple2)).isFalse();
        }
    }

    @Test
    void restoreToVersion_enablesRedo() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }

        UUID targetVersionId;
        try (var _ = ctx.begin(ReadWrite.READ)) {
            targetVersionId = ctx.getChangeLog().getUndoHistory().getFirst().getChangeId();
        }

        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        ctx.restoreToVersion(targetVersionId);

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.canRedo()).isTrue();
        }
    }

    @Test
    void restoreToVersion_multipleVersionsBack() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }

        UUID targetVersionId;
        try (var _ = ctx.begin(ReadWrite.READ)) {
            targetVersionId = ctx.getChangeLog().getUndoHistory().getFirst().getChangeId();
        }

        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple2);
            ctx.commit("second");
        }

        var triple3 = TestRDFUtils.triple("s3 p3 o3");
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple3);
            ctx.commit("third");
        }

        ctx.restoreToVersion(targetVersionId);

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isTrue();
            assertThat(ctx.getRdfGraph().contains(triple2)).isFalse();
            assertThat(ctx.getRdfGraph().contains(triple3)).isFalse();
        }
    }

    @Test
    void restoreToVersion_currentVersion_doesNothing() {
        try (var _ = ctx.begin(ReadWrite.WRITE)) {
            ctx.getRdfGraph().add(triple);
            ctx.commit("first");
        }

        UUID currentVersionId;
        try (var _ = ctx.begin(ReadWrite.READ)) {
            currentVersionId = ctx.getChangeLog().getUndoHistory().getFirst().getChangeId();
        }

        ctx.restoreToVersion(currentVersionId);

        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().contains(triple)).isTrue();
            assertThat(ctx.canRedo()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // getters
    // -------------------------------------------------------------------------

    @Test
    void getRdfGraph_returnsNonNull() {
        assertThat(ctx.getRdfGraph()).isNotNull();
    }

    @Test
    void getDiagramLayout_returnsNonNull() {
        assertThat(ctx.getDiagramLayout()).isNotNull();
    }

    @Test
    void getCustomSHACL_returnsNonNull() {
        assertThat(ctx.getCustomSHACL()).isNotNull();
    }

    @Test
    void getChangeLog_initialState_isEmpty() {
        try (var _ = ctx.begin(ReadWrite.READ)) {
            assertThat(ctx.getChangeLog().getUndoHistory()).size().isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a thread that begins a transaction in the given mode, signals {@code lockAcquired}
     * once the lock is held, then blocks until {@code releaseLock} is counted down before ending
     * the transaction.
     */
    private Thread startLockHolder(
            ReadWrite mode, CountDownLatch lockAcquired, CountDownLatch releaseLock) {
        var holder =
                new Thread(
                        () -> {
                            ctx.begin(mode);
                            lockAcquired.countDown();
                            try {
                                releaseLock.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                ctx.end();
                            }
                        });
        holder.start();
        return holder;
    }
}
