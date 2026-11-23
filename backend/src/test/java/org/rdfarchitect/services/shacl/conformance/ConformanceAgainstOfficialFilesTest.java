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
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ConformanceFinding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * CGMES 3.0 profiles compared with every official constraints file named after them, the way a user
 * imports a release.
 */
class ConformanceAgainstOfficialFilesTest {

    private static final String RELEASE =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/";

    private static final String CIM = "http://iec.ch/TC57/CIM100#";

    private static final String WORKSPACE = "cgmes";

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);
    private final SHACLStoringService documents = new SHACLStoringService(databasePort);
    private final ConformanceService service = new ConformanceService(databasePort);

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(WORKSPACE);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void aRuleTheCrossProfileFileStatesOnAnAbstractClassIsComparedOnItsSubclasses() {
        var operation = profile("Operation");

        var report = service.compare(operation);

        // The implicit cross-profile file targets cim:Measurement and cim:Control, which data is
        // never typed with; its rules hold for Analog, Discrete, Command and the other subclasses.
        assertThat(report.getFindings())
                .noneMatch(finding -> finding.getKind() == ConformanceFinding.Kind.NOT_IN_SCHEMA);
        assertThat(report.getFindings())
                .filteredOn(
                        finding ->
                                finding.getPath().equals(CIM + "Measurement.PowerSystemResource"))
                .filteredOn(finding -> finding.getTargetClass().equals(CIM + "Analog"))
                .singleElement()
                .satisfies(
                        finding ->
                                assertThat(finding.getStatedIn())
                                        .contains(
                                                "61970-600-2_Operation-AP-Con-Complex-Implicit-CrossProfile-SHACL.ttl"));
    }

    @Test
    void aValueTypeListingClassesOnlyAnotherProfileDeclaresIsNotAContradiction() {
        // The Operation files list the equipment a measurement may be on, declared in Equipment,
        // which is not in this workspace.
        var report = service.compare(profile("Operation"));

        assertThat(report.getContradictedCount()).isZero();
        assertThat(report.getFindings())
                .filteredOn(
                        finding ->
                                finding.getPath().equals(CIM + "Measurement.PowerSystemResource"))
                .isNotEmpty()
                .allSatisfy(
                        finding ->
                                assertThat(finding.getMessage())
                                        .contains("cannot be checked")
                                        .contains("cim:ACLineSegment"));
    }

    @Test
    void withTheProfileDeclaringThemTheSameListAgrees() {
        profile("Equipment");
        var report = service.compare(profile("Operation"));

        assertThat(report.getContradictedCount()).isZero();
        assertThat(report.getFindings())
                .noneMatch(finding -> finding.getMessage().contains("cannot be checked"));
    }

    /** Imports a profile's vocabulary as a schema, with every official file named after it. */
    private GraphIdentifier profile(String name) {
        var schema = new GraphIdentifier(WORKSPACE, name);
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.source(
                        Path.of(RELEASE + "RDFS/61970-600-2_" + name + "-AP-Voc-RDFS2020.rdf")
                                .toUri()
                                .toString())
                .parse(graph);
        DatabaseFixtures.createGraph(databasePort, schema, graph);
        try (var files = Files.list(Path.of(RELEASE + "SHACL/TTL"))) {
            for (var file : files.sorted().toList()) {
                if (file.getFileName().toString().contains("_" + name + "-AP-")) {
                    documents.createShapesDocument(
                            WORKSPACE,
                            List.of(name),
                            file.getFileName().toString(),
                            null,
                            Files.readString(file),
                            Lang.TURTLE);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return schema;
    }
}
