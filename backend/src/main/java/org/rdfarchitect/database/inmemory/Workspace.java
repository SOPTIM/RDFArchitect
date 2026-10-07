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

import lombok.Getter;
import lombok.Setter;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagramCollection;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.models.changelog.CapturedState;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.models.changelog.ParticipantId;
import org.rdfarchitect.models.changelog.ParticipantVersion;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.WorkspaceChangeLog;
import org.rdfarchitect.models.changelog.WorkspaceChangeLog.HistorySince;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.changelog.WorkspaceHistoryStep;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.RDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * A workspace: a set of named graphs with a shared prefix mapping, its own custom diagrams and
 * layout, and the transaction and changelog that all of them take part in.
 *
 * <p>The transaction belongs here rather than to the individual graph, so that a change touching
 * several graphs commits or rolls back as a whole. One lock guards the whole workspace, following
 * the single-writer multiple-reader principle; nested transactions join the running one and only
 * the outermost block takes the lock.
 */
public class Workspace {

    private static final Logger logger = LoggerFactory.getLogger(Workspace.class);

    private static final String DEFAULT_GRAPH_NAME = "default";
    private static final String INITIAL_CHANGE_MESSAGE = "loaded workspace";

    @Setter @Getter private volatile boolean isReadOnly = true;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private final WorkspaceTransactionContext txnContext;

    private final WorkspaceChangeLog changeLog;

    private final TransactionCoordinator coordinator;

    private final GraphCollection graphs;

    private final CustomDiagramCollection customDiagrams;

    private final DiagramLayoutDelta diagramLayout;

    private final CrossProfileDiagramInfo crossProfileDiagramInfo;

    private final WorkspacePrefixes prefixes;

    public Workspace(String workspaceName) {
        this.txnContext = new WorkspaceTransactionContext(workspaceName);
        this.changeLog =
                new WorkspaceChangeLog(
                        INITIAL_CHANGE_MESSAGE, GraphCompressionConfig.getMaxVersions());
        this.coordinator = new TransactionCoordinator(txnContext, changeLog, this::identify);
        this.graphs = new GraphCollection(txnContext);
        this.customDiagrams = new CustomDiagramCollection(txnContext);
        this.diagramLayout = new DiagramLayoutDelta(txnContext);
        this.crossProfileDiagramInfo = new CrossProfileDiagramInfo(txnContext);
        this.prefixes = new WorkspacePrefixes(txnContext);
    }

    /**
     * Builds a workspace from a Jena dataset, taking over its prefixes and every one of its graphs.
     *
     * @param workspaceName the name the workspace is known by
     * @param dataset the dataset to read
     */
    public Workspace(String workspaceName, Dataset dataset) {
        this(workspaceName);
        try (var transaction = begin(ReadWrite.WRITE)) {
            this.prefixes.addAll(dataset.getPrefixMapping());
            if (!dataset.getDefaultModel().isEmpty()) {
                graphs.put(
                        DEFAULT_GRAPH_NAME,
                        new GraphWithContext(dataset.getDefaultModel().getGraph(), txnContext));
            }
            for (Iterator<Resource> it = dataset.listModelNames(); it.hasNext(); ) {
                var graphURI = it.next().getURI();
                graphs.put(
                        graphURI,
                        new GraphWithContext(
                                dataset.getNamedModel(graphURI).getGraph(), txnContext));
            }
            transaction.commit(INITIAL_CHANGE_MESSAGE);
        }
        // The load itself is not something the user can undo; it is the state they started from.
        changeLog.forgetHistory(INITIAL_CHANGE_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    /**
     * Begins a transaction on this workspace, or joins the one this thread already holds on it.
     *
     * @param mode the transaction mode
     * @return the transaction, to be used in try-with-resources
     */
    public WorkspaceTransaction begin(ReadWrite mode) {
        if (txnContext.begin(mode)) {
            acquireLock(mode);
            txnContext.onOutermostBegun(() -> abandonStaleTransaction(mode));
        }
        return new Transaction();
    }

    /**
     * Ends a transaction whose caller never closed it, so that the thread running it can be used
     * again: whatever it changed is rolled back and the lock is given back.
     *
     * <p>Rolling back can only fail on a workspace that is already damaged, and the lock has to be
     * released either way — otherwise the workspace stays locked for good — so a failure here is
     * logged rather than thrown.
     */
    private void abandonStaleTransaction(ReadWrite mode) {
        try {
            if (mode == ReadWrite.WRITE && !txnContext.isAborted()) {
                coordinator.abort();
            }
        } catch (RuntimeException e) {
            logger.error("Could not roll back an abandoned transaction.", e);
        } finally {
            var lock = mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
            lock.unlock();
        }
    }

    private void acquireLock(ReadWrite mode) {
        var lock = mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
        var timeoutLength = GraphCompressionConfig.getLockTimeoutSeconds();
        try {
            if (!lock.tryLock(timeoutLength, TimeUnit.SECONDS)) {
                txnContext.end();
                throw new GraphTransactionException(
                        "Timeout: could not acquire lock within %s second."
                                .formatted(timeoutLength));
            }
        } catch (InterruptedException _) {
            txnContext.end();
            Thread.currentThread().interrupt();
            throw new GraphTransactionException("Interrupted while waiting for lock.");
        }
    }

    /** A running transaction on this workspace. */
    private final class Transaction implements WorkspaceTransaction {

        private boolean closed;

        @Override
        public GraphContext graph(String graphUri) {
            return getGraphWithContext(graphUri);
        }

        @Override
        public List<String> graphUris() {
            return graphs.uris();
        }

        @Override
        public Map<UUID, CustomDiagram> diagrams() {
            return customDiagrams.get();
        }

        @Override
        public DiagramLayoutDelta layout() {
            return diagramLayout;
        }

        @Override
        public CrossProfileDiagramInfo crossProfileInfo() {
            return crossProfileDiagramInfo;
        }

        @Override
        public PrefixMapping prefixes() {
            return prefixes.readOnlyView();
        }

        @Override
        public void createGraph(String graphUri, Graph graph) {
            Workspace.this.createGraph(graphUri, graph);
        }

        @Override
        public void deleteGraph(String graphUri) {
            Workspace.this.deleteGraph(graphUri);
        }

        @Override
        public void renameGraph(String oldGraphUri, String newGraphUri) {
            Workspace.this.renameGraph(oldGraphUri, newGraphUri);
        }

        @Override
        public void setPrefixes(PrefixMapping prefixMapping) {
            prefixes.setAll(prefixMapping);
        }

        @Override
        public ReadWrite mode() {
            return txnContext.transactionMode();
        }

        @Override
        public void commit(String message) {
            coordinator.commit(message);
        }

        @Override
        public void commitWithoutHistory() {
            coordinator.commitWithoutHistory();
        }

        @Override
        public void abort() {
            coordinator.abort();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            doEnd();
        }
    }

    // -------------------------------------------------------------------------
    // Commit, abort, end
    // -------------------------------------------------------------------------

    private void doEnd() {
        var mode = txnContext.transactionMode();
        try {
            if (txnContext.isOutermost() && mode == ReadWrite.WRITE && !txnContext.isAborted()) {
                coordinator.finishOutermostWrite();
            }
        } finally {
            if (txnContext.end()) {
                var lock = mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
                lock.unlock();
            }
        }
    }

    /**
     * Works out what a participant represents by looking it up among the workspace's graphs. Done
     * on demand rather than remembered, so that a renamed graph does not leave a stale identifier
     * behind.
     */
    private ParticipantId identify(TransactionParticipant participant) {
        var id = identifyOrNull(participant);
        if (id == null) {
            throw new IllegalStateException(
                    "A participant enrolled that does not belong to this workspace: "
                            + participant);
        }
        return id;
    }

    /**
     * Works out what a participant represents, or {@code null} if the workspace no longer holds it.
     *
     * <p>Unknown is a real answer when a recorded change is being read rather than written: the
     * graph a change belongs to may have been deleted since, and the entry still has to be
     * readable.
     */
    private ParticipantId identifyOrNull(Object participant) {
        for (var entry : graphs.view().entrySet()) {
            var kind = kindWithin(entry.getValue(), participant);
            if (kind != null) {
                return ParticipantId.ofGraph(kind, entry.getKey());
            }
        }
        if (participant == graphs) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.GRAPHS);
        }
        if (participant == customDiagrams) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.DIAGRAMS);
        }
        if (participant == crossProfileDiagramInfo) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.COLORS);
        }
        if (participant == prefixes) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.PREFIXES);
        }
        if (participant == diagramLayout) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.DL);
        }
        return null;
    }

    private static ParticipantId.Kind kindWithin(GraphWithContext graph, Object participant) {
        if (participant == graph.getRdfGraph()) {
            return ParticipantId.Kind.RDF;
        }
        if (participant == graph.getCustomSHACL()) {
            return ParticipantId.Kind.SHACL;
        }
        if (participant == graph.getDiagramLayout()) {
            return ParticipantId.Kind.DL;
        }
        if (participant == graph.customDiagrams()) {
            return ParticipantId.Kind.DIAGRAMS;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Version control
    // -------------------------------------------------------------------------

    /** Returns whether the workspace has a change that can be undone. */
    public boolean canUndo() {
        try (var transaction = begin(ReadWrite.READ)) {
            return changeLog.canUndo();
        }
    }

    /**
     * Returns the change the next undo would take back, or {@code null} if there is none.
     *
     * @return the pending change
     */
    public WorkspaceChangeLogEntry pendingUndo() {
        try (var transaction = begin(ReadWrite.READ)) {
            var pending = changeLog.pendingUndo();
            return pending == null ? null : asCurrentlyIdentified(pending);
        }
    }

    /** Returns whether the workspace has an undone change that can be reapplied. */
    public boolean canRedo() {
        try (var transaction = begin(ReadWrite.READ)) {
            return changeLog.canRedo();
        }
    }

    /**
     * Rolls back the most recent change anywhere in the workspace.
     *
     * @return what was undone and what is left to undo or redo
     */
    public WorkspaceHistoryStep undo() {
        try (var transaction = begin(ReadWrite.WRITE)) {
            return stepOf(changeLog.undo());
        }
    }

    /**
     * Reapplies the most recently undone change.
     *
     * @return what was redone and what is left to undo or redo
     */
    public WorkspaceHistoryStep redo() {
        try (var transaction = begin(ReadWrite.WRITE)) {
            return stepOf(changeLog.redo());
        }
    }

    private WorkspaceHistoryStep stepOf(WorkspaceChangeLogEntry change) {
        return new WorkspaceHistoryStep(
                asCurrentlyIdentified(change), changeLog.canUndo(), changeLog.canRedo());
    }

    /**
     * Renders an entry with its participants identified as they stand now rather than as they stood
     * when the commit was recorded.
     *
     * <p>A graph that has been renamed since would otherwise keep every change ever made to it
     * under the name it used to have, and its changelog would look as though it only began at the
     * rename. The entry holds the participants themselves, so where they belong now is a question
     * that can still be answered — except for a graph deleted since, where the recorded identifier
     * is the best there is.
     *
     * <p>What a graph lifecycle change names is deliberately left alone: creating, deleting and
     * renaming a graph are recorded against the URI they acted on, which is what their message says
     * as well.
     */
    private WorkspaceChangeLogEntry asCurrentlyIdentified(WorkspaceChangeLogEntry entry) {
        var renamed = new HashMap<ParticipantId, ParticipantId>();
        var participants =
                entry.participants().stream()
                        .map(
                                version -> {
                                    var current = currentIdOf(version);
                                    if (current.equals(version.id())) {
                                        return version;
                                    }
                                    renamed.put(version.id(), current);
                                    return version.identifiedAs(current);
                                })
                        .toList();
        if (renamed.isEmpty()) {
            return entry;
        }
        var deltas =
                entry.deltas().stream()
                        .map(
                                delta -> {
                                    var current = renamed.get(delta.participant());
                                    return current == null ? delta : delta.identifiedAs(current);
                                })
                        .toList();
        return new WorkspaceChangeLogEntry(
                entry.changeId(),
                entry.timestamp(),
                entry.message(),
                participants,
                deltas,
                entry.undone());
    }

    /**
     * Returns what a recorded version belongs to now, falling back to what it belonged to when it
     * was recorded for a graph the workspace no longer holds.
     */
    private ParticipantId currentIdOf(ParticipantVersion version) {
        var current = identifyOrNull(version.participant());
        return current != null ? current : version.id();
    }

    /**
     * Moves the workspace to the given version.
     *
     * <p>A move covering the whole workspace steps through the recorded history, forwards or back,
     * and writes nothing: the changes it steps over stay where they are and can be stepped over
     * again, which is what lets a user walk the changelog in both directions.
     *
     * <p>A move covering only part of the workspace cannot step, because stepping takes the whole
     * workspace with it. It is written forward as a new commit instead, which puts the schemas it
     * covers back and leaves the rest alone — at the cost of discarding whatever lay ahead, as any
     * new commit does.
     *
     * @param versionId the change to move to
     * @param scope how much of the workspace to put back
     * @return where the workspace ended up and what is left to undo or redo; {@link
     *     WorkspaceHistoryStep#change()} is {@code null} if there was nothing to do
     */
    public WorkspaceHistoryStep restoreToVersion(UUID versionId, RevertScope scope) {
        try (var transaction = begin(ReadWrite.WRITE)) {
            var ahead = indexAhead(versionId);
            if (ahead >= 0) {
                requireWholeWorkspace(scope);
                return stepForward(ahead + 1);
            }
            var history = changeLog.historySince(versionId);
            if (history.after().isEmpty()) {
                return nothingRestored();
            }
            if (scope.isEverything()) {
                return stepBack(history.after().size());
            }
            return revertAsCommit(history, scope);
        }
    }

    /**
     * Refuses a move that covers part of the workspace where only a whole one is possible.
     *
     * <p>Stepping through the history takes the whole workspace with it, and a change that lies
     * ahead can only be reached by stepping: there is no older state of it to put back.
     */
    private static void requireWholeWorkspace(RevertScope scope) {
        if (!scope.isEverything()) {
            throw new GraphVersionControlException(
                    "Cannot move part of the workspace to a change that has been undone: "
                            + "reaching it again reapplies the whole change.");
        }
    }

    private WorkspaceHistoryStep stepForward(int steps) {
        for (int i = 0; i < steps; i++) {
            changeLog.redo();
        }
        return stepOf(changeLog.undoHistory().getFirst());
    }

    private WorkspaceHistoryStep stepBack(int steps) {
        for (int i = 0; i < steps; i++) {
            changeLog.undo();
        }
        return stepOf(changeLog.undoHistory().getFirst());
    }

    /**
     * Puts the schemas a scope covers back as a new commit, leaving the rest of the workspace where
     * it is.
     */
    private WorkspaceHistoryStep revertAsCommit(HistorySince history, RevertScope scope) {
        var captured = captureBefore(history.after(), scope);
        if (captured.isEmpty()) {
            return nothingRestored();
        }
        var before = changeLog.undoHistory().getFirst();
        reinstate(captured);
        coordinator.commit(
                "restored %s to \"%s\"".formatted(scope.describe(), history.change().message()));
        var head = changeLog.undoHistory().getFirst();
        // Reinstating a state the workspace was already in writes nothing, so there is no entry:
        // a revert that changes nothing must not be reported as one that did.
        return head.changeId().equals(before.changeId()) ? nothingRestored() : stepOf(head);
    }

    /** A state taken from a participant, with the participant it was taken from. */
    private record CapturedParticipant(ChangeLogParticipant participant, CapturedState state) {}

    /**
     * Writes the captured states back, leaving out the graphs the workspace no longer holds.
     *
     * <p>A graph deleted since the version being restored still has its old contents captured,
     * because the changes being taken back still name it, but writing them back would fill a graph
     * the workspace does not hold — a change no changelog entry could name, because there is
     * nothing left to name it after. Its contents are left alone instead, which is also what makes
     * undoing the restore bring the graph back the way it was rather than the way it started.
     */
    private void reinstate(List<CapturedParticipant> captured) {
        captured.stream()
                .filter(each -> identifyOrNull(each.participant()) != null)
                .forEach(each -> each.state().reinstate());
    }

    /**
     * Returns how far ahead of the workspace a change lies, or {@code -1} if it is not ahead.
     *
     * <p>A change an undo has stepped over is not behind the workspace to be put back but in front
     * of it to be reached again, which is a different move with a different answer.
     */
    private int indexAhead(UUID versionId) {
        var ahead = changeLog.redoHistory();
        for (int i = 0; i < ahead.size(); i++) {
            if (ahead.get(i).changeId().equals(versionId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Takes the state everything in scope was in at a version, without going there.
     *
     * <p>Each participant keeps its own versions, so how far back its state lies is simply how many
     * of them the commits being taken back gave it. Asking it directly leaves the workspace where
     * it stands: nothing is stepped, nothing has to be stepped back afterwards, and a restore
     * covering part of the workspace never touches the rest.
     */
    private List<CapturedParticipant> captureBefore(
            List<WorkspaceChangeLogEntry> toRevert, RevertScope scope) {
        var versionsGained = new LinkedHashMap<ChangeLogParticipant, Integer>();
        var oldestTakenBack = new HashMap<ChangeLogParticipant, UUID>();
        for (var entry : toRevert.reversed()) {
            for (var version : entry.participants()) {
                // Identified as it stands now, like the changelog identifies it when it is read:
                // the name a change is shown under has to be the name it can be restored with, or
                // a restore of a renamed graph would match nothing and quietly do nothing.
                if (!scope.covers(currentIdOf(version))) {
                    continue;
                }
                versionsGained.merge(version.participant(), 1, Integer::sum);
                oldestTakenBack.putIfAbsent(version.participant(), version.versionId());
            }
        }
        return versionsGained.entrySet().stream()
                .map(
                        gained ->
                                captureBefore(
                                        gained.getKey(),
                                        gained.getValue(),
                                        oldestTakenBack.get(gained.getKey())))
                .toList();
    }

    private static CapturedParticipant captureBefore(
            ChangeLogParticipant participant, int versionsGained, UUID oldestTakenBack) {
        requireAgreementOnHistory(participant, versionsGained, oldestTakenBack);
        return new CapturedParticipant(participant, participant.capture(versionsGained));
    }

    /**
     * Refuses to take a state the log and the participant do not agree on.
     *
     * <p>How far back a state lies is counted from the entries being taken back, while the state
     * itself is read from the participant's own chain. The two are separate accounts of the same
     * history, and if they ever disagreed the restore would put back a state nobody asked for and
     * say nothing about it. The oldest version being taken back has to be the one the participant
     * is about to step over; participants that track no version id cannot be checked this way.
     */
    private static void requireAgreementOnHistory(
            ChangeLogParticipant participant, int versionsGained, UUID oldestTakenBack) {
        if (oldestTakenBack == null) {
            return;
        }
        var standingThere = participant.versionIdAt(versionsGained - 1);
        if (!oldestTakenBack.equals(standingThere)) {
            throw new GraphVersionControlException(
                    ("Cannot restore: the oldest change being taken back names version %s, "
                                    + "but %d versions back the participant holds %s.")
                            .formatted(oldestTakenBack, versionsGained - 1, standingThere));
        }
    }

    private WorkspaceHistoryStep nothingRestored() {
        return new WorkspaceHistoryStep(null, changeLog.canUndo(), changeLog.canRedo());
    }

    /**
     * Returns the recorded changes of the workspace, newest first.
     *
     * <p>Includes what an undo has stepped over. Those commits still happened and a redo brings
     * them back, so dropping them from the history would leave a user who pressed Ctrl+Z watching
     * their work disappear from the record of it.
     */
    public List<WorkspaceChangeLogEntry> getChangeHistory() {
        try (var transaction = begin(ReadWrite.READ)) {
            return Stream.concat(
                            // Furthest ahead first: the redo stack hands out the next one to
                            // reapply first, which is the other way round from newest first.
                            changeLog.redoHistory().reversed().stream()
                                    .map(WorkspaceChangeLogEntry::asUndone),
                            changeLog.undoHistory().stream())
                    .map(this::asCurrentlyIdentified)
                    .toList();
        }
    }

    // -------------------------------------------------------------------------
    // Graphs
    // -------------------------------------------------------------------------

    /**
     * Returns the graph identified by {@code graphUri}, creating the default graph if it is asked
     * for and does not exist yet.
     *
     * <p>A reader gets an empty graph that is not kept: a read transaction enrols nothing, so the
     * insertion could neither be rolled back nor seen by a concurrent reader doing the same.
     *
     * @param graphUri the graph URI
     * @return the graph's contents
     */
    GraphWithContext getGraphWithContext(String graphUri) {
        var expanded = prefixes.expandPrefix(graphUri);
        var existing = graphs.get(expanded);
        if (existing != null) {
            return existing;
        }
        assertValidGraphName(expanded);
        if (!expanded.equals(DEFAULT_GRAPH_NAME)) {
            throw new ResourceNotFoundException("Graph URI " + expanded + " does not exist.");
        }
        var defaultGraph = new GraphWithContext(GraphFactory.createDefaultGraph(), txnContext);
        if (txnContext.transactionMode() == ReadWrite.WRITE) {
            graphs.put(DEFAULT_GRAPH_NAME, defaultGraph);
        }
        return defaultGraph;
    }

    private void createGraph(String graphUri, Graph newGraph) {
        var expanded = prefixes.expandPrefix(graphUri);
        assertValidGraphName(expanded);
        graphs.put(expanded, new GraphWithContext(newGraph, txnContext));
        prefixes.addAll(newGraph.getPrefixMapping());
        crossProfileDiagramInfo.setColor(expanded, CrossProfileUtils.generateRandomDarkColor());
    }

    private void renameGraph(String oldGraphUri, String newGraphUri) {
        var oldUri = prefixes.expandPrefix(oldGraphUri);
        var newUri = prefixes.expandPrefix(newGraphUri);
        assertValidGraphName(oldUri);
        assertValidGraphName(newUri);
        if (oldUri.equals(newUri)) {
            return;
        }
        if (!graphs.contains(oldUri)) {
            throw new ResourceNotFoundException("Graph URI " + oldUri + " does not exist.");
        }
        if (graphs.contains(newUri)) {
            throw new ResourceConflictException("Graph URI " + newUri + " already exists.");
        }
        graphs.rename(oldUri, newUri);
        renameGraphInDiagrams(oldUri, newUri);
        crossProfileDiagramInfo.renameGraph(oldUri, newUri);
    }

    private void renameGraphInDiagrams(String oldGraphUri, String newGraphUri) {
        var oldUri = new URI(oldGraphUri);
        var newUri = new URI(newGraphUri);
        rewriteGraphUri(customDiagrams.get().values(), oldUri, newUri);
        for (var graph : graphs.view().values()) {
            rewriteGraphUri(graph.getCustomDiagrams().values(), oldUri, newUri);
        }
    }

    private void rewriteGraphUri(Collection<CustomDiagram> diagrams, URI oldUri, URI newUri) {
        for (var diagram : diagrams) {
            var classes = diagram.getClasses();
            if (classes.stream().noneMatch(entry -> oldUri.equals(entry.getGraphUri()))) {
                continue;
            }
            diagram.setClasses(
                    classes.stream()
                            .map(
                                    entry ->
                                            oldUri.equals(entry.getGraphUri())
                                                    ? new ClassInDiagram(entry.getUuid(), newUri)
                                                    : entry)
                            .toList());
        }
    }

    private void deleteGraph(String graphUri) {
        graphs.remove(prefixes.expandPrefix(graphUri));
    }

    /** Returns a snapshot of the current graph URIs. */
    public List<String> listGraphUris() {
        try (var transaction = begin(ReadWrite.READ)) {
            return graphs.uris();
        }
    }

    // -------------------------------------------------------------------------
    // Prefixes
    // -------------------------------------------------------------------------

    /** Returns the namespace prefixes shared by all graphs of the workspace. */
    public PrefixMappingReadOnly getPrefixMapping() {
        try (var transaction = begin(ReadWrite.READ)) {
            return prefixes.readOnlyView();
        }
    }

    private void assertValidGraphName(String graphUri) {
        if (!graphUri.equals(DEFAULT_GRAPH_NAME) && !RDFUtils.isURL(graphUri)) {
            throw new IllegalArgumentException("Graph Uri " + graphUri + " is not a valid URI");
        }
    }
}
