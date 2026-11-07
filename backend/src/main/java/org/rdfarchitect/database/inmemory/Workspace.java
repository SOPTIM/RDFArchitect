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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagramCollection;
import org.rdfarchitect.database.snapshots.ShapesDocumentSeeds;
import org.rdfarchitect.database.snapshots.ShapesGraphNaming;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.exception.graph.GraphVersionControlException;
import org.rdfarchitect.exception.graph.RestoreRefusedException;
import org.rdfarchitect.exception.graph.RestoreTargetChangedException;
import org.rdfarchitect.exception.graph.UndoTargetChangedException;
import org.rdfarchitect.models.changelog.CapturedState;
import org.rdfarchitect.models.changelog.ChangeLogParticipant;
import org.rdfarchitect.models.changelog.HistoryPosition;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
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
    private static final String INITIAL_CHANGE_MESSAGE = "Loaded the workspace";

    @Setter @Getter private volatile boolean isReadOnly = true;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private final WorkspaceChangeLog changeLog;

    /**
     * The parts a transaction works on, from here down. They are read by {@link
     * WorkspaceTransactionImpl}, which is the workspace's own handle rather than something a caller
     * can build.
     */
    @Getter(AccessLevel.PACKAGE)
    private final WorkspaceTransactionContext txnContext;

    @Getter(AccessLevel.PACKAGE)
    private final TransactionCoordinator coordinator;

    @Getter(AccessLevel.PACKAGE)
    private final GraphCollection graphs;

    @Getter(AccessLevel.PACKAGE)
    private final CustomDiagramCollection customDiagrams;

    @Getter(AccessLevel.PACKAGE)
    private final DiagramLayoutDelta diagramLayout;

    @Getter(AccessLevel.PACKAGE)
    private final CrossProfileDiagramInfo crossProfileDiagramInfo;

    @Getter(AccessLevel.PACKAGE)
    private final WorkspacePrefixes prefixes;

    @Getter(AccessLevel.PACKAGE)
    private final ShapesDocumentCollection shapesDocuments;

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
        this.shapesDocuments = new ShapesDocumentCollection(txnContext);
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
                // A dataset stores shapes documents in graphs of their own, which are not graphs
                // of the workspace.
                if (!ShapesGraphNaming.isReserved(graphURI)) {
                    graphs.put(
                            graphURI,
                            new GraphWithContext(
                                    dataset.getNamedModel(graphURI).getGraph(), txnContext));
                }
            }
            ShapesDocumentSeeds.of(dataset).forEach(shapesDocuments::restore);
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
        return new WorkspaceTransactionImpl(this);
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
            lockFor(mode).unlock();
        }
    }

    /** The lock a transaction in this mode holds, taken on begin and given back on close. */
    Lock lockFor(ReadWrite mode) {
        return mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
    }

    // The lock is held for as long as the transaction runs, so it is released by whoever ends it:
    // the transaction's own close, or the sweep of one that was abandoned.
    @SuppressWarnings("java:S2222")
    private void acquireLock(ReadWrite mode) {
        var lock = lockFor(mode);
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

    // -------------------------------------------------------------------------
    // Changelog participants
    // -------------------------------------------------------------------------

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
        if (shapesDocuments.holds(participant)) {
            return ParticipantId.ofWorkspace(ParticipantId.Kind.SHACL);
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
        try (var _ = begin(ReadWrite.READ)) {
            return changeLog.canUndo();
        }
    }

    /**
     * Returns the change the next undo would take back, or {@code null} if there is none.
     *
     * @return the change an undo would take back
     */
    public WorkspaceChangeLogEntry undoPreview() {
        try (var _ = begin(ReadWrite.READ)) {
            var preview = changeLog.undoPreview();
            return preview == null ? null : asCurrentlyIdentified(preview);
        }
    }

    /** Returns whether the workspace has an undone change that can be reapplied. */
    public boolean canRedo() {
        try (var _ = begin(ReadWrite.READ)) {
            return changeLog.canRedo();
        }
    }

    /**
     * Rolls back the most recent change anywhere in the workspace.
     *
     * @return what was undone and what is left to undo or redo
     */
    public WorkspaceHistoryStep undo() {
        try (var _ = begin(ReadWrite.WRITE)) {
            return stepOf(changeLog.undo());
        }
    }

    /**
     * Rolls back the most recent change, provided it is still the one the caller means.
     *
     * <p>The editor reads the change from {@link #undoPreview()} before every undo, to say what it
     * undoes and to ask first if it removes something. That read and this undo are two
     * transactions, so another request may commit in between; checking under the write lock keeps
     * the undo from taking back a change the user was never shown or asked about.
     *
     * @param changeId the change the undo preview showed
     * @return what was undone and what is left to undo or redo
     * @throws UndoTargetChangedException if the newest change is a different one
     */
    public WorkspaceHistoryStep undoIfLatest(UUID changeId) {
        try (var _ = begin(ReadWrite.WRITE)) {
            var newest = changeLog.undoPreview();
            if (newest == null || !changeId.equals(newest.changeId())) {
                throw new UndoTargetChangedException();
            }
            return stepOf(changeLog.undo());
        }
    }

    /**
     * Reapplies the most recently undone change.
     *
     * @return what was redone and what is left to undo or redo
     */
    public WorkspaceHistoryStep redo() {
        try (var _ = begin(ReadWrite.WRITE)) {
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
     * is the best there is. Such a version is marked as no longer held, so that the entry is not
     * offered for restoring a graph created under the same URI afterwards.
     *
     * <p>What a graph lifecycle change names is deliberately left alone: creating, deleting and
     * renaming a graph are recorded against the URI they acted on, which is what their message says
     * as well.
     */
    private WorkspaceChangeLogEntry asCurrentlyIdentified(WorkspaceChangeLogEntry entry) {
        // Keyed by the participant rather than its recorded identifier: within one change a graph
        // can be renamed away from a URI and another created under it, and both are recorded
        // under that URI.
        var renamed = new IdentityHashMap<ChangeLogParticipant, ParticipantId>();
        var participants = new ArrayList<ParticipantVersion>();
        var changed = false;
        for (var version : entry.participants()) {
            var current = identifyOrNull(version.participant());
            if (current == null) {
                participants.add(version.asNoLongerHeld());
                changed = true;
            } else if (current.equals(version.id())) {
                participants.add(version);
            } else {
                renamed.put(version.participant(), current);
                participants.add(version.identifiedAs(current));
                changed = true;
            }
        }
        if (!changed) {
            return entry;
        }
        var deltas =
                entry.deltas().stream()
                        .map(
                                delta -> {
                                    var current = renamed.get(delta.owner());
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
        return restoreToVersion(versionId, scope, null);
    }

    /**
     * Moves the workspace to the given version, provided it still stands where it stood when the
     * move was confirmed.
     *
     * <p>The confirmation lists what the move takes back and what it leaves ahead, read from the
     * changelog in an earlier request. A commit, undo or redo landing in between — or a change
     * committed and undone again, which leaves a redo a move held to some schemas would discard —
     * would make the move do something that list did not show, so it is checked under the write
     * lock the move runs in.
     *
     * @param versionId the change to move to
     * @param scope how much of the workspace to put back
     * @param expectedPosition where the workspace stood in its history when the move was confirmed,
     *     or {@code null} to move without checking
     * @return where the workspace ended up and what is left to undo or redo; {@link
     *     WorkspaceHistoryStep#change()} is {@code null} if there was nothing to do
     * @throws RestoreTargetChangedException if the workspace has moved in its history since
     */
    public WorkspaceHistoryStep restoreToVersion(
            UUID versionId, RevertScope scope, HistoryPosition expectedPosition) {
        try (var transaction = begin(ReadWrite.WRITE)) {
            if (expectedPosition != null && !expectedPosition.admits(historyPosition())) {
                throw new RestoreTargetChangedException();
            }
            var ahead = indexAhead(versionId);
            if (ahead >= 0) {
                requireWholeWorkspace(scope);
                return stepForward(ahead + 1);
            }
            var history = changeLog.historySince(versionId);
            requireStillHeld(scope);
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
            throw new RestoreRefusedException(
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
        rebindRenamedGraphs(captured, graphSetVersionsIn(history.after()));
        coordinator.commit(
                "Restored %s to the state after: %s"
                        .formatted(scope.describe(), history.change().message()));
        var head = changeLog.undoHistory().getFirst();
        // Reinstating a state the workspace was already in writes nothing, so there is no entry:
        // a revert that changes nothing must not be reported as one that did.
        return head.changeId().equals(before.changeId()) ? nothingRestored() : stepOf(head);
    }

    /** A state taken from a participant, with the participant it was taken from. */
    private record CapturedParticipant(ChangeLogParticipant participant, CapturedState state) {}

    private static void reinstate(List<CapturedParticipant> captured) {
        captured.forEach(each -> each.state().reinstate());
    }

    /**
     * Points the reinstated diagrams at the URIs their graphs have now.
     *
     * <p>A rename rewrites every diagram that shows the renamed graph, and the rename itself stays
     * where it is: a restore held to some schemas leaves the set of graphs alone. Reinstated as the
     * version left them, the diagrams would point at URIs the graphs have left since — at nothing,
     * or at another graph that has moved in.
     *
     * @param captured what was reinstated
     * @param graphSetVersionsBack how many versions back the set of graphs stood at the version
     */
    private void rebindRenamedGraphs(List<CapturedParticipant> captured, int graphSetVersionsBack) {
        var renamed = renamedSince(graphSetVersionsBack);
        if (renamed.isEmpty()) {
            return;
        }
        captured.stream()
                .map(CapturedParticipant::participant)
                .filter(CustomDiagramCollection.class::isInstance)
                .map(CustomDiagramCollection.class::cast)
                .forEach(
                        diagrams ->
                                WorkspaceTransactionImpl.rewriteGraphUris(
                                        diagrams.get().values(), renamed));
    }

    /** Returns the URI each graph renamed since a version of the set of graphs has now. */
    private Map<URI, URI> renamedSince(int graphSetVersionsBack) {
        var renamed = new HashMap<URI, URI>();
        graphs.graphsByUriAt(graphSetVersionsBack)
                .forEach(
                        (uriThen, graph) ->
                                graphs.view().entrySet().stream()
                                        .filter(now -> now.getValue() == graph)
                                        .map(Map.Entry::getKey)
                                        .filter(uriNow -> !uriNow.equals(uriThen))
                                        .findFirst()
                                        .ifPresent(
                                                uriNow ->
                                                        renamed.put(
                                                                new URI(uriThen),
                                                                new URI(uriNow))));
        return renamed;
    }

    /**
     * Returns the graphs held both now and right before the given commits, under the URIs they have
     * now — the graphs a restore held to them can take back that far.
     *
     * @param since the commits made since, newest first
     */
    private Set<String> graphsHeldAfter(List<WorkspaceChangeLogEntry> since) {
        var heldThen = Collections.newSetFromMap(new IdentityHashMap<GraphWithContext, Boolean>());
        try {
            heldThen.addAll(graphs.graphsAt(graphSetVersionsIn(since)));
        } catch (GraphVersionControlException beyondRetention) {
            // Folded into the oldest state kept: no graph can be restored that far on its own.
            return Set.of();
        }
        return graphs.view().entrySet().stream()
                .filter(entry -> heldThen.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** Returns how many versions the commits being taken back gave the set of graphs. */
    private int graphSetVersionsIn(List<WorkspaceChangeLogEntry> entries) {
        return (int)
                entries.stream()
                        .flatMap(entry -> entry.participants().stream())
                        .filter(version -> version.participant() == graphs)
                        .count();
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
     *
     * <p>Only a graph that was there at the version, and still is, can be put back. A URI can name
     * more than one graph over time — one deleted and another created under the same URI, or
     * replaced by an import — and putting back what the version held would then take the graph now
     * standing there back past its creation, a state the URI never had.
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
                // A deleted graph's changes are no part of the history of a graph that stands
                // under its URI now, and one with nothing standing there is refused beforehand.
                var current = identifyOrNull(version.participant());
                if (current == null || !scope.covers(current)) {
                    continue;
                }
                versionsGained.merge(version.participant(), 1, Integer::sum);
                oldestTakenBack.putIfAbsent(version.participant(), version.versionId());
            }
        }
        var graphsThen = graphs.graphsAt(graphSetVersionsIn(toRevert));
        versionsGained.keySet().forEach(participant -> requireHeldThen(participant, graphsThen));
        // A graph created since may have no changes of its own to take back, yet the URI held
        // something else at the version, or nothing at all.
        scope.graphUris().stream()
                .map(graphs.view()::get)
                .filter(Objects::nonNull)
                .forEach(graph -> requireHeldThen(graph.getRdfGraph(), graphsThen));
        return versionsGained.entrySet().stream()
                .map(
                        gained ->
                                captureBefore(
                                        gained.getKey(),
                                        gained.getValue(),
                                        oldestTakenBack.get(gained.getKey())))
                .toList();
    }

    /**
     * Refuses to restore a graph the workspace does not hold, whether it was deleted since or never
     * there.
     *
     * <p>Asked of the scope rather than of the changes being taken back: a graph deleted without a
     * change of its own since the version leaves only workspace-wide changes behind, and would
     * otherwise be reported as already standing at that version.
     */
    private void requireStillHeld(RevertScope scope) {
        scope.graphUris().stream()
                .filter(graphUri -> !graphs.contains(graphUri))
                .findFirst()
                .ifPresent(
                        graphUri -> {
                            throw new RestoreRefusedException(
                                    ("Cannot restore %s to that version: the workspace holds no "
                                                    + "schema under that URI.")
                                            .formatted(graphUri));
                        });
    }

    /**
     * Refuses to take a graph back to a version from before it was created, which includes one that
     * replaced another under the same URI.
     */
    private void requireHeldThen(
            ChangeLogParticipant participant, Collection<GraphWithContext> graphsThen) {
        if (graphsThen.stream().noneMatch(graph -> kindWithin(graph, participant) != null)) {
            throw new RestoreRefusedException(
                    ("Cannot restore %s to that version: the schema now under that URI was "
                                    + "created after it.")
                            .formatted(identifyOrNull(participant).scope()));
        }
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

    /** Returns where the workspace stands in its history. */
    private HistoryPosition historyPosition() {
        var behind = changeLog.undoHistory();
        var ahead = changeLog.redoHistory();
        var current = behind.isEmpty() ? null : behind.getFirst().changeId();
        // The redo stack hands out the nearest change first, so the newest one sits at its end.
        var newest = ahead.isEmpty() ? current : ahead.getLast().changeId();
        return new HistoryPosition(current, newest);
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
        try (var _ = begin(ReadWrite.READ)) {
            var behind = changeLog.undoHistory();
            var reached = new ArrayList<WorkspaceChangeLogEntry>(behind.size());
            for (int i = 0; i < behind.size(); i++) {
                reached.add(
                        asCurrentlyIdentified(behind.get(i))
                                .withRestoreTargetGraphUris(graphsHeldAfter(behind.subList(0, i))));
            }
            return Stream.concat(
                            // Furthest ahead first: the redo stack hands out the next one to
                            // reapply first, which is the other way round from newest first.
                            // Nothing is restored to one of them by graph: they can only be
                            // reached again by stepping, which takes the whole workspace along.
                            changeLog.redoHistory().reversed().stream()
                                    .map(WorkspaceChangeLogEntry::asUndone)
                                    .map(this::asCurrentlyIdentified),
                            reached.stream())
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

    /** Returns a snapshot of the current graph URIs. */
    public List<String> listGraphUris() {
        try (var _ = begin(ReadWrite.READ)) {
            return graphs.uris();
        }
    }

    // -------------------------------------------------------------------------
    // Prefixes
    // -------------------------------------------------------------------------

    /** Returns the namespace prefixes shared by all graphs of the workspace. */
    public PrefixMappingReadOnly getPrefixMapping() {
        try (var _ = begin(ReadWrite.READ)) {
            return prefixes.readOnlyView();
        }
    }

    static void assertValidGraphName(String graphUri) {
        if (!graphUri.equals(DEFAULT_GRAPH_NAME) && !RDFUtils.isURL(graphUri)) {
            throw new IllegalArgumentException("Graph Uri " + graphUri + " is not a valid URI");
        }
    }
}
