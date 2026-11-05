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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.ShapesDocumentSeed;
import org.rdfarchitect.database.StoredText;
import org.rdfarchitect.exception.graph.GraphTransactionException;
import org.rdfarchitect.models.changelog.ValueChange;
import org.rdfarchitect.rdf.graph.wrapper.SnapshotParticipant;
import org.rdfarchitect.rdf.graph.wrapper.WorkspaceTransactionContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which shapes documents a graph holds.
 *
 * <p>Making the set of documents a participant is what puts creating and deleting a document in the
 * same undo stack as its contents, the way {@link GraphCollection} does for graphs. A deleted
 * document is not torn down, only unlinked: the snapshot undo would return to still holds it, with
 * its own history intact, and it becomes unreachable once the deletion falls out of the changelog.
 *
 * <p>Takes part in a transaction only once it is written to, so a graph nobody has given a document
 * records nothing here.
 */
final class ShapesDocumentCollection extends SnapshotParticipant<Map<UUID, ShapesDocument>> {

    private final WorkspaceTransactionContext txnContext;

    /** In the order they were added. A document's place in the list is its own {@code order}. */
    private final Map<UUID, ShapesDocument> documents = new LinkedHashMap<>();

    ShapesDocumentCollection(WorkspaceTransactionContext txnContext) {
        super(txnContext);
        this.txnContext = txnContext;
    }

    /** Returns the documents as they stand, by id, in the order they were added. */
    Map<UUID, ShapesDocument> view() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(documents));
    }

    ShapesDocument create(String name, ShapesDocument.Origin origin) {
        beginWrite();
        var document =
                new ShapesDocument(
                        UUID.randomUUID(),
                        origin,
                        new ShapesDocument.State(name, null, true, nextOrder(), null),
                        txnContext,
                        this);
        documents.put(document.getId(), document);
        return document;
    }

    /**
     * Puts a document back as storage described it.
     *
     * @param seed what storage recorded
     */
    void restore(ShapesDocumentSeed seed) {
        beginWrite();
        var document =
                new ShapesDocument(
                        seed.id(),
                        seed.origin(),
                        new ShapesDocument.State(
                                seed.name(),
                                seed.sourceFileName(),
                                seed.enabled(),
                                seed.order(),
                                StoredText.of(seed.rawText())),
                        txnContext,
                        this);
        var model = ModelFactory.createModelForGraph(document.getGraph());
        model.setNsPrefixes(seed.graph().getPrefixMapping());
        model.add(ModelFactory.createModelForGraph(seed.graph()));
        documents.put(document.getId(), document);
    }

    /**
     * Removes a document.
     *
     * <p>Its triples are cleared in the same change, which is what makes the changelog entry list
     * what a delete took away, and an undo bring it back along with the document.
     *
     * @param documentId the document to remove; an unknown one is ignored
     */
    void remove(UUID documentId) {
        var document = documents.get(documentId);
        if (document == null) {
            return;
        }
        beginWrite();
        document.getGraph().clear();
        documents.remove(documentId);
    }

    /**
     * Returns whether {@code participant} is this collection or part of a document created for it,
     * including one that has been removed since.
     *
     * @param participant the participant to place
     * @return whether it belongs here
     */
    boolean holds(Object participant) {
        return participant == this
                || (participant instanceof ShapesDocument.Part part && part.holder() == this);
    }

    /**
     * Identifies the state of every document, in a form an undo returns to.
     *
     * <p>Derived from what the documents hold rather than minted per commit, so no commit has to
     * know about whoever is keeping something derived from them.
     */
    UUID contentVersion() {
        var content =
                documents.values().stream()
                        .map(ShapesDocument::contentVersion)
                        .collect(Collectors.joining("\n"));
        return UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8));
    }

    private void beginWrite() {
        beginChange();
        // Announcing a change returns quietly in a read transaction, so that a reference can be
        // handed to a reader. Here it would be a change nothing records.
        if (txnContext.transactionMode() != ReadWrite.WRITE) {
            throw new GraphTransactionException("Cannot write in a read transaction.");
        }
    }

    /**
     * One past the highest position held rather than the count: removing a document leaves a gap in
     * the numbering, so counting would hand the new document a position another one already holds,
     * and readers sorting by it would have no order left for the two.
     */
    private int nextOrder() {
        return documents.values().stream().mapToInt(ShapesDocument::getOrder).max().orElse(-1) + 1;
    }

    @Override
    protected Map<UUID, ShapesDocument> snapshot() {
        return new LinkedHashMap<>(documents);
    }

    @Override
    protected void restore(Map<UUID, ShapesDocument> state) {
        documents.clear();
        documents.putAll(state);
    }

    @Override
    protected List<String> describeAdditions(
            Map<UUID, ShapesDocument> before, Map<UUID, ShapesDocument> after) {
        return after.values().stream()
                .filter(document -> !before.containsKey(document.getId()))
                .map(ShapesDocument::getName)
                .toList();
    }

    @Override
    protected List<ValueChange> describeValueChanges(
            Map<UUID, ShapesDocument> before, Map<UUID, ShapesDocument> after) {
        return ValueChange.between(namesOf(before), namesOf(after));
    }

    /** Not collected into a map, which refuses two documents of one name: storage can hold them. */
    private static Map<String, String> namesOf(Map<UUID, ShapesDocument> documents) {
        var names = new HashMap<String, String>();
        documents.values().forEach(d -> names.put("document: " + d.getName(), d.getName()));
        return names;
    }
}
