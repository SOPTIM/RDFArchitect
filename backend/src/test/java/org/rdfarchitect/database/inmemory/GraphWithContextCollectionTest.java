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
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.rdf.TestRDFUtils;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;

import java.util.List;
import java.util.Map;

class GraphWithContextCollectionTest {

    private List<Graph> exampleGraphs;

    @BeforeEach
    void setUp() {
        exampleGraphs = List.of();
    }

    @AfterEach
    void tearDown() {
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

    private static final String DEFAULT_GRAPH_NAME = "default";

    @Test
    void constructor_noArgs_returnsEmptyCollection() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        int size = collection.listGraphUris().size();

        // Assert
        assertThat(size).isZero();
    }

    @Test
    void constructor_emptyDataset_returnsEmptyCollection() {
        // Arrange
        GraphWithContextCollection collection =
                new GraphWithContextCollection(DatasetFactory.create());

        // Act
        int size = collection.listGraphUris().size();

        // Assert
        assertThat(size).isZero();
    }

    @Test
    void constructor_nonEmptyDataset_returnsCollectionWithGraphs() {
        // Arrange
        var dataset = DatasetFactory.create();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph(), createExampleGraph());
        dataset.addNamedModel(
                "http://example.org/graph1",
                ModelFactory.createModelForGraph(exampleGraphs.get(0)));
        dataset.addNamedModel(
                "http://example.org/graph2",
                ModelFactory.createModelForGraph(exampleGraphs.get(1)));
        dataset.addNamedModel(
                "http://example.org/graph3",
                ModelFactory.createModelForGraph(exampleGraphs.get(2)));

        GraphWithContextCollection collection = new GraphWithContextCollection(dataset);

        // Act
        List<String> graphUris = collection.listGraphUris();
        int size = graphUris.size();

        // Assert
        assertThat(size).isEqualTo(3);
        for (String uri : graphUris) {
            GraphRewindableWithUUIDs graph = collection.begin(uri, TxnType.READ);
            assertThat(graph).isNotNull();
            assertThat(graph.find().toList()).hasSize(9);
            assertThat(graph.transactionType()).isEqualTo(TxnType.READ);
            assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
            graph.end();
        }
    }

    @Test
    void constructor_defaultGraphOnlyDataset_returnCollectionWith1Graph() {
        // Arrange
        var dataset = DatasetFactory.create();
        Graph model = dataset.getDefaultModel().getGraph();
        model.add(TestRDFUtils.triple("a a a"));
        model.add(TestRDFUtils.triple("b b b"));
        model.add(TestRDFUtils.triple("c c c"));

        GraphWithContextCollection collection = new GraphWithContextCollection(dataset);

        // Act
        List<String> graphUris = collection.listGraphUris();
        int size = graphUris.size();
        GraphRewindableWithUUIDs graph = collection.begin(DEFAULT_GRAPH_NAME, TxnType.READ);

        // Assert
        assertThat(size).isEqualTo(1);
        assertThat(graph).isNotNull();
        assertThat(graph.find().toList()).hasSize(3);
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @Test
    void begin_existingGraphUri_returnsGraphRewindable() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.READ);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @Test
    void begin_nonExistingDefaultGraphUri_returnsEmptyDefaultGraph() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        GraphRewindableWithUUIDs graph = collection.begin(DEFAULT_GRAPH_NAME, TxnType.READ);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.find().toList()).isEmpty();
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @Test
    void begin_nonExistingNamedGraphUri_throwsException() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.begin("http://example.org/nonexistent", TxnType.READ))
                .withMessage("Graph URI http://example.org/nonexistent does not exist.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void begin_invalidUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.begin(graphUri, TxnType.READ))
                .withMessage("Graph Uri " + graphUri + " is not a valid URI");
    }

    @Test
    void begin_existingNamedGraphWithWrite_graphRewindableInWriteTransaction() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.WRITE);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        assertThat(graph.transactionType()).isEqualTo(TxnType.WRITE);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.WRITE);
        graph.end();
    }

    @Test
    void begin_existingNamedGraphWithReadAndEndTransaction_graphRewindableInReadPromote() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.READ_PROMOTE);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ_PROMOTE);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @Test
    void begin_existingNamedGraphWithReadCommitedPromote_graphRewindableInReadCommitedPromote() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.READ_COMMITTED_PROMOTE);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ_COMMITTED_PROMOTE);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.org/graph1",
                "http://example.org/graph2",
                "http://example.org/graph3",
                DEFAULT_GRAPH_NAME
            })
    void create_validName_returnsGraphRewindable(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());

        // Act
        collection.create(graphUri, exampleGraphs.getFirst());
        GraphRewindableWithUUIDs graph = collection.begin(graphUri, TxnType.READ);

        // Assert
        assertThat(graph).isOfAnyClassIn(GraphRewindableWithUUIDs.class);
        assertThat(graph.isInTransaction()).isTrue();
        assertThat(graph.isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        assertThat(graph.transactionType()).isEqualTo(TxnType.READ);
        assertThat(graph.transactionMode()).isEqualTo(ReadWrite.READ);
        graph.end();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void create_invalidUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());

        // Act/Assert
        var firstGraph = exampleGraphs.getFirst();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.create(graphUri, firstGraph));
    }

    @Test
    void remove_existingGraphUri_removesGraph() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        collection.remove("http://example.org/graph1");

        // Assert
        assertThat(collection.listGraphUris()).isEmpty();
    }

    @Test
    void remove_nonExistingGraphUri_doesNothing() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        collection.remove("http://example.org/nonexistent");

        // Assert
        assertThat(collection.listGraphUris()).isEmpty();
    }

    @Test
    void containsGraph_existingGraphUri_returnsTrue() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        boolean containsGraph = collection.containsGraph("http://example.org/graph1");

        // Assert
        assertThat(containsGraph).isTrue();
    }

    @Test
    void containsGraph_nonExistingGraphUri_returnsFalse() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        boolean containsGraph = collection.containsGraph("http://example.org/nonexistent");

        // Assert
        assertThat(containsGraph).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void containsGraph_invalidGraphUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.containsGraph(graphUri));
    }

    @Test
    void listGraphUris_emptyCollection_returnsEmptyList() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        List<String> graphUris = collection.listGraphUris();

        // Assert
        assertThat(graphUris).isEmpty();
    }

    @Test
    void listGraphUris_nonEmptyCollection_returnsListWithGraphUris() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.get(0));
        collection.create("http://example.org/graph2", exampleGraphs.get(1));
        collection.create("http://example.org/graph3", exampleGraphs.get(2));

        // Act
        List<String> graphUris = collection.listGraphUris();

        // Assert
        assertThat(graphUris)
                .containsExactlyInAnyOrder(
                        "http://example.org/graph1",
                        "http://example.org/graph2",
                        "http://example.org/graph3");
    }

    @Test
    void listGraphUris_emptyDataset_returnsEmptyList() {
        // Arrange
        GraphWithContextCollection collection =
                new GraphWithContextCollection(DatasetFactory.create());

        // Act
        List<String> graphUris = collection.listGraphUris();

        // Assert
        assertThat(graphUris).isEmpty();
    }

    @Test
    void getPrefixMapping_emptyCollection_returnsEmptyPrefixMapping() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_nonEmptyCollection_returnsEmptyPrefixMapping() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.get(0));
        collection.create("http://example.org/graph2", exampleGraphs.get(1));
        collection.create("http://example.org/graph3", exampleGraphs.get(2));

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_datasetWithEmptyPrefixMapping_returnsEmptyPrefixMapping() {
        // Arrange
        GraphWithContextCollection collection =
                new GraphWithContextCollection(DatasetFactory.create());

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_datasetWithPrefixMapping_returnsPrefixMapping() {
        // Arrange
        var dataset = DatasetFactory.create();
        dataset.getDefaultModel().setNsPrefix("ex", "http://example.org/");
        GraphWithContextCollection collection = new GraphWithContextCollection(dataset);

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(Map.of("ex", "http://example.org/"));
    }

    @Test
    void getPrefixMapping_datasetWithMultiplePrefixMappings_returnsPrefixMapping() {
        // Arrange
        var dataset = DatasetFactory.create();
        dataset.getDefaultModel().setNsPrefix("ex", "http://example.org/");
        dataset.getDefaultModel().setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        dataset.getDefaultModel().setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        GraphWithContextCollection collection = new GraphWithContextCollection(dataset);

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                "rdfs", "http://www.w3.org/2000/01/rdf-schema#"));
    }

    @Test
    void getPrefixMapping_datasetWithMultiplePrefixMappingsAndGraphs_returnsPrefixMapping() {
        // Arrange
        var dataset = DatasetFactory.create();
        exampleGraphs =
                List.of(GraphFactory.createDefaultGraph(), GraphFactory.createDefaultGraph());
        dataset.getDefaultModel().setNsPrefix("ex", "http://example.org/");
        dataset.getDefaultModel().setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        dataset.getDefaultModel().setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        dataset.addNamedModel(
                "http://example.org/graph1",
                ModelFactory.createModelForGraph(exampleGraphs.get(0)));
        dataset.getNamedModel("http://example.org/graph1")
                .setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        dataset.addNamedModel(
                "http://example.org/graph2",
                ModelFactory.createModelForGraph(exampleGraphs.get(1)));
        dataset.getNamedModel("http://example.org/graph2")
                .setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        GraphWithContextCollection collection = new GraphWithContextCollection(dataset);

        // Act
        var prefixes = collection.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                "rdfs", "http://www.w3.org/2000/01/rdf-schema#"));
    }

    @Test
    void setPrefixMapping_modifyPrefixMapping_hasNoChangeOnCollection() {
        // Arrange
        var collection = new GraphWithContextCollection();
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        collection.setPrefixMapping(initialPrefixes);

        // Act
        initialPrefixes.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        initialPrefixes.removeNsPrefix("ex");

        // Assert
        var prefixes = collection.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
    }

    @Test
    void setPrefixMapping_emptyPrefixMapping_setsNoPrefixes() {
        // Arrange
        var collection = new GraphWithContextCollection();
        var newPrefixes = new PrefixMappingImpl();

        // Act
        collection.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = collection.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_emptyPrefixMappingIntoNonEmptyGraph_deletesPrefixes() {
        // Arrange
        var collection = new GraphWithContextCollection();
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        collection.setPrefixMapping(initialPrefixes);
        var newPrefixes = new PrefixMappingImpl();

        // Act
        collection.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = collection.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMapping_setsPrefixes() {
        // Arrange
        var collection = new GraphWithContextCollection();
        var newPrefixes = new PrefixMappingImpl();
        newPrefixes.setNsPrefix("ex", "http://example.org/");
        newPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

        // Act
        collection.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = collection.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMappingIntoNonEmptyGraph_overridesPrefixes() {
        // Arrange
        var collection = new GraphWithContextCollection();
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        collection.setPrefixMapping(initialPrefixes);
        var newPrefixes = new PrefixMappingImpl();
        newPrefixes.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        newPrefixes.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");

        // Act
        collection.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = collection.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                                "owl", "http://www.w3.org/2002/07/owl#"));
    }

    @Test
    void undo_existingGraphUri_undoesLastChange() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.WRITE);
        graph.add(TestRDFUtils.triple("a a d"));
        graph.commit();
        graph.end();

        // Act
        collection.undo("http://example.org/graph1");

        // Assert
        graph = collection.begin("http://example.org/graph1", TxnType.READ);
        assertThat(graph.find().toList()).hasSize(9);
        graph.end();
    }

    @Test
    void undo_nonExistingGraphUri_throwsException() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.undo("http://example.org/nonexistent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void undo_invalidGraphUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.undo(graphUri));
    }

    @Test
    void redo_existingGraphUri_restoresLastChange() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());
        GraphRewindableWithUUIDs graph =
                collection.begin("http://example.org/graph1", TxnType.WRITE);
        graph.add(TestRDFUtils.triple("a a d"));
        graph.commit();
        graph.end();
        collection.undo("http://example.org/graph1");

        // Act
        collection.redo("http://example.org/graph1");

        // Assert
        graph = collection.begin("http://example.org/graph1", TxnType.READ);
        assertThat(graph.find().toList()).hasSize(10);
        graph.end();
    }

    @Test
    void redo_nonExistingGraphUri_throwsException() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.redo("http://example.org/nonexistent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void redo_invalidGraphUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.redo(graphUri));
    }
}
