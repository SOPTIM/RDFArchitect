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
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.models.changelog.ContextDelta;
import org.rdfarchitect.rdf.TestRDFUtils;
import org.rdfarchitect.rdf.graph.DeltaCompressible;

import java.util.List;
import java.util.UUID;

/**
 * Several shapes documents per graph, and their place in the context's transactions and history.
 *
 * <p>The context undoes every participant the same number of times, so the risk a document
 * introduces is a version counter out of step with the schema graph's: an undo reaching past the
 * document's creation would otherwise run it off the end of its own history.
 */
class GraphWithContextShapesDocumentTest {

    private GraphWithContextTransactional ctx;
    private Triple shape;
    private Triple otherShape;
    private Triple schemaTriple;

    @BeforeEach
    void setUp() {
        ctx = new GraphWithContextTransactional(GraphFactory.createDefaultGraph());
        shape = TestRDFUtils.triple("shape targetClass Class");
        otherShape = TestRDFUtils.triple("otherShape targetClass OtherClass");
        schemaTriple = TestRDFUtils.triple("Class type Class");
    }

    @AfterEach
    void tearDown() {
        if (ctx.isInTransaction()) {
            ctx.end();
        }
    }

    /** Commits {@code work} as one named change. */
    private void inWriteTransaction(Runnable work, String message) {
        ctx.begin(ReadWrite.WRITE);
        try {
            work.run();
            ctx.commit(message);
        } finally {
            ctx.end();
        }
    }

    /** {@code canUndo} has to be read inside a transaction, while {@code undo} forbids one. */
    private boolean canUndo() {
        ctx.begin(ReadWrite.READ);
        try {
            return ctx.canUndo();
        } finally {
            ctx.end();
        }
    }

    private boolean documentExists(UUID id) {
        ctx.begin(ReadWrite.READ);
        try {
            return ctx.getShapesDocuments().containsKey(id);
        } finally {
            ctx.end();
        }
    }

    private ShapesDocument document(UUID id) {
        return ctx.getShapesDocuments().get(id);
    }

    /** Creates a document holding {@code shape}, committed as one change. */
    private UUID importDocument(String name) {
        var id = new UUID[1];
        inWriteTransaction(
                () -> {
                    var created = ctx.createShapesDocument(name, ShapesDocument.Origin.IMPORTED);
                    created.getGraph().add(shape);
                    created.setRawText("# " + name);
                    id[0] = created.getId();
                },
                "import constraints");
        return id[0];
    }

    private List<String> contextNamesOfLastEntry() {
        ctx.begin(ReadWrite.READ);
        try {
            return ctx.getChangeLog().peekUndo().getContextDeltas().stream()
                    .map(ContextDelta::contextName)
                    .toList();
        } finally {
            ctx.end();
        }
    }

    private boolean documentContains(UUID id, Triple triple) {
        ctx.begin(ReadWrite.READ);
        try {
            return ctx.getShapesDocuments().get(id).getGraph().contains(triple);
        } finally {
            ctx.end();
        }
    }

    // -------------------------------------------------------------------------
    // The default document
    // -------------------------------------------------------------------------

    @Test
    void getCustomSHACL_readsTheDefaultDocument() {
        ctx.begin(ReadWrite.READ);
        var first = ctx.getCustomSHACL();
        var second = ctx.getCustomSHACL();
        ctx.end();

        assertThat(first).isSameAs(second);
        assertThat(ctx.getShapesDocuments())
                .containsOnlyKeys(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID);
        assertThat(ctx.getShapesDocuments().get(GraphContext.DEFAULT_SHAPES_DOCUMENT_ID).getName())
                .isEqualTo(GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME);
    }

    // -------------------------------------------------------------------------
    // Several documents
    // -------------------------------------------------------------------------

    @Test
    void documentsHoldTheirShapesIndependently() {
        var ids = new UUID[2];
        inWriteTransaction(
                () -> {
                    var a = ctx.createShapesDocument("eq.ttl", ShapesDocument.Origin.IMPORTED);
                    var b = ctx.createShapesDocument("tp.ttl", ShapesDocument.Origin.AUTHORED);
                    a.getGraph().add(shape);
                    b.getGraph().add(otherShape);
                    ids[0] = a.getId();
                    ids[1] = b.getId();
                },
                "import two constraint files");

        assertThat(documentContains(ids[0], shape)).isTrue();
        assertThat(documentContains(ids[0], otherShape)).isFalse();
        assertThat(documentContains(ids[1], otherShape)).isTrue();
    }

    @Test
    void documentsKeepTheirInsertionOrder() {
        inWriteTransaction(
                () -> {
                    ctx.createShapesDocument("first.ttl", ShapesDocument.Origin.IMPORTED);
                    ctx.createShapesDocument("second.ttl", ShapesDocument.Origin.IMPORTED);
                },
                "import");

        // The default document is created with the graph, so it leads the list.
        assertThat(ctx.getShapesDocuments().values())
                .extracting(ShapesDocument::getName)
                .containsExactly(
                        GraphContext.DEFAULT_SHAPES_DOCUMENT_NAME, "first.ttl", "second.ttl");
        assertThat(ctx.getShapesDocuments().values())
                .extracting(ShapesDocument::getOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void aDocumentCreatedAfterADeletionDoesNotShareAPosition() {
        // The regression this guards: positions used to be handed out by counting the documents,
        // and a deletion leaves a gap in the numbering — so the next document claimed a position
        // an existing one already held, and every reader that sorts by it fell back to the map's
        // insertion order for the tie.
        var first = new UUID[1];
        inWriteTransaction(
                () -> {
                    first[0] =
                            ctx.createShapesDocument("first.ttl", ShapesDocument.Origin.IMPORTED)
                                    .getId();
                    ctx.createShapesDocument("second.ttl", ShapesDocument.Origin.IMPORTED);
                },
                "import");

        inWriteTransaction(() -> ctx.removeShapesDocument(first[0]), "delete constraints");
        inWriteTransaction(
                () -> ctx.createShapesDocument("third.ttl", ShapesDocument.Origin.IMPORTED),
                "import");

        assertThat(ctx.getShapesDocuments().values())
                .extracting(ShapesDocument::getOrder)
                .doesNotHaveDuplicates();
        assertThat(ctx.getShapesDocuments().values())
                .filteredOn(document -> "third.ttl".equals(document.getName()))
                .singleElement()
                .extracting(ShapesDocument::getOrder)
                .isEqualTo(3);
    }

    @Test
    void removedDocumentNoLongerTakesPart() {
        var id = new UUID[1];
        inWriteTransaction(
                () ->
                        id[0] =
                                ctx.createShapesDocument("gone.ttl", ShapesDocument.Origin.IMPORTED)
                                        .getId(),
                "import");

        inWriteTransaction(() -> ctx.removeShapesDocument(id[0]), "delete constraints");

        assertThat(ctx.getShapesDocuments()).doesNotContainKey(id[0]);
        // Committing and undoing afterwards must not trip over the detached graph.
        assertThatCode(
                        () -> {
                            inWriteTransaction(
                                    () -> ctx.getRdfGraph().add(schemaTriple), "add class");
                            ctx.undo();
                        })
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    @Test
    void shapeEditIsUndoable() {
        var id = new UUID[1];
        inWriteTransaction(
                () ->
                        id[0] =
                                ctx.createShapesDocument("eq.ttl", ShapesDocument.Origin.IMPORTED)
                                        .getId(),
                "import constraints");
        inWriteTransaction(
                () -> ctx.getShapesDocuments().get(id[0]).getGraph().add(shape), "add shape");

        assertThat(documentContains(id[0], shape)).isTrue();

        ctx.undo();

        assertThat(documentContains(id[0], shape)).isFalse();

        ctx.redo();

        assertThat(documentContains(id[0], shape)).isTrue();
    }

    @Test
    void documentCreatedAfterAHistoryCanBeUndoneThroughIt() {
        // The regression this guards: the document's graph starts empty while the schema graph is
        // already several versions deep. Undoing back past its creation used to run it off the end
        // of its own history and throw.
        for (int i = 0; i < 3; i++) {
            var triple = TestRDFUtils.triple("Class" + i + " type Class");
            inWriteTransaction(() -> ctx.getRdfGraph().add(triple), "add class");
        }

        var id = new UUID[1];
        inWriteTransaction(
                () -> {
                    id[0] =
                            ctx.createShapesDocument("late.ttl", ShapesDocument.Origin.IMPORTED)
                                    .getId();
                    ctx.getShapesDocuments().get(id[0]).getGraph().add(shape);
                },
                "import constraints");

        assertThatCode(
                        () -> {
                            while (canUndo()) {
                                ctx.undo();
                            }
                        })
                .doesNotThrowAnyException();

        assertThat(documentExists(id[0])).isFalse();
        ctx.begin(ReadWrite.READ);
        var schemaEmpty = ctx.getRdfGraph().isEmpty();
        ctx.end();
        assertThat(schemaEmpty).isTrue();
    }

    @Test
    void undoingCreationRemovesTheDocumentAndRedoRestoresIt() {
        inWriteTransaction(() -> ctx.getRdfGraph().add(schemaTriple), "add class");

        var id = new UUID[1];
        inWriteTransaction(
                () -> {
                    id[0] =
                            ctx.createShapesDocument("eq.ttl", ShapesDocument.Origin.IMPORTED)
                                    .getId();
                    ctx.getShapesDocuments().get(id[0]).getGraph().add(shape);
                },
                "import constraints");

        ctx.undo();
        assertThat(documentExists(id[0])).isFalse();

        ctx.redo();
        assertThat(documentContains(id[0], shape)).isTrue();
    }

    @Test
    void schemaAndShapesUndoTogetherWhenChangedInOneCommit() {
        var id = new UUID[1];
        inWriteTransaction(
                () ->
                        id[0] =
                                ctx.createShapesDocument("eq.ttl", ShapesDocument.Origin.IMPORTED)
                                        .getId(),
                "import");

        inWriteTransaction(
                () -> {
                    ctx.getRdfGraph().add(schemaTriple);
                    ctx.getShapesDocuments().get(id[0]).getGraph().add(shape);
                },
                "add class and its constraint");

        ctx.undo();

        ctx.begin(ReadWrite.READ);
        var schemaHasTriple = ctx.getRdfGraph().contains(schemaTriple);
        var shapesHaveTriple = ctx.getShapesDocuments().get(id[0]).getGraph().contains(shape);
        ctx.end();

        assertThat(schemaHasTriple).isFalse();
        assertThat(shapesHaveTriple).isFalse();
    }

    @Test
    void schemaEditsDoNotDeepenAnUntouchedDocument() {
        var id = new UUID[1];
        inWriteTransaction(
                () -> {
                    var document =
                            ctx.createShapesDocument("eq.ttl", ShapesDocument.Origin.IMPORTED);
                    document.getGraph().add(shape);
                    id[0] = document.getId();
                },
                "import constraints");
        var depthAfterImport = readDepth(id[0]);

        for (int i = 0; i < 30; i++) {
            var triple = TestRDFUtils.triple("Class" + i + " type Class");
            inWriteTransaction(() -> ctx.getRdfGraph().add(triple), "add class");
        }

        assertThat(readDepth(id[0])).isLessThanOrEqualTo(depthAfterImport + 1);
        assertThat(documentContains(id[0], shape)).isTrue();
    }

    /** How many deltas a read of the document's committed shapes passes through. */
    private int readDepth(UUID id) {
        ctx.begin(ReadWrite.READ);
        try {
            int depth = 0;
            Graph layer = ctx.getShapesDocuments().get(id).getGraph().getLastDelta();
            while (layer instanceof DeltaCompressible delta) {
                depth++;
                layer = delta.getBase();
            }
            return depth;
        } finally {
            ctx.end();
        }
    }

    @Test
    void textAndMetadataRewindWithTheShapes() {
        var id = importDocument("eq.ttl");
        inWriteTransaction(
                () -> {
                    var edited = document(id);
                    edited.getGraph().add(otherShape);
                    edited.setRawText("# edited");
                    edited.setName("renamed.ttl");
                    edited.setEnabled(false);
                    edited.setOrder(7);
                },
                "edit constraints");

        ctx.undo();

        assertThat(documentContains(id, otherShape)).isFalse();
        assertThat(document(id).getRawText()).isEqualTo("# eq.ttl");
        assertThat(document(id).getName()).isEqualTo("eq.ttl");
        assertThat(document(id).isEnabled()).isTrue();
        assertThat(document(id).getOrder()).isEqualTo(1);

        ctx.redo();

        assertThat(documentContains(id, otherShape)).isTrue();
        assertThat(document(id).getRawText()).isEqualTo("# edited");
        assertThat(document(id).getName()).isEqualTo("renamed.ttl");
        assertThat(document(id).isEnabled()).isFalse();
        assertThat(document(id).getOrder()).isEqualTo(7);
    }

    @Test
    void aMetadataOnlyChangeIsItsOwnUndoStep() {
        var id = importDocument("eq.ttl");
        inWriteTransaction(() -> document(id).setEnabled(false), "disable constraints");
        inWriteTransaction(() -> ctx.getRdfGraph().add(schemaTriple), "add class");

        ctx.undo();
        assertThat(document(id).isEnabled()).isFalse();

        ctx.undo();
        assertThat(document(id).isEnabled()).isTrue();
        assertThat(documentContains(id, shape)).isTrue();
    }

    @Test
    void removalIsUndoable() {
        var id = importDocument("gone.ttl");
        inWriteTransaction(() -> ctx.removeShapesDocument(id), "delete constraints");
        assertThat(documentExists(id)).isFalse();

        ctx.undo();

        assertThat(documentContains(id, shape)).isTrue();
        assertThat(document(id).getRawText()).isEqualTo("# gone.ttl");
        assertThat(document(id).getName()).isEqualTo("gone.ttl");

        ctx.redo();

        assertThat(documentExists(id)).isFalse();
    }

    @Test
    void anAbortedRemovalKeepsTheDocument() {
        var id = importDocument("kept.ttl");

        ctx.begin(ReadWrite.WRITE);
        ctx.removeShapesDocument(id);
        ctx.abort();
        ctx.end();

        assertThat(documentContains(id, shape)).isTrue();
    }

    @Test
    void anAbortedCreationLeavesNothingBehindForRedo() {
        // The regression this guards: a document created in a transaction that never committed
        // stayed a participant with no redo history, so the next redo ran it off the end of its
        // history and threw.
        importDocument("eq.ttl");
        inWriteTransaction(() -> ctx.getRdfGraph().add(schemaTriple), "add class");
        ctx.undo();

        var orphan = new UUID[1];
        ctx.begin(ReadWrite.WRITE);
        orphan[0] = ctx.createShapesDocument("orphan.ttl", ShapesDocument.Origin.AUTHORED).getId();
        ctx.end();

        assertThat(documentExists(orphan[0])).isFalse();
        assertThatCode(ctx::redo).doesNotThrowAnyException();
        ctx.begin(ReadWrite.READ);
        var schemaHasTriple = ctx.getRdfGraph().contains(schemaTriple);
        ctx.end();
        assertThat(schemaHasTriple).isTrue();
    }

    @Test
    void aDocumentNoVersionCanReachStopsTakingPart() {
        var id = importDocument("discarded.ttl");
        ctx.undo();
        // A new commit discards the redo that could have brought the document back.
        inWriteTransaction(() -> ctx.getRdfGraph().add(schemaTriple), "add class");

        assertThat(contextNamesOfLastEntry()).doesNotContain("shacl:discarded.ttl");
        assertThat(documentExists(id)).isFalse();
    }

    @Test
    void theChangelogNamesADocumentByItsCurrentName() {
        var id = importDocument("before.ttl");
        inWriteTransaction(
                () -> {
                    document(id).setName("after.ttl");
                    document(id).getGraph().add(otherShape);
                },
                "edit constraints");

        assertThat(contextNamesOfLastEntry()).contains("shacl:after.ttl");
    }

    @Test
    void theDocumentsVersionFollowsDocumentChangesAndTheSchemaVersionDoesNot() {
        var id = importDocument("eq.ttl");
        ctx.begin(ReadWrite.READ);
        var schemaVersion = ctx.getRdfGraphVersion();
        var shapesVersion = ctx.getShapesDocumentsVersion();
        ctx.end();

        inWriteTransaction(() -> document(id).setEnabled(false), "disable constraints");

        ctx.begin(ReadWrite.READ);
        assertThat(ctx.getRdfGraphVersion()).isEqualTo(schemaVersion);
        assertThat(ctx.getShapesDocumentsVersion()).isNotEqualTo(shapesVersion);
        ctx.end();

        ctx.undo();

        ctx.begin(ReadWrite.READ);
        assertThat(ctx.getShapesDocumentsVersion()).isEqualTo(shapesVersion);
        ctx.end();
    }
}
