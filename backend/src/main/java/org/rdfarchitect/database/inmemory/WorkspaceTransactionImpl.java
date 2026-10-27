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
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagramCollection;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A running transaction on a workspace, and the only place where the graphs of one are created,
 * renamed and deleted: those changes reach the graph collection, the prefixes and the cross-profile
 * colours together, which is something only a transaction can promise.
 *
 * <p>Handed out by {@link Workspace#begin(ReadWrite)}, which has already taken the lock and told
 * the context that a transaction is running. This is the handle to it and gives both back on {@link
 * #close()}.
 */
final class WorkspaceTransactionImpl implements WorkspaceTransaction {

    private final Workspace workspace;

    private final WorkspaceTransactionContext txnContext;
    private final TransactionCoordinator coordinator;
    private final GraphCollection graphs;
    private final CustomDiagramCollection customDiagrams;
    private final DiagramLayoutDelta diagramLayout;
    private final CrossProfileDiagramInfo crossProfileDiagramInfo;
    private final WorkspacePrefixes prefixes;

    private boolean closed;

    WorkspaceTransactionImpl(Workspace workspace) {
        this.workspace = workspace;
        this.txnContext = workspace.getTxnContext();
        this.coordinator = workspace.getCoordinator();
        this.graphs = workspace.getGraphs();
        this.customDiagrams = workspace.getCustomDiagrams();
        this.diagramLayout = workspace.getDiagramLayout();
        this.crossProfileDiagramInfo = workspace.getCrossProfileDiagramInfo();
        this.prefixes = workspace.getPrefixes();
    }

    @Override
    public GraphContext graph(String graphUri) {
        return workspace.getGraphWithContext(graphUri);
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
    public CrossProfileDiagramInfo crossProfileColors() {
        return crossProfileDiagramInfo;
    }

    @Override
    public UUID crossProfileDiagramId() {
        return crossProfileDiagramInfo.getCrossProfileDiagramUUID();
    }

    @Override
    public PrefixMapping prefixes() {
        return prefixes.readOnlyView();
    }

    @Override
    public void createGraph(String graphUri, Graph newGraph) {
        var expanded = prefixes.expandPrefix(graphUri);
        Workspace.assertValidGraphName(expanded);
        graphs.put(expanded, new GraphWithContext(newGraph, txnContext));
        prefixes.addAll(newGraph.getPrefixMapping());
        crossProfileDiagramInfo.setColor(expanded, CrossProfileUtils.generateRandomDarkColor());
    }

    @Override
    public void deleteGraph(String graphUri) {
        graphs.remove(prefixes.expandPrefix(graphUri));
    }

    @Override
    public void renameGraph(String oldGraphUri, String newGraphUri) {
        var oldUri = prefixes.expandPrefix(oldGraphUri);
        var newUri = prefixes.expandPrefix(newGraphUri);
        Workspace.assertValidGraphName(oldUri);
        Workspace.assertValidGraphName(newUri);
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
        end();
    }

    private void renameGraphInDiagrams(String oldGraphUri, String newGraphUri) {
        var oldUri = new URI(oldGraphUri);
        var newUri = new URI(newGraphUri);
        rewriteGraphUri(customDiagrams.get().values(), oldUri, newUri);
        for (var graph : graphs.view().values()) {
            rewriteGraphUri(graph.getCustomDiagrams().values(), oldUri, newUri);
        }
    }

    private static void rewriteGraphUri(
            Collection<CustomDiagram> diagrams, URI oldUri, URI newUri) {
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

    private void end() {
        var mode = txnContext.transactionMode();
        try {
            if (txnContext.isOutermost() && mode == ReadWrite.WRITE && !txnContext.isAborted()) {
                coordinator.finishOutermostWrite();
            }
        } finally {
            if (txnContext.end()) {
                workspace.lockFor(mode).unlock();
            }
        }
    }
}
