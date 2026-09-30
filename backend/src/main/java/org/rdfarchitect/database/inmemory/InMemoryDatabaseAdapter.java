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
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class InMemoryDatabaseAdapter implements DatabasePort {

    private final InMemoryDatabase database;

    @Override
    public WorkspaceTransaction beginTransaction(String workspaceName, ReadWrite mode) {
        return database.beginTransaction(workspaceName, mode);
    }

    @Override
    public boolean canUndo(String workspaceName) {
        return database.canUndo(workspaceName);
    }

    @Override
    public boolean canRedo(String workspaceName) {
        return database.canRedo(workspaceName);
    }

    @Override
    public WorkspaceChangeLogEntry undo(String workspaceName) {
        return database.undo(workspaceName);
    }

    @Override
    public WorkspaceChangeLogEntry redo(String workspaceName) {
        return database.redo(workspaceName);
    }

    @Override
    public void restoreToVersion(String workspaceName, UUID versionId) {
        database.restoreToVersion(workspaceName, versionId);
    }

    @Override
    public List<WorkspaceChangeLogEntry> listChanges(String workspaceName) {
        return database.listChanges(workspaceName);
    }

    @Override
    public PrefixMapping getPrefixMapping(String datasetName) {
        return database.getPrefixMapping(datasetName);
    }

    @Override
    public List<String> listGraphUris(String datasetName) {
        return database.listGraphUris(datasetName);
    }

    @Override
    public void persist(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier) {
        database.writeToDatabase(databaseConnection, graphIdentifier);
    }

    @Override
    public List<String> listDatasets() {
        return database.listDatasets();
    }

    @Override
    public void createDataset(String datasetName) {
        database.createDataset(datasetName);
    }

    @Override
    public void createWorkspaceIfAbsent(String workspaceName) {
        database.createWorkspaceIfAbsent(workspaceName);
    }

    @Override
    public void deleteDataset(String datasetName) {
        database.deleteDataset(datasetName);
    }

    @Override
    public void renameDataset(String oldDatasetName, String newDatasetName) {
        database.renameDataset(oldDatasetName, newDatasetName);
    }

    @Override
    public void fetchFromDatabase(DatabaseConnection databaseConnection) {
        database.fetchFromDatabase(databaseConnection);
    }

    @Override
    public void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token) {
        database.fetchSnapshot(databaseConnection, base64Token);
    }

    @Override
    public boolean isReadOnly(String datasetName) {
        return database.isReadOnly(datasetName);
    }

    @Override
    public void enableEditing(String datasetName) {
        database.enableEditing(datasetName);
    }

    @Override
    public void disableEditing(String datasetName) {
        database.disableEditing(datasetName);
    }
}
