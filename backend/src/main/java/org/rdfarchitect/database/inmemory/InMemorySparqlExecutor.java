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
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateRequest;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.function.Function;

@UtilityClass
public class InMemorySparqlExecutor {

    public void executeSingleUpdate(
            GraphRewindableWithUUIDs graph, UpdateRequest update, String graphUri) {
        executeSingleUpdate(graph, graphUri, g -> update);
    }

    public void executeSingleUpdate(
            GraphRewindableWithUUIDs graph, Update update, String graphUri) {
        executeSingleUpdate(graph, graphUri, g -> new UpdateRequest().add(update));
    }

    /**
     * Opens a single WRITE transaction, lets {@code updateBuilder} build the {@link UpdateRequest}
     * with reads on the open transaction (so reads-then-writes happen atomically and stay
     * consistent), and executes the resulting update.
     */
    public void executeSingleUpdate(
            GraphRewindableWithUUIDs graph,
            String graphUri,
            Function<GraphRewindableWithUUIDs, UpdateRequest> updateBuilder) {
        try {
            graph.begin(TxnType.WRITE);
            var update = updateBuilder.apply(graph);
            var dataset = SessionDataStore.wrapGraphInDataset(graph, graphUri);
            UpdateExecutionFactory.create(update, dataset).execute();
            graph.commit();
        } finally {
            graph.end();
        }
    }

    public ResultSet executeSingleQuery(
            GraphRewindableWithUUIDs graph, Query query, String graphUri) {
        QueryExecution queryExecution = null;
        try {
            graph.begin(TxnType.READ);
            var dataset = SessionDataStore.wrapGraphInDataset(graph, graphUri);
            queryExecution = QueryExecutionFactory.create(query, dataset);
            var resultSet = queryExecution.execSelect();
            return ResultSetFactory.copyResults(resultSet);
        } finally {
            graph.end();
            if (queryExecution != null) {
                queryExecution.close();
            }
        }
    }
}
