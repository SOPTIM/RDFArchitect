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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.formatter.ResultSetFormatterImpl;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.rdfarchitect.services.shacl.SHACLStoringService;

import java.util.List;

/** Persisting a graph writes its shapes documents too, and fetching brings them back. */
class SessionDataStorePersistShapesTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # kept verbatim
            ex:TerminalShape a sh:NodeShape ; sh:targetClass ex:Terminal .
            """;

    /** Stands in for the external triple store: inserts append, as a graph store POST does. */
    private final org.apache.jena.query.Dataset remote = DatasetFactory.createGeneral();

    private DatabaseConnection connection;
    private SessionDataStoreImpl store;
    private SHACLStoringService documents;

    @BeforeEach
    void setUp() {
        connection = mock(DatabaseConnection.class);
        when(connection.listDatasets()).thenReturn(List.of(DATASET));
        when(connection.getPrefixMapping(DATASET))
                .thenReturn(new PrefixMappingReadOnly(PrefixMapping.Factory.create()));
        when(connection.sendSelect(any(Query.class), eq(DATASET)))
                .thenAnswer(
                        invocation -> {
                            try (var execution =
                                    QueryExecutionFactory.create(
                                            invocation.<Query>getArgument(0), remote)) {
                                return new ResultSetFormatterImpl(
                                        ResultSetFactory.copyResults(execution.execSelect()));
                            }
                        });
        doAnswer(
                        invocation -> {
                            GraphSource source = invocation.getArgument(0);
                            var target = remote.getNamedModel(source.graphName());
                            source.graph().find().forEach(target.getGraph()::add);
                            return null;
                        })
                .when(connection)
                .insertGraph(any(GraphSource.class), eq(DATASET));
        doAnswer(
                        invocation -> {
                            remote.removeNamedModel(invocation.<String>getArgument(1));
                            return null;
                        })
                .when(connection)
                .deleteGraph(eq(DATASET), anyString());

        store = new SessionDataStoreImpl();
        var schema = GraphFactory.createDefaultGraph();
        schema.add(
                Triple.create(
                        NodeFactory.createURI("http://ex.org/EQ#Terminal"),
                        NodeFactory.createURI("http://www.w3.org/2000/01/rdf-schema#label"),
                        NodeFactory.createLiteralString("Terminal")));
        store.create(GRAPH, schema);
        var port = mock(DatabasePort.class);
        when(port.getGraphWithContext(any(GraphIdentifier.class)))
                .thenAnswer(invocation -> store.getGraphWithContext(invocation.getArgument(0)));
        documents = new SHACLStoringService(port);
    }

    private SessionDataStoreImpl fetched() {
        var reloaded = new SessionDataStoreImpl();
        reloaded.fetchFromDatabase(connection);
        return reloaded;
    }

    @Test
    void persistedDocumentsComeBackWithTheirTextAndMetadata() {
        var id = documents.createShapesDocument(GRAPH, "eq.ttl", "EQ.ttl", SHAPES, Lang.TURTLE);
        documents.updateShapesDocument(GRAPH, id.getId(), null, false, null);

        store.writeToDatabase(connection, GRAPH);

        try (var ctx = fetched().getGraphWithContext(GRAPH).begin(ReadWrite.READ)) {
            var document = ctx.getShapesDocuments().get(id.getId());
            assertThat(document).isNotNull();
            assertThat(document.getName()).isEqualTo("eq.ttl");
            assertThat(document.getSourceFileName()).isEqualTo("EQ.ttl");
            assertThat(document.isEnabled()).isFalse();
            assertThat(document.getRawText()).isEqualTo(SHAPES);
            assertThat(document.getGraph().isEmpty()).isFalse();
        }
    }

    @Test
    void persistingAgainReplacesWhatWasWrittenBefore() {
        var kept = documents.createShapesDocument(GRAPH, "kept.ttl", null, SHAPES, Lang.TURTLE);
        var deleted = documents.createShapesDocument(GRAPH, "gone.ttl", null, SHAPES, Lang.TURTLE);
        store.writeToDatabase(connection, GRAPH);

        documents.updateShapesDocument(GRAPH, kept.getId(), "renamed.ttl", null, null);
        documents.deleteShapesDocument(GRAPH, deleted.getId());
        store.writeToDatabase(connection, GRAPH);

        try (var ctx = fetched().getGraphWithContext(GRAPH).begin(ReadWrite.READ)) {
            var byId = ctx.getShapesDocuments();
            assertThat(byId).doesNotContainKey(deleted.getId());
            assertThat(byId.get(kept.getId()).getName()).isEqualTo("renamed.ttl");
        }
    }

    @Test
    void theShapesDoNotBecomeGraphsOfTheirOwn() {
        documents.createShapesDocument(GRAPH, "eq.ttl", null, SHAPES, Lang.TURTLE);

        store.writeToDatabase(connection, GRAPH);

        assertThat(fetched().listGraphUris(DATASET)).containsExactly(GRAPH.graphUri());
    }
}
