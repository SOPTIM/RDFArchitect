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

package org.rdfarchitect.database.command;

import org.apache.jena.graph.Graph;
import org.apache.jena.update.Update;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.source.GraphSource;

/** Defines how to execute a SPARQL {@link Update} against a dataset of a database. */
public interface DatabaseUpdateCommand {

    /**
     * Sets the endpoint where the database is accessible.
     *
     * @param endpoint Endpoint (e.g. url, path)
     * @return This {@link DatabaseUpdateCommand}
     */
    DatabaseUpdateCommand setEndpoint(String endpoint);

    /**
     * Sets the name of the dataset in the database the {@link Update} is against.
     *
     * @param datasetName Name of the dataset
     * @return This {@link DatabaseUpdateCommand}
     */
    DatabaseUpdateCommand setDatasetName(String datasetName) throws DataAccessException;

    /**
     * Sets the {@link Update} to be executed.
     *
     * @param update {@link Update}
     * @return This {@link DatabaseUpdateCommand object} with the update query set
     */
    DatabaseUpdateCommand setUpdate(Update update);

    /** Executes the {@link Update} against the specified dataset of the database. */
    void execute() throws DataAccessException;

    /**
     * Inserts a graph into the specified dataset of the database.
     *
     * @param graphSource {@link GraphSource} containing the to be inserted {@link Graph}
     */
    void insertGraph(GraphSource graphSource) throws DataAccessException;

    /**
     * Deletes a graph from the specified dataset of the database.
     *
     * @param graphName name of the graph to be deleted
     */
    void deleteGraph(String graphName);

    /** Deletes the specified dataset from the database. */
    void deleteDataset();

    /**
     * Appends a new prefix substitution to a datasets current prefixes. If a prefix with the same
     * substitute already exist, it will be replaced.
     *
     * @param prefix the substituted prefix
     * @param uri the extended prefix
     */
    void addPrefix(String prefix, String uri);

    /**
     * Delete a prefix from a specified dataset.
     *
     * @param prefix The substituted prefix.
     */
    void deletePrefix(String prefix);
}
