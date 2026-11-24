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
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.util.List;
import java.util.UUID;

/**
 * A rule targeting a class applies to every class deriving from it, as {@code sh:targetClass} does.
 *
 * <p>The schema's side only has the classes data can be typed with, so a rule written on an
 * abstract class is compared on the concrete classes deriving from it.
 */
class ConformanceInheritanceTest {

    private static final GraphIdentifier SCHEMA = new GraphIdentifier("ws", "Base");

    /** An abstract class with two concrete subclasses, and one attribute on the abstract class. */
    static final String VOCABULARY =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix cim:  <http://ex.org/cim#> .

            cim:String a rdfs:Class ; rdfs:label "String"@en ; cims:stereotype "Primitive" .
            cim:Base a rdfs:Class ; rdfs:label "Base"@en .
            cim:Sub a rdfs:Class ; rdfs:label "Sub"@en ; rdfs:subClassOf cim:Base ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:Sub2 a rdfs:Class ; rdfs:label "Sub2"@en ; rdfs:subClassOf cim:Base ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:Other a rdfs:Class ; rdfs:label "Other"@en ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#concrete> .
            cim:Base.name a rdf:Property ; rdfs:label "name"@en ; rdfs:domain cim:Base ;
                cims:dataType cim:String ; cims:multiplicity cims:M:1..1 ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#attribute> .
            cim:Other.code a rdf:Property ; rdfs:label "code"@en ; rdfs:domain cim:Other ;
                cims:dataType cim:String ; cims:multiplicity cims:M:0..1 ;
                cims:stereotype <http://iec.ch/TC57/NonStandard/UML#attribute> .
            """;

    static final String PREFIXES =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim: <http://ex.org/cim#> .
            @prefix ex:  <http://ex.org/shapes#> .
            """;

    /** Base.name as the schema implies it. */
    private static final String NAME =
            "sh:path cim:Base.name ; sh:minCount 1 ; sh:maxCount 1 ; sh:datatype xsd:string ;"
                    + " sh:nodeKind sh:Literal";

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);
    private final SHACLStoringService documents = new SHACLStoringService(databasePort);
    private final ConformanceService service = new ConformanceService(databasePort);

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(SCHEMA.workspaceName());
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(VOCABULARY, Lang.TURTLE).parse(graph);
        DatabaseFixtures.createGraph(databasePort, SCHEMA, graph);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void aRuleOnAnAbstractClassIsComparedOnTheClassesDerivingFromIt() {
        var report =
                compareWith(
                        "ex:BaseShape a sh:NodeShape ; sh:targetClass cim:Base ;"
                                + " sh:property [ %s ] .".formatted(NAME));

        assertThat(report.getFindings())
                .noneMatch(finding -> finding.getPath().equals("http://ex.org/cim#Base.name"));
        assertThat(report.getNotInSchemaCount()).isZero();
        assertThat(report.getCompared()).isEqualTo(2);
        assertThat(report.getAgreeing()).isEqualTo(2);
    }

    @Test
    void aRuleOnAClassAndOneOnItsSubclassApplyTogether() {
        var report =
                compareWith(
                        """
                        ex:BaseShape a sh:NodeShape ; sh:targetClass cim:Base ;
                            sh:property [ sh:path cim:Base.name ; sh:minCount 1 ; sh:maxCount 1 ] .
                        ex:SubShape a sh:NodeShape ; sh:targetClass cim:Sub, cim:Sub2 ;
                            sh:property [ sh:path cim:Base.name ; sh:datatype xsd:string ;
                                          sh:nodeKind sh:Literal ] .
                        """);

        assertThat(report.getFindings())
                .noneMatch(finding -> finding.getPath().equals("http://ex.org/cim#Base.name"));
        assertThat(report.getCompared()).isEqualTo(2);
    }

    @Test
    void aDifferenceInheritedFromTheSuperclassIsReportedOnEachSubclass() {
        var report =
                compareWith(
                        "ex:BaseShape a sh:NodeShape ; sh:targetClass cim:Base ;"
                                + " sh:property [ %s ] ."
                                        .formatted(NAME.replace("sh:maxCount 1", "sh:maxCount 2")));

        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getPath().equals("http://ex.org/cim#Base.name"))
                .extracting(ConformanceFinding::getKind, ConformanceFinding::getTargetClass)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                ConformanceFinding.Kind.DIFFERENT, "http://ex.org/cim#Sub"),
                        org.assertj.core.groups.Tuple.tuple(
                                ConformanceFinding.Kind.DIFFERENT, "http://ex.org/cim#Sub2"));
        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getPath().equals("http://ex.org/cim#Base.name"))
                .allSatisfy(
                        finding -> assertThat(finding.getStatedIn()).containsExactly("doc.ttl"));
    }

    @Test
    void aPropertyNoClassBelowTheTargetHasIsReportedOnceOnTheTarget() {
        var report =
                compareWith(
                        "ex:BaseShape a sh:NodeShape ; sh:targetClass cim:Base ;"
                                + " sh:property [ sh:path cim:Other.code ; sh:maxCount 1 ] .");

        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getKind() == ConformanceFinding.Kind.NOT_IN_SCHEMA)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getTargetClass())
                                    .isEqualTo("http://ex.org/cim#Base");
                            assertThat(finding.getMessage()).contains("does not have it");
                        });
        assertThat(report.getNotInSchemaCount()).isEqualTo(1);
    }

    private ConformanceReport compareWith(String shapes) {
        documents.createShapesDocument(
                SCHEMA.workspaceName(),
                List.of(SCHEMA.schemaName()),
                "doc.ttl",
                null,
                PREFIXES + shapes,
                Lang.TURTLE);
        return service.compare(SCHEMA);
    }
}
