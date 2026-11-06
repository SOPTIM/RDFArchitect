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

package org.rdfarchitect.database.implementations.http.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.implementations.DatabaseAdminProtocol;
import org.rdfarchitect.exception.database.DataAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What replacing graphs sends to the store.
 *
 * <p>It has to be one SPARQL update. Several requests could stop halfway, and a graph store PUT
 * over an existing graph clears the prefixes of the whole dataset on Fuseki, every schema's
 * included.
 */
class HttpUpdateCommandReplaceGraphsTest {

    private static final String DATASET = "cgmes";
    private static final String OLD = "urn:rdfa:shacl:http%3A%2F%2Fex.org%2FEQ:old";
    private static final String KEPT = "urn:rdfa:shacl:http%3A%2F%2Fex.org%2FEQ:kept";
    private static final String METADATA = "urn:rdfa:shacl-meta:http%3A%2F%2Fex.org%2FEQ";

    private record Received(String method, String contentType, String body) {}

    private final List<Received> received = new ArrayList<>();
    private int status = 204;
    private HttpServer server;
    private HttpUpdateCommandImpl command;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/" + DATASET,
                exchange -> {
                    received.add(
                            new Received(
                                    exchange.getRequestMethod(),
                                    exchange.getRequestHeaders().getFirst("Content-Type"),
                                    new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            StandardCharsets.UTF_8)));
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
        server.start();
        var admin = mock(DatabaseAdminProtocol.class);
        when(admin.listDatasets()).thenReturn(List.of(DATASET));
        command =
                new HttpUpdateCommandImpl(admin)
                        .setEndpoint("http://localhost:" + server.getAddress().getPort())
                        .setDatasetName(DATASET);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static Graph holding(String object) {
        var graph = GraphFactory.createDefaultGraph();
        graph.add(
                Triple.create(
                        NodeFactory.createBlankNode("b:1"),
                        NodeFactory.createURI("http://ex.org/says"),
                        NodeFactory.createLiteralString(object)));
        return graph;
    }

    @Test
    void itIsOneSparqlUpdateRequest() {
        command.replaceGraphs(List.of(OLD), Map.of(KEPT, holding("new")));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().method()).isEqualTo("POST");
        assertThat(received.getFirst().contentType()).startsWith("application/sparql-update");
    }

    @Test
    void whatItSendsRemovesAndReplacesAndKeepsTheTextExact() {
        var text = "line \"one\"\nline \\two\t\u20ac # not a comment";
        var written = new LinkedHashMap<String, Graph>();
        written.put(KEPT, holding("new"));
        written.put(METADATA, holding(text));
        var store = DatasetFactory.createTxnMem();
        store.getNamedModel(OLD).getGraph().add(holding("stale").find().next());
        store.getNamedModel(KEPT).getGraph().add(holding("stale").find().next());
        store.getPrefixMapping().setNsPrefix("cim", "http://iec.ch/TC57/CIM100#");

        command.replaceGraphs(List.of(OLD, KEPT), written);
        UpdateAction.execute(UpdateFactory.create(received.getFirst().body()), store);

        assertThat(store.containsNamedModel(OLD)).isFalse();
        var kept = store.getNamedModel(KEPT).getGraph().find().toList();
        assertThat(kept).hasSize(1);
        assertThat(kept.getFirst().getObject().getLiteralLexicalForm()).isEqualTo("new");
        var metadata = store.getNamedModel(METADATA).getGraph().find().toList();
        assertThat(metadata.getFirst().getObject().getLiteralLexicalForm()).isEqualTo(text);
        assertThat(store.getPrefixMapping().getNsPrefixURI("cim")).isNotNull();
    }

    @Test
    void onlyRemovingIsFineToo() {
        command.replaceGraphs(List.of(OLD), Map.of());

        var store = DatasetFactory.createTxnMem();
        store.getNamedModel(OLD).getGraph().add(holding("stale").find().next());
        UpdateAction.execute(UpdateFactory.create(received.getFirst().body()), store);
        assertThat(store.containsNamedModel(OLD)).isFalse();
    }

    @Test
    void aRefusalFromTheStoreIsReportedAsOne() {
        status = 500;

        assertThatThrownBy(() -> command.replaceGraphs(List.of(OLD), Map.of(KEPT, holding("new"))))
                .isInstanceOf(DataAccessException.class);
    }
}
