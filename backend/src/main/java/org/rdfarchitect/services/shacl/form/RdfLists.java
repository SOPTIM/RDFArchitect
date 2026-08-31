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
import java.util.List;

/**
 * Reading the RDF collections SHACL uses for {@code sh:in} and {@code sh:ignoredProperties}.
 *
 * <p>Walked by hand rather than through Jena's model API because the form works on a bare graph,
 * and because a malformed list in a half-written document should end the walk rather than throw.
 */
final class RdfLists {

    private static final int MAX_LENGTH = 1000;

    private RdfLists() {}

    /** The list's members, as written. Empty when the node is absent or not a list. */
    static List<String> values(Graph graph, Node head) {
        var values = new ArrayList<String>();
        var seen = new java.util.HashSet<Node>();
        var current = head;
        while (current != null && !current.equals(RDF.nil.asNode()) && values.size() < MAX_LENGTH) {
            if (!seen.add(current)) {
                break;
            }
            var first = objectOf(graph, current, RDF.first.asNode());
            if (first == null) {
                break;
            }
            values.add(asString(first));
            current = objectOf(graph, current, RDF.rest.asNode());
        }
        return List.copyOf(values);
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
