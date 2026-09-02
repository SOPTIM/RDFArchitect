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

package org.rdfarchitect.services.shacl.form;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Reading the RDF collections SHACL uses for {@code sh:in} and {@code sh:ignoredProperties}.
 *
 * <p>Walked by hand rather than through Jena's model API because the form works on a bare graph,
 * and because a malformed list in a half-written document should end the walk rather than throw.
 */
final class RdfLists {

    private static final int MAX_LENGTH = 1000;

    private RdfLists() {}

    /**
     * The list's members as nodes, or empty when the node is absent, is not a list, or is
     * malformed.
     *
     * <p>A truncated or cyclic list is reported as absent rather than as the prefix that could be
     * walked, so a caller deciding whether the form may rewrite the shape is not told a partial
     * list is the whole of it.
     */
    static Optional<List<Node>> nodes(Graph graph, Node head) {
        if (head == null) {
            return Optional.empty();
        }
        var members = new ArrayList<Node>();
        var seen = new HashSet<Node>();
        var current = head;
        while (!current.equals(RDF.nil.asNode())) {
            if (members.size() >= MAX_LENGTH || !seen.add(current)) {
                return Optional.empty();
            }
            var first = objectOf(graph, current, RDF.first.asNode());
            var rest = objectOf(graph, current, RDF.rest.asNode());
            if (first == null || rest == null) {
                return Optional.empty();
            }
            members.add(first);
            current = rest;
        }
        return Optional.of(List.copyOf(members));
    }

    /** The list's members, as written. Empty when the node is absent or not a well-formed list. */
    static List<String> values(Graph graph, Node head) {
        return nodes(graph, head).orElseGet(List::of).stream().map(RdfLists::asString).toList();
    }

    /** The list's members that are IRIs, which is what {@code sh:ignoredProperties} holds. */
    static List<String> uris(Graph graph, Node head) {
        return values(graph, head);
    }

    private static Node objectOf(Graph graph, Node subject, Node predicate) {
        return graph.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .findFirst()
                .orElse(null);
    }

    private static String asString(Node node) {
        if (node.isURI()) {
            return node.getURI();
        }
        return node.isLiteral() ? node.getLiteralLexicalForm() : node.toString();
    }
}
