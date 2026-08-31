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
 * Coverage is not disagreement, and a graph's constraints are its enabled documents together.
 *
 * <p>Both were wrong at once, and the CGMES 3.0 DiagramLayout profile is what showed it: importing
 * one of its constraints files reported "0 of 49 property constraints agree". The profile implies
 * 49 property constraints; the file in question is 55 lines long and carries a single cross-profile
 * rule. It disagreed with nothing — it simply talked about almost nothing, and the report scored
 * silence as disagreement while ignoring whatever the graph's other documents said.
 */
class ConformanceAcrossDocumentsTest {

    private static final String BASE =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/";
    private static final String VOCABULARY = "RDFS/61970-600-2_DiagramLayout-AP-Voc-RDFS2020.rdf";

    /** The 49 property constraints the DiagramLayout profile implies, stated in full. */
    private static final String SIMPLE =
            "SHACL/TTL/61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.ttl";

    /** 55 lines: one inverse-association rule and nothing else. */
    private static final String ONE_RULE =
            "SHACL/TTL/61970-600-2_DiagramLayout-AP-Con-Complex-InverseAssociation-SHACL.ttl";

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH =
            new GraphIdentifier(DATASET, "http://ex.org/DiagramLayout");

    /** How many property constraints the profile implies. Asserted, not assumed, in the tests. */
    private static final int IMPLIED = 49;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SHACLStoringService documents;
    private ConformanceService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);

        var schema = GraphFactory.createDefaultGraph();
        RDFParser.source(Path.of(BASE + VOCABULARY).toUri().toString()).parse(schema);
        databasePort.createGraph(GRAPH, schema);

        documents = new SHACLStoringService(databasePort);
        service = new ConformanceService(databasePort);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    // -------------------------------------------------------------------------
    // What the user reported
    // -------------------------------------------------------------------------

    @Test
    void aFileThatCarriesOneRuleIsNotADisagreementWithFortyNineConstraints() throws IOException {
        var documentId = add("one-rule.ttl", official(ONE_RULE));

        var report = service.compare(GRAPH, documentId);

        // Nothing to compare, so nothing can disagree. The old report said "0 of 49 agree".
        assertThat(report.getImpliedBySchema()).isEqualTo(IMPLIED);
        assertThat(report.getCompared()).isZero();
        assertThat(report.getContradictedCount()).isZero();
        assertThat(report.getDifferentCount()).isZero();
        assertThat(report.getMissingInDocumentCount()).isEqualTo(IMPLIED);
    }

    @Test
    void theProfilesOwnConstraintsFileAgreesWithItCompletely() throws IOException {
        var documentId = add("simple.ttl", official(SIMPLE));

        var report = service.compare(GRAPH, documentId);

        assertThat(report.getCompared()).isEqualTo(IMPLIED);
        assertThat(report.getAgreeing()).isEqualTo(IMPLIED);
        assertThat(report.isConforms()).isTrue();
    }

    @Test
    void theOneRuleFileStopsLookingLikeAGapOnceItsNeighbourIsThere() throws IOException {
        add("simple.ttl", official(SIMPLE));
        var documentId = add("one-rule.ttl", official(ONE_RULE));

        var report = service.compare(GRAPH, documentId);

        // The question is about the graph's constraints, and the neighbouring document states them.
        assertThat(report.getCompared()).isEqualTo(IMPLIED);
        assertThat(report.getMissingInDocumentCount()).isZero();
        assertThat(report.isConforms()).isTrue();
        assertThat(report.getDocuments()).contains("simple.ttl", "one-rule.ttl");
    }

    // -------------------------------------------------------------------------
    // Which documents take part
    // -------------------------------------------------------------------------

    @Test
    void aDisabledDocumentDoesNotCount() throws IOException {
        var simple = add("simple.ttl", official(SIMPLE));
        var documentId = add("one-rule.ttl", official(ONE_RULE));
        documents.updateShapesDocument(GRAPH, simple, null, false, null);

        var report = service.compare(GRAPH, documentId);

        assertThat(report.getCompared()).isZero();
        assertThat(report.getDocuments()).doesNotContain("simple.ttl");
    }

    @Test
    void theOpenDocumentIsReadEvenWhenItIsDisabled() throws IOException {
        var simple = add("simple.ttl", official(SIMPLE));
        documents.updateShapesDocument(GRAPH, simple, null, false, null);

        var report = service.compare(GRAPH, simple);

        // Otherwise opening a disabled document and asking the question answers about everything
        // except the document you are looking at.
        assertThat(report.getDocuments()).contains("simple.ttl");
        assertThat(report.getCompared()).isEqualTo(IMPLIED);
    }

    // -------------------------------------------------------------------------
    // Provenance
    // -------------------------------------------------------------------------

    @Test
    void aFindingNamesTheDocumentThatStatesIt() {
        var documentId =
                add(
                        "wrong-datatype.ttl",
                        """
                        @prefix sh:  <http://www.w3.org/ns/shacl#> .
                        @prefix cim: <http://iec.ch/TC57/CIM100#> .
                        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                        @prefix ex:  <http://example.org/> .

                        ex:Diagram a sh:NodeShape ;
                            sh:targetClass cim:Diagram ;
                            sh:property [
                                sh:path cim:Diagram.x1InitialView ;
                                sh:datatype xsd:integer ;
                            ] .
                        """);

        var report = service.compare(GRAPH, documentId);

        // A merged right-hand side would otherwise leave the user no way to find the file to fix.
        assertThat(report.getFindings())
                .filteredOn(finding -> finding.getKind() == ConformanceFinding.Kind.CONTRADICTED)
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getPath()).endsWith("Diagram.x1InitialView");
                            assertThat(finding.getStatedIn()).containsExactly("wrong-datatype.ttl");
                        });
    }

    @Test
    void aConstraintNoDocumentStatesNamesNoDocument() throws IOException {
        var documentId = add("one-rule.ttl", official(ONE_RULE));

        var report = service.compare(GRAPH, documentId);

        assertThat(report.getFindings())
                .allSatisfy(
                        finding -> {
                            assertThat(finding.getKind())
                                    .isEqualTo(ConformanceFinding.Kind.MISSING_IN_DOCUMENT);
                            assertThat(finding.getStatedIn()).isEmpty();
                        });
    }

    // -------------------------------------------------------------------------

    private UUID add(String name, String turtle) {
        return documents.createShapesDocument(GRAPH, name, null, turtle, Lang.TURTLE).getId();
    }

    private static String official(String file) throws IOException {
        return Files.readString(Path.of(BASE + file));
    }
}
