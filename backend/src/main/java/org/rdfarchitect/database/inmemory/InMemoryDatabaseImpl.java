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
import org.apache.jena.query.TxnType;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class that provides {@link SessionDataStore SessionStores}.
 * Forwards all calls to the {@link SessionDataStore} of the current session. The session is extracted from the {@link SessionContext}.
 */
public class InMemoryDatabaseImpl implements InMemoryDatabase {

    private final ConcurrentHashMap<String, SessionDataStore> sessionStores = new ConcurrentHashMap<>();

    @Override
    public void deleteDataset(String datasetName) {
        getOrCreateSessionDataStore().deleteDataset(datasetName);
    }

    @Override
    public List<String> listDatasets() {
        return getOrCreateSessionDataStore().listDatasets();
    }

    @Override
    public GraphRewindableWithUUIDs begin(GraphIdentifier graphIdentifier, TxnType txnType) {
        return getOrCreateSessionDataStore().begin(graphIdentifier, txnType);
    }

    @Override
    public GraphRewindableWithUUIDs getGraph(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().getGraph(graphIdentifier);
    }

    @Override
    public void create(GraphIdentifier graphIdentifier, Graph newGraph) {
        getOrCreateSessionDataStore().create(graphIdentifier, newGraph);
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
    public void writeToDatabase(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
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
    public void undo(GraphIdentifier graphIdentifier) {
        getOrCreateSessionDataStore().undo(graphIdentifier);
    }

    @Override
    public void redo(GraphIdentifier graphIdentifier) {
        getOrCreateSessionDataStore().redo(graphIdentifier);
    }

    @Override
    public boolean canUndo(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().canUndo(graphIdentifier);
    }

    @Override
    public boolean canRedo(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().canRedo(graphIdentifier);
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

    @Override
    public void restore(GraphIdentifier graphIdentifier, UUID versionId) {
        getOrCreateSessionDataStore().restore(graphIdentifier, versionId);
    }

    /**
     * Returns the SessionDataStore for the current session, creating one if necessary.
     */
    private SessionDataStore getOrCreateSessionDataStore() {
        return sessionStores.computeIfAbsent(SessionContext.getSessionId(), k -> new SessionDataStoreImpl());
    }
}
