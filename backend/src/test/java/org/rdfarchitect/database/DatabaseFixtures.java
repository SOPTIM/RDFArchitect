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
import org.apache.jena.query.ReadWrite;

/** Setting up a {@link DatabasePort} for tests of what reads through it. */
public final class DatabaseFixtures {

    private DatabaseFixtures() {}

    /** Creates the graph, and its workspace if there is none yet, as one commit. */
    public static void createGraph(
            DatabasePort databasePort, GraphIdentifier graphIdentifier, Graph graph) {
        databasePort.createWorkspaceIfAbsent(graphIdentifier.datasetName());
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(graphIdentifier.graphUri(), graph);
            transaction.commit("Created the graph");
        }
    }
}
