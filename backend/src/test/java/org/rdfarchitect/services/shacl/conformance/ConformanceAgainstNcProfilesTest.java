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

package org.rdfarchitect.services.shacl.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ConformanceFinding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The official NC constraints compared with their own vocabulary.
 *
 * <p>They state every rule for each CIM namespace in use, so the comparison has to tell another CIM
 * version's spelling of a class from a class the schema is missing.
 */
class ConformanceAgainstNcProfilesTest {

    private static final String BASE =
            "../external/entsoe-application-profiles-library/NCP/CurrentRelease/";
    private static final String VOCABULARY = "RDFS/EquipmentReliability-AP-Voc-RDFS2020.rdf";
    private static final String CONSTRAINTS = "SHACL/EquipmentReliability-AP-Con-Simple-SHACL.ttl";

    private static final GraphIdentifier GRAPH =
            new GraphIdentifier("nc", "http://ex.org/EquipmentReliability");

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private ConformanceService service;
    private UUID documentId;

    @BeforeEach
    void setUp() throws IOException {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(GRAPH.datasetName());
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.source(Path.of(BASE, VOCABULARY).toUri().toString()).parse(schema);
        databasePort.createGraph(GRAPH, schema);
        documentId =
                new SHACLStoringService(databasePort)
                        .createShapesDocument(
                                GRAPH,
                                "er.ttl",
                                null,
                                Files.readString(Path.of(BASE, CONSTRAINTS)),
                                Lang.TURTLE)
                        .getId();
        service = new ConformanceService(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void anotherCimVersionsSpellingIsNeitherMissingFromTheSchemaNorAContradiction() {
        var report = service.compare(GRAPH, documentId);

        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getKind() != ConformanceFinding.Kind.DIFFERENT)
                .noneMatch(
                        finding ->
                                finding.getTargetClass().contains("CIM-schema-cim16#")
                                        || finding.getTargetClass().contains("TC57/CIM100#"));
        assertThat(report.getContradictedCount()).isZero();
        assertThat(report.getCompared()).isGreaterThan(1000);
    }
}
