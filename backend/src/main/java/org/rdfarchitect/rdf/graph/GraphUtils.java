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

package org.rdfarchitect.rdf.graph;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.graph.GraphFactory;

/**
 * Utility class for graph operations
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GraphUtils {

    public static Graph deepCopy(Graph graph) {
        var newGraph = GraphFactory.createDefaultGraph();
        var iterator = graph.find();
        while (iterator.hasNext()) {
            newGraph.add(iterator.next());
        }
        return newGraph;
    }
}
