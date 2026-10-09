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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.shacl.SHACLStoringService;

import java.util.UUID;

/** Replacing a schema keeps the constraints written for it. */
class InMemoryDatabaseReplaceGraphTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # written for the previous release
            ex:TerminalShape a sh:NodeShape ; sh:targetClass ex:Terminal .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);
    private SHACLStoringService documents;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createGraph(GRAPH, schema("Terminal"));
        // A new workspace starts read-only, as an imported one does.
        databasePort.enableEditing(DATASET);
        documents = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    private static org.apache.jena.graph.Graph schema(String className) {
        var graph = GraphFactory.createDefaultGraph();
        graph.add(
                Triple.create(
                        NodeFactory.createURI("http://ex.org/EQ#" + className),
                        NodeFactory.createURI("http://www.w3.org/2000/01/rdf-schema#label"),
                        NodeFactory.createLiteralString(className)));
        return graph;
    }

    @Test
    void documentsSurviveReplacingTheSchema() {
        var created =
                documents.createShapesDocument(GRAPH, "eq.ttl", "EQ.ttl", SHAPES, Lang.TURTLE);
        documents.updateShapesDocument(GRAPH, created.getId(), null, false, null);
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, SHAPES + "\n# default\n");

        databasePort.replaceGraph(GRAPH, schema("Switch"));

        try (var ctx = databasePort.getGraphWithContext(GRAPH).begin(ReadWrite.READ)) {
            assertThat(
                            ctx.getRdfGraph()
                                    .contains(
                                            NodeFactory.createURI("http://ex.org/EQ#Switch"),
                                            Node.ANY,
                                            Node.ANY))
                    .isTrue();
            var document = ctx.getShapesDocuments().get(created.getId());
            assertThat(document).isNotNull();
            assertThat(document.getName()).isEqualTo("eq.ttl");
            assertThat(document.isEnabled()).isFalse();
            assertThat(document.getRawText()).isEqualTo(SHAPES);
            assertThat(document.getGraph().isEmpty()).isFalse();
            assertThat(document.getGraph().getPrefixMapping().getNsPrefixURI("ex"))
                    .isEqualTo("http://ex.org/EQ#");
            assertThat(
                            ctx.getShapesDocuments()
                                    .get(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID)
                                    .getRawText())
                    .endsWith("# default\n");
        }
    }

    @Test
    void replacingWithNothingAlsoKeepsTheDocuments() {
        var created = documents.createShapesDocument(GRAPH, "eq.ttl", null, SHAPES, Lang.TURTLE);

        databasePort.replaceGraph(GRAPH, null);

        try (var ctx = databasePort.getGraphWithContext(GRAPH).begin(ReadWrite.READ)) {
            assertThat(ctx.getRdfGraph().isEmpty()).isTrue();
            assertThat(ctx.getShapesDocuments()).containsKey(created.getId());
        }
    }

    @Test
    void replacingAGraphThatDoesNotExistCreatesIt() {
        var other = new GraphIdentifier(DATASET, "http://ex.org/TP");

        databasePort.replaceGraph(other, schema("Junction"));

        assertThat(databasePort.listGraphUris(DATASET)).contains(other.graphUri());
    }
}
