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

package org.rdfarchitect.rdf.graph.source.implementations;

import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GraphSourceImplTest {

    @Test
    void getGraph_validGraph_returnsSameGraph() {
        // Arrange
        Graph graph = GraphFactory.createDefaultGraph();
        GraphSourceImpl graphSource = new GraphSourceImpl(graph, "any");

        // Act
        Graph resultGraph = graphSource.graph();

        // Assert
        assertThat(resultGraph).isEqualTo(graph);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:1", "https://localhost:1", "http://www.google.de", "http://", "https://", "default"})
    void getGraphName_validGraphName_returnsSameGraphName(String graphName) {
        // Arrange
        Graph graph = GraphFactory.createDefaultGraph();
        GraphSourceImpl graphSource = new GraphSourceImpl(graph, graphName);

        // Act
        String resultGraphName = graphSource.graphName();

        // Assert
        assertThat(resultGraphName).isEqualTo(graphName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:1", "https://localhost:1", "http://www.google.de", "http://", "https://", "default"})
    void getGraphSourceType_anyInput_returnsSameGraphSourceType(String graphName) {
        // Arrange
        Graph graph = GraphFactory.createDefaultGraph();
        GraphSourceImpl graphSource = new GraphSourceImpl(graph, graphName);

        // Act
        String resultGraphSourceType = graphSource.getGraphSourceType();

        // Assert
        assertThat(resultGraphSourceType).isEqualTo("graph");
    }
}