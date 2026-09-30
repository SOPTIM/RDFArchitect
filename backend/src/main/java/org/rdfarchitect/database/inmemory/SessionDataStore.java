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
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.UUID;

public interface SessionDataStore {

    static Dataset wrapGraphInDataset(Graph graph, String graphUri) {
        if (graphUri == null || graphUri.equals("default")) {
            return DatasetFactory.wrap(ModelFactory.createModelForGraph(graph));
        }
        return DatasetFactory.createGeneral()
                .addNamedModel(graphUri, ModelFactory.createModelForGraph(graph));
    }

    /**
     * Creates an empty Dataset without any graphs. If the dataset already exists, nothing happens.
     *
     * @param datasetName The name of the Dataset to be created.
     */
    void createDataset(String datasetName);

    /**
     * Deletes a complete Dataset with all containing graphs. Waits for ongoing transactions on
     * individual graphs before deleting.
     *
     * @param datasetName The name of the Dataset to be deleted.
     */
    void deleteDataset(String datasetName);

    /**
     * Renames a Dataset, keeping all of its graphs, diagrams and namespaces.
     *
     * @param oldDatasetName The current name of the Dataset.
     * @param newDatasetName The name to rename the Dataset to.
     * @throws DataAccessException if the Dataset does not exist or the new name is already taken.
     */
    void renameDataset(String oldDatasetName, String newDatasetName);

    /**
     * Lists the names of all workspaces of this session.
     *
     * @return the workspace names
     */
    List<String> listDatasets();

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
     * Checks whether a Graph exists in a specified dataset.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the
     *     graph URI.
     * @return True if the graph exists, otherwise False.
     */
    boolean containsGraph(GraphIdentifier graphIdentifier);

    /**
     * List all graphs contained in a specified Dataset
     *
     * @param datasetName The name of the dataset.
     * @return A list of GraphUris
     * @throws DataAccessException if the dataset does not exist.
     */
    List<String> listGraphUris(String datasetName);

    /**
     * Lists the prefixes belonging to a specified dataset.
     *
     * @param datasetName The name of the dataset.
     * @return {@link PrefixMappingReadOnly}
     * @throws DataAccessException if the dataset does not exist.
     */
    PrefixMappingReadOnly getPrefixMapping(String datasetName);

    /**
     * Writes a specified graph to a database.
     *
     * @param databaseConnection The connection to the persistent Database.
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the
     *     graph URI.
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    void writeToDatabase(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier);

    /**
     * Drops the contents of this {@link SessionDataStoreImpl} and releases all its resources, then
     * fetches the state of an external database and writes it to this {@link SessionDataStoreImpl}.
     *
     * @param databaseConnection The connection to the external Database.
     */
    void fetchFromDatabase(DatabaseConnection databaseConnection);

    /**
     * Fetches the snapshot identified by the provided Base64 token and inserts it into the
     * currently displayed data.
     *
     * @param databaseConnection The connection to the external Database.
     * @param base64Token The Base64 token under which the snapshot has been persisted in the
     *     database
     */
    void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token);

    /**
     * Checks if a dataset is currently set to read-only.
     *
     * @param datasetName The name of the dataset.
     * @return true if the dataset is set to read-only, otherwise false
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    boolean isReadOnly(String datasetName);

    /**
     * Enables editing for a read-only dataset
     *
     * @param datasetName The name of the dataset.
     * @throws DataAccessException if the dataset does not exist.
     */
    void enableEditing(String datasetName);

    /**
     * Disables editing for a dataset, making it read-only again.
     *
     * @param datasetName The name of the dataset.
     * @throws DataAccessException if the dataset does not exist.
     */
    void disableEditing(String datasetName);
}
