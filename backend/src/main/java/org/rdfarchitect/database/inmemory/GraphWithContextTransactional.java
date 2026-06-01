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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.exception.graph.GraphNotInATransactionException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.models.changelog.ChangeLog;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.cim.CIMModifyingUtils;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;
import org.rdfarchitect.rdf.graph.wrapper.Rewindable;
import org.rdfarchitect.rdf.graph.wrapper.TransactionContext;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Top-level context object that holds an {@link RDFGraphDelta} and a {@link DiagramLayoutDelta}
 * under a single SWMR lock and a shared {@link TransactionContext}.
 *
 * <p>This is the only authorised entry point for transactions. Callers must call {@link
 * #begin(ReadWrite)} here; the two inner components have no lock of their own and rely on this
 * class to manage synchronisation.
 */
public class GraphWithContextTransactional implements GraphContext {

    private static final Logger logger =
            LoggerFactory.getLogger(GraphWithContextTransactional.class);

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final TransactionContext txnContext = new TransactionContext();

    private final RDFGraphDelta rdfGraph;
    private final DiagramLayoutDelta diagramLayout;
    private final RDFGraphDelta customSHACL;
    private final ChangeLog changeLog = new ChangeLog(txnContext);
    private final ConcurrentHashMap<UUID, CustomDiagram> customDiagrams = new ConcurrentHashMap<>();

    /** The fixed participants that are always part of every transaction. */
    private final List<TransactionParticipant> coreTransactionParticipants;

    private final List<NamedRewindable> coreRewindables;
    private final AtomicInteger stepsSinceNamedCommit = new AtomicInteger(0);

    private record NamedRewindable(String name, Rewindable rewindable) {}

    public GraphWithContextTransactional(Graph base) {
        txnContext.begin(ReadWrite.WRITE);
        int maxVersions = GraphCompressionConfig.getMaxVersions();
        int compressCount = GraphCompressionConfig.getCompressCount();
        GraphUtils.enhanceWithUUIDs(base);
        CIMModifyingUtils.replaceCommentDatatype(base);
        this.rdfGraph =
                new RDFGraphDelta(
                        GraphFactory.createDefaultGraph(), maxVersions, compressCount, txnContext);
        var rdfModel = ModelFactory.createModelForGraph(rdfGraph);
        rdfModel.setNsPrefixes(base.getPrefixMapping());
        rdfModel.add(ModelFactory.createModelForGraph(base));
        this.diagramLayout = new DiagramLayoutDelta(txnContext);
        this.customSHACL =
                new RDFGraphDelta(
                        GraphFactory.createDefaultGraph(), maxVersions, compressCount, txnContext);
        this.coreTransactionParticipants = List.of(rdfGraph, diagramLayout, customSHACL, changeLog);
        this.coreRewindables =
                List.of(
                        new NamedRewindable("rdf", rdfGraph),
                        new NamedRewindable("shacl", customSHACL));
        commit("imported graph");
        txnContext.end();
    }

    // -------------------------------------------------------------------------
    // Helpers — build the effective participant / rewindable lists
    // -------------------------------------------------------------------------

    /**
     * Returns the core transaction participants. Custom diagrams are managed separately since they
     * are not part of the undo/redo history.
     */
    private List<TransactionParticipant> allTransactionParticipants() {
        return coreTransactionParticipants;
    }

    /**
     * Returns the core rewindable components. Custom diagrams are intentionally excluded: their
     * state is not tracked in the changelog and is not undoable.
     */
    private List<NamedRewindable> allRewindables() {
        return coreRewindables;
    }

    // -------------------------------------------------------------------------
    // GraphContext methods
    // -------------------------------------------------------------------------

    @Override
    public RDFGraphDelta getRdfGraph() {
        return rdfGraph;
    }

    @Override
    public DiagramLayoutDelta getDiagramLayout() {
        return diagramLayout;
    }

    @Override
    public RDFGraphDelta getCustomSHACL() {
        return customSHACL;
    }

    @Override
    public ChangeLog getChangeLog() {
        return changeLog;
    }

    @Override
    public Map<UUID, CustomDiagram> getCustomDiagrams() {
        return customDiagrams;
    }

    // -------------------------------------------------------------------------
    // Transactional methods
    // -------------------------------------------------------------------------

    @Override
    public GraphWithContextTransactional begin(ReadWrite mode) {
        if (txnContext.isInTransaction()) {
            throw new GraphTransactionException("A transaction is already active on this thread.");
        }
        txnContext.begin(mode);
        switch (mode) {
            case READ -> rwLock.readLock().lock();
            case WRITE -> rwLock.writeLock().lock();
        }
        if (mode == ReadWrite.WRITE) {
            customDiagrams.values().forEach(CustomDiagram::beginTransaction);
        }
        return this;
    }

    @Override
    public void commit() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Cannot commit a read transaction.");
        }
        GraphUtils.enhanceWithUUIDs(rdfGraph);
        changeLog.clearRedo();
        allTransactionParticipants().forEach(TransactionParticipant::commit);
        customDiagrams.values().forEach(CustomDiagram::commit);
        stepsSinceNamedCommit.incrementAndGet();
        logger.debug("Context committed.");
    }

    @Override
    public void commit(String message) {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Cannot commit a read transaction.");
        }
        GraphUtils.enhanceWithUUIDs(rdfGraph);
        changeLog.clearRedo();

        // Commit graph participants to capture their deltas.
        rdfGraph.commit();
        diagramLayout.commit();
        customSHACL.commit();
        customDiagrams.values().forEach(CustomDiagram::commit);
        stepsSinceNamedCommit.incrementAndGet();
        int steps = stepsSinceNamedCommit.get();
        stepsSinceNamedCommit.set(0);
        var contextDeltas =
                coreRewindables.stream()
                        .map(
                                nr -> {
                                    var delta = nr.rewindable().getLastDelta();
                                    return new ContextDelta(
                                            nr.name(),
                                            new WeakReference<>(delta.getAdditions()),
                                            new WeakReference<>(delta.getDeletions()));
                                })
                        .toList();
        changeLog.push(new ChangeLogEntry(message, steps, contextDeltas));
        changeLog.commit();
        logger.debug("Context committed with message: {}", message);
    }

    private ChangeLogEntry applyHistoryStep(
            String noHistoryMessage,
            String inTransactionMessage,
            java.util.function.BooleanSupplier canApply,
            java.util.function.Supplier<ChangeLogEntry> peekEntry,
            Runnable bufferMove,
            java.util.function.Consumer<Rewindable> action) {
        if (isInTransaction()) {
            throw new GraphTransactionException(inTransactionMessage);
        }
        rwLock.writeLock().lock();
        try {
            txnContext.begin(ReadWrite.WRITE);
            try {
                if (!canApply.getAsBoolean()) {
                    throw new GraphVersionControlException(noHistoryMessage);
                }
                var entry = peekEntry.get();
                bufferMove.run();
                changeLog.commit();
                for (int i = 0; i < entry.getSteps(); i++) {
                    allRewindables().forEach(nr -> action.accept(nr.rewindable()));
                }
                stepsSinceNamedCommit.set(0);
                return entry;
            } finally {
                txnContext.end();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public ChangeLogEntry undo() {
        var entry =
                applyHistoryStep(
                        "Cannot undo: no history available.",
                        "Cannot undo while a transaction is active.",
                        this::canUndoUnchecked,
                        changeLog::peekUndo,
                        changeLog::moveToRedo,
                        Rewindable::undo);
        logger.debug("Context undone.");
        return entry;
    }

    @Override
    public ChangeLogEntry redo() {
        var entry =
                applyHistoryStep(
                        "Cannot redo: no future history available.",
                        "Cannot redo while a transaction is active.",
                        this::canRedoUnchecked,
                        changeLog::peekRedo,
                        changeLog::moveToUndo,
                        Rewindable::redo);
        logger.debug("Context redone.");
        return entry;
    }

    @Override
    public boolean canUndo() {
        return changeLog.canUndo();
    }

    @Override
    public boolean canRedo() {
        return changeLog.canRedo();
    }

    private boolean canUndoUnchecked() {
        return changeLog.canUndo();
    }

    private boolean canRedoUnchecked() {
        return changeLog.canRedo();
    }

    @Override
    public void restoreToVersion(UUID versionId) {
        if (isInTransaction()) {
            throw new GraphTransactionException(
                    "Cannot restore version while a transaction is active.");
        }
        rwLock.writeLock().lock();
        try {
            txnContext.begin(ReadWrite.WRITE);
            try {
                var undoHistory = changeLog.getUndoHistory();
                boolean found =
                        undoHistory.stream()
                                .anyMatch(entry -> entry.getChangeId().equals(versionId));
                if (!found) {
                    throw new GraphVersionControlException(
                            "Version " + versionId + " not found in undo history.");
                }
                while (canUndoUnchecked()) {
                    var top = changeLog.peekUndo();
                    if (top.getChangeId().equals(versionId)) {
                        break;
                    }
                    changeLog.moveToRedo();
                    changeLog.commit();
                    for (int i = 0; i < top.getSteps(); i++) {
                        allRewindables().forEach(nr -> nr.rewindable().undo());
                    }
                }
                stepsSinceNamedCommit.set(0);
                logger.debug("Restored to version {}.", versionId);
            } finally {
                txnContext.end();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void abort() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.READ) {
            throw new GraphTransactionException("Cannot abort a read transaction.");
        }
        allTransactionParticipants().forEach(TransactionParticipant::abort);
        customDiagrams.values().forEach(CustomDiagram::abort);
        logger.debug("Context aborted.");
    }

    @Override
    public void end() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.WRITE
                && !rdfGraph.isClosed()
                && allTransactionParticipants().stream()
                        .anyMatch(TransactionParticipant::hasChanges)) {
            logger.warn("Ending write transaction with uncommitted changes — aborting.");
            allTransactionParticipants().forEach(TransactionParticipant::abort);
            customDiagrams.values().forEach(CustomDiagram::abort);
        }
        var lock =
                txnContext.transactionMode() == ReadWrite.READ
                        ? rwLock.readLock()
                        : rwLock.writeLock();
        lock.unlock();
        txnContext.end();
        logger.debug("Context transaction ended.");
    }

    @Override
    public boolean isInTransaction() {
        return txnContext.isInTransaction();
    }

    @Override
    public ReadWrite transactionMode() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        return txnContext.transactionMode();
    }

    // -------------------------------------------------------------------------
    // AutoClosable method
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        if (isInTransaction()) {
            end();
        }
    }
}
