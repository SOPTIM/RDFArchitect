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
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class WorkspaceTest {

    private static final String WORKSPACE = "workspace";

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
        Workspace workspace = new Workspace(WORKSPACE);

        // Act
        int size = workspace.listGraphUris().size();

        // Assert
        assertThat(size).isZero();
    }

    @Test
    void constructor_emptyDataset_returnsEmptyCollection() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE, DatasetFactory.create());

        // Act
        int size = workspace.listGraphUris().size();

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

        Workspace workspace = new Workspace(WORKSPACE, dataset);

        // Act
        List<String> graphUris = workspace.listGraphUris();
        int size = graphUris.size();

        // Assert
        assertThat(size).isEqualTo(3);
        for (String uri : graphUris) {
            try (var transaction = workspace.begin(ReadWrite.READ)) {
                var ctx = transaction.graph(uri);
                assertThat(ctx).isNotNull();
                assertThat(ctx.getRdfGraph().find().toList()).hasSize(9);
                assertThat(transaction.mode()).isEqualTo(ReadWrite.READ);
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

        Workspace workspace = new Workspace(WORKSPACE, dataset);

        // Act
        List<String> graphUris = workspace.listGraphUris();
        int size = graphUris.size();

        // Assert
        assertThat(size).isEqualTo(1);
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var ctx = transaction.graph(DEFAULT_GRAPH_NAME);
            assertThat(ctx).isNotNull();
            assertThat(ctx.getRdfGraph().find().toList()).hasSize(3);
            assertThat(transaction.mode()).isEqualTo(ReadWrite.READ);
        }
    }

    @ParameterizedTest
    @EnumSource(ReadWrite.class)
    void begin_existingGraphUri_returnsGraphRewindable(ReadWrite mode) {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        try (var transaction = workspace.begin(mode)) {
            var ctx = transaction.graph("http://example.org/graph1");
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.getRdfGraph().isIsomorphicWith(exampleGraphs.get(1))).isTrue();
            assertThat(transaction.mode()).isEqualTo(mode);
        }
    }

    @Test
    void begin_nonExistingDefaultGraphUri_returnsEmptyDefaultGraph() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var ctx = transaction.graph(DEFAULT_GRAPH_NAME);
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.getRdfGraph().find().toList()).isEmpty();
            assertThat(transaction.mode()).isEqualTo(ReadWrite.READ);
        }
    }

    @Test
    void begin_nonExistingNamedGraphUri_throwsException() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> workspace.getGraphWithContext("http://example.org/nonexistent"))
                .withMessage("Graph URI http://example.org/nonexistent does not exist.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void begin_invalidUri_throwsException(String graphUri) {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> workspace.getGraphWithContext(graphUri))
                .withMessage("Graph Uri " + graphUri + " is not a valid URI");
    }

    @Test
    void begin_readThenEndThenBeginWrite_allowsWriteAfterReadEnds() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act - begin READ, end it, then begin WRITE
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var readCtx = transaction.graph("http://example.org/graph1");
            assertThat(readCtx.getRdfGraph().find().toList()).hasSize(9);
        }
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var writeCtx = transaction.graph("http://example.org/graph1");
            // Assert
            assertThat(writeCtx).isNotNull();
            assertThat(transaction.mode()).isEqualTo(ReadWrite.WRITE);
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a d"));
            transaction.commit("add triple");
        }

        // Verify the write persisted
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var verifyCtx = transaction.graph("http://example.org/graph1");
            assertThat(verifyCtx.getRdfGraph().find().toList()).hasSize(10);
        }
    }

    @Test
    void begin_writeCommitThenRead_seesCommittedChanges() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act - begin WRITE, commit changes, end, then begin READ
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var writeCtx = transaction.graph("http://example.org/graph1");
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a d"));
            writeCtx.getRdfGraph().add(TestRDFUtils.triple("a a e"));
            transaction.commit("add two triples");
        }

        // Assert - READ sees the committed changes
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var readCtx = transaction.graph("http://example.org/graph1");
            assertThat(readCtx).isNotNull();
            assertThat(transaction.mode()).isEqualTo(ReadWrite.READ);
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
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());

        // Act
        workspace.create(graphUri, exampleGraphs.getFirst());
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var ctx = transaction.graph(graphUri);
            // Assert
            assertThat(ctx).isNotNull();
            assertThat(ctx.getRdfGraph().isIsomorphicWith(exampleGraphs.get(1))).isTrue();
            assertThat(transaction.mode()).isEqualTo(ReadWrite.READ);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "bar", "otherInvalidUri"})
    void create_invalidUri_throwsException(String graphUri) {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());

        // Act/Assert
        var firstGraph = exampleGraphs.getFirst();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> workspace.create(graphUri, firstGraph));
    }

    @Test
    void remove_existingGraphUri_removesGraph() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        workspace.remove("http://example.org/graph1");

        // Assert
        assertThat(workspace.listGraphUris()).isEmpty();
    }

    @Test
    void rename_existingGraphUri_keepsContentUnderNewUri() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());

        // Act
        workspace.rename("http://example.org/graph1", "http://example.org/graph2");

        // Assert
        assertThat(workspace.listGraphUris()).containsExactly("http://example.org/graph2");
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var ctx = transaction.graph("http://example.org/graph2");
            assertThat(ctx.getRdfGraph().isIsomorphicWith(exampleGraphs.get(1))).isTrue();
        }
    }

    @Test
    void rename_updatesGraphUriInCustomDiagrams() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());
        var diagramId = UUID.randomUUID();
        var classUuid = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(
                List.of(
                        new ClassInDiagram(classUuid, new URI("http://example.org/graph1")),
                        new ClassInDiagram(
                                UUID.randomUUID(), new URI("http://example.org/other"))));
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.diagrams().put(diagramId, diagram);
            transaction.commit("added a diagram");
        }

        // Act
        workspace.rename("http://example.org/graph1", "http://example.org/graph2");

        // Assert
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(transaction.diagrams().get(diagramId).getClasses())
                    .extracting(entry -> entry.getGraphUri().toString())
                    .containsExactly("http://example.org/graph2", "http://example.org/other");
        }
    }

    @Test
    void rename_updatesGraphUriInSchemaScopedCustomDiagrams() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());
        workspace.create("http://example.org/other", exampleGraphs.get(1));
        var ownDiagramId = UUID.randomUUID();
        var ownDiagram = new CustomDiagram(ownDiagramId);
        ownDiagram.setClasses(
                List.of(
                        new ClassInDiagram(
                                UUID.randomUUID(), new URI("http://example.org/graph1"))));
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            transaction
                    .graph("http://example.org/graph1")
                    .getCustomDiagrams()
                    .put(ownDiagramId, ownDiagram);
        }
        var foreignDiagramId = UUID.randomUUID();
        var foreignDiagram = new CustomDiagram(foreignDiagramId);
        foreignDiagram.setClasses(
                List.of(
                        new ClassInDiagram(
                                UUID.randomUUID(), new URI("http://example.org/graph1"))));
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            transaction
                    .graph("http://example.org/other")
                    .getCustomDiagrams()
                    .put(foreignDiagramId, foreignDiagram);
        }

        // Act
        workspace.rename("http://example.org/graph1", "http://example.org/graph2");

        // Assert
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThat(
                            transaction
                                    .graph("http://example.org/graph2")
                                    .getCustomDiagrams()
                                    .get(ownDiagramId)
                                    .getClasses())
                    .extracting(entry -> entry.getGraphUri().toString())
                    .containsExactly("http://example.org/graph2");
            assertThat(
                            transaction
                                    .graph("http://example.org/other")
                                    .getCustomDiagrams()
                                    .get(foreignDiagramId)
                                    .getClasses())
                    .extracting(entry -> entry.getGraphUri().toString())
                    .containsExactly("http://example.org/graph2");
        }
    }

    @Test
    void rename_movesCrossProfileColorToNewUri() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.crossProfileInfo().setColor("http://example.org/graph1", "#123456");
            transaction.commit("assigned a colour");
        }

        // Act
        workspace.rename("http://example.org/graph1", "http://example.org/graph2");

        // Assert
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var info = transaction.crossProfileInfo();
            assertThat(info.getColor("http://example.org/graph2")).isEqualTo("#123456");
            assertThat(info.getColor("http://example.org/graph1")).isNull();
        }
    }

    @Test
    void rename_nonExistingGraphUri_throwsException() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act/Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () ->
                                workspace.rename(
                                        "http://example.org/missing", "http://example.org/graph2"));
    }

    @Test
    void rename_alreadyTakenGraphUri_throwsException() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.getFirst());
        workspace.create("http://example.org/graph2", exampleGraphs.get(1));

        // Act/Assert
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                workspace.rename(
                                        "http://example.org/graph1", "http://example.org/graph2"));
    }

    @Test
    void remove_nonExistingGraphUri_doesNothing() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act
        workspace.remove("http://example.org/nonexistent");

        // Assert
        assertThat(workspace.listGraphUris()).isEmpty();
    }

    @Test
    void listGraphUris_emptyCollection_returnsEmptyList() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act
        List<String> graphUris = workspace.listGraphUris();

        // Assert
        assertThat(graphUris).isEmpty();
    }

    @Test
    void listGraphUris_nonEmptyCollection_returnsListWithGraphUris() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.get(0));
        workspace.create("http://example.org/graph2", exampleGraphs.get(1));
        workspace.create("http://example.org/graph3", exampleGraphs.get(2));

        // Act
        List<String> graphUris = workspace.listGraphUris();

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
        Workspace workspace = new Workspace(WORKSPACE, DatasetFactory.create());

        // Act
        List<String> graphUris = workspace.listGraphUris();

        // Assert
        assertThat(graphUris).isEmpty();
    }

    @Test
    void getPrefixMapping_emptyCollection_returnsEmptyPrefixMapping() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);

        // Act
        var prefixes = workspace.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_nonEmptyCollection_returnsEmptyPrefixMapping() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE);
        exampleGraphs = List.of(createExampleGraph(), createExampleGraph(), createExampleGraph());
        workspace.create("http://example.org/graph1", exampleGraphs.get(0));
        workspace.create("http://example.org/graph2", exampleGraphs.get(1));
        workspace.create("http://example.org/graph3", exampleGraphs.get(2));

        // Act
        var prefixes = workspace.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_datasetWithEmptyPrefixMapping_returnsEmptyPrefixMapping() {
        // Arrange
        Workspace workspace = new Workspace(WORKSPACE, DatasetFactory.create());

        // Act
        var prefixes = workspace.getPrefixMapping();

        // Assert
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void getPrefixMapping_datasetWithPrefixMapping_returnsPrefixMapping() {
        // Arrange
        var dataset = DatasetFactory.create();
        dataset.getDefaultModel().setNsPrefix("ex", "http://example.org/");
        Workspace workspace = new Workspace(WORKSPACE, dataset);

        // Act
        var prefixes = workspace.getPrefixMapping();

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
        Workspace workspace = new Workspace(WORKSPACE, dataset);

        // Act
        var prefixes = workspace.getPrefixMapping();

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
        Workspace workspace = new Workspace(WORKSPACE, dataset);

        // Act
        var prefixes = workspace.getPrefixMapping();

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
        var workspace = new Workspace(WORKSPACE);
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        workspace.setPrefixMapping(initialPrefixes);

        // Act
        initialPrefixes.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        initialPrefixes.removeNsPrefix("ex");

        // Assert
        var prefixes = workspace.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
    }

    @Test
    void setPrefixMapping_emptyPrefixMapping_setsNoPrefixes() {
        // Arrange
        var workspace = new Workspace(WORKSPACE);
        var newPrefixes = new PrefixMappingImpl();

        // Act
        workspace.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = workspace.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_emptyPrefixMappingIntoNonEmptyGraph_deletesPrefixes() {
        // Arrange
        var workspace = new Workspace(WORKSPACE);
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        workspace.setPrefixMapping(initialPrefixes);
        var newPrefixes = new PrefixMappingImpl();

        // Act
        workspace.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = workspace.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap()).isEmpty();
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMapping_setsPrefixes() {
        // Arrange
        var workspace = new Workspace(WORKSPACE);
        var newPrefixes = new PrefixMappingImpl();
        newPrefixes.setNsPrefix("ex", "http://example.org/");
        newPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

        // Act
        workspace.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = workspace.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "ex", "http://example.org/",
                                "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
    }

    @Test
    void setPrefixMapping_nonEmptyPrefixMappingIntoNonEmptyGraph_overridesPrefixes() {
        // Arrange
        var workspace = new Workspace(WORKSPACE);
        var initialPrefixes = new PrefixMappingImpl();
        initialPrefixes.setNsPrefix("ex", "http://example.org/");
        initialPrefixes.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        workspace.setPrefixMapping(initialPrefixes);
        var newPrefixes = new PrefixMappingImpl();
        newPrefixes.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        newPrefixes.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");

        // Act
        workspace.setPrefixMapping(newPrefixes);

        // Assert
        var prefixes = workspace.getPrefixMapping();
        assertThat(prefixes.getNsPrefixMap())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                                "owl", "http://www.w3.org/2002/07/owl#"));
    }
}
