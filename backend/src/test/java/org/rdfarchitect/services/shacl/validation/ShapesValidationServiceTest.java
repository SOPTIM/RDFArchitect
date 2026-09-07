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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.List;
import java.util.UUID;

/**
 * Validating a graph's shapes against the schema its workspace holds.
 *
 * <p>The schema here is a small RDFS vocabulary rather than a real profile, so that these tests are
 * about the service's behaviour — positions, per-document reporting, contradictions — and not about
 * CIM. Validation against real ENTSO-E profiles is covered by {@link
 * ShapesValidationAgainstEntsoeProfilesTest}.
 */
class ShapesValidationServiceTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final String SCHEMA =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .

            <http://ex.org/EQ> a owl:Ontology ;
                owl:versionIRI <http://ex.org/EQ/1.0> .

            cim:ACLineSegment a rdfs:Class .
            cim:Terminal a rdfs:Class .
            cim:ACLineSegment.length a rdf:Property ;
                rdfs:domain cim:ACLineSegment .
            cim:Terminal.sequenceNumber a rdf:Property ;
                rdfs:domain cim:Terminal .
            """;

    private static final String VALID_SHAPES =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix cim: <http://iec.ch/TC57/CIM100#> .
            @prefix ex:  <http://ex.org/shapes#> .

            ex:ACLineSegmentShape
                a sh:NodeShape ;
                sh:targetClass cim:ACLineSegment ;
                sh:property [
                    sh:path cim:ACLineSegment.length ;
                    sh:minCount 1 ;
                ] .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SHACLStoringService documents;
    private ShapesValidationService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        databasePort.createGraph(GRAPH, schema);
        documents = new SHACLStoringService(databasePort);
        service = new ShapesValidationService(databasePort, new SchemaIndexCache(databasePort));
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    // -------------------------------------------------------------------------
    // Terms the schema does or does not have
    // -------------------------------------------------------------------------

    @Test
    void shapesOverKnownTermsReportNoError() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);

        var report = service.validateShapes(GRAPH, null);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrorCount()).isZero();
        assertThat(report.getProfiles()).contains("http://ex.org/EQ/1.0");
        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getDocumentId())
                                    .isEqualTo(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID);
                            assertThat(result.isValid()).isTrue();
                        });
    }

    @Test
    void anUnknownClassIsReportedAtItsLineAndColumn() {
        var shapes = VALID_SHAPES.replace("cim:ACLineSegment ;", "cim:ACLineSegmentt ;");
        documents.replaceShapesDocumentText(GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, shapes);

        var report = service.validateShapes(GRAPH, null);

        assertThat(report.isValid()).isFalse();
        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("UNKNOWN_CLASS");
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.ERROR);
                            assertThat(finding.getSource())
                                    .isEqualTo(ShapesValidationFinding.Source.SHAPE);
                            assertThat(finding.getTerm())
                                    .isEqualTo("http://iec.ch/TC57/CIM100#ACLineSegmentt");
                            assertThat(finding.getLine())
                                    .isEqualTo(lineOf(shapes, "cim:ACLineSegmentt"));
                            assertThat(finding.getColumn()).isNotNull();
                        });
    }

    @Test
    void anUnknownPropertyIsReported() {
        var shapes = VALID_SHAPES.replace("ACLineSegment.length", "ACLineSegment.lenght");
        documents.replaceShapesDocumentText(GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, shapes);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("UNKNOWN_PROPERTY");
                            assertThat(finding.getLine())
                                    .isEqualTo(lineOf(shapes, "ACLineSegment.lenght"));
                        });
    }

    // -------------------------------------------------------------------------
    // Which documents are validated
    // -------------------------------------------------------------------------

    @Test
    void disabledDocumentsAreLeftOutButCanStillBeAskedForById() {
        var broken = VALID_SHAPES.replace("cim:ACLineSegment ;", "cim:Nonsense ;");
        var info = documents.createShapesDocument(GRAPH, "draft.ttl", null, broken, Lang.TURTLE);
        documents.updateShapesDocument(GRAPH, info.getId(), null, false, null);

        assertThat(service.validateShapes(GRAPH, null).getDocuments())
                .extracting(ShapesDocumentValidationResult::getDocumentId)
                .doesNotContain(info.getId());

        var targeted = service.validateShapes(GRAPH, info.getId());
        assertThat(targeted.getDocuments()).hasSize(1);
        assertThat(targeted.isValid()).isFalse();
    }

    @Test
    void anUnknownDocumentIdIsNotFound() {
        var missing = UUID.randomUUID();
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.validateShapes(GRAPH, missing));
    }

    @Test
    void eachDocumentIsReportedSeparately() {
        var broken =
                VALID_SHAPES
                        .replace("cim:ACLineSegment ;", "cim:Nonsense ;")
                        .replace("ex:ACLineSegmentShape", "ex:OtherShape");
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        var second = documents.createShapesDocument(GRAPH, "extra.ttl", null, broken, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(report.getDocuments()).hasSize(2);
        assertThat(report.getDocuments())
                .filteredOn(result -> result.getDocumentId().equals(second.getId()))
                .singleElement()
                .satisfies(result -> assertThat(result.getErrorCount()).isPositive());
        assertThat(report.getDocuments())
                .filteredOn(
                        result ->
                                result.getDocumentId()
                                        .equals(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID))
                .singleElement()
                .satisfies(result -> assertThat(result.isValid()).isTrue());
    }

    // -------------------------------------------------------------------------
    // Turtle that is not stored
    // -------------------------------------------------------------------------

    @Test
    void turtleThatDoesNotParseIsReportedAsAFindingWithItsPosition() {
        var broken =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .

                ex:NoSuchPrefixShape
                    a sh:NodeShape .
                """;

        var report = service.validateTurtle(GRAPH, "draft.ttl", broken, null);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getDocumentId()).isNull();
                            assertThat(result.getDocumentName()).isEqualTo("draft.ttl");
                        });
        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getSource())
                                    .isEqualTo(ShapesValidationFinding.Source.SYNTAX);
                            assertThat(finding.getCode()).isEqualTo("TURTLE_PARSE_ERROR");
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.ERROR);
                            assertThat(finding.getMessage()).contains("prefix");
                            assertThat(finding.getLine()).isEqualTo(3);
                        });
    }

    @Test
    void turtleThatParsesIsCheckedAgainstTheSchema() {
        var report = service.validateTurtle(GRAPH, "draft.ttl", VALID_SHAPES, null);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getDocumentId()).isNull();
                            assertThat(result.getFindings()).isEmpty();
                        });
    }

    // -------------------------------------------------------------------------
    // Contradictions between documents
    // -------------------------------------------------------------------------

    @Test
    void oneShapeIriInTwoDocumentsIsReportedInBoth() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        var second =
                documents.createShapesDocument(GRAPH, "copy.ttl", null, VALID_SHAPES, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(report.getDocuments())
                .allSatisfy(
                        result ->
                                assertThat(result.getFindings())
                                        .anySatisfy(
                                                finding -> {
                                                    assertThat(finding.getCode())
                                                            .isEqualTo("DUPLICATE_SHAPE_IRI");
                                                    assertThat(finding.getSource())
                                                            .isEqualTo(
                                                                    ShapesValidationFinding.Source
                                                                            .CONFLICT);
                                                    assertThat(finding.getTerm())
                                                            .isEqualTo(
                                                                    "http://ex.org/shapes#ACLineSegmentShape");
                                                }));
        assertThat(report.getDocuments())
                .extracting(ShapesDocumentValidationResult::getDocumentId)
                .contains(second.getId());
        assertThat(report.isValid()).isFalse();
    }

    @Test
    void aCardinalityPairNothingCanSatisfyIsReported() {
        var tighter =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/other#> .

                ex:ACLineSegmentMaxShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:maxCount 0 ;
                    ] .
                """;
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        documents.createShapesDocument(GRAPH, "tighter.ttl", null, tighter, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "UNSATISFIABLE_CARDINALITY".equals(finding.getCode()))
                .hasSize(2)
                .allSatisfy(
                        finding -> {
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.ERROR);
                            assertThat(finding.getMessage())
                                    .contains("sh:minCount 1")
                                    .contains("sh:maxCount 0");
                        });
    }

    @Test
    void aMaxCountThatIsNotANumberDoesNotHideTheConflict() {
        // The regression this guards: an unparsable count used to sort below every real one, so it
        // won the search for the strictest max, widened the bound to Integer.MAX_VALUE, and the
        // genuinely unsatisfiable pair it displaced went unreported.
        var tighter =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/other#> .

                ex:ACLineSegmentMaxShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:maxCount 0 ;
                    ] .
                """;
        var malformed =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/third#> .

                ex:ACLineSegmentVagueShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:maxCount "many" ;
                    ] .
                """;
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        documents.createShapesDocument(GRAPH, "tighter.ttl", null, tighter, Lang.TURTLE);
        documents.createShapesDocument(GRAPH, "vague.ttl", null, malformed, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "UNSATISFIABLE_CARDINALITY".equals(finding.getCode()))
                .hasSize(2)
                .allSatisfy(
                        finding ->
                                assertThat(finding.getMessage())
                                        .contains("sh:minCount 1")
                                        .contains("sh:maxCount 0"));
    }

    @Test
    void aContradictionInsideOneDocumentIsReportedOnce() {
        // Both halves of the pair are in the same document and anchor on the same node shape, so
        // the two calls that report it produce the same finding — which nothing downstream removes.
        var selfContradictory =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:TerminalShape
                    a sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property [
                        sh:path cim:Terminal.sequenceNumber ;
                        sh:minCount 2 ;
                    ] ;
                    sh:property [
                        sh:path cim:Terminal.sequenceNumber ;
                        sh:maxCount 1 ;
                    ] .
                """;
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, selfContradictory);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "UNSATISFIABLE_CARDINALITY".equals(finding.getCode()))
                .hasSize(1);
    }

    @Test
    void twoDatatypesOnOnePathAreReported() {
        var stringShape = datatypeShapes("http://www.w3.org/2001/XMLSchema#string", "ex:First");
        var intShape = datatypeShapes("http://www.w3.org/2001/XMLSchema#integer", "ex:Second");
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, stringShape);
        documents.createShapesDocument(GRAPH, "second.ttl", null, intShape, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "CONFLICTING_DATATYPE".equals(finding.getCode()))
                .hasSize(2)
                .allSatisfy(
                        finding ->
                                assertThat(finding.getMessage())
                                        .contains("XMLSchema#string")
                                        .contains("XMLSchema#integer"));
    }

    @Test
    void complementaryConstraintsInTwoDocumentsAreNotAConflict() {
        var datatypeOnly = datatypeShapes("http://www.w3.org/2001/XMLSchema#string", "ex:Other");
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        documents.createShapesDocument(GRAPH, "datatype.ttl", null, datatypeOnly, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, null);

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getSource)
                .doesNotContain(ShapesValidationFinding.Source.CONFLICT);
    }

    // -------------------------------------------------------------------------
    // Unsaved text against the stored documents
    // -------------------------------------------------------------------------

    @Test
    void unsavedTextIsNotFoundToConflictWithItsOwnSavedCopy() {
        var stored =
                documents.createShapesDocument(GRAPH, "own.ttl", null, VALID_SHAPES, Lang.TURTLE);

        var report = service.validateTurtle(GRAPH, "own.ttl", VALID_SHAPES, stored.getId());

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void unsavedTextThatCollidesWithAnotherDocumentIsReported() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        var edited =
                documents.createShapesDocument(GRAPH, "draft.ttl", null, "", Lang.TURTLE).getId();

        var report = service.validateTurtle(GRAPH, "draft.ttl", VALID_SHAPES, edited);

        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("DUPLICATE_SHAPE_IRI");
                            assertThat(finding.getMessage()).contains("custom");
                        });
        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(result -> assertThat(result.getDocumentId()).isEqualTo(edited));
    }

    @Test
    void unsavedTextWithoutADocumentIdIsCheckedOnItsOwn() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);

        var report = service.validateTurtle(GRAPH, "draft.ttl", VALID_SHAPES, null);

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void anIdNoStoredDocumentHasIsComparedAgainstEverythingStored() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);

        var report = service.validateTurtle(GRAPH, "new.ttl", VALID_SHAPES, UUID.randomUUID());

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .contains("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void askingForOneDocumentStillReportsItsConflictsWithTheOthers() {
        documents.replaceShapesDocumentText(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, VALID_SHAPES);
        var second =
                documents.createShapesDocument(GRAPH, "copy.ttl", null, VALID_SHAPES, Lang.TURTLE);

        var report = service.validateShapes(GRAPH, second.getId());

        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(result -> assertThat(result.getDocumentId()).isEqualTo(second.getId()));
        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .contains("DUPLICATE_SHAPE_IRI");
    }

    private static String datatypeShapes(String datatype, String shapeName) {
        return """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                %s
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:datatype <%s> ;
                    ] .
                """
                .formatted(shapeName, datatype);
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
