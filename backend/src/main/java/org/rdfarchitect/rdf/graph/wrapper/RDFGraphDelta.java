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
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * A {@link Graph} implementation backed by {@link DeltaCompressible} deltas. Has no lock of its own
 * — transaction lifecycle is managed exclusively by {@link
 * org.rdfarchitect.database.inmemory.GraphWithContextTransactional}. All {@link Graph} methods
 * enforce that the coordinator has an active transaction via the shared {@link TransactionContext}.
 */
public class RDFGraphDelta implements Graph, TransactionParticipant, Rewindable {

    private static final Logger logger = LoggerFactory.getLogger(RDFGraphDelta.class);

    private final TransactionContext txnContext;

    private final Deque<DeltaCompressible> pastDeltas;
    private DeltaCompressible currentDelta;
    private final Deque<DeltaCompressible> futureDeltas;

    private final int maxVersions;
    private final int compressCount;

    public RDFGraphDelta(
            @NotNull Graph base,
            int maxVersions,
            int compressCount,
            TransactionContext txnContext) {
        this.txnContext = txnContext;
        this.maxVersions = maxVersions;
        this.compressCount = compressCount;
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
        return currentDelta.getPrefixMapping();
    }

    // -------------------------------------------------------------------------
    // TransactionParticipant
    // -------------------------------------------------------------------------

    @Override
    public void commit() {
        pastDeltas.push(currentDelta);
        currentDelta = new DeltaCompressible(head());
        futureDeltas.clear();
        if (countVersions() > maxVersions) {
            compressBase();
        }
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
        if (!canUndo()) {
            throw new GraphVersionControlException("Cannot undo: already at the oldest version.");
        }
        futureDeltas.push(pastDeltas.pop());
        currentDelta = new DeltaCompressible(head());
    }

    @Override
    public void redo() {
        if (!canRedo()) {
            throw new GraphVersionControlException("Cannot redo: already at the newest version.");
        }
        pastDeltas.push(futureDeltas.pop());
        currentDelta = new DeltaCompressible(head());
    }

    @Override
    public boolean canUndo() {
        return currentVersion() > 0;
    }

    @Override
    public boolean canRedo() {
        return !futureDeltas.isEmpty();
    }

    @Override
    public void restore(UUID versionId) {
        if (!containsDelta(versionId)) {
            throw new GraphVersionControlException(
                    "Cannot restore to version " + versionId + ": does not exist.");
        }
        while (!pastDeltas.isEmpty() && !pastDeltas.peek().getVersionId().equals(versionId)) {
            pastDeltas.pop();
        }
        currentDelta = new DeltaCompressible(head());
    }

    @Override
    public DeltaCompressible getLastDelta() {
        return pastDeltas.peek();
    }

    @Override
    public boolean hasChanges() {
        return !currentDelta.getAdditions().isEmpty() || !currentDelta.getDeletions().isEmpty();
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

    private int countVersions() {
        return pastDeltas.size() + futureDeltas.size();
    }

    private boolean containsDelta(UUID versionId) {
        return pastDeltas.stream().anyMatch(d -> d.getVersionId().equals(versionId));
    }

    private void compressBase() {
        int deleteCount = Math.min(pastDeltas.size() - 1, compressCount);
        for (int i = 0; i < deleteCount; i++) {
            pastDeltas.removeLast();
        }
        pastDeltas.getLast().compress();
    }
}
