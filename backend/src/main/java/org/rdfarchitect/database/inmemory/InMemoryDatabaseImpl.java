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

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CrossProfileDiagramInfo;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class that provides {@link SessionDataStore SessionStores}. Forwards all calls to the
 * {@link SessionDataStore} of the current session. The session is extracted from the {@link
 * SessionContext}.
 */
@RequiredArgsConstructor
public class InMemoryDatabaseImpl implements InMemoryDatabase {

    private final SchemaConfig schemaConfig;

    private final ConcurrentHashMap<String, SessionDataStore> sessionStores =
            new ConcurrentHashMap<>();

    @Override
    public void deleteDataset(String datasetName) {
        getOrCreateSessionDataStore().deleteDataset(datasetName);
    }

    @Override
    public List<String> listDatasets() {
        return getOrCreateSessionDataStore().listDatasets();
    }

    @Override
    public Map<UUID, CustomDiagram> getDatasetDiagrams(String datasetName) {
        return getOrCreateSessionDataStore().getDatasetDiagrams(datasetName);
    }

    @Override
    public DiagramLayout getDatasetDiagramLayout(String datasetName) {
        return getOrCreateSessionDataStore().getDatasetDiagramLayout(datasetName);
    }

    @Override
    public CrossProfileDiagramInfo getCrossProfileDiagramInfo(String datasetName) {
        return getOrCreateSessionDataStore().getCrossProfileDiagramInfo(datasetName);
    }

    @Override
    public GraphContext getGraphWithContext(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().getGraphWithContext(graphIdentifier);
    }

    @Override
    public void createGraph(GraphIdentifier graphIdentifier, Graph newGraph) {
        var store = getOrCreateSessionDataStore();
        store.create(graphIdentifier, newGraph);
        var currentPrefixMapping =
                new PrefixMappingImpl()
                        .setNsPrefixes(store.getPrefixMapping(graphIdentifier.datasetName()))
                        .setNsPrefixes(newGraph.getPrefixMapping());
        store.setPrefixMapping(graphIdentifier.datasetName(), currentPrefixMapping);
        store.getCrossProfileDiagramInfo(graphIdentifier.datasetName())
                .setColor(graphIdentifier.graphUri(), CrossProfileUtils.generateRandomDarkColor());
    }

    @Override
    public void createEmptyGraph(GraphIdentifier graphIdentifier) {
        var store = getOrCreateSessionDataStore();
        var datasetName = graphIdentifier.datasetName();
        var isNewDataset = !store.listDatasets().contains(datasetName);
        store.create(graphIdentifier, GraphFactory.createDefaultGraph());
        if (isNewDataset) {
            store.enableEditing(datasetName);
            var configNamespaces = schemaConfig.getNamespaces();
            var prefixMapping = new PrefixMappingImpl().setNsPrefixes(PrefixMapping.Standard);
            for (var entry : configNamespaces.entrySet()) {
                prefixMapping.setNsPrefix(entry.getKey(), entry.getValue());
            }
            store.setPrefixMapping(datasetName, prefixMapping);
        }
    }

    @Override
    public void remove(GraphIdentifier graphIdentifier) {
        getOrCreateSessionDataStore().remove(graphIdentifier);
    }

    @Override
    public boolean containsGraph(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().containsGraph(graphIdentifier);
    }

    @Override
    public List<String> listGraphUris(String datasetName) {
        return getOrCreateSessionDataStore().listGraphUris(datasetName);
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String datasetName) {
        return getOrCreateSessionDataStore().getPrefixMapping(datasetName);
    }

    @Override
    public void setPrefixMapping(String datasetName, PrefixMapping newPrefixes) {
        getOrCreateSessionDataStore().setPrefixMapping(datasetName, newPrefixes);
    }

    @Override
    public void writeToDatabase(
            DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
        getOrCreateSessionDataStore().writeToDatabase(databaseConnection, graphIdentifier);
    }

    @Override
    public void fetchFromDatabase(DatabaseConnection databaseConnection) {
        getOrCreateSessionDataStore().fetchFromDatabase(databaseConnection);
    }

    @Override
    public void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token) {
        getOrCreateSessionDataStore().fetchSnapshot(databaseConnection, base64Token);
    }

    @Override
    public boolean isReadOnly(String datasetName) {
        return getOrCreateSessionDataStore().isReadOnly(datasetName);
    }

    @Override
    public void enableEditing(String datasetName) {
        getOrCreateSessionDataStore().enableEditing(datasetName);
    }

    @Override
    public void disableEditing(String datasetName) {
        getOrCreateSessionDataStore().disableEditing(datasetName);
    }

    /** Returns the SessionDataStore for the current session, creating one if necessary. */
    private SessionDataStore getOrCreateSessionDataStore() {
        return sessionStores.computeIfAbsent(
                SessionContext.getSessionId(), _ -> new SessionDataStoreImpl());
    }
}
