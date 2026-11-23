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

package org.rdfarchitect.database.snapshots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.DatabaseConfig;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.formatter.ResultSetFormatterImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A share snapshot stored in Fuseki carries the shapes documents, and loading it gives back the
 * workspace's schemas and nothing more.
 *
 * <p>The adapter talks to a stand-in for Fuseki that keeps every graph it is sent, and the load
 * reads those graphs back through the connection the session store uses.
 */
class FusekiSnapshotAdapterDocumentsTest {

    private static final String WORKSPACE = "cgmes";

    private static final Triple SHAPE =
            Triple.create(
                    NodeFactory.createURI("http://example.com/LineShape"),
                    RDF.type.asNode(),
                    NodeFactory.createURI("http://www.w3.org/ns/shacl#NodeShape"));

    private static final String SHAPE_TEXT =
            "# kept as written\n<http://example.com/LineShape> a"
                    + " <http://www.w3.org/ns/shacl#NodeShape> .\n";

    /** Every graph the adapter wrote, as Fuseki would hold it. */
    private final Dataset stored = DatasetFactory.createGeneral();

    private final List<String> datasets = new ArrayList<>();

    private HttpServer server;
    private DatabasePort databasePort;
    private FusekiSnapshotAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        var config = new DatabaseConfig();
        config.setHttpEndpoint("http://localhost:" + server.getAddress().getPort());

        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        adapter = new FusekiSnapshotAdapter(databasePort, readingBack(), config);
        SessionContext.setSessionId("session-a");
        databasePort.createWorkspaceIfAbsent(WORKSPACE);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        SessionContext.clear();
    }

    private void answer(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        var status = 200;
        if (path.equals("/$/datasets") && exchange.getRequestMethod().equals("POST")) {
            var form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            datasets.add(form.substring("dbName=".length(), form.indexOf('&')));
        } else if (exchange.getRequestMethod().equals("PUT")) {
            var graphName =
                    URLDecoder.decode(
                            exchange.getRequestURI().getRawQuery().substring("graph=".length()),
                            StandardCharsets.UTF_8);
            var lang =
                    RDFLanguages.contentTypeToLang(
                            exchange.getRequestHeaders().getFirst("Content-Type"));
            var graph = GraphFactory.createDefaultGraph();
            RDFParser.source(exchange.getRequestBody()).lang(lang).parse(graph);
            stored.addNamedModel(graphName, ModelFactory.createModelForGraph(graph));
            status = 201;
        } else if (!path.equals("/$/ping")) {
            status = 404;
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    /** The connection the session store fetches a snapshot through, reading what was stored. */
    private DatabaseConnection readingBack() {
        var connection = mock(DatabaseConnection.class);
        when(connection.listDatasets()).thenAnswer(_ -> List.copyOf(datasets));
        when(connection.getPrefixMapping(anyString()))
                .thenReturn(new PrefixMappingReadOnly(PrefixMapping.Factory.create()));
        when(connection.sendSelect(any(Query.class), anyString()))
                .thenAnswer(
                        invocation -> {
                            try (var execution =
                                    QueryExecutionFactory.create(
                                            invocation.<Query>getArgument(0), stored)) {
                                return new ResultSetFormatterImpl(
                                        ResultSetFactory.copyResults(execution.execSelect()));
                            }
                        });
        return connection;
    }

    private void givenSchema(String schemaName) {
        var schema = GraphFactory.createDefaultGraph();
        schema.add(
                Triple.create(
                        NodeFactory.createURI("http://example.com/" + schemaName + "#Line"),
                        RDFS.label.asNode(),
                        NodeFactory.createLiteralString("Line")));
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.WRITE)) {
            transaction.createGraph(schemaName, schema);
            transaction.commit("Created " + schemaName);
        }
    }

    @Test
    void aFetchedSnapshotHoldsTheSchemasAndTheDocumentsAndNothingElse() {
        givenSchema("EQ");
        givenSchema("TP");
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.WRITE)) {
            var withShapes =
                    transaction.createShapesDocument(
                            "eq.ttl", ShapesDocument.Origin.IMPORTED, List.of("EQ", "TP"));
            withShapes.getGraph().add(SHAPE);
            withShapes.setRawText(SHAPE_TEXT);
            transaction.createShapesDocument(
                    "draft.ttl", ShapesDocument.Origin.AUTHORED, List.of());
            transaction.commit("Added documents");
        }

        var token = adapter.createSnapshot(WORKSPACE);
        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        var snapshotName = SnapshotUtils.constructSnapshotName(WORKSPACE, token);
        assertThat(stored.containsNamedModel(ShapesGraphNaming.metadataGraph())).isTrue();
        assertThat(databasePort.listSchemaNames(snapshotName))
                .containsExactlyInAnyOrder("EQ", "TP");
        try (var transaction = databasePort.beginTransaction(snapshotName, ReadWrite.READ)) {
            var documents = transaction.shapesDocuments().values();
            assertThat(documents)
                    .extracting(ShapesDocument::getName)
                    .containsExactlyInAnyOrder("eq.ttl", "draft.ttl");
            var withShapes =
                    documents.stream().filter(d -> d.getName().equals("eq.ttl")).findFirst();
            assertThat(withShapes)
                    .hasValueSatisfying(
                            document -> {
                                assertThat(document.getRawText()).isEqualTo(SHAPE_TEXT);
                                assertThat(document.getSchemas())
                                        .containsExactlyInAnyOrder("EQ", "TP");
                                assertThat(document.getGraph().contains(SHAPE)).isTrue();
                            });
        }
    }
}
