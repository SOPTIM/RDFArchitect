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
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * NC profiles against the CGMES profiles they build on, in one workspace.
 *
 * <p>An NC profile adds properties to classes CGMES declares and inherits the rest, and its
 * constraints state value types with {@code sh:class} where generated SHACL lists the permitted
 * {@code rdf:type}s. Neither may read as disagreement.
 */
class ConformanceAcrossProfilesTest {

    private static final String LIBRARY = "../external/entsoe-application-profiles-library/";
    private static final String CGMES = LIBRARY + "CGMES/CurrentRelease/RDFS/61970-600-2_";
    private static final String NC = LIBRARY + "NCP/CurrentRelease/";

    private static final String DATASET = "workspace";

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);
    private final SHACLStoringService documents = new SHACLStoringService(databasePort);
    private final ConformanceService service = new ConformanceService(databasePort);

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);
        load("http://ex.org/EQ", CGMES + "Equipment-AP-Voc-RDFS2020.rdf");
        load("http://ex.org/SSH", CGMES + "SteadyStateHypothesis-AP-Voc-RDFS2020.rdf");
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void anNcProfileIsComparedAgainstTheClassesItBuildsOn() {
        var report = compareOfficial("SteadyStateInstruction");

        // Generated from the NC graph alone, every constraint on a CGMES class was "not in the
        // schema" (379 of them) — with that class declared right next to it.
        assertThat(report.getCompared()).isGreaterThan(600);
        assertThat(report.getContradictedCount()).isZero();
        assertThat(report.getDifferentCount()).isZero();
        // NC files also target the CIM16 classes, for older data. No schema here uses that
        // namespace, so there is nothing to compare them with, and they are not reported as
        // classes the schema is missing.
        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getKind() == ConformanceFinding.Kind.NOT_IN_SCHEMA)
                .noneSatisfy(
                        finding ->
                                assertThat(finding.getTargetClass())
                                        .startsWith("http://iec.ch/TC57/CIM100#"))
                .noneSatisfy(finding -> assertThat(finding.getTargetClass()).contains("cim16"));
    }

    @Test
    void aGraphIsOnlyAskedToCoverWhatItDeclaresItself() {
        var report = compareOfficial("SteadyStateInstruction");

        assertThat(report.getFindings())
                .filteredOn(
                        finding -> finding.getKind() == ConformanceFinding.Kind.MISSING_IN_DOCUMENT)
                .noneSatisfy(
                        finding ->
                                assertThat(finding.getPath())
                                        .isEqualTo("http://iec.ch/TC57/CIM100#ACLineSegment.r"));
    }

    @Test
    void anNcValueTypeStatedWithShClassAgreesWithTheGeneratedTypeList() throws IOException {
        // The subclasses AssessedElement's ranges admit are declared across the NC profiles.
        try (var files = Files.list(Path.of(NC + "RDFS"))) {
            files.filter(file -> !file.getFileName().toString().startsWith("AssessedElement"))
                    .filter(file -> file.toString().endsWith(".rdf"))
                    .sorted()
                    .forEach(file -> load("http://ex.org/" + file.getFileName(), file.toString()));
        }
        var report = compareOfficial("AssessedElement");

        // Every association used to read as DIFFERENT: the generated (p rdf:type) sh:in list was
        // not read at all, so the schema side stated no value type.
        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getKind() == ConformanceFinding.Kind.DIFFERENT)
                .allSatisfy(finding -> assertThat(finding.getSchemaSays()).contains("of class"));
        assertThat(report.getContradictedCount()).isZero();
    }

    @Test
    void askingAgainGivesTheSameAnswer() {
        var graph = load("http://ex.org/AE", NC + "RDFS/AssessedElement-AP-Voc-RDFS2020.rdf");
        var documentId = document(graph, "AssessedElement");

        var first = service.compare(graph, documentId);
        var second = service.compare(graph, documentId);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aClassIsAbstractOrNotAsTheCheckedProfileSaysNotAsAnotherDoes() {
        // SteadyStateHypothesis marks cim:Equipment concrete; Equipment does not. Taking SSH's word
        // expected the Equipment constraints to state rules on the abstract class itself.
        var eq = new GraphIdentifier(DATASET, "http://ex.org/EQ");
        var id =
                documents
                        .createShapesDocument(
                                eq,
                                "eq.ttl",
                                null,
                                read(
                                        LIBRARY
                                                + "CGMES/CurrentRelease/SHACL/TTL/"
                                                + "61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl"),
                                Lang.TURTLE)
                        .getId();

        var report = service.compare(eq, id);

        assertThat(report.getFindings())
                .noneMatch(
                        finding ->
                                finding.getTargetClass()
                                        .equals("http://iec.ch/TC57/CIM100#Equipment"));
        assertThat(report.getMissingInDocumentCount()).isZero();
    }

    private ConformanceReport compareOfficial(String profile) {
        var graph =
                load("http://ex.org/" + profile, NC + "RDFS/" + profile + "-AP-Voc-RDFS2020.rdf");
        return service.compare(graph, document(graph, profile));
    }

    private UUID document(GraphIdentifier graph, String profile) {
        return documents
                .createShapesDocument(
                        graph,
                        profile + ".ttl",
                        null,
                        read(NC + "SHACL/" + profile + "-AP-Con-Simple-SHACL.ttl"),
                        Lang.TURTLE)
                .getId();
    }

    private GraphIdentifier load(String graphUri, String file) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.source(Path.of(file).toUri().toString()).parse(graph);
        var identifier = new GraphIdentifier(DATASET, graphUri);
        databasePort.createGraph(identifier, graph);
        return identifier;
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of(file));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read "
                            + file
                            + " — is the entsoe-application-profiles-library submodule initialised?",
                    e);
        }
    }
}
