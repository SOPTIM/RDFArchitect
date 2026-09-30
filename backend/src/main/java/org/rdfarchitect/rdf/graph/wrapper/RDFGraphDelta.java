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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link Graph} implementation backed by {@link DeltaCompressible} deltas. Has no lock of its own
 * — transaction lifecycle is managed exclusively by {@link
 * org.rdfarchitect.database.inmemory.GraphWithContext}. All {@link Graph} methods enforce that the
 * coordinator has an active transaction via the shared {@link TransactionContext}.
 */
public class RDFGraphDelta
        implements Graph, TransactionParticipant, DeltaSource, ChangeLogParticipant {

    private static final Logger logger = LoggerFactory.getLogger(RDFGraphDelta.class);

    private final WorkspaceTransactionContext txnContext;

    /**
     * What to enrol when this graph is written to. A graph that is the inner store of a larger
     * participant enrols that participant instead of itself, so that a changelog entry names the
     * thing the user changed rather than its implementation.
     */
    private final TransactionParticipant enrolAs;

    private final Deque<DeltaCompressible> pastDeltas;
    private DeltaCompressible currentDelta;
    private final Deque<DeltaCompressible> futureDeltas;

    public RDFGraphDelta(@NotNull Graph base, WorkspaceTransactionContext txnContext) {
        this(base, txnContext, null);
    }

    /**
     * Creates a graph that enrols {@code enrolAs} rather than itself when written to.
     *
     * @param base the initial contents
     * @param txnContext the transaction context of the owning workspace
     * @param enrolAs the participant to enrol on a write, or {@code null} for this graph itself
     */
    public RDFGraphDelta(
            @NotNull Graph base,
            WorkspaceTransactionContext txnContext,
            TransactionParticipant enrolAs) {
        this.txnContext = txnContext;
        this.enrolAs = enrolAs != null ? enrolAs : this;
        pastDeltas = new ArrayDeque<>();
        pastDeltas.push(new DeltaCompressible(base));
        currentDelta = new DeltaCompressible(head());
        futureDeltas = new ArrayDeque<>();
    }

    // -------------------------------------------------------------------------
    // Graph interface — all check transaction state via txnContext
    // -------------------------------------------------------------------------

    @Override
    public void add(Triple t) throws AddDeniedException {
        checkWriteTransaction();
        currentDelta.add(t);
    }

    @Override
    public void delete(Triple t) throws DeleteDeniedException {
        checkWriteTransaction();
        currentDelta.delete(t);
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        checkTransaction();
        return currentDelta.find(m);
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        return find(
                Triple.create(
                        s != null ? s : Node.ANY,
                        p != null ? p : Node.ANY,
                        o != null ? o : Node.ANY));
    }

    @Override
    public boolean contains(Triple t) {
        checkTransaction();
        return currentDelta.contains(t);
    }

    @Override
    public boolean contains(Node s, Node p, Node o) {
        return contains(Triple.create(s, p, o));
    }

    @Override
    public boolean isIsomorphicWith(Graph g) {
        checkTransaction();
        return currentDelta.isIsomorphicWith(g);
    }

    @Override
    public void clear() {
        checkWriteTransaction();
        currentDelta.clear();
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        checkWriteTransaction();
        currentDelta.remove(s, p, o);
    }

    @Override
    public void close() {
        checkWriteTransaction();
        if (!futureDeltas.isEmpty()) {
            futureDeltas.peekFirst().close();
        }
        currentDelta.close();
    }

    @Override
    public boolean isClosed() {
        checkTransaction();
        return currentDelta.isClosed();
    }

    @Override
    public boolean isEmpty() {
        checkTransaction();
        return currentDelta.isEmpty();
    }

    @Override
    public int size() {
        checkTransaction();
        return currentDelta.size();
    }

    @Override
    public TransactionHandler getTransactionHandler() {
        checkTransaction();
        return currentDelta.getTransactionHandler();
    }

    @Override
    public GraphEventManager getEventManager() {
        checkTransaction();
        return currentDelta.getEventManager();
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        checkTransaction();
        if (txnContext.transactionMode() != ReadWrite.READ) {
            // A write through the mapping we hand out never reaches add() or delete(), so it
            // has to be enrolled here. The commit drops enrolments that changed nothing.
            txnContext.enroll(enrolAs);
        }
        return currentDelta.getPrefixMapping();
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    @Override
    public void commit() {
        pastDeltas.push(currentDelta);
        currentDelta = new DeltaCompressible(head());
        logger.debug("Committed transaction.");
    }

    @Override
    public void abort() {
        if (!hasChanges()) {
            logger.debug("Aborting a transaction with no changes.");
            return;
        }
        currentDelta = new DeltaCompressible(head());
        logger.debug("Aborted transaction.");
    }

    @Override
    public void undo() {
        if (currentVersion() == 0) {
            throw new GraphVersionControlException("Cannot undo: already at the oldest version.");
        }
        futureDeltas.push(pastDeltas.pop());
        currentDelta = new DeltaCompressible(head());
    }

    @Override
    public void redo() {
        if (futureDeltas.isEmpty()) {
            throw new GraphVersionControlException("Cannot redo: already at the newest version.");
        }
        pastDeltas.push(futureDeltas.pop());
        currentDelta = new DeltaCompressible(head());
    }

    @Override
    public DeltaCompressible getLastDelta() {
        return pastDeltas.peek();
    }

    @Override
    public boolean hasChanges() {
        return !currentDelta.getAdditions().isEmpty()
                || !currentDelta.getDeletions().isEmpty()
                || currentDelta.hasPrefixChanges();
    }

    // -------------------------------------------------------------------------
    // Internals
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
        txnContext.enroll(enrolAs);
    }

    private DeltaCompressible head() {
        var head = pastDeltas.peek();
        if (head == null) {
            throw new IllegalStateException("Delta stack is empty.");
        }
        return head;
    }

    private int currentVersion() {
        return pastDeltas.size() - 1;
    }

    @Override
    public void discardOldestVersion() {
        if (pastDeltas.size() < 2) {
            return;
        }
        pastDeltas.removeLast();
        pastDeltas.getLast().compress();
    }

    @Override
    public void discardRedoHistory() {
        futureDeltas.clear();
    }
}
