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

import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.TestRDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemorySparqlExecutorTest {

    private List<Graph> exampleGraphs;

    private Graph setUpGraph;

    private SessionDataStoreImpl inMemoryDatabase;

    @BeforeEach
    void setUp() {
        String datasetName = UUID.randomUUID().toString();
        setUpGraph = GraphFactory.createDefaultGraph();
        inMemoryDatabase = new SessionDataStoreImpl();
        inMemoryDatabase.create(new GraphIdentifier(datasetName, "Http://validBeforeEach.uri"), setUpGraph);
    }

    @AfterEach
    void tearDown() {
        inMemoryDatabase.listDatasets().forEach(inMemoryDatabase::deleteDataset);
        exampleGraphs.forEach(Graph::close);
        exampleGraphs = null;
        setUpGraph.close();
        setUpGraph = null;
    }

    Graph createExampleGraph() {
        var graph = GraphFactory.createDefaultGraph();
        graph.add(TestRDFUtils.triple("a a a"));
        graph.add(TestRDFUtils.triple("a a b"));
        graph.add(TestRDFUtils.triple("a a c"));
        graph.add(TestRDFUtils.triple("a b a"));
        graph.add(TestRDFUtils.triple("a b b"));
        graph.add(TestRDFUtils.triple("a b c"));
        graph.add(TestRDFUtils.triple("a c a"));
        graph.add(TestRDFUtils.triple("a c b"));
        graph.add(TestRDFUtils.triple("a c c"));
        return graph;
    }

    @Test
    void executeSingleQuery_onDefaultWithValidAllQuery_shouldReturnAllResults() {
        // Arrange
        String graphUri = "default";
        exampleGraphs = List.of(
                  createExampleGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(0), 20, 5);
        String queryString = "SELECT * WHERE { ?s ?p ?o }";
        var query = QueryFactory.create(queryString);

        // Act
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graphRewindable, query, graphUri);

        // Assert
        assertNotNull(resultSet);
        assertThat(resultSet.getResultVars()).hasSize(3);
        while (resultSet.hasNext()) {
            var result = resultSet.next();
            assertThat(result.get("s")).isNotNull();
            assertThat(result.get("p")).isNotNull();
            assertThat(result.get("o")).isNotNull();
        }
        assertThat(resultSet.getRowNumber()).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://anyUri", "http://example.org/graph"})
    void executeSingleQuery_onNamedWithValidAllQuery_shouldReturnAllResults(String graphUri) {
        // Arrange
        exampleGraphs = List.of(
                  createExampleGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(0), 20, 5);
        String queryString = "SELECT * FROM <" + graphUri + "> WHERE { ?s ?p ?o }";
        var query = QueryFactory.create(queryString);

        // Act
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graphRewindable, query, graphUri);

        // Assert
        assertNotNull(resultSet);
        assertThat(resultSet.getResultVars()).hasSize(3);
        while (resultSet.hasNext()) {
            var result = resultSet.next();
            assertThat(result.get("s")).isNotNull();
            assertThat(result.get("p")).isNotNull();
            assertThat(result.get("o")).isNotNull();
        }
        assertThat(resultSet.getRowNumber()).isEqualTo(9);
    }

    @Test
    void executeSingleQuery_onDefaultWithValidNoResultQuery_shouldReturnEmptyResultSet() {
        // Arrange
        String graphUri = "default";
        exampleGraphs = List.of(
                  createExampleGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(0), 20, 5);
        String queryString = "SELECT * WHERE { ?s ?p ?o . FILTER(?p = <http://nonexisting>) }";
        var query = QueryFactory.create(queryString);

        // Act
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graphRewindable, query, graphUri);

        // Assert
        assertNotNull(resultSet);
        assertThat(resultSet.getResultVars()).hasSize(3);
        assertThat(resultSet.hasNext()).isFalse();
        assertThat(resultSet.getRowNumber()).isZero();
    }

    @Test
    void executeSingleQuery_onNamedWithValidNoResultQuery_shouldReturnEmptyResultSet() {
        // Arrange
        String graphUri = "http://example.org/graph";
        exampleGraphs = List.of(
                  createExampleGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(0), 20, 5);
        String queryString = "SELECT * FROM <" + graphUri + "> WHERE { ?s ?p ?o . FILTER(?p = <http://nonexisting>) }";
        var query = QueryFactory.create(queryString);

        // Act
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graphRewindable, query, graphUri);

        // Assert
        assertNotNull(resultSet);
        assertThat(resultSet.getResultVars()).hasSize(3);
        assertThat(resultSet.hasNext()).isFalse();
        assertThat(resultSet.getRowNumber()).isZero();
    }

    @Test
    void executeSingleQuery_queryNotSelectedExistentGraph_emptyResult() {
        // Arrange
        String graphUri = "default";
        exampleGraphs = List.of(
                  createExampleGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(0), 20, 5);
        String queryString = "SELECT * FROM <http://nonexisting> WHERE { ?s ?p ?o }";
        var query = QueryFactory.create(queryString);

        // Act
        var resultSet = InMemorySparqlExecutor.executeSingleQuery(graphRewindable, query, graphUri);

        // Assert
        assertNotNull(resultSet);
        assertThat(resultSet.getResultVars()).hasSize(3);
        assertThat(resultSet.hasNext()).isFalse();
        assertThat(resultSet.getRowNumber()).isZero();
    }

    @Test
    void executeSingleUpdate_onDefaultWithValidUpdate_shouldUpdateGraph() {
        // Arrange
        String graphUri = "default";
        exampleGraphs = List.of(
                  createExampleGraph(),
                  GraphFactory.createDefaultGraph()
                               );
        var graphRewindable = new GraphRewindableWithUUIDs(exampleGraphs.get(1), 20, 5);

        //Act
        for (var t : exampleGraphs.get(0).find().toList()) {
            var update = new UpdateBuilder()
                      .addInsert(t)
                      .addOptional(Node.ANY, Node.ANY, Node.ANY)
                      .build();
            InMemorySparqlExecutor.executeSingleUpdate(graphRewindable, update, graphUri);
        }

        //Assert
        try {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.isIsomorphicWith(exampleGraphs.get(0))).isTrue();
        } finally {
            graphRewindable.end();
        }
    }
}
