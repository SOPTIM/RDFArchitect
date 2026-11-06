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

package org.rdfarchitect.database.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** A graph's shapes documents taking part in the workspace's transactions and history. */
class WorkspaceShapesDocumentTest {

    private static final String GRAPH = "http://example.org/graph";
    private static final String OTHER_GRAPH = "http://example.org/other";

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace("workspace");
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.createGraph(GRAPH, GraphFactory.createDefaultGraph());
            transaction.createGraph(OTHER_GRAPH, GraphFactory.createDefaultGraph());
            transaction.commit("Created the graphs");
        }
    }

    private static Triple triple(String name) {
        var node = NodeFactory.createURI("http://example.org/" + name);
        return Triple.create(node, node, node);
    }

    private void write(String graphUri, Consumer<GraphContext> change, String message) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            change.accept(transaction.graph(graphUri));
            transaction.commit(message);
        }
    }

    /** A change to the documents, which belong to no graph. */
    private void change(Consumer<WorkspaceTransaction> change, String message) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            change.accept(transaction);
            transaction.commit(message);
        }
    }

    private <T> T read(Function<GraphContext, T> reader) {
        return read(GRAPH, reader);
    }

    private <T> T read(String graphUri, Function<GraphContext, T> reader) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return reader.apply(transaction.graph(graphUri));
        }
    }

    private <T> T readDocuments(Function<WorkspaceTransaction, T> reader) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return reader.apply(transaction);
        }
    }

    private Map<UUID, ShapesDocument> documents() {
        return readDocuments(WorkspaceTransaction::shapesDocuments);
    }

    private UUID documentsVersion() {
        return readDocuments(WorkspaceTransaction::shapesDocumentsVersion);
    }

    private UUID schemaVersion() {
        return read(GraphContext::getRdfGraphVersion);
    }

    private int order(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getOrder());
    }

    private boolean enabled(UUID id) {
        return readDocuments(transaction -> document(transaction, id).isEnabled());
    }

    private String name(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getName());
    }

    private String sourceFile(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getSourceFileName());
    }

    private Set<String> schemasOf(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getSchemas());
    }

    private boolean schemaHas(String graphUri, Triple triple) {
        return read(graphUri, context -> context.getRdfGraph().contains(triple));
    }

    private List<Integer> orders() {
        return documents().values().stream().map(ShapesDocument::getOrder).toList();
    }

    private UUID create(String name) {
        return create(name, GRAPH);
    }

    private UUID create(String name, String... schemas) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var document =
                    transaction.createShapesDocument(
                            name, ShapesDocument.Origin.AUTHORED, List.of(schemas));
            transaction.commit("Created " + name);
            return document.getId();
        }
    }

    private ShapesDocument document(WorkspaceTransaction transaction, UUID id) {
        return transaction.shapesDocuments().get(id);
    }

    private void edit(UUID id, String text, Triple... triples) {
        change(
                transaction -> {
                    var document = document(transaction, id);
                    document.getGraph().clear();
                    for (var triple : triples) {
                        document.getGraph().add(triple);
                    }
                    document.setRawText(text);
                },
                "Edited");
    }

    private String textOf(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getRawText());
    }

    private List<Triple> triplesOf(UUID id) {
        return readDocuments(transaction -> document(transaction, id).getGraph().find().toList());
    }

    private List<String> namesInOrder() {
        return documents().values().stream().map(ShapesDocument::getName).toList();
    }

    private WorkspaceChangeLogEntry newestChange() {
        return workspace.getChangeHistory().getFirst();
    }

    private WorkspaceChangeLogEntry changeNamed(String message) {
        return workspace.getChangeHistory().stream()
                .filter(entry -> entry.message().equals(message))
                .findFirst()
                .orElseThrow();
    }

    // -------------------------------------------------------------------------
    // The documents themselves
    // -------------------------------------------------------------------------

    @Test
    void aWorkspaceStartsWithoutDocuments() {
        assertThat(documents()).isEmpty();
    }

    @Test
    void documentsHoldTheirShapesIndependently() {
        var first = create("first.ttl");
        var second = create("second.ttl");

        edit(first, "first", triple("a"));

        assertThat(triplesOf(first)).containsExactly(triple("a"));
        assertThat(triplesOf(second)).isEmpty();
        assertThat(textOf(second)).isNull();
    }

    @Test
    void aDocumentCanApplyToOneSchemaSeveralOrNone() {
        var one = create("one.ttl", GRAPH);
        var two = create("two.ttl", GRAPH, OTHER_GRAPH);
        var none = create("none.ttl", new String[0]);

        assertThat(schemasOf(one)).containsExactly(GRAPH);
        assertThat(schemasOf(two)).containsExactlyInAnyOrder(GRAPH, OTHER_GRAPH);
        assertThat(schemasOf(none)).isEmpty();
    }

    @Test
    void theSchemasADocumentAppliesToAreUndoneLikeTheRest() {
        var id = create("document.ttl", GRAPH);

        change(
                transaction -> document(transaction, id).setSchemas(List.of(OTHER_GRAPH)),
                "Moved it");
        assertThat(schemasOf(id)).containsExactly(OTHER_GRAPH);

        workspace.undo();
        assertThat(schemasOf(id)).containsExactly(GRAPH);
    }

    @Test
    void documentsKeepTheOrderTheyWereAdded() {
        create("b.ttl");
        create("a.ttl");
        create("c.ttl");

        assertThat(namesInOrder()).containsExactly("b.ttl", "a.ttl", "c.ttl");
        assertThat(orders()).containsExactly(0, 1, 2);
    }

    @Test
    void aDocumentCreatedAfterADeletionDoesNotShareAPosition() {
        var first = create("first.ttl");
        create("second.ttl");
        change(transaction -> transaction.removeShapesDocument(first), "Removed");

        var third = create("third.ttl");

        assertThat(order(third)).isEqualTo(2);
    }

    @Test
    void removingAnUnknownDocumentChangesNothing() {
        var before = workspace.getChangeHistory().size();

        change(
                transaction -> transaction.removeShapesDocument(UUID.randomUUID()),
                "Removed nothing");

        assertThat(workspace.getChangeHistory()).hasSize(before);
    }

    @Test
    void aDocumentCannotBeChangedInAReadTransaction() {
        var id = create("document.ttl");

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            assertThatThrownBy(() -> document(transaction, id).setName("renamed.ttl"))
                    .isInstanceOf(GraphTransactionException.class);
            assertThatThrownBy(
                            () ->
                                    transaction.createShapesDocument(
                                            "new.ttl", ShapesDocument.Origin.AUTHORED, List.of()))
                    .isInstanceOf(GraphTransactionException.class);
            assertThatThrownBy(() -> transaction.removeShapesDocument(id))
                    .isInstanceOf(GraphTransactionException.class);
        }
        assertThat(namesInOrder()).containsExactly("document.ttl");
    }

    // -------------------------------------------------------------------------
    // Beside the custom SHACL graph and the schema
    // -------------------------------------------------------------------------

    @Test
    void aWorkspaceWithoutDocumentsRecordsNothingAboutThem() {
        write(GRAPH, context -> context.getRdfGraph().add(triple("schema")), "Edited the schema");

        var change = newestChange();
        assertThat(change.affectedKinds()).containsExactly("rdf");
        assertThat(change.deltas()).hasSize(1);
        assertThat(change.removedOnUndo()).isEmpty();
    }

    @Test
    void anEditOfADocumentLeavesTheSchemaOutOfTheChange() {
        var id = create("document.ttl");

        edit(id, "text", triple("a"));

        assertThat(newestChange().affectedKinds()).containsExactly("shacl");
    }

    // -------------------------------------------------------------------------
    // Undo and redo
    // -------------------------------------------------------------------------

    @Test
    void anEditIsUndoneAndRedoneWithItsText() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        edit(id, "second", triple("b"));

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("first");
        assertThat(triplesOf(id)).containsExactly(triple("a"));

        workspace.redo();

        assertThat(textOf(id)).isEqualTo("second");
        assertThat(triplesOf(id)).containsExactly(triple("b"));
    }

    @Test
    void undoingCreationRemovesTheDocumentAndRedoBringsItBack() {
        var id = create("document.ttl");

        workspace.undo();
        assertThat(documents()).isEmpty();

        workspace.redo();
        assertThat(documents()).containsOnlyKeys(id);
    }

    @Test
    void undoingACreationThatWroteContentTakesItAllBack() {
        UUID id;
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var document =
                    transaction.createShapesDocument(
                            "new.ttl", ShapesDocument.Origin.IMPORTED, List.of(GRAPH));
            document.getGraph().add(triple("a"));
            document.setRawText("text");
            document.setSourceFileName("new.ttl");
            id = document.getId();
            transaction.commit("Imported");
        }

        workspace.undo();
        assertThat(documents()).isEmpty();

        workspace.redo();
        assertThat(textOf(id)).isEqualTo("text");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
        assertThat(sourceFile(id)).isEqualTo("new.ttl");
    }

    @Test
    void aDocumentCreatedLateCanBeUndoneThroughEarlierHistory() {
        write(GRAPH, context -> context.getRdfGraph().add(triple("schema")), "Edited the schema");
        var id = create("document.ttl");
        edit(id, "text", triple("a"));

        workspace.undo();
        workspace.undo();
        workspace.undo();

        assertThat(documents()).isEmpty();
        assertThat(schemaHas(GRAPH, triple("schema"))).isFalse();
    }

    @Test
    void schemaAndShapesChangedInOneCommitAreUndoneTogether() {
        var id = create("document.ttl");
        change(
                transaction -> {
                    transaction.graph(GRAPH).getRdfGraph().add(triple("schema"));
                    document(transaction, id).getGraph().add(triple("shape"));
                },
                "Edited both");

        workspace.undo();

        assertThat(schemaHas(GRAPH, triple("schema"))).isFalse();
        assertThat(triplesOf(id)).isEmpty();
    }

    @Test
    void aMetadataChangeIsItsOwnUndoStep() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));

        change(transaction -> document(transaction, id).setName("renamed.ttl"), "Renamed");
        change(transaction -> document(transaction, id).setEnabled(false), "Disabled");
        change(transaction -> document(transaction, id).setOrder(4), "Moved");

        workspace.undo();
        assertThat(order(id)).isZero();
        assertThat(enabled(id)).isFalse();

        workspace.undo();
        assertThat(enabled(id)).isTrue();
        assertThat(name(id)).isEqualTo("renamed.ttl");

        workspace.undo();
        assertThat(name(id)).isEqualTo("document.ttl");
        assertThat(textOf(id)).isEqualTo("text");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void aRemovalIsUndoneWithEverythingTheDocumentHeld() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        change(transaction -> document(transaction, id).setEnabled(false), "Disabled");

        change(transaction -> transaction.removeShapesDocument(id), "Removed");
        assertThat(documents()).isEmpty();

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
        assertThat(enabled(id)).isFalse();

        workspace.redo();
        assertThat(documents()).isEmpty();
    }

    @Test
    void anAbortKeepsARemovedDocumentAndDiscardsACreatedOne() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var before = workspace.getChangeHistory().size();

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.removeShapesDocument(id);
            transaction.createShapesDocument("new.ttl", ShapesDocument.Origin.AUTHORED, List.of());
            transaction.abort();
        }

        assertThat(namesInOrder()).containsExactly("document.ttl");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
        assertThat(workspace.getChangeHistory()).hasSize(before);
    }

    @Test
    void anAbortedEditLeavesTextAndMetadataAlone() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var document = document(transaction, id);
            document.setRawText("changed");
            document.setName("changed.ttl");
            document.getGraph().add(triple("b"));
            transaction.abort();
        }

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(name(id)).isEqualTo("document.ttl");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void writingWhatADocumentAlreadyHoldsRecordsNothing() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var before = workspace.getChangeHistory().size();

        change(
                transaction -> {
                    var document = document(transaction, id);
                    document.setRawText("text");
                    document.setName("document.ttl");
                    document.setEnabled(true);
                },
                "Saved again");

        assertThat(workspace.getChangeHistory()).hasSize(before);
    }

    // -------------------------------------------------------------------------
    // The changelog
    // -------------------------------------------------------------------------

    @Test
    void theEntryOfARemovalListsWhatTheDocumentHeld() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"), triple("b"));

        change(transaction -> transaction.removeShapesDocument(id), "Removed");

        var deletions =
                newestChange().deltas().stream()
                        .map(ContextDelta::deletions)
                        .filter(Objects::nonNull)
                        .map(reference -> (Graph) reference.get())
                        .flatMap(graph -> graph.find().toList().stream())
                        .toList();
        assertThat(deletions).containsExactlyInAnyOrder(triple("a"), triple("b"));
    }

    @Test
    void anEntryIsFiledUnderEverySchemaTheDocumentAppliesTo() {
        var id = create("both.ttl", GRAPH, OTHER_GRAPH);

        assertThat(newestChange().affectedGraphUris())
                .containsExactlyInAnyOrder(GRAPH, OTHER_GRAPH);
        assertThat(newestChange().affectedKinds()).containsExactly("shacl");

        edit(id, "text", triple("a"));

        assertThat(newestChange().affectedGraphUris())
                .containsExactlyInAnyOrder(GRAPH, OTHER_GRAPH);
    }

    @Test
    void aDocumentChangeShowsInASchemasChangelogButIsNotRestorableThere() {
        create("document.ttl");

        assertThat(newestChange().affectsGraph(GRAPH)).isTrue();
        assertThat(newestChange().restorableGraphUris()).isEmpty();
    }

    @Test
    void anEntryOfADocumentApplyingToNoSchemaIsFiledUnderNone() {
        create("none.ttl", new String[0]);

        assertThat(newestChange().affectedGraphUris()).isEmpty();
    }

    @Test
    void anEntryIsFiledUnderTheSchemaADocumentLeftAsWellAsTheOneItJoined() {
        var id = create("document.ttl", GRAPH);

        change(
                transaction -> document(transaction, id).setSchemas(List.of(OTHER_GRAPH)),
                "Moved it");

        assertThat(newestChange().affectedGraphUris())
                .containsExactlyInAnyOrder(GRAPH, OTHER_GRAPH);
    }

    @Test
    void undoingACreationSaysWhatItWouldRemove() {
        create("document.ttl");

        assertThat(workspace.undoPreview().removedOnUndo()).containsExactly("document.ttl");
    }

    @Test
    void aMetadataChangeIsReportedValueByValueUnderTheDocumentsName() {
        var id = create("document.ttl");

        change(
                transaction -> {
                    document(transaction, id).setName("renamed.ttl");
                    document(transaction, id).setEnabled(false);
                },
                "Changed");

        assertThat(valuesOf(newestChange()))
                .contains(
                        new ValueChange("renamed.ttl: name", "document.ttl", "renamed.ttl"),
                        new ValueChange("renamed.ttl: enabled", "true", "false"));
    }

    @Test
    void anEditThatOnlyChangesTheTextStillSaysSo() {
        var id = create("document.ttl");
        edit(id, "# one", triple("a"));

        edit(id, "# two", triple("a"));

        assertThat(valuesOf(newestChange()))
                .containsExactly(new ValueChange("document.ttl: text", "5 bytes", "5 bytes"));
    }

    private static List<ValueChange> valuesOf(WorkspaceChangeLogEntry entry) {
        return entry.deltas().stream().flatMap(delta -> delta.values().stream()).toList();
    }

    // -------------------------------------------------------------------------
    // Restoring
    // -------------------------------------------------------------------------

    @Test
    void restoringOneSchemaLeavesDocumentsAlone() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        write(GRAPH, context -> context.getRdfGraph().add(triple("old")), "Edited the schema");
        var target = newestChange().changeId();
        write(GRAPH, context -> context.getRdfGraph().add(triple("new")), "Edited the schema");
        edit(id, "second", triple("b"));

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(schemaHas(GRAPH, triple("new"))).isFalse();
        assertThat(textOf(id)).isEqualTo("second");
        assertThat(triplesOf(id)).containsExactly(triple("b"));
    }

    @Test
    void restoringOneSchemaDoesNotBringBackADeletedDocument() {
        var id = create("document.ttl");
        write(GRAPH, context -> context.getRdfGraph().add(triple("old")), "Edited the schema");
        var target = newestChange().changeId();
        write(GRAPH, context -> context.getRdfGraph().add(triple("new")), "Edited the schema");
        change(transaction -> transaction.removeShapesDocument(id), "Removed");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(documents()).isEmpty();
    }

    @Test
    void restoringOneSchemaKeepsADocumentCreatedSince() {
        write(GRAPH, context -> context.getRdfGraph().add(triple("old")), "Edited the schema");
        var target = newestChange().changeId();
        write(GRAPH, context -> context.getRdfGraph().add(triple("new")), "Edited the schema");
        var id = create("document.ttl");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(documents()).containsOnlyKeys(id);
    }

    @Test
    void restoringOneSchemaOverNothingButDocumentEditsChangesNothing() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        var target = newestChange().changeId();
        edit(id, "second", triple("b"));

        var step = workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(step.change()).isNull();
        assertThat(textOf(id)).isEqualTo("second");
    }

    @Test
    void restoringTheWholeWorkspaceStepsDocumentsBackToo() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        var target = newestChange().changeId();
        edit(id, "second", triple("b"));

        workspace.restoreToVersion(target, RevertScope.everything());

        assertThat(textOf(id)).isEqualTo("first");
        assertThat(newestChange().undone()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Versions
    // -------------------------------------------------------------------------

    @Test
    void theDocumentsVersionFollowsTheDocumentsAndNotTheSchema() {
        var id = create("document.ttl");
        var afterCreate = documentsVersion();

        write(GRAPH, context -> context.getRdfGraph().add(triple("schema")), "Edited the schema");
        assertThat(documentsVersion()).isEqualTo(afterCreate);

        edit(id, "text", triple("a"));
        var afterEdit = documentsVersion();
        assertThat(afterEdit).isNotEqualTo(afterCreate);

        change(transaction -> document(transaction, id).setEnabled(false), "Disabled");
        assertThat(documentsVersion()).isNotEqualTo(afterEdit);

        workspace.undo();
        assertThat(documentsVersion()).isEqualTo(afterEdit);
    }

    @Test
    void theDocumentsVersionNoticesAnEditThatOnlyChangesTheText() {
        var id = create("document.ttl");
        edit(id, "# one", triple("a"));
        var before = documentsVersion();

        change(transaction -> document(transaction, id).setRawText("# two"), "Edited a comment");

        assertThat(documentsVersion()).isNotEqualTo(before);
    }

    @Test
    void theSchemaVersionFollowsTheSchemaAndNotTheDocuments() {
        var id = create("document.ttl");
        var before = schemaVersion();

        edit(id, "text", triple("a"));
        assertThat(schemaVersion()).isEqualTo(before);

        write(GRAPH, context -> context.getRdfGraph().add(triple("schema")), "Edited the schema");
        var edited = schemaVersion();
        assertThat(edited).isNotEqualTo(before);

        workspace.undo();
        assertThat(schemaVersion()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // What becomes of a schema
    // -------------------------------------------------------------------------

    @Test
    void renamingASchemaPointsItsDocumentsAtTheNewUri() {
        var id = create("both.ttl", GRAPH, OTHER_GRAPH);
        var elsewhere = create("elsewhere.ttl", OTHER_GRAPH);
        var renamed = "http://example.org/renamed";

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.renameGraph(GRAPH, renamed);
            transaction.commit("Renamed the schema");
        }

        assertThat(schemasOf(id)).containsExactlyInAnyOrder(renamed, OTHER_GRAPH);
        assertThat(schemasOf(elsewhere)).containsExactly(OTHER_GRAPH);

        workspace.undo();

        assertThat(schemasOf(id)).containsExactlyInAnyOrder(GRAPH, OTHER_GRAPH);
    }

    @Test
    void deletingASchemaLeavesItsDocumentsAsTheyAre() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var version = documentsVersion();

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(GRAPH);
            transaction.commit("Deleted the schema");
        }

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(schemasOf(id)).containsExactly(GRAPH);
        assertThat(documentsVersion()).isEqualTo(version);
    }

    @Test
    void replacingASchemaKeepsItsDocumentsWithNothingAskedOfThem() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var replacement = GraphFactory.createDefaultGraph();
        replacement.add(triple("new-schema"));

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(GRAPH);
            transaction.createGraph(GRAPH, replacement);
            transaction.commit("Replaced the schema");
        }

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(schemasOf(id)).containsExactly(GRAPH);
        assertThat(schemaHas(GRAPH, triple("new-schema"))).isTrue();
        assertThat(newestChange().affectedKinds()).doesNotContain("shacl");

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(schemaHas(GRAPH, triple("new-schema"))).isFalse();
    }
}
