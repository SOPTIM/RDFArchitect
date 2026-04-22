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
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SessionDataStore {

    static Dataset wrapGraphInDataset(Graph graph, String graphUri) {
        if (graphUri == null || graphUri.equals("default")) {
            return DatasetFactory.wrap(ModelFactory.createModelForGraph(graph));
        }
        return DatasetFactory.createGeneral().addNamedModel(graphUri, ModelFactory.createModelForGraph(graph));
    }

    /**
     * Deletes a complete Dataset with all containing graphs. Waits for ongoing transactions on individual graphs before deleting.
     *
     * @param datasetName The name of the Dataset to be deleted.
     */
    void deleteDataset(String datasetName);

    List<String> listDatasets();

    /**
     * Begin a transaction on a {@link GraphRewindableWithUUIDs}.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     * @param txnType         The transactionType
     *
     * @return The {@link GraphRewindableWithUUIDs} the {@link Transactional Transaction} is performed on.
     */
    GraphRewindableWithUUIDs begin(GraphIdentifier graphIdentifier, TxnType txnType);

    /**
     * Get a {@link GraphWithContext} from the database.
     *
     * @param graphIdentifier The identifier of the graph.
     *
     * @return The {@link GraphWithContext}.
     */
    GraphWithContext getGraphWithContext(GraphIdentifier graphIdentifier);

    /**
     * Get all {@link CustomDiagram} for a dataset.
     *
     * @param datasetName literal dataset name
     *
     * @return map of custom diagrams belonging to the dataset
     */
    Map<UUID, CustomDiagram> getDatasetDiagrams(String datasetName);

    /**
     * Get the {@link DiagramLayout} for all custom diagrams defined on a dataset
     *
     * @param datasetName literal dataset name
     *
     * @return diagram layout for the dataset
     */
    DiagramLayout getDatasetDiagramLayout(String datasetName);

    /**
     * Creates a new {@link GraphRewindableWithUUIDs} in a specified dataset. If the dataset does not exist yet, it will be created.
     * If the {@link GraphRewindableWithUUIDs} already exists nothing happens.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     * @param newGraph        The new Graph.
     */
    void create(GraphIdentifier graphIdentifier, Graph newGraph);

    /**
     * Deletes a {@link GraphRewindableWithUUIDs} from a specified dataset. If the graph or dataset does not exist, nothing happens.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     */
    void remove(GraphIdentifier graphIdentifier);

    /**
     * Checks whether a Graph exists in a specified dataset.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @return True if the graph exists, otherwise False.
     */
    boolean containsGraph(GraphIdentifier graphIdentifier);

    /**
     * List all graphs contained in a specified Dataset
     *
     * @param datasetName The name of the dataset.
     *
     * @return A list of GraphUris
     *
     * @throws DataAccessException if the dataset does not exist.
     */
    List<String> listGraphUris(String datasetName);

    /**
     * Lists the prefixes belonging to a specified dataset.
     *
     * @param datasetName The name of the dataset.
     *
     * @return {@link PrefixMappingReadOnly}
     *
     * @throws DataAccessException if the dataset does not exist.
     */
    PrefixMappingReadOnly getPrefixMapping(String datasetName);

    /**
     * Replace all prefixes in a specified dataset.
     *
     * @param datasetName The name of the dataset.
     * @param newPrefixes the new Prefixes.
     */
    void setPrefixMapping(String datasetName, PrefixMapping newPrefixes);

    /**
     * Writes a specified graph to a database.
     *
     * @param databaseConnection The connection to the persistent Database.
     * @param graphIdentifier    The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    void writeToDatabase(DatabaseConnection databaseConnection, GraphIdentifier graphIdentifier);

    /**
     * Drops the contents of this {@link SessionDataStoreImpl} and releases all its resources, then fetches the state of an external database and writes it to this
     * {@link SessionDataStoreImpl}.
     *
     * @param databaseConnection The connection to the external Database.
     */
    void fetchFromDatabase(DatabaseConnection databaseConnection);

    /**
     * Fetches the snapshot identified by the provided Base64 token and inserts it into the currently displayed data.
     *
     * @param databaseConnection The connection to the external Database.
     * @param base64Token        The Base64 token under which the snapshot has been persisted in the database
     */
    void fetchSnapshot(DatabaseConnection databaseConnection, String base64Token);

    /**
     * Undoes the last change made to a graph.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    void undo(GraphIdentifier graphIdentifier);

    /**
     * Redoes the last previously undone change made to a graph.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    void redo(GraphIdentifier graphIdentifier);

    /**
     * Checks whether the last change made to a graph can be undone.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @return True if the last change can be undone, otherwise False.
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    boolean canUndo(GraphIdentifier graphIdentifier);

    /**
     * Checks whether the last undone change can be redone.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     *
     * @return True if the last undone change can be redone, otherwise False.
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    boolean canRedo(GraphIdentifier graphIdentifier);

    /**
     * Checks if a dataset is currently set to read-only.
     *
     * @param datasetName The name of the dataset.
     *
     * @return true if the dataset is set to read-only, otherwise false
     *
     * @throws DataAccessException if the dataset or graph does not exist.
     */
    boolean isReadOnly(String datasetName);

    /**
     * Enables editing for a read-only dataset
     *
     * @param datasetName The name of the dataset.
     *
     * @throws DataAccessException if the dataset does not exist.
     */
    void enableEditing(String datasetName);

    /**
     * Disables editing for a dataset, making it read-only again.
     *
     * @param datasetName The name of the dataset.
     *
     * @throws DataAccessException if the dataset does not exist.
     */
    void disableEditing(String datasetName);

    /**
     * Restores a graph to a specific version.
     *
     * @param graphIdentifier The identifier of the graph, which includes the dataset name and the graph URI.
     * @param versionId       The ID of the version to restore to.
     *
     * @throws DataAccessException if the graph does not exist.
     */
    void restore(GraphIdentifier graphIdentifier, UUID versionId);
}
