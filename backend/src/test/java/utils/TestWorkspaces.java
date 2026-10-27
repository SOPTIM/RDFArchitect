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

package utils;

import lombok.experimental.UtilityClass;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.SessionDataStore;

/** Sets up the graphs a test works on. */
@UtilityClass
public class TestWorkspaces {

    /**
     * Creates a workspace and a graph in it, the way an upload does.
     *
     * @param databasePort the port under test
     * @param identifier the workspace and graph to create
     * @param graph the graph contents
     */
    public static void createGraph(
            DatabasePort databasePort, GraphIdentifier identifier, Graph graph) {
        databasePort.createWorkspaceIfAbsent(identifier.datasetName());
        try (var transaction =
                databasePort.beginTransaction(identifier.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(identifier.graphUri(), graph);
            transaction.commit("created graph %s".formatted(identifier.graphUri()));
        }
    }

    /**
     * Creates a workspace and a graph in it, for a test that holds the store itself rather than the
     * port in front of it.
     *
     * @param store the store under test
     * @param identifier the workspace and graph to create
     * @param graph the graph contents
     */
    public static void createGraph(
            SessionDataStore store, GraphIdentifier identifier, Graph graph) {
        store.createDatasetIfAbsent(identifier.datasetName());
        try (var transaction = store.beginTransaction(identifier.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(identifier.graphUri(), graph);
            transaction.commit("created graph %s".formatted(identifier.graphUri()));
        }
    }
}
