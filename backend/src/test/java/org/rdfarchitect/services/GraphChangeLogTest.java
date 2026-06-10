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

package org.rdfarchitect.services;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.models.changelog.ChangeLog;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.rdf.graph.wrapper.TransactionContext;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

class GraphChangeLogTest {

    private TransactionContext txnContext;
    private ChangeLog changeLog;
    private ChangeLogEntry entry1;
    private ChangeLogEntry entry2;
    private ChangeLogEntry entry3;
    private UUID version1;
    private UUID version2;

    @BeforeEach
    void setUp() {
        txnContext = new TransactionContext();
        changeLog = new ChangeLog(txnContext);

        version1 = UUID.randomUUID();
        version2 = UUID.randomUUID();
        var version3 = UUID.randomUUID();

        entry1 = createEntry("entry1", version1);
        entry2 = createEntry("entry2", version2);
        entry3 = createEntry("entry3", version3);
    }

    @AfterEach
    void tearDown() {
        if (txnContext.isInTransaction()) {
            txnContext.end();
        }
    }

    private ChangeLogEntry createEntry(String message, UUID versionId) {
        var contextDeltas =
                List.of(
                        new ContextDelta(
                                "rdf",
                                new WeakReference<>(GraphFactory.createDefaultGraph()),
                                new WeakReference<>(GraphFactory.createDefaultGraph())));
        var entry = new ChangeLogEntry(message, 1, contextDeltas);
        entry.setChangeId(versionId);
        return entry;
    }

    /** Helper: buffers a push and commits it within a write transaction. */
    private void pushAndCommit(ChangeLogEntry entry) {
        txnContext.begin(ReadWrite.WRITE);
        changeLog.push(entry);
        changeLog.commit();
        txnContext.end();
    }

    /** Helper: begins a write transaction. */
    private void beginWrite() {
        txnContext.begin(ReadWrite.WRITE);
    }

    /** Helper: begins a read transaction. */
    private void beginRead() {
        txnContext.begin(ReadWrite.READ);
    }

    /** Helper: ends the current transaction. */
    private void endTxn() {
        txnContext.end();
    }

    // -------------------------------------------------------------------------
    // Transaction enforcement
    // -------------------------------------------------------------------------

    @Nested
    class TransactionEnforcement {

        @Test
        void push_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.push(entry1));
        }

        @Test
        void push_inReadTransaction_throws() {
            beginRead();
            assertThrows(GraphNotInAWriteTransactionException.class, () -> changeLog.push(entry1));
        }

        @Test
        void clearRedo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.clearRedo());
        }

        @Test
        void clearRedo_inReadTransaction_throws() {
            beginRead();
            assertThrows(GraphNotInAWriteTransactionException.class, () -> changeLog.clearRedo());
        }

        @Test
        void moveToRedo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.moveToRedo());
        }

        @Test
        void moveToRedo_inReadTransaction_throws() {
            beginRead();
            assertThrows(GraphNotInAWriteTransactionException.class, () -> changeLog.moveToRedo());
        }

        @Test
        void moveToUndo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.moveToUndo());
        }

        @Test
        void moveToUndo_inReadTransaction_throws() {
            beginRead();
            assertThrows(GraphNotInAWriteTransactionException.class, () -> changeLog.moveToUndo());
        }

        @Test
        void restore_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.restore(version1));
        }

        @Test
        void restore_inReadTransaction_throws() {
            beginRead();
            assertThrows(
                    GraphNotInAWriteTransactionException.class, () -> changeLog.restore(version1));
        }

        @Test
        void peekUndo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.peekUndo());
        }

        @Test
        void peekRedo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.peekRedo());
        }

        @Test
        void canUndo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.canUndo());
        }

        @Test
        void canRedo_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.canRedo());
        }

        @Test
        void getUndoHistory_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.getUndoHistory());
        }

        @Test
        void getRedoHistory_outsideTransaction_throws() {
            assertThrows(GraphNotInATransactionException.class, () -> changeLog.getRedoHistory());
        }

        @Test
        void peekUndo_inReadTransaction_succeeds() {
            pushAndCommit(entry1);
            beginRead();
            assertDoesNotThrow(() -> changeLog.peekUndo());
        }

        @Test
        void peekRedo_inReadTransaction_succeeds() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertDoesNotThrow(() -> changeLog.peekRedo());
        }

        @Test
        void getUndoHistory_inReadTransaction_succeeds() {
            beginRead();
            assertDoesNotThrow(() -> changeLog.getUndoHistory());
        }

        @Test
        void getRedoHistory_inReadTransaction_succeeds() {
            beginRead();
            assertDoesNotThrow(() -> changeLog.getRedoHistory());
        }

        @Test
        void canUndo_inReadTransaction_succeeds() {
            beginRead();
            assertDoesNotThrow(() -> changeLog.canUndo());
        }

        @Test
        void canRedo_inReadTransaction_succeeds() {
            beginRead();
            assertDoesNotThrow(() -> changeLog.canRedo());
        }
    }

    // -------------------------------------------------------------------------
    // commit / abort / hasChanges
    // -------------------------------------------------------------------------

    @Nested
    class TransactionParticipantBehavior {

        @Test
        void push_doesNotApplyBeforeCommit() {
            beginWrite();
            changeLog.push(entry1);

            assertThat(changeLog.getUndoHistory()).isEmpty();
            assertTrue(changeLog.hasChanges());
        }

        @Test
        void commit_appliesPendingPush() {
            pushAndCommit(entry1);

            beginRead();
            assertThat(changeLog.getUndoHistory()).hasSize(1);
            assertThat(changeLog.getUndoHistory().getFirst()).isEqualTo(entry1);
            assertFalse(changeLog.hasChanges());
        }

        @Test
        void abort_discardsPendingPush() {
            beginWrite();
            changeLog.push(entry1);
            changeLog.abort();

            assertThat(changeLog.getUndoHistory()).isEmpty();
            assertFalse(changeLog.hasChanges());
        }

        @Test
        void abort_discardsPendingMoveToRedo() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.abort();

            assertThat(changeLog.getUndoHistory()).hasSize(2);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void abort_discardsPendingMoveToUndo() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.abort();

            assertThat(changeLog.getUndoHistory()).hasSize(1);
            assertThat(changeLog.getRedoHistory()).hasSize(1);
        }

        @Test
        void abort_discardsPendingRestore() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.restore(version1);
            changeLog.abort();

            assertThat(changeLog.getUndoHistory()).hasSize(3);
        }

        @Test
        void abort_discardsPendingClearRedo() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.clearRedo();
            changeLog.abort();

            assertThat(changeLog.getRedoHistory()).hasSize(1);
        }

        @Test
        void hasChanges_noMutations_returnsFalse() {
            assertFalse(changeLog.hasChanges());
        }

        @Test
        void hasChanges_afterCommit_returnsFalse() {
            pushAndCommit(entry1);
            assertFalse(changeLog.hasChanges());
        }

        @Test
        void hasChanges_afterAbort_returnsFalse() {
            beginWrite();
            changeLog.push(entry1);
            changeLog.abort();
            assertFalse(changeLog.hasChanges());
        }

        @Test
        void hasChanges_multiplePendingMutations_returnsTrue() {
            beginWrite();
            changeLog.push(entry1);
            changeLog.push(entry2);
            changeLog.clearRedo();
            assertTrue(changeLog.hasChanges());
        }
    }

    // -------------------------------------------------------------------------
    // push
    // -------------------------------------------------------------------------

    @Nested
    class PushTests {

        @Test
        void push_singleEntry() {
            pushAndCommit(entry1);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void push_multipleEntries_orderIsNewestFirst() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry2, entry1);
        }

        @Test
        void push_multipleInSameTransaction_orderIsNewestFirst() {
            beginWrite();
            changeLog.push(entry1);
            changeLog.push(entry2);
            changeLog.push(entry3);
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry2, entry1);
        }

        @Test
        void push_clearsRedoStack() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertTrue(changeLog.canRedo());
            endTxn();

            pushAndCommit(entry3);

            beginRead();
            assertFalse(changeLog.canRedo());
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry1);
        }
    }

    // -------------------------------------------------------------------------
    // clearRedo
    // -------------------------------------------------------------------------

    @Nested
    class ClearRedoTests {

        @Test
        void clearRedo_emptyRedoStack_noEffect() {
            pushAndCommit(entry1);

            beginWrite();
            changeLog.clearRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
        }

        @Test
        void clearRedo_nonEmptyRedoStack_clearsAll() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).hasSize(2);
            endTxn();

            beginWrite();
            changeLog.clearRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertFalse(changeLog.canRedo());
        }
    }

    // -------------------------------------------------------------------------
    // moveToRedo
    // -------------------------------------------------------------------------

    @Nested
    class MoveToRedoTests {

        @Test
        void moveToRedo_movesTopUndoToRedo() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2);
        }

        @Test
        void moveToRedo_twice_movesTopTwoEntries() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
        }

        @Test
        void moveToRedo_inSeparateTransactions_redoOrderIsCorrect() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
        }

        @Test
        void moveToRedo_notAppliedBeforeCommit() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();

            assertThat(changeLog.getUndoHistory()).hasSize(2);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // moveToUndo
    // -------------------------------------------------------------------------

    @Nested
    class MoveToUndoTests {

        @Test
        void moveToUndo_movesTopRedoBackToUndo() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void moveToUndo_afterMultipleMoveToRedo_restoresOneAtATime() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
        }

        @Test
        void moveToUndo_fullRoundtrip_restoresOriginalState() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertFalse(changeLog.canRedo());
        }

        @Test
        void moveToUndo_notAppliedBeforeCommit() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();

            assertThat(changeLog.getUndoHistory()).hasSize(1);
            assertThat(changeLog.getRedoHistory()).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // peekUndo / peekRedo
    // -------------------------------------------------------------------------

    @Nested
    class PeekTests {

        @Test
        void peekUndo_returnsTopWithoutMutating() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginRead();
            var peeked = changeLog.peekUndo();

            assertThat(peeked).isEqualTo(entry2);
            assertThat(changeLog.getUndoHistory()).hasSize(2);
        }

        @Test
        void peekUndo_emptyStack_returnsNull() {
            beginRead();
            assertThat(changeLog.peekUndo()).isNull();
        }

        @Test
        void peekUndo_calledTwice_returnsSameInstance() {
            pushAndCommit(entry1);

            beginRead();
            var first = changeLog.peekUndo();
            var second = changeLog.peekUndo();
            assertThat(first).isSameAs(second);
        }

        @Test
        void peekRedo_returnsTopWithoutMutating() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            var peeked = changeLog.peekRedo();

            assertThat(peeked).isEqualTo(entry2);
            assertThat(changeLog.getRedoHistory()).hasSize(1);
        }

        @Test
        void peekRedo_emptyStack_returnsNull() {
            beginRead();
            assertThat(changeLog.peekRedo()).isNull();
        }

        @Test
        void peekRedo_calledTwice_returnsSameInstance() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            var first = changeLog.peekRedo();
            var second = changeLog.peekRedo();
            assertThat(first).isSameAs(second);
        }

        @Test
        void peekRedo_afterMultipleMoveToRedo_returnsLastMoved() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.peekRedo()).isEqualTo(entry2);
        }
    }

    // -------------------------------------------------------------------------
    // canUndo / canRedo
    // -------------------------------------------------------------------------

    @Nested
    class CanUndoRedoTests {

        @Test
        void canUndo_emptyLog_returnsFalse() {
            beginRead();
            assertFalse(changeLog.canUndo());
        }

        @Test
        void canUndo_singleEntry_returnsFalse() {
            pushAndCommit(entry1);
            beginRead();
            assertFalse(changeLog.canUndo());
        }

        @Test
        void canUndo_twoEntries_returnsTrue() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginRead();
            assertTrue(changeLog.canUndo());
        }

        @Test
        void canUndo_threeEntries_returnsTrue() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);
            beginRead();
            assertTrue(changeLog.canUndo());
        }

        @Test
        void canUndo_afterUndoingAllButOne_returnsFalse() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            assertFalse(changeLog.canUndo());
        }

        @Test
        void canRedo_noUndoneChanges_returnsFalse() {
            pushAndCommit(entry1);
            beginRead();
            assertFalse(changeLog.canRedo());
        }

        @Test
        void canRedo_afterMoveToRedo_returnsTrue() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            assertTrue(changeLog.canRedo());
        }

        @Test
        void canRedo_afterMoveToRedoThenMoveToUndo_returnsFalse() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();

            assertFalse(changeLog.canRedo());
        }

        @Test
        void canRedo_afterMoveToRedoThenPush_returnsFalse() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            pushAndCommit(entry3);

            beginRead();
            assertFalse(changeLog.canRedo());
        }
    }

    // -------------------------------------------------------------------------
    // getUndoHistory
    // -------------------------------------------------------------------------

    @Nested
    class GetUndoHistoryTests {

        @Test
        void getUndoHistory_emptyLog_returnsEmptyList() {
            beginRead();
            assertThat(changeLog.getUndoHistory()).isEmpty();
        }

        @Test
        void getUndoHistory_returnsImmutableCopy() {
            pushAndCommit(entry1);
            beginRead();
            var history = changeLog.getUndoHistory();
            assertThrows(UnsupportedOperationException.class, () -> history.add(entry2));
        }

        @Test
        void getUndoHistory_singleEntry() {
            pushAndCommit(entry1);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
        }

        @Test
        void getUndoHistory_multipleEntries_newestFirst() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry2, entry1);
        }

        @Test
        void getUndoHistory_afterMoveToRedo_excludesMovedEntry() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
        }

        @Test
        void getUndoHistory_afterMoveToRedoThenMoveToUndo_includesRestoredEntry() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
        }
    }

    // -------------------------------------------------------------------------
    // getRedoHistory
    // -------------------------------------------------------------------------

    @Nested
    class GetRedoHistoryTests {

        @Test
        void getRedoHistory_emptyLog_returnsEmptyList() {
            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void getRedoHistory_nothingUndone_returnsEmptyList() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void getRedoHistory_returnsImmutableCopy() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            var history = changeLog.getRedoHistory();
            assertThrows(UnsupportedOperationException.class, () -> history.add(entry3));
        }

        @Test
        void getRedoHistory_afterSingleMoveToRedo_containsMovedEntry() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.getRedoHistory()).containsExactly(entry2);
        }

        @Test
        void getRedoHistory_afterMultipleMoveToRedo_containsAllInOrder() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();

            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
        }

        @Test
        void getRedoHistory_afterMoveToRedoInSeparateTransactions_containsAllInOrder() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
        }

        @Test
        void getRedoHistory_afterMoveToRedoThenPartialMoveToUndo_reflectsCurrentState() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
        }

        @Test
        void getRedoHistory_afterMoveToRedoThenFullMoveToUndo_isEmpty() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void getRedoHistory_afterClearRedo_isEmpty() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).hasSize(1);
            endTxn();

            beginWrite();
            changeLog.clearRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void getRedoHistory_afterPush_isEmpty() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).hasSize(1);
            endTxn();

            pushAndCommit(entry3);

            beginRead();
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void getRedoHistory_afterRestore_containsMovedEntries() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.restore(version1);
            changeLog.commit();

            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
        }

        @Test
        void getRedoHistory_afterRestoreToMiddle_containsOnlyNewerEntries() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.restore(version2);
            changeLog.commit();

            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
        }

        // -------------------------------------------------------------------------
        // restore
        // -------------------------------------------------------------------------

        @Nested
        class RestoreTests {

            @Test
            void restore_toOldestVersion_leavesOnlyThatEntry() {
                pushAndCommit(entry1);
                pushAndCommit(entry2);
                pushAndCommit(entry3);

                beginWrite();
                changeLog.restore(version1);
                changeLog.commit();

                assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
                assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
            }

            @Test
            void restore_toMiddleVersion_keepsOlderEntries() {
                pushAndCommit(entry1);
                pushAndCommit(entry2);
                pushAndCommit(entry3);

                beginWrite();
                changeLog.restore(version2);
                changeLog.commit();

                assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
                assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
            }

            @Test
            void restore_toCurrentVersion_noChange() {
                pushAndCommit(entry1);
                pushAndCommit(entry2);

                beginWrite();
                changeLog.restore(version2);
                changeLog.commit();

                assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
                assertThat(changeLog.getRedoHistory()).isEmpty();
            }

            @Test
            void restore_invalidVersion_clearsUndoStack() {
                pushAndCommit(entry1);
                pushAndCommit(entry2);

                beginWrite();
                changeLog.restore(UUID.randomUUID());
                changeLog.commit();

                assertThat(changeLog.getUndoHistory()).isEmpty();
            }

            @Test
            void restore_notAppliedBeforeCommit() {
                pushAndCommit(entry1);
                pushAndCommit(entry2);
                pushAndCommit(entry3);

                beginWrite();
                changeLog.restore(version1);

                assertThat(changeLog.getUndoHistory()).hasSize(3);
                assertThat(changeLog.getRedoHistory()).isEmpty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Complex scenarios
    // -------------------------------------------------------------------------

    @Nested
    class ComplexScenarios {

        @Test
        void undoRedoUndoRedo_fullCycle() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            // Undo entry3.
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
            endTxn();

            // Undo entry2.
            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
            endTxn();

            // Redo entry2.
            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
            endTxn();

            // Redo entry3.
            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry2, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void undoThenPush_invalidatesRedoHistory() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2);
            endTxn();

            pushAndCommit(entry3);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertFalse(changeLog.canRedo());
        }

        @Test
        void restoreThenPush_invalidatesRedoHistory() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.restore(version1);
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).hasSize(2);
            endTxn();

            var entry4 = createEntry("entry4", UUID.randomUUID());
            pushAndCommit(entry4);

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry4, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void multipleMutationsInSingleTransaction_allApplied() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.clearRedo();
            changeLog.commit();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
            assertFalse(changeLog.canUndo());
            assertFalse(changeLog.canRedo());
        }

        @Test
        void abortAfterMultipleMutations_discardsAll() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.moveToRedo();
            changeLog.moveToRedo();
            changeLog.clearRedo();
            changeLog.abort();

            assertThat(changeLog.getUndoHistory()).containsExactly(entry3, entry2, entry1);
            assertThat(changeLog.getRedoHistory()).isEmpty();
        }

        @Test
        void restoreThenMoveToUndo_redoesFromRestoredState() {
            pushAndCommit(entry1);
            pushAndCommit(entry2);
            pushAndCommit(entry3);

            beginWrite();
            changeLog.restore(version1);
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getRedoHistory()).containsExactly(entry2, entry3);
            endTxn();

            // Redo entry2.
            beginWrite();
            changeLog.moveToUndo();
            changeLog.commit();
            endTxn();

            beginRead();
            assertThat(changeLog.getUndoHistory()).containsExactly(entry2, entry1);
            assertThat(changeLog.getRedoHistory()).containsExactly(entry3);
        }
    }
}
