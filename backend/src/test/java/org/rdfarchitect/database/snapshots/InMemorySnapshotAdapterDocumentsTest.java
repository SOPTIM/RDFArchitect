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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import java.util.UUID;
import java.util.function.Consumer;

/** Sharing a workspace in memory carries its shapes documents along. */
class InMemorySnapshotAdapterDocumentsTest {

    private static final String DATASET = "cgmes";
    private static final String GRAPH_URI = "http://example.com/EquipmentProfile";

    private static final Triple SHAPE =
            Triple.create(
                    NodeFactory.createURI("http://example.com/Shape"),
                    RDF.type.asNode(),
                    NodeFactory.createURI("http://www.w3.org/ns/shacl#NodeShape"));

    private static final String SHAPE_TEXT =
            "# kept as written\n<http://example.com/Shape> a"
                    + " <http://www.w3.org/ns/shacl#NodeShape> .\n";

    private DatabasePort databasePort;
    private InMemorySnapshotAdapter adapter;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        adapter = new InMemorySnapshotAdapter(databasePort);
        SessionContext.setSessionId("session-a");
        databasePort.createWorkspaceIfAbsent(DATASET);
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH_URI, GraphFactory.createDefaultGraph());
            transaction.commit("Created the graph");
        }
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private void write(String dataset, Consumer<GraphContext> change) {
        try (var transaction = databasePort.beginTransaction(dataset, ReadWrite.WRITE)) {
            change.accept(transaction.graph(GRAPH_URI));
            transaction.commit("Changed");
        }
    }

    private void read(String dataset, Consumer<GraphContext> reader) {
        try (var transaction = databasePort.beginTransaction(dataset, ReadWrite.READ)) {
            reader.accept(transaction.graph(GRAPH_URI));
        }
    }

    private UUID createImported() {
        var id = new UUID[1];
        write(
                DATASET,
                graph -> {
                    var document =
                            graph.createShapesDocument(
                                    "official.ttl", ShapesDocument.Origin.IMPORTED);
                    document.getGraph().add(SHAPE);
                    document.setRawText(SHAPE_TEXT);
                    document.setSourceFileName("official.ttl");
                    document.setEnabled(false);
                    id[0] = document.getId();
                });
        return id[0];
    }

    @Test
    void aFetchBringsBackDocumentsWithTheirTextAndMetadata() {
        var imported = createImported();
        var empty = new UUID[1];
        write(
                DATASET,
                graph ->
                        empty[0] =
                                graph.createShapesDocument(
                                                "draft.ttl", ShapesDocument.Origin.AUTHORED)
                                        .getId());
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        read(
                SnapshotUtils.constructSnapshotName(DATASET, token),
                graph -> {
                    var documents = graph.getShapesDocuments();
                    var document = documents.get(imported);
                    assertThat(document.getName()).isEqualTo("official.ttl");
                    assertThat(document.getOrigin()).isEqualTo(ShapesDocument.Origin.IMPORTED);
                    assertThat(document.getRawText()).isEqualTo(SHAPE_TEXT);
                    assertThat(document.isEnabled()).isFalse();
                    assertThat(document.getGraph().contains(SHAPE)).isTrue();
                    assertThat(documents.get(empty[0]).getName()).isEqualTo("draft.ttl");
                });
    }

    @Test
    void eachFetchLoadsItsOwnCopyOfTheDocuments() {
        var id = createImported();
        var token = adapter.createSnapshot(DATASET);
        var snapshotName = SnapshotUtils.constructSnapshotName(DATASET, token);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);
        write(snapshotName, graph -> graph.getShapesDocuments().get(id).getGraph().clear());

        SessionContext.setSessionId("session-c");
        adapter.fetchSnapshot(token);

        read(
                snapshotName,
                graph ->
                        assertThat(graph.getShapesDocuments().get(id).getGraph().contains(SHAPE))
                                .isTrue());
    }

    @Test
    void aSnapshotIsNotChangedByLaterEditsToTheWorkspace() {
        var id = createImported();
        var token = adapter.createSnapshot(DATASET);
        write(DATASET, graph -> graph.getShapesDocuments().get(id).setRawText("edited"));

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        read(
                SnapshotUtils.constructSnapshotName(DATASET, token),
                graph ->
                        assertThat(graph.getShapesDocuments().get(id).getRawText())
                                .isEqualTo(SHAPE_TEXT));
    }

    @Test
    void aFetchedDocumentLeavesNothingToUndo() {
        createImported();
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        assertThat(databasePort.canUndo(SnapshotUtils.constructSnapshotName(DATASET, token)))
                .isFalse();
    }

    @Test
    void aWorkspaceWithoutDocumentsFetchesWithoutAny() {
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        read(
                SnapshotUtils.constructSnapshotName(DATASET, token),
                graph -> assertThat(graph.getShapesDocuments()).isEmpty());
    }

    @Test
    void customShaclStaysOutOfASnapshotAsBefore() {
        write(DATASET, (GraphContext graph) -> addTo(graph.getCustomSHACL()));
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        read(
                SnapshotUtils.constructSnapshotName(DATASET, token),
                graph -> assertThat(graph.getCustomSHACL().isEmpty()).isTrue());
    }

    private static void addTo(Graph graph) {
        graph.add(SHAPE);
    }
}
