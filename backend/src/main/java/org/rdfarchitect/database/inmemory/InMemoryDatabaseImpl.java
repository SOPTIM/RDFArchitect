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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.models.changelog.WorkspaceHistoryStep;

import java.util.List;
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
    public void createDataset(String datasetName) {
        var store = getOrCreateSessionDataStore();
        if (store.listDatasets().contains(datasetName)) {
            throw new ResourceConflictException("Dataset " + datasetName + " already exists");
        }
        store.createDataset(datasetName);
        initializeNewDataset(store, datasetName);
    }

    @Override
    public void createWorkspaceIfAbsent(String workspaceName) {
        if (!getOrCreateSessionDataStore().listDatasets().contains(workspaceName)) {
            createDataset(workspaceName);
        }
    }

    @Override
    public void deleteDataset(String datasetName) {
        getOrCreateSessionDataStore().deleteDataset(datasetName);
    }

    @Override
    public void renameDataset(String oldDatasetName, String newDatasetName) {
        getOrCreateSessionDataStore().renameDataset(oldDatasetName, newDatasetName);
    }

    @Override
    public List<String> listDatasets() {
        return getOrCreateSessionDataStore().listDatasets();
    }

    @Override
    public WorkspaceTransaction beginTransaction(String workspaceName, ReadWrite mode) {
        return getOrCreateSessionDataStore().beginTransaction(workspaceName, mode);
    }

    @Override
    public boolean canUndo(String workspaceName) {
        return getOrCreateSessionDataStore().canUndo(workspaceName);
    }

    @Override
    public WorkspaceChangeLogEntry pendingUndo(String workspaceName) {
        return getOrCreateSessionDataStore().pendingUndo(workspaceName);
    }

    @Override
    public boolean canRedo(String workspaceName) {
        return getOrCreateSessionDataStore().canRedo(workspaceName);
    }

    @Override
    public WorkspaceHistoryStep undo(String workspaceName) {
        return getOrCreateSessionDataStore().undo(workspaceName);
    }

    @Override
    public WorkspaceHistoryStep redo(String workspaceName) {
        return getOrCreateSessionDataStore().redo(workspaceName);
    }

    @Override
    public void restoreToVersion(String workspaceName, UUID versionId) {
        getOrCreateSessionDataStore().restoreToVersion(workspaceName, versionId);
    }

    @Override
    public List<WorkspaceChangeLogEntry> listChanges(String workspaceName) {
        return getOrCreateSessionDataStore().listChanges(workspaceName);
    }

    private void initializeNewDataset(SessionDataStore store, String datasetName) {
        store.enableEditing(datasetName);
        var prefixMapping = new PrefixMappingImpl().setNsPrefixes(PrefixMapping.Standard);
        for (var entry : schemaConfig.getNamespaces().entrySet()) {
            prefixMapping.setNsPrefix(entry.getKey(), entry.getValue());
        }
        try (var transaction = store.beginTransaction(datasetName, ReadWrite.WRITE)) {
            transaction.setPrefixes(prefixMapping);
            transaction.commit("created workspace %s".formatted(datasetName));
        }
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
