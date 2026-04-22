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

import org.apache.jena.query.Query;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.update.Update;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;
import org.rdfarchitect.rdf.graph.source.GraphSource;

import java.util.List;

/** Defines how this application communicates with a database. */
public interface DatabaseConnection {

    // insert

    /**
     * Executes an {@link Update insertUpdate} against the default dataset of the database.
     *
     * @param insertUpdate {@link Update Insert update} to be executed
     */
    void sendInsert(Update insertUpdate) throws DataAccessException;

    /**
     * Executes an {@link Update insertUpdate} against a specific dataset in the database.
     *
     * @param insertUpdate {@link Update Insert update} to be executed
     * @param datasetName Name of the dataset
     */
    void sendInsert(Update insertUpdate, String datasetName) throws DataAccessException;

    /**
     * Inserts a graph via a {@link GraphSource} into the default dataset of the database.
     *
     * @param graphSource Source of a graph
     */
    void insertGraph(GraphSource graphSource) throws DataAccessException;

    /**
     * Inserts a graph via a {@link GraphSource} into a specific dataset of the graph.
     *
     * @param graphSource Source of a graph
     * @param datasetName Name of the dataset
     */
    void insertGraph(GraphSource graphSource, String datasetName) throws DataAccessException;

    // delete

    /**
     * Executes a {@link Update deleteUpdate} against the default dataset of the database.
     *
     * @param deleteUpdate {@link Update DeleteUpdate} to be executed
     */
    void sendDelete(Update deleteUpdate) throws DataAccessException;

    /**
     * Executes a {@link Update deleteUpdate} against a specific dataset of the database.
     *
     * @param deleteUpdate {@link Update DeleteUpdate} to be executed
     * @param datasetName Name of the dataset
     */
    void sendDelete(Update deleteUpdate, String datasetName) throws DataAccessException;

    /**
     * Deletes a specific graph from the default dataset of the database.
     *
     * @param graphName Name of the graph to be deleted
     */
    void deleteGraph(String graphName) throws DataAccessException;

    /**
     * Deletes a specific graph from a specific dataset of the database.
     *
     * @param graphName Name of the graph to be deleted
     * @param datasetName Name of the dataset
     */
    void deleteGraph(String datasetName, String graphName) throws DataAccessException;

    /** Deletes the default graph from the default dataset of the database. */
    void deleteDefaultGraph() throws DataAccessException;

    /**
     * Deletes the default graph from a specific dataset of the database.
     *
     * @param datasetName Name of the dataset
     */
    void deleteDefaultGraph(String datasetName) throws DataAccessException;

    /** Deletes the default dataset from a database. */
    void deleteDataset();

    /**
     * Deletes a specified dataset from a database
     *
     * @param datasetName Name of the dataset to be deleted
     */
    void deleteDataset(String datasetName);

    // update

    /**
     * Executes an {@link Update update} against the default dataset of the database.
     *
     * @param update {@link Update} to be executed
     */
    void sendUpdate(Update update) throws DataAccessException;

    /**
     * Executes an {@link Update update} against a specific dataset of the database.
     *
     * @param update {@link Update} to be executed
     * @param datasetName Name of the dataset
     */
    void sendUpdate(Update update, String datasetName) throws DataAccessException;

    // select

    /**
     * Executes a {@link Query} against the default dataset of the database.
     *
     * @param query {@link Query} to be executed
     * @return {@link ResultFormatter} for formatted query results
     */
    ResultFormatter sendSelect(Query query) throws DataAccessException;

    /**
     * Executes a {@link Query} against a specific dataset of the database.
     *
     * @param query {@link Query} to be executed
     * @param datasetName Name of the dataset
     * @return {@link ResultFormatter} for formatted query results
     */
    ResultFormatter sendSelect(Query query, String datasetName) throws DataAccessException;

    /**
     * Get the {@link PrefixMapping} of a specified dataset from the database.
     *
     * @param datasetName Name of the dataset.
     * @return {@link PrefixMappingReadOnly}
     */
    PrefixMappingReadOnly getPrefixMapping(String datasetName);

    /**
     * Appends a new prefix substitution to a datasets current prefixes. If a prefix with the same
     * substitute already exist, it will be replaced.
     *
     * @param datasetName Name of the dataset.
     * @param prefix the substituted prefix
     * @param uri the extended prefix
     */
    void addPrefix(String datasetName, String prefix, String uri);

    /**
     * Delete a prefix from a specified dataset.
     *
     * @param datasetName Name of the dataset.
     * @param prefix The substituted prefix.
     */
    void deletePrefix(String datasetName, String prefix);

    /**
     * List the names of all datasets in this database.
     *
     * @return a list of dataset names
     */
    List<String> listDatasets();
}
