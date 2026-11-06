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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * How several shapes documents combine when the service reads a graph's constraints.
 *
 * <p>SHACL is conjunctive and has no precedence, so every enabled document contributes and none
 * overrides another. A disabled document contributes nothing — that is what switching one off
 * means.
 */
class SHACLStoringServiceDocumentsTest {

    private static final GraphIdentifier GRAPH_IDENTIFIER =
            new GraphIdentifier("cgmes", "http://example.org/EQ");

    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        DatabaseFixtures.createGraph(
                databasePort, GRAPH_IDENTIFIER, GraphFactory.createDefaultGraph());
        databasePort.enableEditing(GRAPH_IDENTIFIER.datasetName());
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    /** Adds a document holding one shape, committed as a named change. */
    private ShapesDocument givenDocument(String name, String shapeTriple, boolean enabled) {
        try (var transaction =
                databasePort.beginTransaction(GRAPH_IDENTIFIER.datasetName(), ReadWrite.WRITE)) {
            var document =
                    transaction.createShapesDocument(
                            name,
                            ShapesDocument.Origin.IMPORTED,
                            List.of(GRAPH_IDENTIFIER.graphUri()));
            document.setEnabled(enabled);
            document.getGraph().add(TestRDFUtils.triple(shapeTriple));
            transaction.commit("import " + name);
            return document;
        }
    }

    /** Every enabled document, as the combined export reads them. */
    private String exportedCustomShapes() {
        return service.exportCombinedSHACLGraph(GRAPH_IDENTIFIER, RDFFormat.TURTLE)
                .toString(StandardCharsets.UTF_8);
    }

    @Test
    void shapesFromEveryEnabledDocumentAreIncluded() {
        givenDocument("eq.ttl", "EqShape targetClass ACLineSegment", true);
        givenDocument("tp.ttl", "TpShape targetClass Terminal", true);

        var exported = exportedCustomShapes();

        assertThat(exported).contains("EqShape").contains("TpShape");
    }

    @Test
    void neitherDocumentOverridesTheOtherOnTheSameTargetClass() {
        // The two shapes constrain the same class. SHACL applies both; a precedence rule here would
        // disagree with the file the user exports.
        givenDocument("cardinality.ttl", "ShapeA targetClass ACLineSegment", true);
        givenDocument("datatypes.ttl", "ShapeB targetClass ACLineSegment", true);

        var exported = exportedCustomShapes();

        assertThat(exported).contains("ShapeA").contains("ShapeB");
    }

    @Test
    void disabledDocumentContributesNothing() {
        givenDocument("eq.ttl", "EqShape targetClass ACLineSegment", true);
        givenDocument("draft.ttl", "DraftShape targetClass Terminal", false);

        var exported = exportedCustomShapes();

        assertThat(exported).contains("EqShape").doesNotContain("DraftShape");
    }

    @Test
    void reEnablingADocumentBringsItsShapesBack() {
        var draft = givenDocument("draft.ttl", "DraftShape targetClass Terminal", false);

        assertThat(exportedCustomShapes()).doesNotContain("DraftShape");

        service.updateShapesDocument(
                GRAPH_IDENTIFIER.datasetName(), draft.getId(), null, true, null, null);

        assertThat(exportedCustomShapes()).contains("DraftShape");
    }

    @Test
    void graphWithoutAnyImportedDocumentExportsNoShapes() {
        // A graph starts with no documents.
        assertThat(exportedCustomShapes()).doesNotContain("targetClass");
    }

    @Test
    void readOnlyWorkspace_refusesEveryWriteToItsDocuments() {
        var document = givenDocument("official.ttl", "<urn:s> <urn:p> <urn:o>", true);
        var id = document.getId();
        databasePort.disableEditing(GRAPH_IDENTIFIER.datasetName());

        assertThatThrownBy(
                        () ->
                                service.createShapesDocument(
                                        GRAPH_IDENTIFIER.datasetName(),
                                        List.of(GRAPH_IDENTIFIER.graphUri()),
                                        "new.ttl",
                                        null,
                                        "",
                                        Lang.TURTLE))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(
                        () ->
                                service.replaceShapesDocumentText(
                                        GRAPH_IDENTIFIER.datasetName(), id, ""))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(
                        () ->
                                service.updateShapesDocument(
                                        GRAPH_IDENTIFIER.datasetName(), id, "x", false, 0, null))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.deleteShapesDocument(GRAPH_IDENTIFIER.datasetName(), id))
                .isInstanceOf(ResourceConflictException.class);

        assertThat(service.listShapesDocuments(GRAPH_IDENTIFIER.datasetName()))
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.getName()).isEqualTo("official.ttl");
                            assertThat(info.isEnabled()).isTrue();
                            assertThat(info.getTripleCount()).isEqualTo(1);
                        });
    }
}
