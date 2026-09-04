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

import org.apache.jena.graph.Triple;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.rdf.TestRDFUtils;

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

        assertThat(documentContains(id[0], shape)).isFalse();
        ctx.begin(ReadWrite.READ);
        var schemaEmpty = ctx.getRdfGraph().isEmpty();
        ctx.end();
        assertThat(schemaEmpty).isTrue();
    }

    @Test
    void undoingPastCreationLeavesTheDocumentEmptyThenRedoRestoresIt() {
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
        assertThat(documentContains(id[0], shape)).isFalse();

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
}
