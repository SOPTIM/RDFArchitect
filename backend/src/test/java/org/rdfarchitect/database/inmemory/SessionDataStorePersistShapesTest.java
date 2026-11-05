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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.graph.PrefixMappingReadOnly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabaseConnection;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.rdf.formatter.ResultSetFormatterImpl;
import org.rdfarchitect.rdf.graph.source.GraphSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Persisting a graph writes its shapes documents too, and fetching brings them back. */
class SessionDataStorePersistShapesTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final Triple SHAPE =
            Triple.create(
                    NodeFactory.createURI("http://ex.org/EQ#TerminalShape"),
                    NodeFactory.createURI("http://www.w3.org/ns/shacl#targetClass"),
                    NodeFactory.createURI("http://ex.org/EQ#Terminal"));

    private static final String SHAPES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # kept verbatim
            ex:TerminalShape sh:targetClass ex:Terminal .
            """;

    /** Stands in for the external triple store: inserts append, as a graph store POST does. */
    private final Dataset remote = DatasetFactory.createGeneral();

    private DatabaseConnection connection;
    private SessionDataStoreImpl store;

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
        store.createDataset(DATASET);
        var schema = GraphFactory.createDefaultGraph();
        schema.add(
                Triple.create(
                        NodeFactory.createURI("http://ex.org/EQ#Terminal"),
                        NodeFactory.createURI("http://www.w3.org/2000/01/rdf-schema#label"),
                        NodeFactory.createLiteralString("Terminal")));
        try (var transaction = store.beginTransaction(DATASET, ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH.graphUri(), schema);
            transaction.commit("Created the graph");
        }
    }

    private UUID create(String name, String sourceFile) {
        return write(
                graph -> {
                    var document = graph.createShapesDocument(name, ShapesDocument.Origin.IMPORTED);
                    document.getGraph().add(SHAPE);
                    document.setRawText(SHAPES);
                    document.setSourceFileName(sourceFile);
                    return document.getId();
                });
    }

    private <T> T write(Function<GraphContext, T> change) {
        try (var transaction = store.beginTransaction(DATASET, ReadWrite.WRITE)) {
            var result = change.apply(transaction.graph(GRAPH.graphUri()));
            transaction.commit("Changed");
            return result;
        }
    }

    private SessionDataStoreImpl fetched() {
        var reloaded = new SessionDataStoreImpl();
        reloaded.fetchFromDatabase(connection);
        return reloaded;
    }

    @Test
    void persistedDocumentsComeBackWithTheirTextAndMetadata() {
        var id = create("eq.ttl", "EQ.ttl");
        write(
                graph -> {
                    graph.getShapesDocuments().get(id).setEnabled(false);
                    return null;
                });

        store.writeToDatabase(connection, GRAPH);

        try (var transaction = fetched().beginTransaction(DATASET, ReadWrite.READ)) {
            var document = transaction.graph(GRAPH.graphUri()).getShapesDocuments().get(id);
            assertThat(document.getName()).isEqualTo("eq.ttl");
            assertThat(document.getSourceFileName()).isEqualTo("EQ.ttl");
            assertThat(document.isEnabled()).isFalse();
            assertThat(document.getRawText()).isEqualTo(SHAPES);
            assertThat(document.getGraph().contains(SHAPE)).isTrue();
        }
    }

    @Test
    void persistingAgainReplacesWhatWasWrittenBefore() {
        var kept = create("kept.ttl", null);
        var deleted = create("gone.ttl", null);
        store.writeToDatabase(connection, GRAPH);

        write(
                graph -> {
                    graph.getShapesDocuments().get(kept).setName("renamed.ttl");
                    graph.removeShapesDocument(deleted);
                    return null;
                });
        store.writeToDatabase(connection, GRAPH);

        try (var transaction = fetched().beginTransaction(DATASET, ReadWrite.READ)) {
            var documents = transaction.graph(GRAPH.graphUri()).getShapesDocuments();
            assertThat(documents).containsOnlyKeys(kept);
            assertThat(documents.get(kept).getName()).isEqualTo("renamed.ttl");
        }
    }

    @Test
    void theShapesDoNotBecomeGraphsOfTheirOwn() {
        create("eq.ttl", null);

        store.writeToDatabase(connection, GRAPH);

        assertThat(fetched().listGraphUris(DATASET)).containsExactly(GRAPH.graphUri());
    }

    @Test
    void persistingAGraphWithoutDocumentsWritesOnlyTheSchema() {
        store.writeToDatabase(connection, GRAPH);

        verify(connection, never()).deleteGraph(eq(DATASET), anyString());
        var written = new ArrayList<String>();
        remote.listModelNames().forEachRemaining(resource -> written.add(resource.getURI()));
        assertThat(written).containsExactly(GRAPH.graphUri());
    }
}
