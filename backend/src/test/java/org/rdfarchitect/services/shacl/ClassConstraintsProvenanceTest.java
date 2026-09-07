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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
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
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.shacl.dto.PropertyShape;
import org.rdfarchitect.shacl.dto.PropertyShapesWrapper;
import org.rdfarchitect.shacl.dto.SHACLToClassRelations;
import org.rdfarchitect.shacl.dto.ShapeOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * A shape read at class level can be traced back to the file it came from.
 *
 * <p>The class dialog reads the graph's constraints merged from every enabled document, which is
 * the only way to answer "what constrains this class" — official constraints are split across files
 * on purpose. Merging is also what throws away the one thing a reader needs in order to change a
 * rule, so the merge has to put the file back.
 */
class ClassConstraintsProvenanceTest {

    private static final String BASE =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/";
    private static final String VOCABULARY = "RDFS/61970-600-2_DiagramLayout-AP-Voc-RDFS2020.rdf";
    private static final String SIMPLE =
            "SHACL/TTL/61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.ttl";

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH =
            new GraphIdentifier(DATASET, "http://ex.org/DiagramLayout");
    private static final String DIAGRAM = "http://iec.ch/TC57/CIM100#Diagram";

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SHACLStoringService service;
    private UUID diagramUUID;

    @BeforeEach
    void setUp() throws IOException {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);

        var schema = GraphFactory.createDefaultGraph();
        RDFParser.source(Path.of(BASE + VOCABULARY).toUri().toString()).parse(schema);
        databasePort.createGraph(GRAPH, schema);

        service = new SHACLStoringService(databasePort);
        service.createShapesDocument(
                GRAPH,
                "simple.ttl",
                "simple.ttl",
                Files.readString(Path.of(BASE + SIMPLE)),
                Lang.TURTLE);
        diagramUUID = uuidOf(DIAGRAM);
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    @Test
    void everyShapeNamesTheDocumentItWasReadFrom() {
        var shapes = shapesOf(custom());

        assertThat(shapes).isNotEmpty();
        assertThat(shapes)
                .allSatisfy(
                        shape ->
                                assertThat(shape.getOrigins())
                                        .isNotEmpty()
                                        .allSatisfy(
                                                origin ->
                                                        assertThat(origin.getDocumentName())
                                                                .isEqualTo("simple.ttl")));
    }

    @Test
    void theLineIsWhereTheShapeStartsInThatDocument() throws IOException {
        var text = Files.readString(Path.of(BASE + SIMPLE)).lines().toList();

        var shape =
                shapesOf(custom()).stream()
                        .filter(candidate -> candidate.getId().startsWith("http"))
                        .findFirst()
                        .orElseThrow();
        var origin = shape.getOrigins().getFirst();

        // Counted in the document's own text, because that is what the workbench opens.
        assertThat(origin.getLine()).isNotNull().isPositive();
        var localName = shape.getId().substring(shape.getId().lastIndexOf('#') + 1);
        assertThat(text.get(origin.getLine() - 1)).contains(localName);
    }

    @Test
    void aPropertysRuleIsSummarisedInWords() {
        var wrapper =
                custom().getPropertyShapes().stream()
                        .filter(candidate -> "Diagram.x1InitialView".equals(candidate.getLabel()))
                        .findFirst()
                        .orElseThrow();

        // The point of the summary is that the rule can be read without expanding the Turtle.
        assertThat(wrapper.getSummary()).contains("0..1").contains("xsd:float");
    }

    @Test
    void generatedShapesNameNoDocumentBecauseTheyHaveNone() {
        var generated = service.getSHACLToClassRelations(GRAPH, diagramUUID).getGenerated();

        assertThat(shapesOf(generated))
                .isNotEmpty()
                .allSatisfy(shape -> assertThat(shape.getOrigins()).isEmpty());
    }

    @Test
    void aSecondDocumentIsNamedOnItsOwnShapes() {
        service.createShapesDocument(
                GRAPH,
                "mine.ttl",
                null,
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:MyDiagramShape a sh:NodeShape ;
                    sh:targetClass cim:Diagram ;
                    sh:property ex:MyOrientationRule .

                ex:MyOrientationRule a sh:PropertyShape ;
                    sh:path cim:Diagram.orientation ;
                    sh:maxCount 1 .
                """,
                Lang.TURTLE);

        var byDocument =
                shapesOf(custom()).stream()
                        .flatMap(shape -> shape.getOrigins().stream())
                        .map(ShapeOrigin::getDocumentName)
                        .distinct()
                        .toList();

        assertThat(byDocument).containsExactlyInAnyOrder("simple.ttl", "mine.ttl");
    }

    private SHACLToClassRelations custom() {
        return service.getSHACLToClassRelations(GRAPH, diagramUUID).getCustom();
    }

    private static List<PropertyShape> shapesOf(SHACLToClassRelations relations) {
        return relations.getPropertyShapes().stream()
                .map(PropertyShapesWrapper::getPropertyShapes)
                .flatMap(List::stream)
                .toList();
    }

    private UUID uuidOf(String classUri) {
        try (var ctx = databasePort.getGraphWithContext(GRAPH).begin(ReadWrite.READ)) {
            return ctx.getRdfGraph().stream(
                            NodeFactory.createURI(classUri), RDFA.uuid.asNode(), Node.ANY)
                    .map(triple -> UUID.fromString(triple.getObject().getLiteralLexicalForm()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
