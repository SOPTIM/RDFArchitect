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

package org.rdfarchitect.services.shacl.validation;

import de.soptim.opencgmes.cimvocabcheck.core.schema.SchemaIndex;
import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The namespaces the classes and properties of a schema index are declared in.
 *
 * <p>A term in a namespace no profile of the workspace uses is either written in another vocabulary
 * — official NC constraints name each type under every CIM version's namespace, and only one of
 * those can be in the workspace — or written wrongly. This tells the two apart.
 */
final class KnownNamespaces {

    /** How many characters two namespaces may differ in for one to read as a misspelling. */
    private static final int MISSPELLING_EDITS = 2;

    /** Vocabularies a constraints document is written in, which are never what it is about. */
    private static final Set<String> STANDARD =
            Set.of(Shacl.NS, RDF.uri, RDFS.uri, OWL2.NS, XSD.NS);

    private final Set<String> namespaces;

    /** Per undeclared namespace, the declared one it is a few characters away from. */
    private final Map<String, Optional<String>> nearby = new HashMap<>();

    private KnownNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    static KnownNamespaces of(SchemaIndex index) {
        var namespaces = new HashSet<String>();
        Stream.concat(index.allClasses().stream(), index.allProperties().stream())
                .filter(Node::isURI)
                .forEach(term -> namespaces.add(namespaceOf(term.getURI())));
        return new KnownNamespaces(namespaces);
    }

    /** Whether some term of the index shares {@code term}'s namespace. */
    boolean declares(Node term) {
        return namespaces.contains(namespaceOf(term.getURI()));
    }

    /**
     * The declared namespace {@code term}'s looks like a misspelling of, if any: one at most two
     * characters away, letter case aside, or one whose closing {@code #} or {@code /} is missing,
     * so that the local name runs on from it.
     */
    Optional<String> misspeltAs(Node term) {
        var uri = term.getURI();
        var namespace = namespaceOf(uri);
        var runOn =
                namespaces.stream()
                        .filter(known -> runsOnFrom(uri, namespace, known))
                        .sorted()
                        .findFirst();
        if (runOn.isPresent()) {
            return runOn;
        }
        return nearby.computeIfAbsent(
                namespace,
                ignored ->
                        namespaces.stream()
                                .filter(
                                        known ->
                                                withinEdits(
                                                        namespace.toLowerCase(Locale.ROOT),
                                                        known.toLowerCase(Locale.ROOT),
                                                        MISSPELLING_EDITS))
                                .sorted()
                                .findFirst());
    }

    /**
     * The undeclared namespaces a document writes more of its property paths in than all declared
     * ones together. Such a namespace is what the document is about, not a second spelling beside
     * it: other versions' spellings name types, while the properties a document constrains are its
     * own.
     */
    Set<String> writtenMostlyIn(Graph shapes) {
        var undeclared = new HashMap<String, Integer>();
        int declared = 0;
        for (Node property : pathProperties(shapes)) {
            var namespace = namespaceOf(property.getURI());
            if (namespaces.contains(namespace)) {
                declared++;
            } else if (!STANDARD.contains(namespace)) {
                undeclared.merge(namespace, 1, Integer::sum);
            }
        }
        var mostly = new HashSet<String>();
        for (var entry : undeclared.entrySet()) {
            if (entry.getValue() > declared) {
                mostly.add(entry.getKey());
            }
        }
        return mostly;
    }

    /** The properties the document's {@code sh:path}s name, inside sequence and other paths too. */
    private static Set<Node> pathProperties(Graph shapes) {
        var properties = new HashSet<Node>();
        var visited = new HashSet<Node>();
        var pending = new ArrayDeque<Node>();
        shapes.find(Node.ANY, Shacl.PATH, Node.ANY).forEach(path -> pending.add(path.getObject()));
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (node.isURI()) {
                properties.add(node);
            } else if (node.isBlank() && visited.add(node)) {
                shapes.find(node, Node.ANY, Node.ANY)
                        .forEach(step -> pending.add(step.getObject()));
            }
        }
        properties.remove(RDF.nil.asNode());
        return properties;
    }

    static String namespaceOf(Node term) {
        return namespaceOf(term.getURI());
    }

    /** Up to and including the last {@code #} or {@code /}, which is how CIM namespaces end. */
    private static String namespaceOf(String uri) {
        return uri.substring(0, Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/')) + 1);
    }

    /**
     * Whether {@code uri} is {@code known} with its closing {@code #} or {@code /} left out, as
     * {@code cim:} bound to {@code <http://iec.ch/TC57/CIM100>} writes it.
     */
    private static boolean runsOnFrom(String uri, String namespace, String known) {
        var stem = known.substring(0, known.length() - 1);
        return namespace.length() < stem.length()
                && stem.startsWith(namespace)
                && uri.length() > stem.length()
                && uri.startsWith(stem);
    }

    /**
     * Whether {@code a} becomes {@code b} in at most {@code limit} insertions, deletions or swaps.
     */
    private static boolean withinEdits(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return false;
        }
        var previous = new int[b.length() + 1];
        var current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > limit) {
                return false;
            }
            var swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()] <= limit;
    }
}
