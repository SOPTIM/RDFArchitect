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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.GraphWithContextTransactional;
import org.rdfarchitect.rdf.TestRDFUtils;

import java.nio.charset.StandardCharsets;

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

    private GraphWithContextTransactional context;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        context = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        var databasePort = mock(DatabasePort.class);
        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(context);
        service = new SHACLStoringService(databasePort);
    }

    /** Adds a document holding one shape, committed as a named change. */
    private ShapesDocument givenDocument(String name, String shapeTriple, boolean enabled) {
        context.begin(ReadWrite.WRITE);
        try {
            var document = context.createShapesDocument(name, ShapesDocument.Origin.IMPORTED);
            document.setEnabled(enabled);
            document.getGraph().add(TestRDFUtils.triple(shapeTriple));
            context.commit("import " + name);
            return document;
        } finally {
            context.end();
        }
    }

    private String exportedCustomShapes() {
        return service.exportCustomSHACLGraph(GRAPH_IDENTIFIER, RDFFormat.TURTLE)
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

        draft.setEnabled(true);

        assertThat(exportedCustomShapes()).contains("DraftShape");
    }

    @Test
    void graphWithoutAnyImportedDocumentExportsNoShapes() {
        // Only the default document exists, and it is empty.
        assertThat(exportedCustomShapes()).doesNotContain("targetClass");
    }
}
