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
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.shacl.effective.ClassHierarchy;
import org.rdfarchitect.services.shacl.effective.EffectiveConstraints;
import org.rdfarchitect.shacl.dto.ConformanceFinding;

import java.util.List;

/**
 * Telling "stricter than the schema" apart from "contradicts the schema".
 *
 * <p>That distinction is the whole value of the feature. A profile that narrows what the schema
 * allows is doing its job; one that asserts something the schema rules out means the two have
 * drifted, and somebody has to decide which is right.
 */
class ConformanceComparatorTest {

    private static final String CIM = "http://iec.ch/TC57/CIM100#";
    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

    private static final PrefixMapping PREFIXES =
            new PrefixMappingImpl()
                    .setNsPrefix("sh", "http://www.w3.org/ns/shacl#")
                    .setNsPrefix("cim", CIM)
                    .setNsPrefix("xsd", XSD);

    /** A shapes graph for one property of one class, written the way both sides write them. */
    private static String shapes(String clauses) {
        return """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.r ; %s ] .
                """
                .formatted(clauses);
    }

    private static List<ConformanceFinding> compare(String schema, String document) {
        return ConformanceComparator.compare(
                constraints(schema),
                EffectiveConstraints.of(java.util.Map.of("document.ttl", graphOf(document))),
                PREFIXES);
    }

    private static java.util.Map<EffectiveConstraints.Key, EffectiveConstraints.Constraint>
            constraints(String turtle) {
        return EffectiveConstraints.of(graphOf(turtle));
    }

    private static org.apache.jena.graph.Graph graphOf(String turtle) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(turtle, Lang.TURTLE).parse(graph);
        return graph;
    }

    // -------------------------------------------------------------------------
    // Agreement
    // -------------------------------------------------------------------------

    @Test
    void identicalConstraintsAreNotReported() {
        assertThat(
                        compare(
                                shapes("sh:minCount 1 ; sh:maxCount 1"),
                                shapes("sh:minCount 1 ; sh:maxCount 1")))
                .isEmpty();
    }

    @Test
    void rulesSplitOverSeveralShapesAreMergedBeforeComparing() {
        // Both sides write cardinality and datatype as separate property shapes; comparing them
        // shape by shape would report differences that are not there.
        var split =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:minCount 1 ] ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:datatype xsd:float ] .
                """;

        assertThat(compare(split, shapes("sh:minCount 1 ; sh:datatype xsd:float"))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Contradiction — the case worth acting on
    // -------------------------------------------------------------------------

    @Test
    void twoDifferentDatatypesCannotBothHold() {
        var findings = compare(shapes("sh:datatype xsd:float"), shapes("sh:datatype xsd:string"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
                            assertThat(finding.getMessage())
                                    .isEqualTo("A value cannot be both xsd:float and xsd:string.");
                            assertThat(finding.getSchemaSays()).isEqualTo("xsd:float");
                            assertThat(finding.getDocumentSays()).isEqualTo("xsd:string");
                            assertThat(finding.getTargetClass()).isEqualTo(CIM + "ACLineSegment");
                            assertThat(finding.getPath()).isEqualTo(CIM + "ACLineSegment.r");
                        });
    }

    @Test
    void aRequirementTheOtherSideForbidsIsAContradiction() {
        var findings = compare(shapes("sh:minCount 1"), shapes("sh:maxCount 0"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
                            assertThat(finding.getMessage())
                                    .isEqualTo(
                                            "The schema requires at least 1, the document allows at most 0.");
                        });
    }

    @Test
    void theContradictionIsReportedFromEitherDirection() {
        var findings = compare(shapes("sh:maxCount 0"), shapes("sh:minCount 1"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding ->
                                assertThat(finding.getMessage())
                                        .isEqualTo(
                                                "The document requires at least 1, the schema"
                                                        + " allows at most 0."));
    }

    @Test
    void twoDifferentValueClassesCannotBothHold() {
        var findings = compare(shapes("sh:class cim:Terminal"), shapes("sh:class cim:BaseVoltage"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
                            assertThat(finding.getMessage()).contains("instance of both");
                        });
    }

    // -------------------------------------------------------------------------
    // Difference — both can hold
    // -------------------------------------------------------------------------

    @Test
    void aDocumentStricterThanTheSchemaIsADifferenceNotAContradiction() {
        // Narrowing what the schema allows is what a profile is for.
        var findings = compare(shapes("sh:maxCount 5"), shapes("sh:maxCount 1"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.DIFFERENT);
                            assertThat(finding.getSchemaSays()).isEqualTo("0..5");
                            assertThat(finding.getDocumentSays()).isEqualTo("0..1");
                        });
    }

    @Test
    void aPropertyWhoseRulesDriftedInTwoWaysIsStillOneFinding() {
        // One property that went wrong is one thing to fix, not two rows to read.
        var findings =
                compare(
                        shapes("sh:minCount 1 ; sh:datatype xsd:float"),
                        shapes("sh:minCount 0 ; sh:datatype xsd:string"));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getKind()).isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
    }

    // -------------------------------------------------------------------------
    // Presence
    // -------------------------------------------------------------------------

    @Test
    void aConstraintOnlyTheSchemaStatesIsMissingFromTheDocument() {
        var findings = compare(shapes("sh:minCount 1"), shapes(""));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.MISSING_IN_DOCUMENT);
                            assertThat(finding.getDocumentSays()).isNull();
                            assertThat(finding.getSchemaSays()).isEqualTo("1..n");
                        });
    }

    @Test
    void aConstraintOnlyTheDocumentStatesIsNotInTheSchema() {
        var findings = compare(shapes(""), shapes("sh:minCount 1"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.NOT_IN_SCHEMA);
                            assertThat(finding.getSchemaSays()).isNull();
                        });
    }

    @Test
    void findingsComeWorstFirst() {
        var schema =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:datatype xsd:float ] ;
                    sh:property [ sh:path cim:ACLineSegment.x ; sh:maxCount 5 ] ;
                    sh:property [ sh:path cim:ACLineSegment.b ; sh:minCount 1 ] .
                """;
        var document =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:datatype xsd:string ] ;
                    sh:property [ sh:path cim:ACLineSegment.x ; sh:maxCount 1 ] .
                """;

        assertThat(compare(schema, document))
                .extracting(ConformanceFinding::getKind)
                .containsExactly(
                        ConformanceFinding.Kind.CONTRADICTED,
                        ConformanceFinding.Kind.DIFFERENT,
                        ConformanceFinding.Kind.MISSING_IN_DOCUMENT);
    }

    @Test
    void aPathExpressionIsLeftOutRatherThanMisreported() {
        // RDFArchitect writes inverse cardinality as sh:path [ sh:inversePath ... ]. There is
        // nothing on the other side to line that up with, so it is not compared.
        var inverse =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path [ sh:inversePath cim:Terminal.ConductingEquipment ] ;
                                  sh:maxCount 1 ] .
                """;

        assertThat(compare(inverse, shapes(""))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Value types — a type list against sh:class
    // -------------------------------------------------------------------------

    /**
     * A range with two concrete subclasses, one of which has a concrete subclass of its own. The
     * range itself is abstract, as association ranges in CIM usually are.
     */
    private static final String HIERARCHY =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .

            cim:Equipment a rdfs:Class .
            cim:Switch a rdfs:Class ; rdfs:subClassOf cim:Equipment ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:Breaker a rdfs:Class ; rdfs:subClassOf cim:Switch ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:ACLineSegment a rdfs:Class ; rdfs:subClassOf cim:Equipment ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:BaseVoltage a rdfs:Class ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            """;

    /** A value type as RDFArchitect and the CGMES files write it: a list of permitted rdf:types. */
    private static String typedAs(String classes) {
        return """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property [ sh:path cim:Terminal.ConductingEquipment ; sh:maxCount 1 ] ;
                    sh:property [ sh:path ( cim:Terminal.ConductingEquipment rdf:type ) ;
                                  sh:in ( %s ) ; sh:nodeKind sh:IRI ] .
                """
                .formatted(classes);
    }

    /** The same value type as NC files write it. */
    private static String ofClass(String valueClass) {
        return """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property [ sh:path cim:Terminal.ConductingEquipment ; sh:maxCount 1 ;
                                  sh:class %s ; sh:nodeKind sh:IRI ] .
                """
                .formatted(valueClass);
    }

    private static List<ConformanceFinding> compareInSchema(String schema, String document) {
        return ConformanceComparator.compare(
                constraints(schema),
                EffectiveConstraints.of(java.util.Map.of("document.ttl", graphOf(document))),
                PREFIXES,
                ClassHierarchy.of(graphOf(HIERARCHY)),
                java.util.Set.of());
    }

    @Test
    void aTypeListOfTheRangeAndItsSubclassesAgreesWithShClassOnTheRange() {
        var generated = typedAs("cim:Equipment cim:Switch cim:Breaker cim:ACLineSegment");

        assertThat(compareInSchema(generated, ofClass("cim:Equipment"))).isEmpty();
    }

    @Test
    void anAbstractClassInATypeListAdmitsNothingExtra() {
        // Official lists name abstract classes too; nothing is ever typed as one.
        var official = typedAs("cim:Breaker cim:Switch cim:ACLineSegment cim:Equipment");

        assertThat(compareInSchema(official, ofClass("cim:Equipment"))).isEmpty();
    }

    @Test
    void aNarrowerTypeListIsADifferenceNotAContradiction() {
        var findings = compareInSchema(typedAs("cim:Switch cim:Breaker"), ofClass("cim:Equipment"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.DIFFERENT);
                            assertThat(finding.getSchemaSays()).contains("of class cim:Switch");
                            assertThat(finding.getDocumentSays())
                                    .contains("of class cim:Equipment");
                        });
    }

    @Test
    void aTypeListNamingOnlyUnrelatedClassesContradictsShClass() {
        var findings = compareInSchema(typedAs("cim:BaseVoltage"), ofClass("cim:Switch"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
                            assertThat(finding.getMessage()).contains("instance of both");
                        });
    }

    // -------------------------------------------------------------------------
    // What cannot fail validation
    // -------------------------------------------------------------------------

    @Test
    void aDeactivatedPropertyShapeStatesNothing() {
        var findings =
                compare(
                        shapes("sh:datatype xsd:float"),
                        shapes("sh:datatype xsd:string ; sh:deactivated true"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding ->
                                assertThat(finding.getKind())
                                        .isEqualTo(ConformanceFinding.Kind.MISSING_IN_DOCUMENT));
    }

    @Test
    void aDeactivatedNodeShapeStatesNothing() {
        var document =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:deactivated true ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:datatype xsd:string ] .
                """;

        assertThat(compare(shapes("sh:datatype xsd:float"), document))
                .extracting(ConformanceFinding::getKind)
                .containsExactly(ConformanceFinding.Kind.MISSING_IN_DOCUMENT);
    }

    @Test
    void aConstraintStatedOnlyAsAWarningIsADifferenceNotAContradiction() {
        var findings =
                compare(
                        shapes("sh:datatype xsd:float"),
                        shapes("sh:datatype xsd:string ; sh:severity sh:Warning"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.DIFFERENT);
                            assertThat(finding.getMessage()).contains("below sh:Violation");
                        });
    }

    @Test
    void anExplicitViolationSeverityIsEnforced() {
        var findings =
                compare(
                        shapes("sh:datatype xsd:float"),
                        shapes("sh:datatype xsd:string ; sh:severity sh:Violation"));

        assertThat(findings)
                .extracting(ConformanceFinding::getKind)
                .containsExactly(ConformanceFinding.Kind.CONTRADICTED);
    }

    // -------------------------------------------------------------------------
    // Enumerations
    // -------------------------------------------------------------------------

    @Test
    void identicalValueListsAgreeWhateverTheirOrder() {
        assertThat(
                        compare(
                                shapes("sh:in ( cim:Kind.a cim:Kind.b )"),
                                shapes("sh:in ( cim:Kind.b cim:Kind.a )")))
                .isEmpty();
    }

    @Test
    void aDocumentExcludingASchemaValueIsADifference() {
        var findings =
                compare(shapes("sh:in ( cim:Kind.a cim:Kind.b )"), shapes("sh:in ( cim:Kind.a )"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.DIFFERENT);
                            assertThat(finding.getSchemaSays())
                                    .isEqualTo("one of cim:Kind.a, cim:Kind.b");
                            assertThat(finding.getDocumentSays()).isEqualTo("one of cim:Kind.a");
                        });
    }

    @Test
    void aDocumentValueTheSchemaDoesNotListIsAContradiction() {
        // How a national extension of an enumeration drifts from its schema.
        var findings =
                compare(
                        shapes("sh:in ( cim:Kind.a cim:Kind.b )"),
                        shapes("sh:in ( cim:Kind.a cim:Kind.national )"));

        assertThat(findings)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.CONTRADICTED);
                            assertThat(finding.getMessage())
                                    .isEqualTo(
                                            "The document allows cim:Kind.national, which the"
                                                    + " schema does not.");
                        });
    }

    @Test
    void twoValueListsOnOneSideAreIntersected() {
        var split =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:Shape a sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:in ( cim:Kind.a cim:Kind.b ) ] ;
                    sh:property [ sh:path cim:ACLineSegment.r ; sh:in ( cim:Kind.b cim:Kind.c ) ] .
                """;

        assertThat(compare(shapes("sh:in ( cim:Kind.b )"), split)).isEmpty();
    }
}
