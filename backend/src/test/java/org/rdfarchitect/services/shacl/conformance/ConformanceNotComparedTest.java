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
import org.rdfarchitect.shacl.dto.ConformanceNotCompared;
import org.rdfarchitect.shacl.dto.ConformanceReport;

import java.util.List;
import java.util.UUID;

/** A report says which rules it did not compare, so that silence is not read as agreement. */
class ConformanceNotComparedTest {

    private static final GraphIdentifier SCHEMA = new GraphIdentifier("ws", "Base");

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);
    private final SHACLStoringService documents = new SHACLStoringService(databasePort);
    private final ConformanceService service = new ConformanceService(databasePort);

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(SCHEMA.workspaceName());
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(ConformanceInheritanceTest.VOCABULARY, Lang.TURTLE).parse(graph);
        DatabaseFixtures.createGraph(databasePort, SCHEMA, graph);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void aDocumentWrittenForAnotherVersionOfTheVocabularySaysSo() {
        var report =
                compareWith(
                        """
                        ex:S a sh:NodeShape ;
                            sh:targetClass <http://ex.org/cim16#Sub>, <http://ex.org/cim16#Sub2> ;
                            sh:property [ sh:path <http://ex.org/cim16#Base.name> ;
                                          sh:maxCount 0 ] .
                        """);

        assertThat(report.getStated()).isZero();
        assertThat(report.getNotCompared())
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.getDocument()).isEqualTo("doc.ttl");
                            assertThat(entry.getReason())
                                    .isEqualTo(ConformanceNotCompared.Reason.OTHER_VOCABULARY);
                            assertThat(entry.getCount()).isEqualTo(2);
                            assertThat(entry.getTerms()).containsExactly("http://ex.org/cim16#");
                            assertThat(entry.getMessage())
                                    .startsWith("2 property constraints are about classes");
                        });
    }

    @Test
    void shapesTheComparisonCannotFollowAreCounted() {
        var report =
                compareWith(
                        """
                        ex:BySubject a sh:NodeShape ; sh:targetSubjectsOf cim:Base.name ;
                            sh:property [ sh:path cim:Base.name ; sh:maxCount 1 ] .
                        ex:Either a sh:NodeShape ; sh:targetClass cim:Sub ;
                            sh:xone ( [ sh:path cim:Base.name ; sh:minCount 1 ]
                                      [ sh:path cim:Other.code ; sh:minCount 1 ] ) .
                        ex:Off a sh:NodeShape ; sh:targetNode cim:Sub ; sh:deactivated true .
                        """);

        assertThat(report.getNotCompared())
                .extracting(
                        ConformanceNotCompared::getReason,
                        ConformanceNotCompared::getCount,
                        ConformanceNotCompared::getMessage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ConformanceNotCompared.Reason.OTHER_TARGET,
                                1,
                                "1 shape chooses what it applies to with sh:targetSubjectsOf"
                                        + " rather than by class."),
                        org.assertj.core.groups.Tuple.tuple(
                                ConformanceNotCompared.Reason.COMBINATION,
                                1,
                                "1 shape combines rules with sh:xone, which a comparison per"
                                        + " class and property cannot follow."));
    }

    @Test
    void aDocumentTheComparisonReadsWhollyIsNotListed() {
        var report =
                compareWith(
                        "ex:S a sh:NodeShape ; sh:targetClass cim:Sub ;"
                                + " sh:property [ sh:path cim:Base.name ; sh:minCount 1 ] .");

        assertThat(report.getNotCompared()).isEmpty();
    }

    private ConformanceReport compareWith(String shapes) {
        documents.createShapesDocument(
                SCHEMA.workspaceName(),
                List.of(SCHEMA.schemaName()),
                "doc.ttl",
                null,
                ConformanceInheritanceTest.PREFIXES + shapes,
                Lang.TURTLE);
        return service.compare(SCHEMA);
    }
}
