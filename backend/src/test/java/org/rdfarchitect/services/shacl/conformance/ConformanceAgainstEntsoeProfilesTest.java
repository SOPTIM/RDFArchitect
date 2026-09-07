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
 * The question Phase 6 exists to answer, asked of the real thing.
 *
 * <p>The CGMES 3.0 Equipment profile ships both an RDFS vocabulary and the constraints file that
 * describes it, so importing one and generating from the other is exactly the situation a user is
 * in. If the two do not line up here, the feature answers nothing.
 */
class ConformanceAgainstEntsoeProfilesTest {

    private static final String BASE =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/";
    private static final String VOCABULARY = "RDFS/61970-600-2_Equipment-AP-Voc-RDFS2020.rdf";
    private static final String CONSTRAINTS =
            "SHACL/TTL/61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl";

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH =
            new GraphIdentifier(DATASET, "http://ex.org/CoreEquipment");

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private ConformanceService service;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);

        var schema = GraphFactory.createDefaultGraph();
        RDFParser.source(uriOf(VOCABULARY)).parse(schema);
        databasePort.createGraph(GRAPH, schema);

        var documents = new SHACLStoringService(databasePort);
        documentId =
                documents
                        .createShapesDocument(
                                GRAPH, "official.ttl", null, read(CONSTRAINTS), Lang.TURTLE)
                        .getId();
        service = new ConformanceService(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void theOfficialConstraintsAndTheProfileTheyDescribeLineUp() {
        var report = service.compare(GRAPH, documentId);

        // Both sides state the same set of class-and-property constraints. This is the assertion
        // that says the matching works at all: nothing is compared by shape name, and generated and
        // official shapes share no naming convention.
        assertThat(report.getCompared()).isGreaterThan(1000);
        assertThat(report.getMissingInDocumentCount()).isZero();
        assertThat(report.getNotInSchemaCount()).isZero();
        assertThat(report.getAgreeing()).isGreaterThan((int) (report.getCompared() * 0.99));
    }

    @Test
    void theOnlyDisagreementIsTheKnownDatatypeMapping() {
        var report = service.compare(GRAPH, documentId);

        // RDFArchitect maps the CIM primitive "MonthDay" to a made-up xsd:MonthDay, where the
        // official file uses xsd:gMonthDay. A real generator bug, and exactly what this feature is
        // meant to surface — see XSDDatatypeMapper.
        assertThat(report.getFindings())
                .allSatisfy(
                        finding ->
                                assertThat(finding.getKind())
                                        .isEqualTo(ConformanceFinding.Kind.CONTRADICTED))
                .allSatisfy(finding -> assertThat(finding.getPath()).contains("Season."))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getSchemaSays()).contains("MonthDay");
                            assertThat(finding.getDocumentSays()).contains("gMonthDay");
                            assertThat(finding.getMessage()).contains("cannot be both");
                        });
    }

    @Test
    void aChangeToTheSchemaShowsUpAsADisagreement() {
        var before = service.compare(GRAPH, documentId);

        // Make one attribute optional that the official constraints require.
        withRelaxedCardinality();

        var after = service.compare(GRAPH, documentId);

        assertThat(after.getFindings()).hasSizeGreaterThan(before.getFindings().size());
        assertThat(after.isConforms()).isFalse();
        assertThat(after.getFindings())
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getPath())
                                    .isEqualTo("http://iec.ch/TC57/CIM100#IdentifiedObject.name");
                            assertThat(finding.getKind())
                                    .isIn(
                                            ConformanceFinding.Kind.DIFFERENT,
                                            ConformanceFinding.Kind.CONTRADICTED);
                        });
    }

    /** Drops the {@code 1..1} multiplicity of IdentifiedObject.name so the schema allows none. */
    private void withRelaxedCardinality() {
        var multiplicity =
                org.apache.jena.graph.NodeFactory.createURI(
                        "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#multiplicity");
        var name =
                org.apache.jena.graph.NodeFactory.createURI(
                        "http://iec.ch/TC57/CIM100#IdentifiedObject.name");
        try (var ctx =
                databasePort
                        .getGraphWithContext(GRAPH)
                        .begin(org.apache.jena.query.ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();
            graph.stream(name, multiplicity, org.apache.jena.graph.Node.ANY)
                    .toList()
                    .forEach(graph::delete);
            graph.add(
                    name,
                    multiplicity,
                    org.apache.jena.graph.NodeFactory.createURI(
                            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:0..1"));
            ctx.commit();
        }
    }

    private static String uriOf(String file) {
        return Path.of(BASE, file).toUri().toString();
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of(BASE, file));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read "
                            + file
                            + " — is the entsoe-application-profiles-library submodule initialised?",
                    e);
        }
    }
}
