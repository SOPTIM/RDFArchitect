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
import org.rdfarchitect.models.changelog.ParticipantId;
import org.rdfarchitect.models.changelog.WorkspaceChangeLog;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.RDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.TransactionParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
        }
        return new Transaction();
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
        if (txnContext.isOutermost() && mode == ReadWrite.WRITE && !txnContext.isAborted()) {
            coordinator.finishOutermostWrite();
        }
        if (txnContext.end()) {
            var lock = mode == ReadWrite.READ ? rwLock.readLock() : rwLock.writeLock();
            lock.unlock();
        }
    }

    /**
     * Works out what a participant represents by looking it up among the workspace's graphs. Done
     * on demand rather than remembered, so that a renamed graph does not leave a stale identifier
     * behind.
     */
    private ParticipantId identify(TransactionParticipant participant) {
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
        throw new IllegalStateException(
                "A participant enrolled that does not belong to this workspace: " + participant);
    }

    private static ParticipantId.Kind kindWithin(
            GraphWithContext graph, TransactionParticipant participant) {
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
            return changeLog.pendingUndo();
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
     * @return the entry that was undone
     */
    public WorkspaceChangeLogEntry undo() {
        try (var transaction = begin(ReadWrite.WRITE)) {
            return changeLog.undo();
        }
    }

    /**
     * Reapplies the most recently undone change.
     *
     * @return the entry that was redone
     */
    public WorkspaceChangeLogEntry redo() {
        try (var transaction = begin(ReadWrite.WRITE)) {
            return changeLog.redo();
        }
    }

    /**
     * Rolls the workspace back to the given version, undoing every change made after it.
     *
     * @param versionId the change to restore to
     */
    public void restoreToVersion(UUID versionId) {
        try (var transaction = begin(ReadWrite.WRITE)) {
            changeLog.restoreTo(versionId);
        }
    }

    /** Returns the recorded changes of the workspace, newest first. */
    public List<WorkspaceChangeLogEntry> getChangeHistory() {
        try (var transaction = begin(ReadWrite.READ)) {
            return changeLog.undoHistory();
        }
    }

    // -------------------------------------------------------------------------
    // Graphs
    // -------------------------------------------------------------------------

    /**
     * Returns the graph identified by {@code graphUri}, creating the default graph if it is asked
     * for and does not exist yet.
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
        graphs.put(DEFAULT_GRAPH_NAME, defaultGraph);
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
