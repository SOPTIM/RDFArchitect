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

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.TxnType;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingMem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.util.List;
import java.util.Map;

class SessionDataStoreImplTest {

    private static final String DEFAULT_GRAPH_NAME = "default";
    private static final String NAME = "a";
    private static final GraphIdentifier GRAPH_IDENTIFIER =
            new GraphIdentifier(NAME, DEFAULT_GRAPH_NAME);

    private List<Graph> exampleGraphs;

    private SessionDataStoreImpl inMemoryDatabase;

    @BeforeEach
    void setUp() {
        inMemoryDatabase = new SessionDataStoreImpl();
        exampleGraphs = List.of();
    }

    @AfterEach
    void tearDown() {
        inMemoryDatabase.listDatasets().forEach(inMemoryDatabase::deleteDataset);
        exampleGraphs.forEach(Graph::close);
    }

    private Graph createExampleGraph() {
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
    void wrapGraph_defaultGraph_datasetWithDefaultGraphInDataset() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());

        // Act
        var dataset =
                SessionDataStore.wrapGraphInDataset(exampleGraphs.getFirst(), DEFAULT_GRAPH_NAME);
        var datasetNullName = SessionDataStore.wrapGraphInDataset(exampleGraphs.getFirst(), null);

        // Assert
        assertThat(dataset.getDefaultModel().getGraph()).isEqualTo(exampleGraphs.getFirst());
        assertThat(datasetNullName.getDefaultModel().getGraph())
                .isEqualTo(exampleGraphs.getFirst());
    }

    @Test
    void wrapGraph_NamedGraph_datasetWithNamedGraphInDataset() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        var graphName = "http://example.com/graph";

        // Act
        var dataset = SessionDataStore.wrapGraphInDataset(exampleGraphs.getFirst(), graphName);

        // Assert
        assertThat(dataset.getNamedModel(graphName).getGraph()).isEqualTo(exampleGraphs.getFirst());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "anyValid", DEFAULT_GRAPH_NAME})
    void create_validDatasetNameNames_createsEmptyCollections(String name) {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());

        // Act
        inMemoryDatabase.create(
                new GraphIdentifier(name, DEFAULT_GRAPH_NAME), exampleGraphs.getFirst());

        // Assert
        assertThat(inMemoryDatabase.listDatasets()).contains(name);
    }

    @Test
    void deleteDataset_validName_removesDataset() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.deleteDataset(NAME);

        // Assert
        assertThat(inMemoryDatabase.listDatasets()).isEmpty();
    }

    @Test
    void deleteDataset_nonExistingName_doesNothing() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(
                new GraphIdentifier("any", DEFAULT_GRAPH_NAME), exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.deleteDataset("other");

        // Assert
        assertThat(inMemoryDatabase.listDatasets()).containsExactly("any");
    }

    @Test
    void deleteDataset_validName_graphsAreClosed() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graph = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.READ);
        graph.end();

        // Act
        inMemoryDatabase.deleteDataset(NAME);
        graph.begin(TxnType.READ);

        // Assert
        assertThat(graph.isClosed()).isTrue();
        graph.end();
    }

    @Test
    void listDatasets_emptyDatabase_returnsEmptyList() {
        // Arrange

        // Act
        var datasets = inMemoryDatabase.listDatasets();

        // Assert
        assertThat(datasets).isEmpty();
    }

    @Test
    void listDatasets_nonEmptyDatabase_returnsAllDatasets() {
        // Arrange
        exampleGraphs =
                List.of(GraphFactory.createDefaultGraph(), GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        inMemoryDatabase.create(new GraphIdentifier("b", DEFAULT_GRAPH_NAME), exampleGraphs.get(1));

        // Act
        var datasets = inMemoryDatabase.listDatasets();

        // Assert
        assertThat(datasets).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void begin_validNameAndGraph_returnsGraph() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        var graph = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.READ);

        // Assert
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        graph.end();
    }

    @Test
    void begin_nonExistingDatasetAndDefaultGraph_returnsEmptyGraph() {
        // Arrange

        // Act
        var graph = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.READ);

        // Assert
        assertThat(graph.isEmpty()).isTrue();
        graph.end();
    }

    @Test
    void begin_nonExistingDatasetAndNamedGraph_throwsException() {
        // Arrange

        // Act + Assert
        var identifier = new GraphIdentifier("a", "http://example.com/graph");
        assertThatThrownBy(() -> inMemoryDatabase.begin(identifier, TxnType.READ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_existingGraphFromExistingDataset_removesGraph() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.remove(GRAPH_IDENTIFIER);

        // Assert
        assertThat(inMemoryDatabase.containsGraph(GRAPH_IDENTIFIER)).isFalse();
    }

    @Test
    void remove_graphFromNonExistingDataset_doesNothing() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.remove(new GraphIdentifier("b", DEFAULT_GRAPH_NAME));

        // Assert
        assertThat(inMemoryDatabase.listDatasets()).containsExactly("a");
    }

    @Test
    void remove_nonExistingGraphFromExistingDataset_doesNothing() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.remove(new GraphIdentifier(NAME, "http://example.com/graph"));

        // Assert
        assertThat(inMemoryDatabase.containsGraph(GRAPH_IDENTIFIER)).isTrue();
    }

    @Test
    void remove_lastExistingGraph_removesDataset() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.remove(GRAPH_IDENTIFIER);

        // Assert
        assertThat(inMemoryDatabase.listDatasets()).isEmpty();
    }

    @Test
    void containsGraph_validNameAndGraph_returnsTrue() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        var contains = inMemoryDatabase.containsGraph(GRAPH_IDENTIFIER);

        // Assert
        assertThat(contains).isTrue();
    }

    @Test
    void listGraphUris_nonEmptyDataset_returnsAllGraphUris() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());

        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        inMemoryDatabase.create(
                new GraphIdentifier(NAME, "http://example.com/graph"), exampleGraphs.get(1));

        // Act
        var graphUris = inMemoryDatabase.listGraphUris(NAME);

        // Assert
        assertThat(graphUris).contains(DEFAULT_GRAPH_NAME, "http://example.com/graph");
    }

    @Test
    void listGraphUris_nonExistingDataset_throwsException() {
        // Arrange

        // Act
        assertThatThrownBy(() -> inMemoryDatabase.listGraphUris("a"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void getPrefixMapping_validDataset_returnsPrefixMapping() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        var prefixes =
                Map.of(
                        "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                        "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                        "xsd", "http://www.w3.org/2001/XMLSchema#",
                        "owl", "http://www.w3.org/2002/07/owl#",
                        "ex", "http://example.com/");

        inMemoryDatabase.setPrefixMapping(NAME, new PrefixMappingImpl().setNsPrefixes(prefixes));

        // Act
        var prefixMapping = inMemoryDatabase.getPrefixMapping(NAME);

        // Assert
        assertThat(prefixMapping.getNsPrefixMap()).containsExactlyInAnyOrderEntriesOf(prefixes);
    }

    @Test
    void getPrefixMapping_nonExistingDataset_emptyPrefixMapping() {
        // Arrange

        // Act
        assertThat(inMemoryDatabase.getPrefixMapping("a").getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_emptyDataset_returnsEmptyPrefixMapping() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());

        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        var prefixMapping = inMemoryDatabase.getPrefixMapping(NAME);

        // Assert
        assertThat(prefixMapping.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_modifyPrefixMapping_hasNoChangeOnCollection() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        var prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefix("ex", "http://example.com/");

        inMemoryDatabase.setPrefixMapping(NAME, prefixes);

        // Act
        prefixes.setNsPrefix("ex2", "http://example2.com/");

        // Assert
        var storedPrefixes = inMemoryDatabase.getPrefixMapping(NAME);
        assertThat(storedPrefixes.getNsPrefixMap())
                .containsExactlyEntriesOf(Map.of("ex", "http://example.com/"));
    }

    @Test
    void setPrefixMapping_emptyPrefixMapping_setsNoPrefixes() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        inMemoryDatabase.setPrefixMapping(NAME, new PrefixMappingMem());
        // Assert
        var storedPrefixes = inMemoryDatabase.getPrefixMapping(NAME);
        assertThat(storedPrefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_emptyPrefixMappingIntoNonEmptyGraph_deletesPrefixes() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        var prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefix("ex", "http://example.com/");

        inMemoryDatabase.setPrefixMapping(NAME, prefixes);

        // Act
        inMemoryDatabase.setPrefixMapping(NAME, new PrefixMappingMem());

        // Assert
        var storedPrefixes = inMemoryDatabase.getPrefixMapping(NAME);
        assertThat(storedPrefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMapping_setsPrefixes() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        var prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefix("ex", "http://example.com/");

        // Act
        inMemoryDatabase.setPrefixMapping(NAME, prefixes);

        // Assert
        var storedPrefixes = inMemoryDatabase.getPrefixMapping(NAME);
        assertThat(storedPrefixes.getNsPrefixMap())
                .containsExactlyEntriesOf(Map.of("ex", "http://example.com/"));
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMappingIntoNonEmptyGraph_overridesPrefixes() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        var prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefix("ex", "http://example.com/");

        inMemoryDatabase.setPrefixMapping(NAME, prefixes);

        var newPrefixes = new PrefixMappingImpl();
        newPrefixes.setNsPrefix("ex2", "http://example2.com/");

        // Act
        inMemoryDatabase.setPrefixMapping(NAME, newPrefixes);

        // Assert
        var storedPrefixes = inMemoryDatabase.getPrefixMapping(NAME);
        assertThat(storedPrefixes.getNsPrefixMap())
                .containsExactlyEntriesOf(Map.of("ex2", "http://example2.com/"));
    }

    @Test
    void undo_nonExistingDataset_throwsException() {
        // Arrange

        // Act
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> inMemoryDatabase.undo(GRAPH_IDENTIFIER));
    }

    @Test
    void undo_nonExistingGraph_doesNothing() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> inMemoryDatabase.undo(GRAPH_IDENTIFIER));
    }

    @Test
    void undo_validGraph_undoesLastOperation() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.commit();
        graphRewindable.end();

        // Act
        inMemoryDatabase.undo(GRAPH_IDENTIFIER);

        // Assert
        graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.READ);
        assertThat(graphRewindable.isIsomorphicWith(exampleGraphs.getFirst())).isTrue();
        graphRewindable.end();
    }

    @Test
    void redo_nonExistingDataset_throwsException() {
        // Arrange

        // Act
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> inMemoryDatabase.redo(GRAPH_IDENTIFIER));
    }

    @Test
    void redo_nonExistingGraph_doesNothing() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> inMemoryDatabase.redo(GRAPH_IDENTIFIER));
    }

    @Test
    void redo_validGraph_restoresLastUndoneOperation() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.commit();
        graphRewindable.end();
        inMemoryDatabase.undo(GRAPH_IDENTIFIER);

        // Act
        inMemoryDatabase.redo(GRAPH_IDENTIFIER);

        // Assert
        graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.READ);
        assertThat(graphRewindable.isIsomorphicWith(exampleGraphs.getFirst())).isFalse();
        assertThat(graphRewindable.contains(TestRDFUtils.triple("a a d"))).isTrue();
        graphRewindable.end();
    }

    @Test
    void canUndo_nonExistingDataset_throwsException() {
        // Arrange

        // Act
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> inMemoryDatabase.canUndo(GRAPH_IDENTIFIER));
    }

    @Test
    void canUndo_nonExistingGraph_throwsException() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act + Assert
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(
                        () ->
                                inMemoryDatabase.canUndo(
                                        new GraphIdentifier("a", "http://example.com/graph")));
    }

    @Test
    void canUndo_validGraphWithNoCommit_returnsFalse() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.end();

        // Act
        var canUndo = inMemoryDatabase.canUndo(GRAPH_IDENTIFIER);

        // Assert
        assertThat(canUndo).isFalse();
    }

    @Test
    void canUndo_validGraphWithCommit_returnsTrue() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.commit();
        graphRewindable.end();

        // Act
        var canUndo = inMemoryDatabase.canUndo(GRAPH_IDENTIFIER);

        // Assert
        assertThat(canUndo).isTrue();
    }

    @Test
    void canRedo_nonExistingDataset_throwsException() {
        // Arrange

        // Act
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> inMemoryDatabase.canRedo(GRAPH_IDENTIFIER));
    }

    @Test
    void canRedo_nonExistingGraph_throwsException() {
        // Arrange
        exampleGraphs = List.of(GraphFactory.createDefaultGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());

        // Act
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(
                        () ->
                                inMemoryDatabase.canRedo(
                                        new GraphIdentifier("a", "http://example.com/graph")));
    }

    @Test
    void canRedo_validGraphWithNoUndoneChange_returnsFalse() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.commit();
        graphRewindable.end();

        // Act
        var canRedo = inMemoryDatabase.canRedo(GRAPH_IDENTIFIER);

        // Assert
        assertThat(canRedo).isFalse();
    }

    @Test
    void canRedo_validGraphAfterUndoneChange_returnsTrue() {
        // Arrange
        exampleGraphs = List.of(createExampleGraph());
        inMemoryDatabase.create(GRAPH_IDENTIFIER, exampleGraphs.getFirst());
        var graphRewindable = inMemoryDatabase.begin(GRAPH_IDENTIFIER, TxnType.WRITE);
        graphRewindable.add(TestRDFUtils.triple("a a d"));
        graphRewindable.commit();
        graphRewindable.end();
        inMemoryDatabase.undo(GRAPH_IDENTIFIER);

        // Act
        var canRedo = inMemoryDatabase.canRedo(GRAPH_IDENTIFIER);

        // Assert
        assertThat(canRedo).isTrue();
    }
}
