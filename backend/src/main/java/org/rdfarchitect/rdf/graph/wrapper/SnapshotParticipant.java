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
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.models.changelog.CapturedState;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.models.changelog.ValueChange;

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

    /** Which graphs the most recent committed version reached. */
    private List<String> lastAffectedGraphs = List.of();

    /** What the most recent committed version changed, value by value. */
    private List<ValueChange> lastValueChanges = List.of();

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
     * Names the graphs a change reached, so that the changelog can show it under each of them.
     *
     * <p>Only worth answering where a participant the workspace owns is really about individual
     * graphs — the set of graphs is, because creating, deleting and renaming one is the change a
     * graph's own changelog must not be missing. The default reports none.
     *
     * @param before the state the commit started from
     * @param after the state it produced
     * @return the URIs of the graphs the change reached
     */
    protected List<String> describeAffectedGraphs(S before, S after) {
        return List.of();
    }

    /** Returns the graphs the most recent version of this participant reached. */
    public List<String> affectedGraphsOfLastVersion() {
        return lastAffectedGraphs;
    }

    /**
     * Says what a commit changed value by value, so that the changelog can open up a change the
     * user cannot read as triples. Worth answering wherever the state is a handful of named values
     * someone might want to look at — the prefixes, the colours. The default reports nothing, which
     * leaves the change showing in the log with no details to expand.
     *
     * @param before the state the commit started from
     * @param after the state it produced
     * @return what changed between the two
     */
    protected List<ValueChange> describeValueChanges(S before, S after) {
        return List.of();
    }

    /** Returns what the most recent version of this participant changed, value by value. */
    public List<ValueChange> valueChangesOfLastVersion() {
        return lastValueChanges;
    }

    /**
     * Announces a mutation, joining the running write transaction. Callers must invoke this before
     * changing anything, including before handing out a reference the caller may change.
     *
     * <p>Outside a transaction this throws rather than letting the mutation through: it could
     * neither be rolled back nor recorded, so it would be a change the workspace does not know
     * about. In a read transaction it returns quietly instead, because the call also guards
     * references handed to readers, which do not go on to change them.
     *
     * @throws GraphNotInATransactionException if this thread is not inside the owning workspace
     */
    protected void beginChange() {
        if (!txnContext.isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.READ) {
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
        lastAffectedGraphs = describeAffectedGraphs(before, after);
        lastValueChanges = describeValueChanges(before, after);
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
    public CapturedState capture(int versionsBack) {
        var captured = stateAt(versionsBack);
        return () -> {
            beginChange();
            restore(captured);
        };
    }

    /**
     * Returns a committed state without stepping to it. The states are kept whole, so looking one
     * up is a read.
     */
    private S stateAt(int versionsBack) {
        return RetainedVersions.at(pastStates, versionsBack);
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

    @Override
    public void foldLastVersionIntoPrevious() {
        if (pastStates.size() < 2) {
            return;
        }
        var newest = pastStates.pop();
        pastStates.pop();
        pastStates.push(newest);
        lastAdditions = List.of();
        lastAffectedGraphs = List.of();
    }
}
