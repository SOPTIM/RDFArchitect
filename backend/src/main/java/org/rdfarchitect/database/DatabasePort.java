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

import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.database.inmemory.GraphWithContext;

import java.util.List;
import java.util.UUID;

public interface DatabasePort {

    /**
     * Get a {@link GraphWithContext} from the database.
     *
     * @param graphIdentifier The identifier of the graph.
     * @return {@link GraphWithContext}
     */
    GraphWithContext getGraphWithContext(GraphIdentifier graphIdentifier);

    /**
     * Loads the namespace prefix mapping for the dataset.
     *
     * @param datasetName literal dataset name
     * @return prefix mapping associated with the dataset
     */
    PrefixMapping getPrefixMapping(String datasetName);

    /**
     * Deletes the graph specified by {@code graphIdentifier}.
     *
     * @param graphIdentifier identifies dataset and graph URI
     */
    void deleteGraph(GraphIdentifier graphIdentifier);

    /**
     * Creates or replaces the graph referenced by {@code graphIdentifier} using the supplied RDF
     * content.
     *
     * @param graphIdentifier identifies dataset and graph URI
     * @param graph graph contents to persist
     */
    void createGraph(GraphIdentifier graphIdentifier, Graph graph);

    /**
     * Creates an empty graph referenced by {@code graphIdentifier}.
     *
     * <p>If the dataset does not exist yet, it will be created.
     *
     * @param graphIdentifier identifies dataset and graph URI
     */
    void createEmptyGraph(GraphIdentifier graphIdentifier);

    /**
     * Indicates whether there is a forward change that can be re-applied on the graph.
     *
     * @param graphIdentifier identifies dataset and graph URI
     * @return {@code true} if redo is possible, otherwise {@code false}
     */
    Boolean canRedo(GraphIdentifier graphIdentifier);

    /**
     * Indicates whether there is a previous change that can be undone on the graph.
     *
     * @param graphIdentifier identifies dataset and graph URI
     * @return {@code true} if undo is possible, otherwise {@code false}
     */
    Boolean canUndo(GraphIdentifier graphIdentifier);

    /**
     * Re-applies the next change in the graph history.
     *
     * @param graphIdentifier identifies dataset and graph URI
     */
    void redo(GraphIdentifier graphIdentifier);

    /**
     * Reverts the latest change recorded in the graph history.
     *
     * @param graphIdentifier identifies dataset and graph URI
     */
    void undo(GraphIdentifier graphIdentifier);

    /**
     * Restores a historic version of the graph identified by {@code graphIdentifier}.
     *
     * @param graphIdentifier identifies dataset and graph URI
     * @param versionId historic version to restore
     */
    void restore(GraphIdentifier graphIdentifier, UUID versionId);

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
     * Sets the complete prefix mapping for the dataset, replacing any existing prefixes.
     *
     * @param datasetName literal dataset name
     * @param prefixMapping new prefix mapping to set
     */
    void setPrefixMapping(String datasetName, PrefixMapping prefixMapping);

    /**
     * Lists all available dataset names.
     *
     * @return dataset names managed by the persistence layer
     */
    List<String> listDatasets();

    /**
     * Removes the dataset identified by {@code datasetName} and clears all graphs that belong to
     * it.
     *
     * @param datasetName the literal dataset name to delete
     */
    void deleteDataset(String datasetName);

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
