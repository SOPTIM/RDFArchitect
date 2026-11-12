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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;
import org.rdfarchitect.shacl.dto.ShapesDocumentInfo;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
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

    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(getClass().getSimpleName());
        databasePort.createWorkspaceIfAbsent(GRAPH.datasetName());
        try (var transaction =
                databasePort.beginTransaction(GRAPH.datasetName(), ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH.graphUri(), GraphFactory.createDefaultGraph());
            transaction.commit("Created the graph");
        }
        databasePort.enableEditing(GRAPH.datasetName());
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private UUID create(String name, String turtle) {
        return service.createShapesDocument(
                        GRAPH.datasetName(),
                        List.of(GRAPH.graphUri()),
                        name,
                        name,
                        turtle,
                        Lang.TURTLE)
                .getId();
    }

    private ShapesDocumentInfo info(UUID id) {
        return service.listShapesDocuments(GRAPH.datasetName()).stream()
                .filter(document -> document.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private List<UUID> listedIds() {
        return service.listShapesDocuments(GRAPH.datasetName()).stream()
                .map(ShapesDocumentInfo::getId)
                .toList();
    }

    @Test
    void theChangelogEntryOfADeleteListsWhatTheDocumentHeld() {
        var id = create("terminal.ttl", FIRST);

        service.deleteShapesDocument(GRAPH.datasetName(), id);

        var deleted =
                history().getFirst().deltas().stream()
                        .map(ContextDelta::deletions)
                        .filter(Objects::nonNull)
                        .map(WeakReference::get)
                        .mapToLong(Graph::size)
                        .sum();
        assertThat(deleted).isEqualTo(2);
    }

    private List<WorkspaceChangeLogEntry> history() {
        return databasePort.listChanges(GRAPH.datasetName());
    }

    @Test
    void undoingAnEditBringsBackTheTextThatWasSaved() {
        var id = create("eq.ttl", FIRST);
        service.replaceShapesDocumentText(GRAPH.datasetName(), id, SECOND);

        databasePort.undo(GRAPH.datasetName());

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), id)).isEqualTo(FIRST);

        databasePort.redo(GRAPH.datasetName());

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), id)).isEqualTo(SECOND);
    }

    @Test
    void restoringAnEarlierVersionBringsBackItsText() {
        var id = create("eq.ttl", FIRST);
        var afterCreation = history().getFirst().changeId();
        service.replaceShapesDocumentText(GRAPH.datasetName(), id, SECOND);
        service.replaceShapesDocumentText(GRAPH.datasetName(), id, SECOND + "\n# and a comment\n");

        databasePort.restoreToVersion(
                GRAPH.datasetName(), afterCreation, RevertScope.everything(), null);

        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), id)).isEqualTo(FIRST);
    }

    @Test
    void undoingARenameRestoresTheName() {
        var id = create("eq.ttl", FIRST);
        service.updateShapesDocument(GRAPH.datasetName(), id, "renamed.ttl", null, null, null);

        databasePort.undo(GRAPH.datasetName());

        assertThat(info(id).getName()).isEqualTo("eq.ttl");
    }

    @Test
    void undoingADisableSwitchesTheDocumentBackOn() {
        var id = create("eq.ttl", FIRST);
        service.updateShapesDocument(GRAPH.datasetName(), id, null, false, null, null);

        databasePort.undo(GRAPH.datasetName());

        assertThat(info(id).isEnabled()).isTrue();
    }

    @Test
    void undoingAMoveRestoresEveryPosition() {
        var first = create("first.ttl", FIRST);
        var second = create("second.ttl", SECOND);
        service.updateShapesDocument(GRAPH.datasetName(), second, null, null, 0, null);

        databasePort.undo(GRAPH.datasetName());

        assertThat(info(first).getOrder()).isLessThan(info(second).getOrder());
    }

    @Test
    void undoingADeleteBringsTheDocumentBack() {
        var id = create("eq.ttl", FIRST);
        service.deleteShapesDocument(GRAPH.datasetName(), id);
        assertThat(listedIds()).doesNotContain(id);

        databasePort.undo(GRAPH.datasetName());

        assertThat(listedIds()).contains(id);
        assertThat(service.getShapesDocumentText(GRAPH.datasetName(), id)).isEqualTo(FIRST);
    }

    @Test
    void undoingACreationRemovesTheDocument() {
        var id = create("eq.ttl", FIRST);

        databasePort.undo(GRAPH.datasetName());

        assertThat(listedIds()).doesNotContain(id);
    }

    @Test
    void savingTheStoredTextAgainRecordsNothing() {
        var id = create("eq.ttl", FIRST);
        var entries = history().size();

        service.replaceShapesDocumentText(GRAPH.datasetName(), id, FIRST);

        assertThat(history()).hasSize(entries);
    }

    @Test
    void anUpdateThatChangesNothingRecordsNothing() {
        var id = create("eq.ttl", FIRST);
        var order = info(id).getOrder();
        var entries = history().size();

        service.updateShapesDocument(GRAPH.datasetName(), id, "eq.ttl", true, order, null);

        assertThat(history()).hasSize(entries);
    }
}
