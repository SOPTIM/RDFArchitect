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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContextTransactional;
import org.rdfarchitect.exception.database.InvalidContentException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

/** What the service accepts as the name of a constraints document. */
class SHACLDocumentNamesTest {

    private static final GraphIdentifier GRAPH = new GraphIdentifier("cgmes", "http://ex.org/EQ");

    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        var context = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        var databasePort = mock(DatabasePort.class);
        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(context);
        service = new SHACLStoringService(databasePort);
    }

    private ShapesDocumentInfo create(String name) {
        return service.createShapesDocument(GRAPH, name, null, "", Lang.TURTLE);
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(create("  eq.ttl \t").getName()).isEqualTo("eq.ttl");
    }

    @Test
    void aBlankNameIsAClientError() {
        assertThatExceptionOfType(InvalidContentException.class).isThrownBy(() -> create("   "));
    }

    @Test
    void aNameWithControlCharactersIsAClientError() {
        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(() -> create("eq\n.ttl"));
    }

    @Test
    void namesDifferingOnlyInCaseClash() {
        create("eq.ttl");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> create("EQ.ttl"));
    }

    @Test
    void aDocumentWithoutANameGetsAFreeOne() {
        var first = create(null);
        var second = create(null);

        assertThat(first.getName()).isNotBlank();
        assertThat(second.getName()).isNotBlank().isNotEqualToIgnoringCase(first.getName());
    }

    @Test
    void renamingValidatesTheNameTheSameWay() {
        var id = create("eq.ttl").getId();
        create("tp.ttl");

        assertThatExceptionOfType(InvalidContentException.class)
                .isThrownBy(() -> service.updateShapesDocument(GRAPH, id, " ", null, null));
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.updateShapesDocument(GRAPH, id, "TP.ttl", null, null));
        assertThat(service.updateShapesDocument(GRAPH, id, " renamed.ttl ", null, null).getName())
                .isEqualTo("renamed.ttl");
    }

    @Test
    void renamingToADifferentCaseOfItsOwnNameIsAllowed() {
        var id = create("eq.ttl").getId();

        assertThat(service.updateShapesDocument(GRAPH, id, "EQ.ttl", null, null).getName())
                .isEqualTo("EQ.ttl");
    }
}
