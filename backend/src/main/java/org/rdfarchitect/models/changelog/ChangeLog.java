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

package org.rdfarchitect.models.changelog;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.rdf.graph.wrapper.TransactionContext;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Manages the undo/redo history of named commits.
 *
 * <p>The changelog maintains two stacks: an <em>undo stack</em> holding committed entries and a
 * <em>redo stack</em> holding entries that have been undone and can be reapplied. Together they
 * form a linear version history that supports arbitrary undo, redo, and restore operations.
 *
 * <p>This class implements {@link TransactionParticipant}. All mutating operations buffer their
 * effect as pending mutations that are only applied when {@link #commit()} is called. Calling
 * {@link #abort()} discards all pending mutations, leaving both stacks unchanged. This ensures that
 * the changelog stays consistent with the graph participants it is coordinated with.
 *
 * <p>All mutating methods require an active write transaction on the shared {@link
 * TransactionContext}. Read-only query methods require at least a read transaction. Violating these
 * preconditions throws {@link GraphNotInATransactionException} or {@link
 * GraphNotInAWriteTransactionException}.
 *
 * <p><strong>Thread safety:</strong> this class is not thread-safe on its own. Synchronisation is
 * the responsibility of the owning coordinator (typically {@link
 * org.rdfarchitect.database.inmemory.GraphWithContextTransactional}).
 *
 * @see TransactionParticipant
 * @see ChangeLogEntry
 */
public class ChangeLog implements TransactionParticipant {

    private final TransactionContext txnContext;

    private final Deque<ChangeLogEntry> undoStack = new ArrayDeque<>();
    private final Deque<ChangeLogEntry> redoStack = new ArrayDeque<>();

    private final List<Runnable> pendingMutations = new ArrayList<>();

    /**
     * Creates a new, empty changelog bound to the given transaction context.
     *
     * @param txnContext the shared transaction context used to enforce transaction preconditions
     */
    public ChangeLog(TransactionContext txnContext) {
        this.txnContext = txnContext;
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    /**
     * Applies all pending mutations to the undo and redo stacks in the order they were buffered.
     * After this call, {@link #hasChanges()} returns {@code false}.
     */
    @Override
    public void commit() {
        pendingMutations.forEach(Runnable::run);
        pendingMutations.clear();
    }

    /**
     * Discards all pending mutations without applying them. Both stacks remain in the state they
     * were in before the current transaction began. After this call, {@link #hasChanges()} returns
     * {@code false}.
     */
    @Override
    public void abort() {
        pendingMutations.clear();
    }

    /**
     * Returns whether there are any pending mutations that have not yet been committed or aborted.
     *
     * @return {@code true} if at least one mutation is buffered, {@code false} otherwise
     */
    @Override
    public boolean hasChanges() {
        return !pendingMutations.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Buffered mutators — require write transaction, deferred until commit()
    // -------------------------------------------------------------------------

    /**
     * Buffers pushing a new entry onto the top of the undo stack. As a side effect, the redo stack
     * is cleared when the mutation is applied, since a new commit invalidates any undone history.
     *
     * @param entry the changelog entry to push
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void push(ChangeLogEntry entry) {
        checkWriteTransaction();
        pendingMutations.add(
                () -> {
                    undoStack.push(entry);
                    redoStack.clear();
                });
    }

    /**
     * Buffers clearing the entire redo stack. This is typically called before a regular (unnamed)
     * commit to ensure that new changes invalidate the redo history.
     *
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void clearRedo() {
        checkWriteTransaction();
        pendingMutations.add(redoStack::clear);
    }

    /**
     * Buffers moving the top entry of the undo stack to the top of the redo stack. This is the
     * write counterpart to {@link #peekUndo()}: the coordinator peeks to obtain the entry, then
     * calls this method to buffer the actual stack move.
     *
     * <p>If the undo stack is empty when the mutation is applied, a {@link
     * java.util.NoSuchElementException} will be thrown at commit time.
     *
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void moveToRedo() {
        checkWriteTransaction();
        pendingMutations.add(
                () -> {
                    var entry = undoStack.pop();
                    redoStack.push(entry);
                });
    }

    /**
     * Buffers moving the top entry of the redo stack back to the top of the undo stack. This is the
     * write counterpart to {@link #peekRedo()}: the coordinator peeks to obtain the entry, then
     * calls this method to buffer the actual stack move.
     *
     * <p>If the redo stack is empty when the mutation is applied, a {@link
     * java.util.NoSuchElementException} will be thrown at commit time.
     *
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void moveToUndo() {
        checkWriteTransaction();
        pendingMutations.add(
                () -> {
                    var entry = redoStack.pop();
                    undoStack.push(entry);
                });
    }

    /**
     * Buffers restoring the changelog to the entry with the given version ID. All entries newer
     * than the target are moved from the undo stack to the redo stack. If no entry with the given
     * ID exists, the undo stack will be emptied entirely.
     *
     * @param versionId the {@link ChangeLogEntry#getChangeId() change ID} to restore to
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void restore(UUID versionId) {
        checkWriteTransaction();
        pendingMutations.add(
                () -> {
                    while (!undoStack.isEmpty()
                            && !undoStack.peek().getChangeId().equals(versionId)) {
                        redoStack.push(undoStack.pop());
                    }
                });
    }

    /**
     * Buffers replacing the oldest (bottom-most) entry of the undo stack with the given entry. As a
     * side effect, the redo stack is cleared when the mutation is applied. This is used during
     * delta compression to replace the base entry with a compressed version.
     *
     * <p>If the undo stack is empty when the mutation is applied, a {@link
     * java.util.NoSuchElementException} will be thrown at commit time.
     *
     * @param entry the replacement entry
     * @throws GraphNotInATransactionException if no transaction is active
     * @throws GraphNotInAWriteTransactionException if the active transaction is read-only
     */
    public void replaceOldest(ChangeLogEntry entry) {
        checkWriteTransaction();
        pendingMutations.add(
                () -> {
                    undoStack.removeLast();
                    undoStack.addLast(entry);
                    redoStack.clear();
                });
    }

    // -------------------------------------------------------------------------
    // read queries
    // -------------------------------------------------------------------------

    /**
     * Returns the most recent entry on the undo stack without modifying it, or {@code null} if the
     * undo stack is empty. This is the read counterpart to {@link #moveToRedo()}.
     *
     * @return the top undo entry, or {@code null} if the stack is empty
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public ChangeLogEntry peekUndo() {
        checkTransaction();
        return undoStack.peek();
    }

    /**
     * Returns the most recent entry on the redo stack without modifying it, or {@code null} if the
     * redo stack is empty. This is the read counterpart to {@link #moveToUndo()}.
     *
     * @return the top redo entry, or {@code null} if the stack is empty
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public ChangeLogEntry peekRedo() {
        checkTransaction();
        return redoStack.peek();
    }

    /**
     * Returns whether the undo stack contains more than one entry. A single entry represents the
     * initial (base) state and cannot be undone.
     *
     * @return {@code true} if there is at least one entry that can be undone
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public boolean canUndo() {
        checkTransaction();
        return undoStack.size() > 1;
    }

    /**
     * Returns whether the redo stack is non-empty, meaning at least one undone entry can be
     * reapplied.
     *
     * @return {@code true} if there is at least one entry that can be redone
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public boolean canRedo() {
        checkTransaction();
        return !redoStack.isEmpty();
    }

    /**
     * Returns an immutable snapshot of the undo stack, ordered from newest (index 0) to oldest.
     *
     * @return an unmodifiable list of undo entries
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public List<ChangeLogEntry> getUndoHistory() {
        checkTransaction();
        return List.copyOf(undoStack);
    }

    /**
     * Returns an immutable snapshot of the redo stack, ordered from the entry that would be redone
     * next (index 0) to the entry that was undone first (last index).
     *
     * @return an unmodifiable list of redo entries
     * @throws GraphNotInATransactionException if no transaction is active
     */
    public List<ChangeLogEntry> getRedoHistory() {
        checkTransaction();
        return List.copyOf(redoStack);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void checkTransaction() {
        if (!txnContext.isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
    }

    private void checkWriteTransaction() {
        checkTransaction();
        if (txnContext.transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
    }
}
