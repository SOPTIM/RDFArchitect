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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.ShapesDocument;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Shapes documents read together as one model.
 *
 * <p>SHACL is conjunctive: shapes targeting the same focus node all apply, and the language has no
 * notion of one shape overriding another. Documents are therefore unioned, never resolved against
 * each other. A precedence rule here would disagree with the file the user exports and with every
 * SHACL engine that validates it. Where two documents cannot both hold, validation says so.
 *
 * <p>Documents are added in {@link ShapesDocument#getOrder()} order, so the result serialises the
 * same way every time. Must be called inside a transaction on the workspace holding the documents.
 * The result is detached from the stored graphs, so writes to it are not persisted.
 */
public final class ShapesUnion {

    private ShapesUnion() {}

    /**
     * The enabled documents. Switching a document off is how a user takes its constraints out of
     * validation and export.
     */
    public static Model enabled(Collection<ShapesDocument> documents) {
        return of(documents, ShapesDocument::isEnabled);
    }

    /** The enabled documents that apply to the schema {@code schemaName}. */
    public static Model enabledFor(Collection<ShapesDocument> documents, String schemaName) {
        return of(
                documents,
                document -> document.isEnabled() && document.getSchemas().contains(schemaName));
    }

    /** The documents {@code included} accepts, whether enabled or not. */
    public static Model of(
            Collection<ShapesDocument> documents, Predicate<ShapesDocument> included) {
        var union = ModelFactory.createDefaultModel();
        documents.stream()
                .filter(included)
                .sorted(Comparator.comparingInt(ShapesDocument::getOrder))
                .forEach(document -> add(union, document));
        return union;
    }

    /**
     * {@code node} as the union holds it: a blank node of {@code document} gets a label of its own.
     *
     * <p>A label only identifies a blank node within the document it was normalised in, and merging
     * on it would fold together two lists of different documents that merely look alike.
     */
    public static Node scoped(Node node, ShapesDocument document) {
        return node.isBlank()
                ? NodeFactory.createBlankNode(document.getId() + ":" + node.getBlankNodeLabel())
                : node;
    }

    /**
     * Adds a document's triples. The first document to declare a prefix keeps it, so a later
     * document rebinding it cannot change what earlier shapes mean.
     */
    private static void add(Model union, ShapesDocument document) {
        document.getGraph()
                .getPrefixMapping()
                .getNsPrefixMap()
                .forEach(
                        (prefix, uri) -> {
                            if (union.getNsPrefixURI(prefix) == null) {
                                union.setNsPrefix(prefix, uri);
                            }
                        });
        document.getGraph()
                .find()
                .forEach(
                        triple ->
                                union.getGraph()
                                        .add(
                                                Triple.create(
                                                        scoped(triple.getSubject(), document),
                                                        triple.getPredicate(),
                                                        scoped(triple.getObject(), document))));
    }
}
