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

package org.rdfarchitect.rdf.graph.wrapper;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A transaction participant that keeps whole copies of its state instead of deltas.
 *
 * <p>Suitable wherever the data is small enough that a copy costs less than tracking what changed —
 * diagrams, colours, prefixes, the set of graphs. The graphs themselves are the counterexample:
 * their delta chain <em>is</em> their current state, so they cannot work this way.
 *
 * <p>Subclasses only say how to take and put back a state, and call {@link #beginChange()} before
 * every mutation. The first call of a transaction takes the snapshot an abort would return to and
 * enrols the participant, so nothing is copied for a transaction that leaves this state alone.
 *
 * @param <S> the snapshot type; must have value equality, since that is how a no-op change is told
 *     from a real one
 */
public abstract class SnapshotParticipant<S>
        implements TransactionParticipant, ChangeLogParticipant {

    private final WorkspaceTransactionContext txnContext;

    /** Committed states, the current one on top. */
    private final Deque<S> pastStates = new ArrayDeque<>();

    /** States that {@link #undo()} moved out of the way, the next one to redo on top. */
    private final Deque<S> futureStates = new ArrayDeque<>();

    /** State at the first change of the running transaction, or {@code null} outside one. */
    private S preTransactionState;

    /** What the most recent committed version brought into existence. */
    private List<String> lastAdditions = List.of();

    protected SnapshotParticipant(WorkspaceTransactionContext txnContext) {
        this.txnContext = txnContext;
    }

    /**
     * Returns a copy of the current state that later mutations do not affect.
     *
     * @return the snapshot
     */
    protected abstract S snapshot();

    /**
     * Replaces the current state with a previously taken snapshot.
     *
     * @param state the state to restore
     */
    protected abstract void restore(S state);

    /**
     * Names what a state gained, so that the changelog can say what undoing a commit would take
     * away again. Only worth answering where something can disappear that the user would miss — a
     * graph, a diagram. The default reports nothing.
     *
     * @param before the state the commit started from
     * @param after the state it produced
     * @return names of what {@code after} holds and {@code before} did not
     */
    protected List<String> describeAdditions(S before, S after) {
        return List.of();
    }

    /**
     * Returns what the most recent version of this participant brought into existence, and would
     * therefore remove again when undone.
     */
    public List<String> additionsOfLastVersion() {
        return lastAdditions;
    }

    /**
     * Announces a mutation, joining the running write transaction. Callers must invoke this before
     * changing anything, including before handing out a reference the caller may change.
     */
    protected void beginChange() {
        if (!txnContext.isInTransaction() || txnContext.transactionMode() == ReadWrite.READ) {
            return;
        }
        if (preTransactionState == null) {
            preTransactionState = snapshot();
        }
        txnContext.enroll(this);
    }

    @Override
    public void commit() {
        if (preTransactionState == null) {
            return;
        }
        var before = preTransactionState;
        var after = snapshot();
        // The state the transaction started from is the version undo has to be able to return to,
        // and it is only known here — a constructor cannot take it, as the subclass is not built
        // yet at that point.
        if (pastStates.isEmpty()) {
            pastStates.push(before);
        }
        preTransactionState = null;
        pastStates.push(after);
        lastAdditions = describeAdditions(before, after);
    }

    @Override
    public void abort() {
        if (preTransactionState == null) {
            return;
        }
        restore(preTransactionState);
        preTransactionState = null;
    }

    @Override
    public boolean hasChanges() {
        return preTransactionState != null && !preTransactionState.equals(snapshot());
    }

    @Override
    public void undo() {
        if (pastStates.size() < 2) {
            return;
        }
        futureStates.push(pastStates.pop());
        restore(pastStates.peek());
    }

    @Override
    public void redo() {
        if (futureStates.isEmpty()) {
            return;
        }
        pastStates.push(futureStates.pop());
        restore(pastStates.peek());
    }

    @Override
    public void discardOldestVersion() {
        if (pastStates.size() < 2) {
            return;
        }
        pastStates.removeLast();
    }

    @Override
    public void discardRedoHistory() {
        futureStates.clear();
    }
}
