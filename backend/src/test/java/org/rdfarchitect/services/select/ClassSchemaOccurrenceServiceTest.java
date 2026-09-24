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

package org.rdfarchitect.services.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.StringReader;
import java.util.UUID;

@SpringBootTest
class ClassSchemaOccurrenceServiceTest {

    private static final String PATH = "src/test/java/org/rdfarchitect/services/extension/";
    private static final String DATASET = "occurrence-ds";
    private static final String CHILD_UUID = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd";

    /** A schema that names itself, as an imported CGMES 3.0 profile does. */
    private static final String NAMED_PROFILE =
            """
            @prefix cim:     <http://iec.ch/TC57/CIM100#> .
            @prefix dcat:    <http://www.w3.org/ns/dcat#> .
            @prefix dcterms: <http://purl.org/dc/terms/> .
            @prefix owl:     <http://www.w3.org/2002/07/owl#> .

            <http://example.org/named#Ontology>
                a              owl:Ontology ;
                dcat:keyword   "TST" ;
                owl:versionIRI <http://example.org/TestProfile/3.0> ;
                dcterms:title  "Test Vocabulary"@en .
            """;

    private final GraphIdentifier sourceGraph =
            new GraphIdentifier(DATASET, "http://example.org/source");
    private final GraphIdentifier targetGraph =
            new GraphIdentifier(DATASET, "http://example.org/target");

    private ClassSchemaOccurrenceService service;
    private DatabasePort databasePort;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        var graphs = new QueryDatasetService(databasePort);
        service =
                new ClassSchemaOccurrenceService(
                        databasePort, graphs, new ClassLocatorService(databasePort, graphs));
        createGraph(sourceGraph, "class-extension-source.ttl");
        createGraph(targetGraph, "class-extension-target-with-core.ttl");
    }

    @Test
    void listSchemaOccurrences_reportsTheSchemaThatDefinesTheClass() {
        var occurrences = service.listSchemaOccurrences(DATASET, CHILD_UUID);

        assertThat(occurrences).hasSize(2);
        var source =
                occurrences.stream()
                        .filter(o -> o.graphUri().equals(sourceGraph.graphUri()))
                        .findFirst()
                        .orElseThrow();
        assertThat(source.present()).isTrue();
        assertThat(source.classUUID()).isEqualTo(UUID.fromString(CHILD_UUID));
        assertThat(source.stub().label()).isEqualTo("Child");
        assertThat(source.stub().superClassUri()).isEqualTo("http://example.org#Base");
        assertThat(source.stub().packageUri()).isEqualTo("http://example.org#SourcePackage");
        assertThat(source.stub().packageLabel()).isEqualTo("SourcePackage");
        // the concrete stereotype is dropped, because an extension never copies it
        assertThat(source.stub().stereotypes()).isEmpty();
    }

    @Test
    void listSchemaOccurrences_reportsTheSchemaWithoutTheClass() {
        var occurrences = service.listSchemaOccurrences(DATASET, CHILD_UUID);

        var target =
                occurrences.stream()
                        .filter(o -> o.graphUri().equals(targetGraph.graphUri()))
                        .findFirst()
                        .orElseThrow();
        assertThat(target.present()).isFalse();
        assertThat(target.classUUID()).isNull();
        assertThat(target.stub()).isNull();
    }

    /**
     * A class is offered under the name the navigation tree shows its schema under, so the
     * occurrence has to carry what the profile calls itself and not just its keyword.
     */
    @Test
    void listSchemaOccurrences_namesEachSchemaTheWayTheSchemaPickersDo() {
        createProfileGraph(new GraphIdentifier(DATASET, "http://example.org/named"));

        var occurrences = service.listSchemaOccurrences(DATASET, CHILD_UUID);

        var named =
                occurrences.stream()
                        .filter(o -> o.graphUri().equals("http://example.org/named"))
                        .findFirst()
                        .orElseThrow();
        assertThat(named.label()).isEqualTo("Test Vocabulary");
        assertThat(named.keyword()).isEqualTo("TST");
    }

    /** A graph that is no CIM profile has no name of its own to report. */
    @Test
    void listSchemaOccurrences_leavesTheNameOfAPlainGraphUnset() {
        var occurrences = service.listSchemaOccurrences(DATASET, CHILD_UUID);

        assertThat(occurrences).allSatisfy(occurrence -> assertThat(occurrence.label()).isNull());
    }

    @Test
    void listSchemaOccurrences_whenTheClassIsUnknown_fails() {
        assertThatThrownBy(
                        () -> service.listSchemaOccurrences(DATASET, UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void createProfileGraph(GraphIdentifier graphIdentifier) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.create().source(new StringReader(NAMED_PROFILE)).lang(Lang.TURTLE).parse(graph);
        databasePort.createGraph(graphIdentifier, graph);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.commit("import");
        }
    }

    private void createGraph(GraphIdentifier graphIdentifier, String fileName) {
        var file = readMultipartFileFromFile(PATH, fileName);
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }
}
