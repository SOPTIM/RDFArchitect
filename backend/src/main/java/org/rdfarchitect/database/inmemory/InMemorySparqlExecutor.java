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

import lombok.experimental.UtilityClass;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;

@UtilityClass
public class InMemorySparqlExecutor {

    /**
     * Runs a query against a single graph, opening a read transaction on its workspace. Joins the
     * caller's transaction when there already is one.
     *
     * @param databasePort the database to read from
     * @param graphIdentifier identifies workspace and graph
     * @param query the query to run
     * @param graphUri the name to expose the graph under, or {@code null} for the default graph
     * @return the query result
     */
    public ResultSet executeSingleQuery(
            DatabasePort databasePort,
            GraphIdentifier graphIdentifier,
            Query query,
            String graphUri) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.READ)) {
            var graph = transaction.graph(graphIdentifier.graphUri()).getRdfGraph();
            var dataset = SessionDataStore.wrapGraphInDataset(graph, graphUri);
            try (var queryExecution = QueryExecutionFactory.create(query, dataset)) {
                var resultSet = queryExecution.execSelect();
                return ResultSetFactory.copyResults(resultSet);
            }
        }
    }
}
