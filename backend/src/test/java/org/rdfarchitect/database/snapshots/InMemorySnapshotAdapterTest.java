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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.SnapshotException;

class InMemorySnapshotAdapterTest {

    private static final String DATASET = "cgmes";
    private static final String GRAPH_URI = "http://example.com/EquipmentProfile";

    private static final Triple CLASS_TRIPLE =
            Triple.create(
                    NodeFactory.createURI("http://example.com/ACLineSegment"),
                    RDF.type.asNode(),
                    RDFS.Class.asNode());

    private DatabasePort databasePort;
    private InMemorySnapshotAdapter adapter;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        adapter = new InMemorySnapshotAdapter(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void createSnapshot_unknownDataset_throws() {
        assertThrows(DataAccessException.class, () -> adapter.createSnapshot("no-such-dataset"));
    }

    @Test
    void fetchSnapshot_unknownToken_throws() {
        assertThrows(SnapshotException.class, () -> adapter.fetchSnapshot("no-such-token"));
    }

    @Test
    void snapshotExists_reflectsCreatedSnapshots() {
        createSampleDataset();

        var token = adapter.createSnapshot(DATASET);

        assertTrue(adapter.snapshotExists(token));
        assertFalse(adapter.snapshotExists("no-such-token"));
    }

    @Test
    void fetchSnapshot_loadsGraphsIntoAnotherSession() {
        SessionContext.setSessionId("session-a");
        createSampleDataset();
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        var snapshotName = SnapshotUtils.constructSnapshotName(DATASET, token);
        assertTrue(databasePort.listDatasets().contains(snapshotName));
        assertTrue(databasePort.listGraphUris(snapshotName).contains(GRAPH_URI));
        try (var ctx =
                databasePort
                        .getGraphWithContext(new GraphIdentifier(snapshotName, GRAPH_URI))
                        .begin(ReadWrite.READ)) {
            assertTrue(ctx.getRdfGraph().contains(CLASS_TRIPLE));
        }
    }

    @Test
    void fetchSnapshot_loadedDatasetIsReadOnly() {
        SessionContext.setSessionId("session-a");
        createSampleDataset();
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        var snapshotName = SnapshotUtils.constructSnapshotName(DATASET, token);
        assertTrue(databasePort.isReadOnly(snapshotName));
    }

    @Test
    void fetchSnapshot_isUnaffectedByLaterChangesToTheDataset() {
        SessionContext.setSessionId("session-a");
        createSampleDataset();
        var token = adapter.createSnapshot(DATASET);
        databasePort.deleteDataset(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        var snapshotName = SnapshotUtils.constructSnapshotName(DATASET, token);
        try (var ctx =
                databasePort
                        .getGraphWithContext(new GraphIdentifier(snapshotName, GRAPH_URI))
                        .begin(ReadWrite.READ)) {
            assertTrue(ctx.getRdfGraph().contains(CLASS_TRIPLE));
        }
    }

    @Test
    void fetchSnapshot_restoresThePrefixMapping() {
        SessionContext.setSessionId("session-a");
        createSampleDataset();
        var prefixes = org.apache.jena.shared.PrefixMapping.Factory.create();
        prefixes.setNsPrefix("ex", "http://example.com/");
        databasePort.setPrefixMapping(DATASET, prefixes);
        var token = adapter.createSnapshot(DATASET);

        SessionContext.setSessionId("session-b");
        adapter.fetchSnapshot(token);

        var snapshotName = SnapshotUtils.constructSnapshotName(DATASET, token);
        assertEquals(
                "http://example.com/",
                databasePort.getPrefixMapping(snapshotName).getNsPrefixURI("ex"));
    }

    private void createSampleDataset() {
        var graph = GraphFactory.createDefaultGraph();
        graph.add(CLASS_TRIPLE);
        databasePort.createGraph(new GraphIdentifier(DATASET, GRAPH_URI), graph);
    }
}
