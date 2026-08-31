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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.GraphWithContextTransactional;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

import java.util.UUID;

/** Managing a graph's shapes documents through the service. */
class SHACLDocumentServiceTest {

    private static final GraphIdentifier GRAPH = new GraphIdentifier("cgmes", "http://ex.org/EQ");

    /** Deliberately carries a comment and spacing that a Jena round-trip would destroy. */
    private static final String TURTLE =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # cardinality of a line segment
            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass ex:ACLineSegment .
            """;

    private static final String RDF_XML =
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:sh="http://www.w3.org/ns/shacl#">
              <rdf:Description rdf:about="http://ex.org/EQ#TerminalShape">
                <sh:targetClass rdf:resource="http://ex.org/EQ#Terminal"/>
              </rdf:Description>
            </rdf:RDF>
            """;

    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        var context = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        var databasePort = mock(DatabasePort.class);
        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(context);
        service = new SHACLStoringService(databasePort);
    }

    private ShapesDocumentInfo createTurtleDocument(String name) {
        return service.createShapesDocument(GRAPH, name, name, TURTLE, Lang.TURTLE);
    }

    @Test
    void newGraphOffersOnlyTheDefaultDocument() {
        assertThat(service.listShapesDocuments(GRAPH))
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.getId())
                                    .isEqualTo(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID);
                            assertThat(info.isDefault()).isTrue();
                            assertThat(info.getTripleCount()).isZero();
                        });
    }

    @Test
    void createdDocumentIsListedWithItsMetadata() {
        var created = createTurtleDocument("eq.ttl");

        assertThat(created.getOrigin()).isEqualTo(ShapesDocument.Origin.IMPORTED);
        assertThat(created.getTripleCount()).isPositive();
        assertThat(service.listShapesDocuments(GRAPH))
                .extracting(ShapesDocumentInfo::getName)
                .containsExactly(GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME, "eq.ttl");
    }

    @Test
    void turtleIsKeptExactlyAsGiven() {
        var created = createTurtleDocument("eq.ttl");

        assertThat(service.getShapesDocumentText(GRAPH, created.getId())).isEqualTo(TURTLE);
    }

    @Test
    void otherSyntaxesAreConvertedToTurtleOnImport() {
        // The editor only works in Turtle, so keeping the original RDF/XML would hand it something
        // it cannot show.
        var created = service.createShapesDocument(GRAPH, "tp.rdf", "tp.rdf", RDF_XML, Lang.RDFXML);

        var text = service.getShapesDocumentText(GRAPH, created.getId());

        assertThat(text).doesNotContain("rdf:RDF").contains("TerminalShape");
    }

    @Test
    void contentWrittenWithoutAnAuthoredTextIsStillReadable() {
        var created = service.createShapesDocument(GRAPH, "eq.ttl", null, TURTLE, Lang.TURTLE);

        service.replaceShapesDocumentText(GRAPH, created.getId(), TURTLE);

        assertThat(service.getShapesDocumentText(GRAPH, created.getId())).isEqualTo(TURTLE);
    }

    @Test
    void renameAndDisableArePersisted() {
        var created = createTurtleDocument("eq.ttl");

        var updated =
                service.updateShapesDocument(GRAPH, created.getId(), "renamed.ttl", false, null);

        assertThat(updated.getName()).isEqualTo("renamed.ttl");
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void omittedFieldsAreLeftAlone() {
        var created = createTurtleDocument("eq.ttl");
        service.updateShapesDocument(GRAPH, created.getId(), null, false, null);

        var updated =
                service.updateShapesDocument(GRAPH, created.getId(), "renamed.ttl", null, null);

        assertThat(updated.getName()).isEqualTo("renamed.ttl");
        assertThat(updated.isEnabled())
                .as("enabled was not passed, so it must not change")
                .isFalse();
    }

    @Test
    void reorderingKeepsThePositionsDense() {
        var first = createTurtleDocument("first.ttl");
        createTurtleDocument("second.ttl");

        service.updateShapesDocument(GRAPH, first.getId(), null, null, 2);

        assertThat(service.listShapesDocuments(GRAPH))
                .extracting(ShapesDocumentInfo::getOrder)
                .containsExactly(0, 1, 2);
        assertThat(service.listShapesDocuments(GRAPH))
                .extracting(ShapesDocumentInfo::getName)
                .containsExactly(
                        GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME, "second.ttl", "first.ttl");
    }

    @Test
    void deletedDocumentDisappears() {
        var created = createTurtleDocument("eq.ttl");

        service.deleteShapesDocument(GRAPH, created.getId());

        assertThat(service.listShapesDocuments(GRAPH))
                .extracting(ShapesDocumentInfo::getName)
                .containsExactly(GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME);
    }

    @Test
    void deletingTheDefaultDocumentIsRefusedAsAClientError() {
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.deleteShapesDocument(
                                        GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID));
    }

    @Test
    void duplicateNamesAreRefused() {
        createTurtleDocument("eq.ttl");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> createTurtleDocument("eq.ttl"));
    }

    @Test
    void renamingOntoAnExistingNameIsRefusedButKeepingOwnNameIsFine() {
        createTurtleDocument("eq.ttl");
        var tp = createTurtleDocument("tp.ttl");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.updateShapesDocument(
                                        GRAPH, tp.getId(), "eq.ttl", null, null));

        var unchanged = service.updateShapesDocument(GRAPH, tp.getId(), "tp.ttl", null, null);
        assertThat(unchanged.getName()).isEqualTo("tp.ttl");
    }

    @Test
    void unknownDocumentIsReportedAsNotFound() {
        var missing = UUID.randomUUID();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getShapesDocumentText(GRAPH, missing));
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteShapesDocument(GRAPH, missing));
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.replaceShapesDocumentText(GRAPH, missing, TURTLE));
    }
}
