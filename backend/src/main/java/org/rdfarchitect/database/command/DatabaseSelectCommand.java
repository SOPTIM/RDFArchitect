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

import org.apache.jena.query.Query;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.formatter.ResultFormatter;

import java.util.List;

/** Defines how to execute a SPARQL {@link Query} against a dataset of a database. */
public interface DatabaseSelectCommand {

    /**
     * Sets the endpoint where the database is accessible.
     *
     * @param endpoint Endpoint (e.g. url, path)
     * @return This {@link DatabaseSelectCommand}
     */
    DatabaseSelectCommand setEndpoint(String endpoint);

    /**
     * Sets the name of the dataset in the database a {@link Query} is executed against.
     *
     * @param datasetName Name of the dataset
     * @return This {@link DatabaseSelectCommand}
     */
    DatabaseSelectCommand setDatasetName(String datasetName);

    /**
     * Sets the {@link Query} to be executed.
     *
     * @param query {@link Query}
     * @return This {@link DatabaseSelectCommand object} with the select query set
     */
    DatabaseSelectCommand setQuery(Query query);

    /**
     * Executes the {@link Query} against the specified dataset of the database.
     *
     * @return result as a {@link ResultFormatter} object
     */
    ResultFormatter execute() throws DataAccessException;

    /** Get the {@link PrefixMapping} of a specified dataset from the database. */
    PrefixMapping getCurrentPrefixMapping();

    /**
     * List the names of all datasets in this database.
     *
     * @return a list of dataset names
     */
    List<String> listDatasets();
}
