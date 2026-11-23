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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Validating a workspace's shapes documents against the schemas it holds.
 *
 * <p>The schema here is a small RDFS vocabulary rather than a real profile, so that these tests are
 * about the service's behaviour — positions, per-document reporting, contradictions — and not about
 * CIM. Validation against real ENTSO-E profiles is covered by {@link
 * ShapesValidationAgainstEntsoeProfilesTest}.
 */
class ShapesValidationServiceTest {

    private static final String WORKSPACE = "cgmes";
    private static final GraphIdentifier SCHEMA_ID = new GraphIdentifier(WORKSPACE, "EQ");

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
    private UUID custom;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(WORKSPACE);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        DatabaseFixtures.createGraph(databasePort, SCHEMA_ID, schema);
        documents = new SHACLStoringService(databasePort);
        service = new ShapesValidationService(databasePort, new SchemaIndexCache(databasePort));
        custom =
                documents
                        .createShapesDocument(
                                SCHEMA_ID.workspaceName(),
                                List.of(SCHEMA_ID.schemaName()),
                                "custom.ttl",
                                null,
                                "",
                                Lang.TURTLE)
                        .getId();
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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrorCount()).isZero();
        assertThat(report.getProfiles()).contains("http://ex.org/EQ/1.0");
        assertThat(report.getDocuments())
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getDocumentId()).isEqualTo(custom);
                            assertThat(result.isValid()).isTrue();
                        });
    }

    @Test
    void anUnknownClassIsReportedAtItsLineAndColumn() {
        var shapes = VALID_SHAPES.replace("cim:ACLineSegment ;", "cim:ACLineSegmentt ;");
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, shapes);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, shapes);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

        assertThat(findings(report.getDocuments()))
                .anySatisfy(
                        finding -> {
                            assertThat(finding.getCode()).isEqualTo("UNKNOWN_PROPERTY");
                            assertThat(finding.getLine())
                                    .isEqualTo(lineOf(shapes, "ACLineSegment.lenght"));
                        });
    }

    @Test
    void anUnknownTermThatIsOnlyAPermittedValueIsAWarning() {
        // Reference data and older-namespace types are listed in sh:in on purpose; a value a
        // constraint permits is not a reference into the schema.
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:ACLineSegmentShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:in ( cim:ReferenceValue ) ;
                    ] .
                """;

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "values.ttl", shapes, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> CIM_REFERENCE_VALUE.equals(finding.getTerm()))
                .isNotEmpty()
                .allSatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.WARNING));
    }

    /** A value-type list naming each type under two CIM versions, as official NC files do. */
    private static String typeListWith(String entries) {
        return """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix cim16: <http://iec.ch/TC57/2013/CIM-schema-cim16#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:TerminalShape
                    a sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property [
                        sh:path ( cim:Terminal.sequenceNumber rdf:type ) ;
                        sh:in ( %s ) ;
                    ] .
                """
                .formatted(entries);
    }

    @Test
    void aTypeOfAnotherCimVersionIsInformationNotAnError() {
        var report =
                service.validateTurtle(
                        SCHEMA_ID.workspaceName(),
                        "nc.ttl",
                        typeListWith("cim:ACLineSegment cim16:ACLineSegment"),
                        null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> finding.getTerm() != null)
                .filteredOn(finding -> finding.getTerm().contains("cim16"))
                .isNotEmpty()
                .allSatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.INFO));
    }

    @Test
    void aMisspeltTypeInTheSchemasOwnNamespaceStaysAnError() {
        var report =
                service.validateTurtle(
                        SCHEMA_ID.workspaceName(),
                        "typo.ttl",
                        typeListWith("cim:ACLineSegmnet"),
                        null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> finding.getTerm() != null)
                .filteredOn(finding -> finding.getTerm().endsWith("ACLineSegmnet"))
                .anySatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.ERROR));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://iec.ch/TC57/CIM10#",
                "http://iec.ch/TC57/CIM100",
                "http://iec.ch/TC57/CIM100/",
                "http://iec.ch/TC57/cim100#"
            })
    void aNamespaceCloseToOneOfTheSchemasKeepsItsErrors(String misspelt) {
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix typo: <%s> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:LineShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.length ] .

                ex:TerminalShape
                    a sh:NodeShape ;
                    sh:targetClass typo:Terminal .
                """
                        .formatted(misspelt);

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "typo.ttl", shapes, null);

        assertThat(report.isValid()).isFalse();
        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> finding.getTerm() != null)
                .filteredOn(finding -> finding.getTerm().startsWith(misspelt))
                .isNotEmpty()
                .allSatisfy(
                        finding -> {
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.ERROR);
                            assertThat(finding.getMessage()).contains("http://iec.ch/TC57/CIM100#");
                        });
    }

    @Test
    void theNamespaceADocumentIsWrittenInKeepsItsErrors() {
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://example.org/my-cim#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:LineShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.length ] .

                ex:TerminalShape
                    a sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property [ sh:path cim:Terminal.sequenceNumber ] .
                """;

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "other.ttl", shapes, null);

        assertThat(report.isValid()).isFalse();
        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> finding.getTerm() != null)
                .filteredOn(finding -> finding.getTerm().startsWith("http://example.org/my-cim#"))
                .hasSizeGreaterThanOrEqualTo(4)
                .allSatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.ERROR));
    }

    @Test
    void anUnknownTermUsedElsewhereTooStaysAnError() {
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:Shape
                    a sh:NodeShape ;
                    sh:targetClass cim:ReferenceValue ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:in ( cim:ReferenceValue ) ;
                    ] .
                """;

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "values.ttl", shapes, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> CIM_REFERENCE_VALUE.equals(finding.getTerm()))
                .anySatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.ERROR));
    }

    @Test
    void aQueryFindingWithoutAPositionIsPlacedAtTheQuery() {
        var shapes =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:QueryShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:sparql [
                        sh:select '''
                            SELECT $this WHERE {
                                $this cim:ACLineSegment.length ?length .
                                BIND (1 AS ?length)
                            }''' ;
                    ] .
                """;

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "query.ttl", shapes, null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> finding.getSource() == ShapesValidationFinding.Source.SPARQL)
                .isNotEmpty()
                .allSatisfy(
                        finding -> {
                            assertThat(finding.getLine()).isNotNull();
                            assertThat(finding.getLine())
                                    .isBetween(lineOf(shapes, "sh:select"), lineOf(shapes, "BIND"));
                        });
    }

    @Test
    void aSchemaWithoutAVersionIriIsNamedByItsName() {
        var draft = new GraphIdentifier(WORKSPACE, "draft");
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(
                        "<http://ex.org/Draft> a <http://www.w3.org/2000/01/rdf-schema#Class> .",
                        Lang.TURTLE)
                .parse(schema);
        DatabaseFixtures.createGraph(databasePort, draft, schema);
        var shapes = VALID_SHAPES.replace("cim:ACLineSegment ;", "cim:Nonsense ;");

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "draft.ttl", shapes, null);

        assertThat(report.getProfiles()).contains("draft");
        assertThat(report.getProfiles()).noneMatch(profile -> profile.startsWith("urn:rdfa:"));
        assertThat(findings(report.getDocuments()))
                .isNotEmpty()
                .allSatisfy(
                        finding -> assertThat(finding.getMessage()).doesNotContain("urn:rdfa:"));
    }

    private static final String CIM_REFERENCE_VALUE = "http://iec.ch/TC57/CIM100#ReferenceValue";

    // -------------------------------------------------------------------------
    // Which documents are validated
    // -------------------------------------------------------------------------

    @Test
    void disabledDocumentsAreLeftOutButCanStillBeAskedForById() {
        var broken = VALID_SHAPES.replace("cim:ACLineSegment ;", "cim:Nonsense ;");
        var info =
                documents.createShapesDocument(
                        SCHEMA_ID.workspaceName(),
                        List.of(SCHEMA_ID.schemaName()),
                        "draft.ttl",
                        null,
                        broken,
                        Lang.TURTLE);
        documents.updateShapesDocument(
                SCHEMA_ID.workspaceName(), info.getId(), null, false, null, null);

        assertThat(service.validateShapes(SCHEMA_ID.workspaceName(), null).getDocuments())
                .extracting(ShapesDocumentValidationResult::getDocumentId)
                .doesNotContain(info.getId());

        var targeted = service.validateShapes(SCHEMA_ID.workspaceName(), info.getId());
        assertThat(targeted.getDocuments()).hasSize(1);
        assertThat(targeted.isValid()).isFalse();
    }

    @Test
    void anUnknownDocumentIdIsNotFound() {
        var missing = UUID.randomUUID();
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.validateShapes(SCHEMA_ID.workspaceName(), missing));
    }

    @Test
    void eachDocumentIsReportedSeparately() {
        var broken =
                VALID_SHAPES
                        .replace("cim:ACLineSegment ;", "cim:Nonsense ;")
                        .replace("ex:ACLineSegmentShape", "ex:OtherShape");
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        var second =
                documents.createShapesDocument(
                        SCHEMA_ID.workspaceName(),
                        List.of(SCHEMA_ID.schemaName()),
                        "extra.ttl",
                        null,
                        broken,
                        Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

        assertThat(report.getDocuments()).hasSize(2);
        assertThat(report.getDocuments())
                .filteredOn(result -> result.getDocumentId().equals(second.getId()))
                .singleElement()
                .satisfies(result -> assertThat(result.getErrorCount()).isPositive());
        assertThat(report.getDocuments())
                .filteredOn(result -> result.getDocumentId().equals(custom))
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

        var report = service.validateTurtle(SCHEMA_ID.workspaceName(), "draft.ttl", broken, null);

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
        var report =
                service.validateTurtle(SCHEMA_ID.workspaceName(), "draft.ttl", VALID_SHAPES, null);

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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        var second =
                documents.createShapesDocument(
                        SCHEMA_ID.workspaceName(),
                        List.of(SCHEMA_ID.schemaName()),
                        "copy.ttl",
                        null,
                        VALID_SHAPES,
                        Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
        // Both definitions apply, which is legal SHACL and how official releases share shapes;
        // worth knowing, not an error.
        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "DUPLICATE_SHAPE_IRI".equals(finding.getCode()))
                .allSatisfy(
                        finding ->
                                assertThat(finding.getSeverity())
                                        .isEqualTo(ShapesValidationFinding.Severity.WARNING));
        assertThat(report.isValid()).isTrue();
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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "tighter.ttl",
                null,
                tighter,
                Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
    void aDeactivatedNodeShapeTakesNoPartInAConflict() {
        var deactivated =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/other#> .

                ex:ACLineSegmentMaxShape
                    a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:deactivated true ;
                    sh:property [
                        sh:path cim:ACLineSegment.length ;
                        sh:maxCount 0 ;
                    ] .
                """;
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "off.ttl",
                null,
                deactivated,
                Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

        assertThat(findings(report.getDocuments()))
                .noneSatisfy(
                        finding ->
                                assertThat(finding.getCode())
                                        .isEqualTo("UNSATISFIABLE_CARDINALITY"));
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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "tighter.ttl",
                null,
                tighter,
                Lang.TURTLE);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "vague.ttl",
                null,
                malformed,
                Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, selfContradictory);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

        assertThat(findings(report.getDocuments()))
                .filteredOn(finding -> "UNSATISFIABLE_CARDINALITY".equals(finding.getCode()))
                .hasSize(1);
    }

    @Test
    void twoDatatypesOnOnePathAreReported() {
        var stringShape = datatypeShapes("http://www.w3.org/2001/XMLSchema#string", "ex:First");
        var intShape = datatypeShapes("http://www.w3.org/2001/XMLSchema#integer", "ex:Second");
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, stringShape);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "second.ttl",
                null,
                intShape,
                Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        documents.createShapesDocument(
                SCHEMA_ID.workspaceName(),
                List.of(SCHEMA_ID.schemaName()),
                "datatype.ttl",
                null,
                datatypeOnly,
                Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), null);

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
                documents.createShapesDocument(
                        SCHEMA_ID.workspaceName(),
                        List.of(SCHEMA_ID.schemaName()),
                        "own.ttl",
                        null,
                        VALID_SHAPES,
                        Lang.TURTLE);

        var report =
                service.validateTurtle(
                        SCHEMA_ID.workspaceName(), "own.ttl", VALID_SHAPES, stored.getId());

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void unsavedTextThatCollidesWithAnotherDocumentIsReported() {
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        var edited =
                documents
                        .createShapesDocument(
                                SCHEMA_ID.workspaceName(),
                                List.of(SCHEMA_ID.schemaName()),
                                "draft.ttl",
                                null,
                                "",
                                Lang.TURTLE)
                        .getId();

        var report =
                service.validateTurtle(
                        SCHEMA_ID.workspaceName(), "draft.ttl", VALID_SHAPES, edited);

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
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);

        var report =
                service.validateTurtle(SCHEMA_ID.workspaceName(), "draft.ttl", VALID_SHAPES, null);

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void anIdNoStoredDocumentHasMeetsNoOtherDocument() {
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);

        var report =
                service.validateTurtle(
                        SCHEMA_ID.workspaceName(), "new.ttl", VALID_SHAPES, UUID.randomUUID());

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    // -------------------------------------------------------------------------
    // Which documents meet
    // -------------------------------------------------------------------------

    private static final GraphIdentifier OTHER_SCHEMA = new GraphIdentifier(WORKSPACE, "other");

    private UUID documentAppliedTo(String name, String turtle, GraphIdentifier... schemas) {
        return documents
                .createShapesDocument(
                        WORKSPACE,
                        Arrays.stream(schemas).map(GraphIdentifier::schemaName).toList(),
                        name,
                        null,
                        turtle,
                        Lang.TURTLE)
                .getId();
    }

    private List<String> codesOf(UUID documentId) {
        return findings(service.validateShapes(WORKSPACE, documentId).getDocuments()).stream()
                .map(ShapesValidationFinding::getCode)
                .toList();
    }

    @Test
    void documentsApplyingToDifferentSchemasDoNotMeet() {
        DatabaseFixtures.createGraph(databasePort, OTHER_SCHEMA, GraphFactory.createDefaultGraph());
        documents.replaceShapesDocumentText(WORKSPACE, custom, VALID_SHAPES);

        var other = documentAppliedTo("other.ttl", VALID_SHAPES, OTHER_SCHEMA);

        assertThat(codesOf(other)).doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void aDocumentApplyingToNoSchemaMeetsNoOther() {
        documents.replaceShapesDocumentText(WORKSPACE, custom, VALID_SHAPES);

        var alone = documentAppliedTo("alone.ttl", VALID_SHAPES);

        assertThat(codesOf(alone)).doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void oneSharedSchemaIsEnoughForDocumentsToMeet() {
        DatabaseFixtures.createGraph(databasePort, OTHER_SCHEMA, GraphFactory.createDefaultGraph());
        documents.replaceShapesDocumentText(WORKSPACE, custom, VALID_SHAPES);

        var both = documentAppliedTo("both.ttl", VALID_SHAPES, SCHEMA_ID, OTHER_SCHEMA);

        assertThat(codesOf(both)).contains("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void aPairSharingTwoSchemasReportsItsConflictOnce() {
        DatabaseFixtures.createGraph(databasePort, OTHER_SCHEMA, GraphFactory.createDefaultGraph());
        documentAppliedTo("first.ttl", VALID_SHAPES, SCHEMA_ID, OTHER_SCHEMA);

        var second = documentAppliedTo("second.ttl", VALID_SHAPES, SCHEMA_ID, OTHER_SCHEMA);

        assertThat(codesOf(second)).filteredOn("DUPLICATE_SHAPE_IRI"::equals).hasSize(1);
    }

    @Test
    void aDocumentApplyingToNoSchemaCanStillContradictItself() {
        var contradiction =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://ex.org/shapes#> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.length ; sh:minCount 2 ] ;
                    sh:property [ sh:path cim:ACLineSegment.length ; sh:maxCount 1 ] .
                """;

        var alone = documentAppliedTo("alone.ttl", contradiction);

        assertThat(codesOf(alone)).contains("UNSATISFIABLE_CARDINALITY");
    }

    @Test
    void unsavedTextOnlyMeetsDocumentsApplyingToItsSchemas() {
        DatabaseFixtures.createGraph(databasePort, OTHER_SCHEMA, GraphFactory.createDefaultGraph());
        documents.replaceShapesDocumentText(WORKSPACE, custom, VALID_SHAPES);
        var other = documentAppliedTo("other.ttl", "", OTHER_SCHEMA);

        var report = service.validateTurtle(WORKSPACE, "other.ttl", VALID_SHAPES, other);

        assertThat(findings(report.getDocuments()))
                .extracting(ShapesValidationFinding::getCode)
                .doesNotContain("DUPLICATE_SHAPE_IRI");
    }

    @Test
    void askingForOneDocumentStillReportsItsConflictsWithTheOthers() {
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, VALID_SHAPES);
        var second =
                documents.createShapesDocument(
                        SCHEMA_ID.workspaceName(),
                        List.of(SCHEMA_ID.schemaName()),
                        "copy.ttl",
                        null,
                        VALID_SHAPES,
                        Lang.TURTLE);

        var report = service.validateShapes(SCHEMA_ID.workspaceName(), second.getId());

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
