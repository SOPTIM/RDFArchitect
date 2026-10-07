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
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;

import java.util.Map;
import java.util.UUID;

/**
 * The contents of a single named graph: its RDF schema, its diagram layout, its custom SHACL and
 * its custom diagrams.
 *
 * <p>Carries no transaction of its own. Graphs take part in the transaction of the workspace that
 * holds them and are reachable only through {@link WorkspaceTransaction#graph(String)}, so a graph
 * cannot be touched outside a transaction.
 */
public interface GraphContext {

    /** Returns the RDF schema of the graph. */
    Graph getRdfGraph();

    /** Returns the diagram layout belonging to the graph. */
    DiagramLayout getDiagramLayout();

    /** Returns the custom SHACL shapes of the graph. */
    Graph getCustomSHACL();

    /** Returns the custom diagrams defined on the graph, by id. */
    Map<UUID, CustomDiagram> getCustomDiagrams();
}
