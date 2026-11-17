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
import org.rdfarchitect.models.changelog.HistoryPosition;
import org.rdfarchitect.models.changelog.RevertScope;
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
    public void createDataset(String workspaceName) {
        var store = getOrCreateSessionDataStore();
        if (store.listDatasets().contains(workspaceName)) {
            throw new ResourceConflictException("Dataset " + workspaceName + " already exists");
        }
        store.createDataset(workspaceName);
        initializeNewDataset(store, workspaceName);
    }

    @Override
    public void createWorkspaceIfAbsent(String workspaceName) {
        // The store decides who creates it; looking first would let two concurrent calls through.
        var store = getOrCreateSessionDataStore();
        if (store.createDatasetIfAbsent(workspaceName)) {
            initializeNewDataset(store, workspaceName);
        }
    }

    @Override
    public void deleteDataset(String workspaceName) {
        getOrCreateSessionDataStore().deleteDataset(workspaceName);
    }

    @Override
    public void renameDataset(String oldWorkspaceName, String newWorkspaceName) {
        getOrCreateSessionDataStore().renameDataset(oldWorkspaceName, newWorkspaceName);
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
    public WorkspaceChangeLogEntry undoPreview(String workspaceName) {
        return getOrCreateSessionDataStore().undoPreview(workspaceName);
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
    public WorkspaceHistoryStep undoIfLatest(String workspaceName, UUID changeId) {
        return getOrCreateSessionDataStore().undoIfLatest(workspaceName, changeId);
    }

    @Override
    public WorkspaceHistoryStep redo(String workspaceName) {
        return getOrCreateSessionDataStore().redo(workspaceName);
    }

    @Override
    public WorkspaceHistoryStep restoreToVersion(
            String workspaceName,
            UUID versionId,
            RevertScope scope,
            HistoryPosition expectedPosition) {
        return getOrCreateSessionDataStore()
                .restoreToVersion(workspaceName, versionId, scope, expectedPosition);
    }

    @Override
    public List<WorkspaceChangeLogEntry> listChanges(String workspaceName) {
        return getOrCreateSessionDataStore().listChanges(workspaceName);
    }

    private void initializeNewDataset(SessionDataStore store, String workspaceName) {
        store.enableEditing(workspaceName);
        var prefixMapping = new PrefixMappingImpl().setNsPrefixes(PrefixMapping.Standard);
        for (var entry : schemaConfig.getNamespaces().entrySet()) {
            prefixMapping.setNsPrefix(entry.getKey(), entry.getValue());
        }
        try (var transaction = store.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            transaction.setPrefixes(prefixMapping);
            // The prefixes it starts with are not a change the user made and cannot be undone.
            transaction.commitWithoutHistory();
        }
    }

    @Override
    public boolean containsGraph(GraphIdentifier graphIdentifier) {
        return getOrCreateSessionDataStore().containsGraph(graphIdentifier);
    }

    @Override
    public List<String> listSchemaNames(String workspaceName) {
        return getOrCreateSessionDataStore().listSchemaNames(workspaceName);
    }

    @Override
    public PrefixMappingReadOnly getPrefixMapping(String workspaceName) {
        return getOrCreateSessionDataStore().getPrefixMapping(workspaceName);
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
    public boolean isReadOnly(String workspaceName) {
        return getOrCreateSessionDataStore().isReadOnly(workspaceName);
    }

    @Override
    public void enableEditing(String workspaceName) {
        getOrCreateSessionDataStore().enableEditing(workspaceName);
    }

    @Override
    public void disableEditing(String workspaceName) {
        getOrCreateSessionDataStore().disableEditing(workspaceName);
    }

    /** Returns the SessionDataStore for the current session, creating one if necessary. */
    private SessionDataStore getOrCreateSessionDataStore() {
        return sessionStores.computeIfAbsent(
                SessionContext.getSessionId(), _ -> new SessionDataStoreImpl());
    }
}
