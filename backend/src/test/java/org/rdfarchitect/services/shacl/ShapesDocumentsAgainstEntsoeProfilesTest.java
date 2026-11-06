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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.snapshots.InMemorySnapshotAdapter;
import org.rdfarchitect.database.snapshots.SnapshotUtils;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** An official constraints file taken through everything a document can go through. */
class ShapesDocumentsAgainstEntsoeProfilesTest {

    private static final Path OFFICIAL_FILE =
            Path.of(
                    "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/SHACL/TTL/"
                            + "61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl");

    private static final GraphIdentifier GRAPH =
            new GraphIdentifier("cgmes", "http://example.org/EQ");

    private String official;
    private long officialTriples;
    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(Files.exists(OFFICIAL_FILE), "submodule not initialised");
        official = Files.readString(OFFICIAL_FILE, StandardCharsets.UTF_8);
        var parsed = GraphFactory.createDefaultGraph();
        RDFParser.fromString(official, Lang.TURTLE).parse(parsed);
        // Structurally identical blank nodes are merged on the way in, so it is the normalised
        // graph a document holds, not everything a plain parse yields.
        officialTriples = GraphUtils.normalizeBlankNodes(parsed).size();

        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createWorkspaceIfAbsent(GRAPH.datasetName());
        try (var transaction =
                databasePort.beginTransaction(GRAPH.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH.graphUri(), GraphFactory.createDefaultGraph());
            transaction.commit("Created the graph");
        }
        databasePort.enableEditing(GRAPH.datasetName());
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void anImportedFileIsHeldAndExportedExactlyAsWritten() {
        var created =
                service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        "eq.ttl",
                        "eq.ttl",
                        official,
                        Lang.TURTLE);

        assertThat(created.getTripleCount()).isEqualTo(officialTriples);
        assertThat(created.getNodeShapeCount()).isPositive();
        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), created.getId()))
                .isEqualTo(official);
        assertThat(
                        service.exportSelectedSHACLGraph(
                                        GRAPH.datasetName(),
                                        RDFFormat.TURTLE,
                                        List.of(created.getId()),
                                        List.of())
                                .toString(StandardCharsets.UTF_8))
                .isEqualTo(official);
    }

    @Test
    void anEditDeleteAndRestoreComeBackToTheFileAsWritten() {
        var id =
                service.createShapesDocument(
                                GRAPH.datasetName(),
                                List.of(GRAPH.graphUri()),
                                "eq.ttl",
                                "eq.ttl",
                                official,
                                Lang.TURTLE)
                        .getId();
        var target = databasePort.listChanges(GRAPH.datasetName()).getFirst().changeId();

        service.replaceShapesDocumentText(GRAPH.datasetName(), id, official + "\n# edited\n");
        service.deleteShapesDocument(GRAPH.datasetName(), id);
        databasePort.restoreToVersion(GRAPH.datasetName(), target, RevertScope.everything(), null);

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), id)).isEqualTo(official);
        assertThat(service.listShapesDocuments(GRAPH.datasetName()).getFirst().getTripleCount())
                .isEqualTo(officialTriples);
    }

    @Test
    void aShareBringsBackTheFileAsWritten() {
        var id =
                service.createShapesDocument(
                                GRAPH.datasetName(),
                                List.of(GRAPH.graphUri()),
                                "eq.ttl",
                                "eq.ttl",
                                official,
                                Lang.TURTLE)
                        .getId();
        var adapter = new InMemorySnapshotAdapter(databasePort);
        var token = adapter.createSnapshot(GRAPH.datasetName());

        SessionContext.setSessionId(getClass().getSimpleName() + "-other");
        adapter.fetchSnapshot(token);

        var shared = SnapshotUtils.constructSnapshotName(GRAPH.datasetName(), token);
        assertThat(service.getShapesDocumentText(shared, id)).isEqualTo(official);
    }
}
