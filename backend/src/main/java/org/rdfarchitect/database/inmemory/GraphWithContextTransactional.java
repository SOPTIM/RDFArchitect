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
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
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
import org.rdfarchitect.rdf.graph.wrapper.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private final ChangeLog changeLog = new ChangeLog(txnContext);
    private final ConcurrentHashMap<UUID, CustomDiagram> customDiagrams = new ConcurrentHashMap<>();

    /**
     * Every document this context keeps a history for: the ones that exist now, and the ones an
     * undo or redo can bring back. Which of them exist is {@link #documentIds}.
     */
    private final Map<UUID, RegisteredDocument> shapesDocuments = new LinkedHashMap<>();

    /**
     * The ids of the documents that exist, in list order.
     *
     * <p>Versioned like any participant, so creating and deleting a document is undone and redone
     * with everything else. A removed document therefore keeps its participants until no reachable
     * version lists it any more — see {@link #pruneUnreachableDocuments()}.
     */
    private final VersionedValue<List<UUID>> documentIds;

    /** Minted by every commit that changes a document; see {@link #getShapesDocumentsVersion()}. */
    private final VersionedValue<UUID> shapesVersion;

    /** Guards {@link #shapesDocuments}. */
    private final Object shapesDocumentsLock = new Object();

    /**
     * Graphs taking part in every transaction: the schema graph plus one per shapes document.
     *
     * <p>Copy-on-write because documents can be added and removed while the context is alive, and
     * the lists are iterated during commit, abort and undo.
     */
    private final List<RDFGraphDelta> graphParticipants = new CopyOnWriteArrayList<>();

    /**
     * Values committed and rewound alongside {@link #graphParticipants}: which documents exist, and
     * each document's text and metadata.
     */
    private final List<VersionedValue<?>> valueParticipants = new CopyOnWriteArrayList<>();

    private final List<NamedRewindable> coreRewindables = new CopyOnWriteArrayList<>();
    private final AtomicInteger stepsSinceNamedCommit = new AtomicInteger(0);

    /** The name is read at commit time, so a renamed document is labelled by its new name. */
    private record NamedRewindable(Supplier<String> name, Rewindable rewindable) {}

    private record RegisteredDocument(
            ShapesDocument document, VersionedValue<ShapesDocument.State> state) {}

    public GraphWithContextTransactional(Graph base) {
        this(base, List.of());
    }

    /**
     * Creates the context and seeds its shapes documents from {@code shapesSeeds}.
     *
     * <p>Used when loading a snapshot, which carries a graph's constraints alongside its schema.
     * Seeding inside the initial transaction keeps the load a single {@code imported graph} commit
     * — writing the shapes afterwards would leave the user an undo step for content they never
     * authored in this session.
     *
     * @param shapesSeeds the documents to restore; empty for a graph without constraints
     */
    public GraphWithContextTransactional(Graph base, List<ShapesDocumentSeed> shapesSeeds) {
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
        this.documentIds = new VersionedValue<>(List.of(), maxVersions, compressCount);
        this.shapesVersion = new VersionedValue<>(UUID.randomUUID(), maxVersions, compressCount);
        this.graphParticipants.add(rdfGraph);
        this.valueParticipants.add(documentIds);
        this.valueParticipants.add(shapesVersion);
        this.coreRewindables.add(new NamedRewindable(() -> "rdf", rdfGraph));
        // Created up front, exactly as the single shapes graph used to be, so that reading a
        // graph's SHACL never has the side effect of adding a transaction participant.
        addShapesDocument(
                DEFAULT_SHAPES_DOCUMENT_ID,
                DEFAULT_SHAPES_DOCUMENT_NAME,
                ShapesDocument.Origin.AUTHORED);
        shapesSeeds.stream()
                .sorted(Comparator.comparingInt(ShapesDocumentSeed::order))
                .forEach(this::restoreShapesDocument);
        commit("imported graph");
        txnContext.end();
    }

    // -------------------------------------------------------------------------
    // Helpers — build the effective participant / rewindable lists
    // -------------------------------------------------------------------------

    private List<TransactionParticipant> allParticipants() {
        var all = new ArrayList<TransactionParticipant>(graphParticipants);
        all.addAll(valueParticipants);
        all.add(diagramLayout);
        all.add(changeLog);
        return all;
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
    public UUID getRdfGraphVersion() {
        return rdfGraph.contentVersionId();
    }

    @Override
    public UUID getShapesDocumentsVersion() {
        return shapesVersion.get();
    }

    @Override
    public DiagramLayoutDelta getDiagramLayout() {
        return diagramLayout;
    }

    @Override
    public Map<UUID, ShapesDocument> getShapesDocuments() {
        synchronized (shapesDocumentsLock) {
            var existing = new LinkedHashMap<UUID, ShapesDocument>();
            for (var id : documentIds.get()) {
                existing.put(id, shapesDocuments.get(id).document());
            }
            return Collections.unmodifiableMap(existing);
        }
    }

    @Override
    public RDFGraphDelta getCustomSHACL() {
        synchronized (shapesDocumentsLock) {
            return shapesDocuments.get(DEFAULT_SHAPES_DOCUMENT_ID).document().getGraph();
        }
    }

    /**
     * Puts a document back as storage described it, reusing its id so links to it survive a reload.
     *
     * <p>The default document already exists, so a seed for it fills the existing one instead of
     * adding a second.
     */
    private void restoreShapesDocument(ShapesDocumentSeed seed) {
        var document =
                DEFAULT_SHAPES_DOCUMENT_ID.equals(seed.id())
                        ? shapesDocuments.get(DEFAULT_SHAPES_DOCUMENT_ID).document()
                        : addShapesDocument(seed.id(), seed.name(), seed.origin());
        document.setName(seed.name());
        document.setSourceFileName(seed.sourceFileName());
        document.setEnabled(seed.enabled());
        document.setOrder(seed.order());
        document.setRawText(seed.rawText());

        var model = ModelFactory.createModelForGraph(document.getGraph());
        model.setNsPrefixes(seed.graph().getPrefixMapping());
        model.add(ModelFactory.createModelForGraph(seed.graph()));
    }

    @Override
    public ShapesDocument createShapesDocument(String name, ShapesDocument.Origin origin) {
        return addShapesDocument(UUID.randomUUID(), name, origin);
    }

    private ShapesDocument addShapesDocument(UUID id, String name, ShapesDocument.Origin origin) {
        int maxVersions = GraphCompressionConfig.getMaxVersions();
        int compressCount = GraphCompressionConfig.getCompressCount();
        var graph =
                new RDFGraphDelta(
                        GraphFactory.createDefaultGraph(), maxVersions, compressCount, txnContext);
        var state =
                new VersionedValue<>(
                        new ShapesDocument.State(name, null, true, 0, null),
                        maxVersions,
                        compressCount);
        // A document created part-way through a session starts with no history, but the context
        // undoes every participant the same number of times. Align it with the schema graph so an
        // undo reaching past its creation does not run it off the end of its history.
        graph.padHistory(rdfGraph.currentVersion());
        state.padHistory(rdfGraph.currentVersion());

        var document = new ShapesDocument(id, origin, graph, state);
        synchronized (shapesDocumentsLock) {
            // One past the highest position held, rather than the count: removing a document
            // leaves a gap in the numbering, so counting would hand the new document a position an
            // existing one already holds — and every reader that sorts by it would fall back to
            // the map's insertion order for the tie, ignoring the arrangement the user chose.
            document.setOrder(nextOrder());
            shapesDocuments.put(id, new RegisteredDocument(document, state));
            var ids = new ArrayList<>(documentIds.get());
            ids.add(id);
            documentIds.set(List.copyOf(ids));
        }
        graphParticipants.add(graph);
        valueParticipants.add(state);
        // The changelog surfaces this name to the user, so it stays "shacl" for the default
        // document — the label the changelog has always shown — and is qualified only when there is
        // more than one document to tell apart.
        Supplier<String> contextName =
                DEFAULT_SHAPES_DOCUMENT_ID.equals(id)
                        ? () -> "shacl"
                        : () -> "shacl:" + document.getName();
        coreRewindables.add(new NamedRewindable(contextName, graph));
        return document;
    }

    /** The position that puts a document last. Callers hold {@link #shapesDocumentsLock}. */
    private int nextOrder() {
        return documentIds.get().stream()
                        .map(shapesDocuments::get)
                        .mapToInt(registered -> registered.document().getOrder())
                        .max()
                        .orElse(-1)
                + 1;
    }

    @Override
    public void removeShapesDocument(UUID documentId) {
        if (DEFAULT_SHAPES_DOCUMENT_ID.equals(documentId)) {
            throw new IllegalArgumentException(
                    "The default shapes document cannot be removed; clear its content instead.");
        }
        synchronized (shapesDocumentsLock) {
            var ids = new ArrayList<>(documentIds.get());
            if (ids.remove(documentId)) {
                documentIds.set(List.copyOf(ids));
            }
        }
    }

    /**
     * Stops documents that no reachable version lists from taking part in transactions.
     *
     * <p>A removed document has to keep committing while an undo can still bring it back, and a
     * document created in a transaction that was then aborted has to go at once — it never existed
     * in any committed version, and it has no redo history to keep in step with the others.
     */
    private void pruneUnreachableDocuments() {
        synchronized (shapesDocumentsLock) {
            var reachable =
                    documentIds.reachableValues().flatMap(List::stream).collect(Collectors.toSet());
            var iterator = shapesDocuments.values().iterator();
            while (iterator.hasNext()) {
                var registered = iterator.next();
                if (reachable.contains(registered.document().getId())) {
                    continue;
                }
                iterator.remove();
                var graph = registered.document().getGraph();
                graphParticipants.remove(graph);
                valueParticipants.remove(registered.state());
                coreRewindables.removeIf(nr -> nr.rewindable() == graph);
            }
        }
    }

    /** Whether the running transaction changed any document — its triples, text, or existence. */
    private boolean shapesDocumentsChanged() {
        synchronized (shapesDocumentsLock) {
            return documentIds.hasChanges()
                    || shapesDocuments.values().stream()
                            .anyMatch(
                                    registered ->
                                            registered.document().getGraph().hasChanges()
                                                    || registered.state().hasChanges());
        }
    }

    private void markShapesDocumentsChange() {
        if (shapesDocumentsChanged()) {
            shapesVersion.set(UUID.randomUUID());
        }
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

        var lock = mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
        try {
            var timeoutLength = GraphCompressionConfig.getLockTimeoutSeconds();
            if (!lock.tryLock(timeoutLength, TimeUnit.SECONDS)) {
                throw new GraphTransactionException(
                        "Timeout: could not acquire lock within %s second."
                                .formatted(timeoutLength));
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new GraphTransactionException("Interrupted while waiting for lock.");
        }

        txnContext.begin(mode);
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
        markShapesDocumentsChange();
        if (graphParticipants.stream().anyMatch(TransactionParticipant::hasChanges)
                || valueParticipants.stream().anyMatch(TransactionParticipant::hasChanges)) {
            graphParticipants.forEach(TransactionParticipant::commit);
            valueParticipants.forEach(TransactionParticipant::commit);
            stepsSinceNamedCommit.incrementAndGet();
        }
        diagramLayout.commit();
        changeLog.commit();
        customDiagrams.values().forEach(CustomDiagram::commit);
        pruneUnreachableDocuments();
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

        markShapesDocumentsChange();
        // Commit graph participants to capture their deltas. Every participant commits, whether or
        // not it changed, so their version counters stay aligned for undo.
        graphParticipants.forEach(RDFGraphDelta::commit);
        valueParticipants.forEach(TransactionParticipant::commit);
        // Before the entry is written, so it does not name documents the commit just discarded.
        pruneUnreachableDocuments();
        diagramLayout.commit();
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
                                            nr.name().get(),
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
            Runnable step) {
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
                    step.run();
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
                        this::undoStep);
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
                        this::redoStep);
        logger.debug("Context redone.");
        return entry;
    }

    /** Moves every versioned participant one version back, keeping them in step. */
    private void undoStep() {
        allRewindables().forEach(nr -> nr.rewindable().undo());
        valueParticipants.forEach(VersionedValue::undo);
    }

    private void redoStep() {
        allRewindables().forEach(nr -> nr.rewindable().redo());
        valueParticipants.forEach(VersionedValue::redo);
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
                        undoStep();
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
        allParticipants().forEach(TransactionParticipant::abort);
        customDiagrams.values().forEach(CustomDiagram::abort);
        pruneUnreachableDocuments();
        logger.debug("Context aborted.");
    }

    @Override
    public void end() {
        if (!isInTransaction()) {
            throw new GraphNotInATransactionException();
        }
        if (txnContext.transactionMode() == ReadWrite.WRITE
                && !rdfGraph.isClosed()
                && allParticipants().stream().anyMatch(TransactionParticipant::hasChanges)) {
            logger.warn("Ending write transaction with uncommitted changes — aborting.");
            allParticipants().forEach(TransactionParticipant::abort);
            customDiagrams.values().forEach(CustomDiagram::abort);
            pruneUnreachableDocuments();
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
