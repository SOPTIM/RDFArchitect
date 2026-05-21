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
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.rdf.TestRDFUtils;

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
            var graphWithCtx = collection.getGraphWithContext(uri);
            try (var ctx = graphWithCtx.begin(ReadWrite.READ)) {
                assertThat(ctx).isNotNull();
                assertThat(ctx.getRdfGraph().find().toList()).hasSize(9);
                assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.READ);
            }
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

        // Assert
        assertThat(size).isEqualTo(1);
        var graphWithCtx = collection.getGraphWithContext(DEFAULT_GRAPH_NAME);
        try (var ctx = graphWithCtx.begin(ReadWrite.READ)) {
            assertThat(ctx).isNotNull();
            assertThat(ctx.getRdfGraph().find().toList()).hasSize(3);
            assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.READ);
        }
    }

    @ParameterizedTest
    @EnumSource(ReadWrite.class)
    void begin_existingGraphUri_returnsGraphRewindable(ReadWrite mode) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        var graphWithCtx = collection.getGraphWithContext("http://example.org/graph1");
        try (var ctx = graphWithCtx.begin(mode)) {
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.isInTransaction()).isTrue();
            assertThat(ctx.getRdfGraph().isIsomorphicWith(exampleGraphs.get(1))).isTrue();
            assertThat(ctx.transactionMode()).isEqualTo(mode);
        }
    }

    @Test
    void begin_nonExistingDefaultGraphUri_returnsEmptyDefaultGraph() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act
        var graphWithCtx = collection.getGraphWithContext(DEFAULT_GRAPH_NAME);
        try (var ctx = graphWithCtx.begin(ReadWrite.READ)) {
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.isInTransaction()).isTrue();
            assertThat(ctx.getRdfGraph().find().toList()).isEmpty();
            assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.READ);
        }
    }

    @Test
    void begin_nonExistingNamedGraphUri_throwsException() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.getGraphWithContext("http://example.org/nonexistent"))
                .withMessage("Graph URI http://example.org/nonexistent does not exist.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void begin_invalidUri_throwsException(String graphUri) {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> collection.getGraphWithContext(graphUri))
                .withMessage("Graph Uri " + graphUri + " is not a valid URI");
    }

    @Test
    void begin_readThenEndThenBeginWrite_allowsWriteAfterReadEnds() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());
        var graphWithCtx = collection.getGraphWithContext("http://example.org/graph1");

        // Act - begin READ, end it, then begin WRITE
        try (var readCtx = graphWithCtx.begin(ReadWrite.READ)) {
            assertThat(readCtx.getRdfGraph().find().toList()).hasSize(9);
        }
        try (var writeCtx = graphWithCtx.begin(ReadWrite.WRITE)) {
            // Assert
            assertThat(writeCtx).isNotNull();
            assertThat(writeCtx.isInTransaction()).isTrue();
            assertThat(writeCtx.transactionMode()).isEqualTo(ReadWrite.WRITE);
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a d"));
            writeCtx.commit("add triple");
        }

        // Verify the write persisted
        try (var verifyCtx = graphWithCtx.begin(ReadWrite.READ)) {
            assertThat(verifyCtx.getRdfGraph().find().toList()).hasSize(10);
        }
    }

    @Test
    void begin_writeCommitThenRead_seesCommittedChanges() {
        // Arrange
        GraphWithContextCollection collection = new GraphWithContextCollection();
        exampleGraphs = List.of(createExampleGraph());
        collection.create("http://example.org/graph1", exampleGraphs.getFirst());
        var graphWithCtx = collection.getGraphWithContext("http://example.org/graph1");

        // Act - begin WRITE, commit changes, end, then begin READ
        try (var writeCtx = graphWithCtx.begin(ReadWrite.WRITE)) {
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a d"));
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a e"));
            writeCtx.commit("add two triples");
        }

        // Assert - READ sees the committed changes
        try (var readCtx = graphWithCtx.begin(ReadWrite.READ)) {
            assertThat(readCtx).isNotNull();
            assertThat(readCtx.isInTransaction()).isTrue();
            assertThat(readCtx.transactionMode()).isEqualTo(ReadWrite.READ);
            assertThat(readCtx.getRdfGraph().find().toList()).hasSize(11);
        }
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
        var graphWithCtx = collection.getGraphWithContext(graphUri);
        try (var ctx = graphWithCtx.begin(ReadWrite.READ)) {
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.isInTransaction()).isTrue();
            assertThat(ctx.getRdfGraph().isIsomorphicWith(exampleGraphs.get(1))).isTrue();
            assertThat(ctx.transactionMode()).isEqualTo(ReadWrite.READ);
        }
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
}
