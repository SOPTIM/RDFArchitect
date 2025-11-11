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

import org.apache.jena.graph.Capabilities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.ClosedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.NotNull;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphNotInAWriteTransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An Implementation of the Graph interface that allows for storing changes and then cycling through them.
 * Perform any amount of update operations on this Graph, then commit to store those changes as a version.
 * Undoing a change and then redoing is possible. However, new changes after an undo will delete all previously following changes.
 * this implementation ensures thread safety by utilising the single-writer multiple-reader principle (SWMR).
 */
public class GraphRewindable implements Graph, Transactional, Rewindable {

    //logger
    protected static final Logger logger = LoggerFactory.getLogger(GraphRewindable.class);

    //thread safety
    protected final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    protected final ThreadLocal<TxnType> transactionType = new ThreadLocal<>();

    //graph storage
    protected final Deque<DeltaCompressible> pastDeltas;

    protected DeltaCompressible currentDelta;

    protected final Deque<DeltaCompressible> futureDeltas;

    //version control
    protected final int maxVersions;

    protected final int compressCount;

    /**
     * Accepts a {@link Graph} that serves as a base version of the {@link GraphRewindableWithUUIDs}.
     *
     * @param base          The base graph
     * @param maxVersions   The maximum amount of versions the graph stores.
     * @param compressCount The amount of versions that are compressed to a new base when compressing.
     */
    public GraphRewindable(@NotNull Graph base, int maxVersions, int compressCount) {
        replaceCommentFormat(base);
        pastDeltas = new ArrayDeque<>();
        pastDeltas.push(new DeltaCompressible(base));
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        futureDeltas = new ArrayDeque<>();
        if (maxVersions < 1) {
            throw new IllegalArgumentException("the maximum amount of Versions must be greater than 1");
        }
        if (maxVersions < compressCount) {
            throw new IllegalArgumentException("The maximum amount of version of a GraphRewindable cannot be smaller than the compress count.");
        }
        if (compressCount < 1) {
            throw new IllegalArgumentException("the maximum amount of versions that would be suppressed at a time cannot be smaller than 1.");
        }
        this.maxVersions = maxVersions;
        this.compressCount = compressCount;
    }

    private void replaceCommentFormat(Graph graph) {
        graph.find(Node.ANY, RDFS.comment.asNode(), Node.ANY).toList().forEach(triple -> {
            var newComment = new RDFSComment(triple.getObject().getLiteralLexicalForm(),
                                             new URI("http://www.w3.org/2001/XMLSchema#string"));
            graph.delete(triple);
            graph.add(triple.getSubject(), RDFS.comment.asNode(), newComment.asTypedLiteral().asNode());
        });
    }

    /**
     * Counts the number of versions of the graph.
     *
     * @return number of versions
     */
    protected int countVersions() {
        return pastDeltas.size() + futureDeltas.size();
    }

    /**
     * @return the currentVersion of the graph
     */
    protected int getCurrentVersion() {
        return pastDeltas.size() - 1;
    }

    /**
     * Checks whether the graph is closed and if so, throws a {@link ClosedException}.
     */
    protected void checkClosed() {
        if (isClosed()) {
            throw new ClosedException("Graph is closed!", this);
        }
    }

    @Override
    public void undo() {
        begin(TxnType.WRITE);
        checkClosed();
        if (getCurrentVersion() == 0) {
            end();
            throw new GraphVersionControlException("Cannot undo last change because this is already the oldest version.");
        }
        futureDeltas.push(pastDeltas.pop());
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        end();
    }

    @Override
    public void redo() {
        begin(TxnType.WRITE);
        checkClosed();
        if (futureDeltas.isEmpty()) {
            end();
            throw new GraphVersionControlException("Cannot redo last change because this is already the newest version.");
        }
        pastDeltas.push(futureDeltas.pop());
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        end();
    }

    @Override
    public boolean canUndo() {
        begin(TxnType.READ);
        try {
            checkClosed();
            return getCurrentVersion() > 0;
        } finally {
            end();
        }
    }

    @Override
    public boolean canRedo() {
        begin(TxnType.READ);
        try {
            checkClosed();
            return !futureDeltas.isEmpty();
        } finally {
            end();
        }
    }

    /**
     * Clears the undo/redo history while keeping the current graph state.
     * Compresses the latest delta into a new base and drops all prior versions.
     */
    public void resetHistory() {
        begin(TxnType.WRITE);
        try {
            checkClosed();
            var latest = pastDeltas.peek();
            if (latest == null) {
                return;
            }
            latest.compress();
            pastDeltas.clear();
            pastDeltas.push(latest);
            currentDelta = new DeltaCompressible(latest);
            futureDeltas.clear();
        } finally {
            end();
        }
    }

    @Override
    public void restore(UUID versionId) {
        begin(TxnType.WRITE);
        try {
            checkClosed();
            if (!containsDelta(versionId, pastDeltas)) {
                throw new GraphVersionControlException("Cannot restore to version " + versionId + " because it does not exist.");
            }
            while (!pastDeltas.isEmpty() && !pastDeltas.peek().getVersionId().equals(versionId)) {
                pastDeltas.pop();
            }
            assert pastDeltas.peek() != null;
            currentDelta = new DeltaCompressible(pastDeltas.peek());
        } finally {
            end();
        }
    }

    /**
     * Checks whether the given versionId is contained in the given deque of deltas.
     *
     * @param versionId The versionId to check for.
     * @param deltas    The deque of deltas to check in.
     *
     * @return True if the versionId is contained, otherwise false.
     */
    private boolean containsDelta(UUID versionId, Deque<DeltaCompressible> deltas) {
        return deltas.stream().anyMatch(d -> d.getVersionId().equals(versionId));
    }

    @Override
    public DeltaCompressible getLastDelta() {
        begin(TxnType.READ);
        try {
            checkClosed();
            return pastDeltas.peek();
        } finally {
            end();
        }
    }

    /**
     * Compresses the {@code versionConfig.compressCount} oldest versions to a new base graph.
     */
    protected void compressBase() {
        int deleteVersionCount = Math.min(pastDeltas.size() - 1, compressCount);
        for (int i = 0; i < deleteVersionCount; i++) {
            pastDeltas.removeLast();
        }
        pastDeltas.getLast().compress();
    }

    /**
     * @return True if the current graph is the same as the last version, otherwise false.
     */
    protected boolean noChangesInTransaction() {
        return currentDelta.getAdditions().isEmpty() && currentDelta.getDeletions().isEmpty();
    }

    //Graph
    @Override
    public boolean dependsOn(Graph other) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.dependsOn(other);
    }

    @Override
    public TransactionHandler getTransactionHandler() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.getTransactionHandler();
    }

    @Override
    public Capabilities getCapabilities() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.getCapabilities();
    }

    @Override
    public GraphEventManager getEventManager() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.getEventManager();
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.getPrefixMapping();
    }

    @Override
    public void add(Triple t) throws AddDeniedException {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        currentDelta.add(t);
    }

    @Override
    public void delete(Triple t) throws DeleteDeniedException {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        currentDelta.delete(t);
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.find(m);
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        return find(Triple.create(s, p, o));
    }

    @Override
    public boolean isIsomorphicWith(Graph g) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.isIsomorphicWith(g);
    }

    @Override
    public boolean contains(Node s, Node p, Node o) {
        return contains(Triple.create(s, p, o));
    }

    @Override
    public boolean contains(Triple t) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.contains(t);
    }

    @Override
    public void clear() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        currentDelta.clear();
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        currentDelta.remove(s, p, o);
    }

    @Override
    public void close() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphNotInAWriteTransactionException();
        }
        if (!futureDeltas.isEmpty()) {
            futureDeltas.peekFirst().close();
        }
        currentDelta.close();
    }

    @Override
    public boolean isEmpty() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.isEmpty();
    }

    @Override
    public int size() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.size();
    }

    @Override
    public boolean isClosed() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return currentDelta.isClosed();
    }

    // Transactional
    @Override
    public void begin(TxnType txnType) {
        if (isInTransaction()) {
            throw new GraphTransactionException("Access to GraphRewindable denied, because there already is an ongoing transaction on this thread.");
        }
        transactionType.set(txnType);
        if (txnType == TxnType.READ || txnType == TxnType.READ_COMMITTED_PROMOTE || txnType == TxnType.READ_PROMOTE) {
            rwLock.readLock().lock();
        } else if (txnType == TxnType.WRITE) {
            rwLock.writeLock().lock();
        }
    }

    @Override
    public boolean promote(Promote promote) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        switch (transactionType()) {
            case READ -> throw new GraphTransactionException("Cannot promote a read transaction!");
            case WRITE -> {
                return true;
            }
            case READ_COMMITTED_PROMOTE -> {
                end();
                begin(TxnType.WRITE);
                return true;
            }
            case READ_PROMOTE -> {
                if (rwLock.getReadLockCount() == 1) {
                    rwLock.readLock().unlock();
                    if (rwLock.writeLock().tryLock()) {
                        transactionType.set(TxnType.WRITE);
                        return true;
                    }
                    rwLock.readLock().lock();
                }
            }
        }
        return false;
    }

    @Override
    public void commit() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Trying to commit a read transaction!");
        }
        if (noChangesInTransaction()) {
            logger.debug("Commiting a transaction with no changes.");
            return;
        }
        pastDeltas.push(currentDelta);
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        futureDeltas.clear();
        if (countVersions() > maxVersions) {
            compressBase();
        }
        logger.debug("Committed transaction.");
    }

    @Override
    public void abort() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Trying to abort a read transaction!");
        }
        if (noChangesInTransaction()) {
            logger.debug("Aborting a transaction with no changes.");
            return;
        }
        assert pastDeltas.peek() != null;
        currentDelta = new DeltaCompressible(pastDeltas.peek());
        logger.debug("Aborted transaction.");
    }

    @Override
    public void end() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (transactionMode() == ReadWrite.WRITE && (!isClosed() && !noChangesInTransaction())) { //abort changes if they are not commited
            abort();
        }
        var lock = transactionMode() == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
        lock.unlock();

        transactionType.remove();
        logger.debug("Ended Transaction.");
    }

    @Override
    public ReadWrite transactionMode() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return switch (transactionType()) {
            case WRITE -> ReadWrite.WRITE;
            case READ, READ_PROMOTE, READ_COMMITTED_PROMOTE -> ReadWrite.READ;
        };
    }

    @Override
    public TxnType transactionType() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return transactionType.get();
    }

    @Override
    public boolean isInTransaction() {
        return transactionType.get() != null;
    }
}
