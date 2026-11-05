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
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.models.changelog.RevertScope;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private <T> T read(Function<GraphContext, T> reader) {
        return read(GRAPH, reader);
    }

    private <T> T read(String graphUri, Function<GraphContext, T> reader) {
        try (var transaction = workspace.begin(ReadWrite.READ)) {
            return reader.apply(transaction.graph(graphUri));
        }
    }

    private Map<UUID, ShapesDocument> documents() {
        return documents(GRAPH);
    }

    private Map<UUID, ShapesDocument> documents(String graphUri) {
        return read(graphUri, GraphContext::getShapesDocuments);
    }

    private UUID documentsVersion() {
        return read(GraphContext::getShapesDocumentsVersion);
    }

    private UUID schemaVersion() {
        return read(GraphContext::getRdfGraphVersion);
    }

    private int order(UUID id) {
        return read(context -> document(context, id).getOrder());
    }

    private boolean enabled(UUID id) {
        return read(context -> document(context, id).isEnabled());
    }

    private String name(UUID id) {
        return read(context -> document(context, id).getName());
    }

    private String sourceFile(UUID id) {
        return read(context -> document(context, id).getSourceFileName());
    }

    private String textOf(String graphUri, UUID id) {
        return read(graphUri, context -> document(context, id).getRawText());
    }

    private boolean schemaHas(String graphUri, Triple triple) {
        return read(graphUri, context -> context.getRdfGraph().contains(triple));
    }

    private List<Triple> customShacl() {
        return read(context -> context.getCustomSHACL().find().toList());
    }

    private List<Integer> orders() {
        return read(
                context ->
                        context.getShapesDocuments().values().stream()
                                .map(ShapesDocument::getOrder)
                                .toList());
    }

    private UUID create(String name) {
        return create(GRAPH, name);
    }

    private UUID create(String graphUri, String name) {
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            var document =
                    transaction
                            .graph(graphUri)
                            .createShapesDocument(name, ShapesDocument.Origin.AUTHORED);
            transaction.commit("Created " + name);
            return document.getId();
        }
    }

    private ShapesDocument document(GraphContext context, UUID id) {
        return context.getShapesDocuments().get(id);
    }

    private void edit(UUID id, String text, Triple... triples) {
        write(
                GRAPH,
                context -> {
                    var document = document(context, id);
                    document.getGraph().clear();
                    for (var triple : triples) {
                        document.getGraph().add(triple);
                    }
                    document.setRawText(text);
                },
                "Edited");
    }

    private String textOf(UUID id) {
        return textOf(GRAPH, id);
    }

    private List<Triple> triplesOf(UUID id) {
        return read(context -> document(context, id).getGraph().find().toList());
    }

    private List<String> namesInOrder() {
        return read(
                context ->
                        context.getShapesDocuments().values().stream()
                                .map(ShapesDocument::getName)
                                .toList());
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
    void aGraphStartsWithoutDocuments() {
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
    void documentsBelongToTheirGraph() {
        create("mine.ttl");

        assertThat(documents(OTHER_GRAPH)).isEmpty();
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
        write(GRAPH, context -> context.removeShapesDocument(first), "Removed");

        var third = create("third.ttl");

        assertThat(order(third)).isEqualTo(2);
    }

    @Test
    void removingAnUnknownDocumentChangesNothing() {
        var before = workspace.getChangeHistory().size();

        write(GRAPH, context -> context.removeShapesDocument(UUID.randomUUID()), "Removed nothing");

        assertThat(workspace.getChangeHistory()).hasSize(before);
    }

    @Test
    void aDocumentCannotBeChangedInAReadTransaction() {
        var id = create("document.ttl");

        try (var transaction = workspace.begin(ReadWrite.READ)) {
            var context = transaction.graph(GRAPH);

            assertThatThrownBy(() -> document(context, id).setName("renamed.ttl"))
                    .isInstanceOf(GraphTransactionException.class);
            assertThatThrownBy(
                            () ->
                                    context.createShapesDocument(
                                            "new.ttl", ShapesDocument.Origin.AUTHORED))
                    .isInstanceOf(GraphTransactionException.class);
            assertThatThrownBy(() -> context.removeShapesDocument(id))
                    .isInstanceOf(GraphTransactionException.class);
        }
        assertThat(namesInOrder()).containsExactly("document.ttl");
    }

    // -------------------------------------------------------------------------
    // Beside the custom SHACL graph and the schema
    // -------------------------------------------------------------------------

    @Test
    void aGraphWithoutDocumentsRecordsNothingAboutThem() {
        write(GRAPH, context -> context.getRdfGraph().add(triple("schema")), "Edited the schema");

        var change = newestChange();
        assertThat(change.affectedKinds()).containsExactly("rdf");
        assertThat(change.deltas()).hasSize(1);
        assertThat(change.removedOnUndo()).isEmpty();
    }

    @Test
    void customShaclAndDocumentsAreSeparateStores() {
        var id = create("document.ttl");

        write(GRAPH, context -> context.getCustomSHACL().add(triple("custom")), "Edited custom");
        edit(id, "text", triple("document"));

        assertThat(customShacl()).containsExactly(triple("custom"));
        assertThat(triplesOf(id)).containsExactly(triple("document"));
    }

    @Test
    void anEditOfCustomShaclDoesNotChangeTheDocumentsVersion() {
        create("document.ttl");
        var before = documentsVersion();

        write(GRAPH, context -> context.getCustomSHACL().add(triple("custom")), "Edited custom");

        assertThat(documentsVersion()).isEqualTo(before);
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
            var context = transaction.graph(GRAPH);
            var document = context.createShapesDocument("new.ttl", ShapesDocument.Origin.IMPORTED);
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
        write(
                GRAPH,
                context -> {
                    context.getRdfGraph().add(triple("schema"));
                    document(context, id).getGraph().add(triple("shape"));
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

        write(GRAPH, context -> document(context, id).setName("renamed.ttl"), "Renamed");
        write(GRAPH, context -> document(context, id).setEnabled(false), "Disabled");
        write(GRAPH, context -> document(context, id).setOrder(4), "Moved");

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
        write(GRAPH, context -> document(context, id).setEnabled(false), "Disabled");

        write(GRAPH, context -> context.removeShapesDocument(id), "Removed");
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
            var context = transaction.graph(GRAPH);
            context.removeShapesDocument(id);
            context.createShapesDocument("new.ttl", ShapesDocument.Origin.AUTHORED);
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
            var document = document(transaction.graph(GRAPH), id);
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

        write(
                GRAPH,
                context -> {
                    var document = document(context, id);
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

        write(GRAPH, context -> context.removeShapesDocument(id), "Removed");

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
    void anEntryIsFiledUnderTheGraphTheDocumentBelongsTo() {
        create(OTHER_GRAPH, "other.ttl");

        assertThat(newestChange().affectedGraphUris()).containsExactly(OTHER_GRAPH);
        assertThat(newestChange().affectedKinds()).containsExactly("shacl");
    }

    @Test
    void undoingACreationSaysWhatItWouldRemove() {
        create("document.ttl");

        assertThat(workspace.undoPreview().removedOnUndo()).containsExactly("document.ttl");
    }

    @Test
    void aMetadataChangeIsReportedValueByValueUnderTheDocumentsName() {
        var id = create("document.ttl");

        write(
                GRAPH,
                context -> {
                    document(context, id).setName("renamed.ttl");
                    document(context, id).setEnabled(false);
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
    void restoringOneSchemaBringsBackItsDocumentsTextAndTriples() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        var target = newestChange().changeId();
        edit(id, "second", triple("b"));

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(textOf(id)).isEqualTo("first");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void restoringOneSchemaBringsBackADeletedDocument() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var target = newestChange().changeId();
        write(GRAPH, context -> context.removeShapesDocument(id), "Removed");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void restoringOneSchemaThroughAnEditAndADeletionUndoesBoth() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        var target = newestChange().changeId();
        edit(id, "second", triple("b"));
        write(GRAPH, context -> context.removeShapesDocument(id), "Removed");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(textOf(id)).isEqualTo("first");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void restoringOneSchemaRemovesADocumentCreatedSince() {
        var target = newestChange().changeId();
        create("document.ttl");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(documents()).isEmpty();
    }

    @Test
    void restoringOneSchemaLeavesTheDocumentsOfAnotherAlone() {
        var mine = create("mine.ttl");
        var theirs = create(OTHER_GRAPH, "theirs.ttl");
        var target = newestChange().changeId();
        edit(mine, "edited", triple("a"));
        write(
                OTHER_GRAPH,
                context -> {
                    var document = document(context, theirs);
                    document.setRawText("edited");
                    document.getGraph().add(triple("b"));
                },
                "Edited theirs");

        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        assertThat(textOf(mine)).isNull();
        assertThat(textOf(OTHER_GRAPH, theirs)).isEqualTo("edited");
    }

    @Test
    void aRestoreOfOneSchemaCanBeUndone() {
        var id = create("document.ttl");
        edit(id, "first", triple("a"));
        var target = newestChange().changeId();
        edit(id, "second", triple("b"));
        workspace.restoreToVersion(target, RevertScope.ofGraphs(GRAPH));

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("second");
        assertThat(triplesOf(id)).containsExactly(triple("b"));
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

        write(GRAPH, context -> document(context, id).setEnabled(false), "Disabled");
        assertThat(documentsVersion()).isNotEqualTo(afterEdit);

        workspace.undo();
        assertThat(documentsVersion()).isEqualTo(afterEdit);
    }

    @Test
    void theDocumentsVersionNoticesAnEditThatOnlyChangesTheText() {
        var id = create("document.ttl");
        edit(id, "# one", triple("a"));
        var before = documentsVersion();

        write(GRAPH, context -> document(context, id).setRawText("# two"), "Edited a comment");

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
    // What becomes of the graph
    // -------------------------------------------------------------------------

    @Test
    void documentsFollowARename() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var renamed = "http://example.org/renamed";
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.renameGraph(GRAPH, renamed);
            transaction.commit("Renamed the graph");
        }

        assertThat(textOf(renamed, id)).isEqualTo("text");
        assertThat(changeNamed("Edited").affectedGraphUris()).containsExactly(renamed);
    }

    @Test
    void documentsComeBackWithADeletedGraph() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.deleteGraph(GRAPH);
            transaction.commit("Deleted the graph");
        }

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(triplesOf(id)).containsExactly(triple("a"));
    }

    @Test
    void replacingAGraphKeepsItsDocumentsAndGivesItTheNewSchema() {
        var id = create("document.ttl");
        edit(id, "text", triple("a"));
        var replacement = GraphFactory.createDefaultGraph();
        replacement.add(triple("new-schema"));

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.replaceGraph(GRAPH, replacement);
            transaction.commit("Replaced the graph");
        }

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(schemaHas(GRAPH, triple("new-schema"))).isTrue();

        workspace.undo();

        assertThat(textOf(id)).isEqualTo("text");
        assertThat(schemaHas(GRAPH, triple("new-schema"))).isFalse();
    }

    @Test
    void documentsCanBeEditedAndUndoneAfterTheirGraphWasReplaced() {
        var id = create("document.ttl");
        edit(id, "before", triple("a"));
        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.replaceGraph(GRAPH, GraphFactory.createDefaultGraph());
            transaction.commit("Replaced the graph");
        }

        edit(id, "after", triple("b"));
        assertThat(newestChange().affectedGraphUris()).containsExactly(GRAPH);

        workspace.undo();
        assertThat(textOf(id)).isEqualTo("before");
    }

    @Test
    void replacingAGraphThatDoesNotExistCreatesIt() {
        var created = "http://example.org/created";
        var schema = GraphFactory.createDefaultGraph();
        schema.add(triple("schema"));

        try (var transaction = workspace.begin(ReadWrite.WRITE)) {
            transaction.replaceGraph(created, schema);
            transaction.commit("Created by replacing");
        }

        assertThat(schemaHas(created, triple("schema"))).isTrue();
        assertThat(documents(created)).isEmpty();
    }
}
