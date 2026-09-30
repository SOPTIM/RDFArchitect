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

package org.rdfarchitect.database;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.UUID;

public interface DatabasePort {

    /**
     * Begins a transaction on a workspace. Graphs, custom diagrams and layout are reachable only
     * through the returned transaction, so that a change spanning several graphs commits or rolls
     * back as a whole.
     *
     * @param workspaceName literal workspace name
     * @param mode the transaction mode
     * @return the running transaction, to be used in try-with-resources
     */
    WorkspaceTransaction beginTransaction(String workspaceName, ReadWrite mode);

    /**
     * Returns whether the workspace has a change that can be undone.
     *
     * @param workspaceName literal workspace name
     * @return {@code true} if there is something to undo
     */
    boolean canUndo(String workspaceName);

    /**
     * Returns whether the workspace has an undone change that can be reapplied.
     *
     * @param workspaceName literal workspace name
     * @return {@code true} if there is something to redo
     */
    boolean canRedo(String workspaceName);

    /**
     * Rolls back the most recent change anywhere in the workspace.
     *
     * @param workspaceName literal workspace name
     * @return the change that was undone
     */
    WorkspaceChangeLogEntry undo(String workspaceName);

    /**
     * Reapplies the most recently undone change of the workspace.
     *
     * @param workspaceName literal workspace name
     * @return the change that was redone
     */
    WorkspaceChangeLogEntry redo(String workspaceName);

    /**
     * Rolls the workspace back to the given version.
     *
     * @param workspaceName literal workspace name
     * @param versionId the change to restore to
     */
    void restoreToVersion(String workspaceName, UUID versionId);

    /**
     * Returns the recorded changes of the workspace, newest first.
     *
     * @param workspaceName literal workspace name
     * @return the change history
     */
    List<WorkspaceChangeLogEntry> listChanges(String workspaceName);

    /**
     * Loads the namespace prefix mapping for the dataset.
     *
     * @param datasetName literal dataset name
     * @return prefix mapping associated with the dataset
     */
    PrefixMapping getPrefixMapping(String datasetName);

    /**
     * Lists all graph URIs belonging to the dataset.
     *
     * @param datasetName literal dataset name
     * @return graph URIs within the dataset
     */
    List<String> listGraphUris(String datasetName);

    /**
     * Persists pending changes of the graph to the backing database.
     *
     * @param databaseConnection resolved database connection
     * @param graphIdentifier identifies dataset and graph URI
     */
    void persist(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier);

    /**
     * Lists all available dataset names.
     *
     * @return dataset names managed by the persistence layer
     */
    List<String> listDatasets();

    /**
     * Creates an empty dataset without any graphs.
     *
     * @param datasetName the literal dataset name to create
     * @throws org.rdfarchitect.exception.database.ResourceConflictException if a dataset with that
     *     name already exists
     */
    void createDataset(String datasetName);

    /**
     * Creates the workspace unless it already exists. For uploads that address a workspace by name
     * and are expected to bring it into existence; everything else must create it explicitly.
     *
     * @param workspaceName the literal workspace name
     */
    void createWorkspaceIfAbsent(String workspaceName);

    /**
     * Removes the dataset identified by {@code datasetName} and clears all graphs that belong to
     * it.
     *
     * @param datasetName the literal dataset name to delete
     */
    void deleteDataset(String datasetName);

    /**
     * Renames a dataset, keeping all of its graphs, diagrams and namespaces.
     *
     * @param oldDatasetName the current literal dataset name
     * @param newDatasetName the literal dataset name to rename to
     */
    void renameDataset(String oldDatasetName, String newDatasetName);

    /**
     * Synchronizes dataset metadata and graph structure from the backing database.
     *
     * @param databaseConnection resolved database connection
     */
    void fetchFromDatabase(DatabaseConnection databaseConnection);

    /**
     * Loads a snapshot represented by {@code base64Token} into the resolved connection.
     *
     * @param databaseConnection resolved database connection
     * @param base64Token base64 encoded snapshot payload
     */
    void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token);

    /**
     * Indicates whether the dataset is currently read-only.
     *
     * @param datasetName literal dataset name
     * @return {@code true} if editing is disabled, otherwise {@code false}
     */
    boolean isReadOnly(String datasetName);

    /**
     * Enables editing operations on the dataset.
     *
     * @param datasetName literal dataset name
     */
    void enableEditing(String datasetName);

    /**
     * Disables editing operations on the dataset, turning it read-only again.
     *
     * @param datasetName literal dataset name
     */
    void disableEditing(String datasetName);
}
