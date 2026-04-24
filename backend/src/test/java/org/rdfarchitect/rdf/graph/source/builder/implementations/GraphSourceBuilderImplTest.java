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

package org.rdfarchitect.rdf.graph.source.builder.implementations;

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.TestRDFUtils;

class GraphSourceBuilderImplTest {

    @Test
    void build_noGraph_throwsDataAccessException() {
        // Arrange
        GraphSourceBuilderImpl builder = new GraphSourceBuilderImpl();

        // Act
        builder.setGraphName("test");

        // Assert
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(builder::build);
    }

    @Test
    void build_noGraphName_graphHasDefaultGraphName() {
        // Arrange
        GraphSourceBuilderImpl builder = new GraphSourceBuilderImpl();
        builder.setGraph(GraphFactory.createDefaultGraph());

        // Assert/Act
        assertThat(builder.build().graphName()).isEqualTo("default");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:1",
                "https://localhost:1",
                "http://www.google.de",
                "http://",
                "https://",
                "default"
            })
    void setGraphName_validName_nameIsSetInBuiltObject(String graphUri) {
        // Arrange
        GraphSourceBuilderImpl builder = new GraphSourceBuilderImpl();

        // Act
        builder.setGraph(GraphFactory.createDefaultGraph());
        builder.setGraphName(graphUri);

        // Assert
        assertThat(builder.build().graphName()).isEqualTo(graphUri);
    }

    @Test
    void build_validEmptyGraph_graphIsSetInBuiltObject() {
        // Arrange
        GraphSourceBuilderImpl builder = new GraphSourceBuilderImpl();
        var graph = GraphFactory.createDefaultGraph();
        builder.setGraph(graph);

        // Act
        var builtGraphSource = builder.build();

        // Assert
        assertThat(builtGraphSource.graph()).isEqualTo(graph);
    }

    @Test
    void build_validNonEmptyGraphAndGraphName_nameAndGraphAreSetInBuiltObject() {
        // Arrange
        GraphSourceBuilderImpl builder = new GraphSourceBuilderImpl();
        var graph = GraphFactory.createDefaultGraph();
        graph.add(TestRDFUtils.triple("a a a"));
        graph.add(TestRDFUtils.triple("a a b"));
        builder.setGraph(graph);

        // Act
        var builtGraphSource = builder.build();

        // Assert
        assertThat(builtGraphSource.graph()).isEqualTo(graph);
        assertThat(builtGraphSource.graph().find().toList())
                .contains(TestRDFUtils.triple("a a a"), TestRDFUtils.triple("a a b"))
                .hasSize(2);
    }
}
