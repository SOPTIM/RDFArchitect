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

package org.rdfarchitect.services.shacl.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validation against the real CGMES profiles from the ENTSO-E application profiles library.
 *
 * <p>The workspace is loaded with the whole current CGMES RDFS release, one graph per profile,
 * which is how a user actually works with it — and the reason official constraints files
 * referencing terms across profiles come back clean here.
 *
 * <p>These tests deliberately assert that specific official files are error-free rather than that
 * all of them are: a few genuinely are not, mostly through SPARQL that a static check rejects, and
 * pretending otherwise would mean loosening the checks until they found nothing.
 */
class ShapesValidationAgainstEntsoeProfilesTest {

    private static final String PROFILES =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/";
    private static final String RDFS = PROFILES + "RDFS/";
    private static final String CONSTRAINTS = PROFILES + "SHACL/TTL/";

    private static final String DATASET = "cgmes";
    private static final String SESSION = "entsoe-profiles-test";

    /** The profile the shapes documents are attached to; any graph in the workspace would do. */
    private static final GraphIdentifier GRAPH =
            new GraphIdentifier(DATASET, "http://ex.org/CoreEquipment");

    /** A large official file that is free of errors, embedded SPARQL included. */
    private static final String CLEAN_COMPLEX = "61970-452_Equipment-AP-Con-Complex-SHACL.ttl";

    /** Structural shapes only, so its findings come from searching the Turtle for a term. */
    private static final String CLEAN_SIMPLE = "61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl";

    /** Constrains classes that live in a different profile of the same workspace. */
    private static final String CROSS_PROFILE =
            "61970-456_StateVariables-AP-Con-Complex-Explicit-CrossProfile-SHACL.ttl";

    private static InMemoryDatabaseImpl database;
    private static InMemoryDatabaseAdapter databasePort;

    private SHACLStoringService documents;
    private ShapesValidationService service;

    @BeforeAll
    static void loadProfiles() throws IOException {
        SessionContext.setSessionId(SESSION);
        database = new InMemoryDatabaseImpl(new SchemaConfig());
        databasePort = new InMemoryDatabaseAdapter(database);
        databasePort.createDataset(DATASET);
        try (Stream<Path> files = Files.list(Path.of(RDFS))) {
            files.filter(path -> path.toString().endsWith(".rdf"))
                    .sorted()
                    .forEach(ShapesValidationAgainstEntsoeProfilesTest::loadProfile);
        }
        assertThat(databasePort.listGraphUris(DATASET)).hasSizeGreaterThan(1);
    }

    private static void loadProfile(Path file) {
        var graph = GraphFactory.createDefaultGraph();
        RDFDataMgr.read(graph, file.toUri().toString());
        databasePort.createGraph(new GraphIdentifier(DATASET, graphUriOf(file)), graph);
    }

    /** The Equipment profile becomes {@link #GRAPH}, the rest keep their file-derived names. */
    private static String graphUriOf(Path file) {
        return file.getFileName().toString().contains("_Equipment-AP-Voc")
                ? GRAPH.graphUri()
                : "http://ex.org/" + file.getFileName();
    }

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(SESSION);
        documents = new SHACLStoringService(databasePort);
        service = new ShapesValidationService(databasePort, new SchemaIndexCache(databasePort));
        clearShapesDocuments();
    }

    /**
     * The workspace is loaded once because indexing the whole profile set is the expensive part;
     * the shapes documents are not, so they are emptied per test. Left in place, one test's copy of
     * a constraints file would collide with the next test's on every shape IRI.
     */
    private void clearShapesDocuments() {
        documents.listShapesDocuments(GRAPH).stream()
                .filter(info -> !info.isDefault())
                .forEach(info -> documents.deleteShapesDocument(GRAPH, info.getId()));
        documents.replaceShapesDocumentText(GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, "");
    }

    @Test
    void everyProfileInTheWorkspaceIsOneTheShapesAreCheckedAgainst() {
        var report = service.validateTurtle(GRAPH, CLEAN_SIMPLE, read(CLEAN_SIMPLE), null);

        assertThat(report.getProfiles())
                .as("profiles the loader recognised, addressed by their owl:versionIRI")
                .contains(
                        "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                        "http://iec.ch/TC57/ns/CIM/StateVariables-EU/3.0",
                        "http://iec.ch/TC57/ns/CIM/Topology-EU/3.0")
                .as(
                        "the header profile: it declares a version IRI but is not addressable as a"
                                + " CIM profile, so it is indexed generically under that IRI")
                .contains("http://iec.ch/TC57/61970-552/ModelDescription/1")
                .as("one graph in this release declares no owl:versionIRI, so it gets a stand-in")
                .anyMatch(profile -> profile.startsWith("urn:rdfa:profile:"));
        assertThat(report.getProfiles()).hasSize(databasePort.listGraphUris(DATASET).size());
    }

    @Test
    void anOfficialConstraintsFileReportsNoError() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, read(CLEAN_COMPLEX));

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(
                        finding -> finding.getSeverity() == ShapesValidationFinding.Severity.ERROR)
                .isEmpty();
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void anOfficialCrossProfileFileReportsNoError() {
        var report = service.validateTurtle(GRAPH, CROSS_PROFILE, read(CROSS_PROFILE), null);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrorCount()).isZero();
    }

    @Test
    void aCorruptedClassNameIsReportedAtTheLineItWasCorruptedOn() {
        var original = read(CLEAN_SIMPLE);
        var corrupted = original.replaceFirst("cim:ACLineSegment\\b", "cim:AClineSegment");
        assertThat(corrupted).isNotEqualTo(original);

        var report = service.validateTurtle(GRAPH, CLEAN_SIMPLE, corrupted, null);

        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("UNKNOWN_CLASS");
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.ERROR);
                            assertThat(finding.getTerm())
                                    .isEqualTo("http://iec.ch/TC57/CIM100#AClineSegment");
                            assertThat(finding.getLine())
                                    .isEqualTo(lineOf(corrupted, "cim:AClineSegment"));
                        });
    }

    @Test
    void aTermFromAnotherProfileNoWorkspaceGraphHasIsReportedAsUnknown() {
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:InventedShape
                    a sh:NodeShape ;
                    sh:targetClass cim:NotAClassAnyProfileDeclares .
                """;

        var report = service.validateTurtle(GRAPH, "invented.ttl", shapes, null);

        assertThat(report.isValid()).isFalse();
        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("UNKNOWN_CLASS");
                            assertThat(finding.getFoundInProfiles()).isEmpty();
                            assertThat(finding.getLine()).isEqualTo(7);
                        });
    }

    @Test
    void everyFindingPointsInsideTheDocumentItBelongsTo() {
        var text = read(CLEAN_COMPLEX);
        var lineCount = text.split("\n", -1).length;
        var info =
                documents.createShapesDocument(
                        GRAPH, CLEAN_COMPLEX, CLEAN_COMPLEX, text, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, info.getId());

        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getDocumentName()).isEqualTo(CLEAN_COMPLEX);
                            assertThat(result.getFindings())
                                    .isNotEmpty()
                                    .allSatisfy(
                                            finding -> {
                                                if (finding.getLine() != null) {
                                                    assertThat(finding.getLine())
                                                            .isBetween(1, lineCount);
                                                }
                                            });
                        });
    }

    private static String read(String constraintsFile) {
        try {
            return Files.readString(Path.of(CONSTRAINTS, constraintsFile));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read "
                            + constraintsFile
                            + " — is the entsoe-application-profiles-library submodule initialised?",
                    e);
        }
    }

    private static List<ShapesValidationFinding> findings(
            List<ShapesDocumentValidationResult> results) {
        return results.stream().flatMap(result -> result.getFindings().stream()).toList();
    }

    /** 1-based line the token first appears on, so a test asserts a position it can point at. */
    private static int lineOf(String text, String token) {
        var lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(token)) {
                return i + 1;
            }
        }
        throw new AssertionError("Token not found in text: " + token);
    }
}
