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
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContextTransactional;
import org.rdfarchitect.models.changelog.ChangeLogEntry;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

import java.util.List;
import java.util.UUID;

/** Undo and redo of what the service does to a graph's shapes documents. */
class SHACLDocumentHistoryTest {

    private static final GraphIdentifier GRAPH = new GraphIdentifier("cgmes", "http://ex.org/EQ");

    private static final String FIRST =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            # the first version, with a comment only the text carries
            ex:TerminalShape a sh:NodeShape ; sh:targetClass ex:Terminal .
            """;

    private static final String SECOND =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://ex.org/EQ#> .

            ex:SwitchShape a sh:NodeShape ; sh:targetClass ex:Switch .
            """;

    private GraphWithContextTransactional context;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        context = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        var databasePort = mock(DatabasePort.class);
        when(databasePort.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(context);
        service = new SHACLStoringService(databasePort);
    }

    private UUID create(String name, String turtle) {
        return service.createShapesDocument(GRAPH, name, name, turtle, Lang.TURTLE).getId();
    }

    private ShapesDocumentInfo info(UUID id) {
        return service.listShapesDocuments(GRAPH).stream()
                .filter(document -> document.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private List<UUID> listedIds() {
        return service.listShapesDocuments(GRAPH).stream().map(ShapesDocumentInfo::getId).toList();
    }

    @Test
    void theChangelogEntryOfADeleteListsWhatTheDocumentHeld() {
        var id = create("terminal.ttl", FIRST);

        service.deleteShapesDocument(GRAPH, id);

        var deleted =
                history().getFirst().getContextDeltas().stream()
                        .filter(delta -> delta.contextName().equals("shacl:terminal.ttl"))
                        .findFirst()
                        .orElseThrow()
                        .deletions()
                        .get();
        assertThat(deleted).isNotNull();
        assertThat(deleted.size()).isEqualTo(2);
    }

    private List<ChangeLogEntry> history() {
        try (var ctx = context.begin(ReadWrite.READ)) {
            return ctx.getChangeLog().getUndoHistory();
        }
    }

    @Test
    void undoingAnEditBringsBackTheTextThatWasSaved() {
        var id = create("eq.ttl", FIRST);
        service.replaceShapesDocumentText(GRAPH, id, SECOND);

        context.undo();

        assertThat(service.getShapesDocumentText(GRAPH, id)).isEqualTo(FIRST);

        context.redo();

        assertThat(service.getShapesDocumentText(GRAPH, id)).isEqualTo(SECOND);
    }

    @Test
    void restoringAnEarlierVersionBringsBackItsText() {
        var id = create("eq.ttl", FIRST);
        var afterCreation = history().getFirst().getChangeId();
        service.replaceShapesDocumentText(GRAPH, id, SECOND);
        service.replaceShapesDocumentText(GRAPH, id, SECOND + "\n# and a comment\n");

        context.restoreToVersion(afterCreation);

        assertThat(service.getShapesDocumentText(GRAPH, id)).isEqualTo(FIRST);
    }

    @Test
    void undoingARenameRestoresTheName() {
        var id = create("eq.ttl", FIRST);
        service.updateShapesDocument(GRAPH, id, "renamed.ttl", null, null);

        context.undo();

        assertThat(info(id).getName()).isEqualTo("eq.ttl");
    }

    @Test
    void undoingADisableSwitchesTheDocumentBackOn() {
        var id = create("eq.ttl", FIRST);
        service.updateShapesDocument(GRAPH, id, null, false, null);

        context.undo();

        assertThat(info(id).isEnabled()).isTrue();
    }

    @Test
    void undoingAMoveRestoresEveryPosition() {
        var first = create("first.ttl", FIRST);
        var second = create("second.ttl", SECOND);
        service.updateShapesDocument(GRAPH, second, null, null, 0);

        context.undo();

        assertThat(info(first).getOrder()).isLessThan(info(second).getOrder());
    }

    @Test
    void undoingADeleteBringsTheDocumentBack() {
        var id = create("eq.ttl", FIRST);
        service.deleteShapesDocument(GRAPH, id);
        assertThat(listedIds()).doesNotContain(id);

        context.undo();

        assertThat(listedIds()).contains(id);
        assertThat(service.getShapesDocumentText(GRAPH, id)).isEqualTo(FIRST);
    }

    @Test
    void undoingACreationRemovesTheDocument() {
        var id = create("eq.ttl", FIRST);

        context.undo();

        assertThat(listedIds()).doesNotContain(id);
    }

    @Test
    void savingTheStoredTextAgainRecordsNothing() {
        var id = create("eq.ttl", FIRST);
        var entries = history().size();

        service.replaceShapesDocumentText(GRAPH, id, FIRST);

        assertThat(history()).hasSize(entries);
    }

    @Test
    void anUpdateThatChangesNothingRecordsNothing() {
        var id = create("eq.ttl", FIRST);
        var order = info(id).getOrder();
        var entries = history().size();

        service.updateShapesDocument(GRAPH, id, "eq.ttl", true, order);

        assertThat(history()).hasSize(entries);
    }
}
