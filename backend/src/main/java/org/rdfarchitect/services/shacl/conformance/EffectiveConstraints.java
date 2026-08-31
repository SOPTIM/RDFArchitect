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

package org.rdfarchitect.services.shacl.conformance;

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a shapes graph actually requires of each property of each class.
 *
 * <p>Shapes are compared by what they say, not by what they are called: generated shapes and
 * official ENTSO-E ones name nothing alike, and one property's rules are routinely spread over
 * several property shapes — RDFArchitect emits separate cardinality, datatype and value-type shapes
 * for a single property, and official files split rules by concern too. So every shape targeting a
 * class is collapsed into one statement per {@code (class, path)} pair.
 *
 * <p>Collapsing is conjunction, which is what SHACL means: every shape applies, so the effective
 * lower bound is the largest {@code sh:minCount} anyone asks for and the effective upper bound is
 * the smallest {@code sh:maxCount}. Datatypes and classes are collected rather than merged, because
 * two different ones is not a stricter rule — it is a contradiction, and the comparison needs to
 * see it as one.
 */
final class EffectiveConstraints {

    /** One property of one class — the only identity a cross-file comparison can rely on. */
    record Key(String targetClass, String path) {}

    /** Everything a shapes graph asks of that property, merged. */
    record Constraint(
            Integer minCount,
            Integer maxCount,
            Set<String> dataTypes,
            Set<String> valueClasses,
            Set<String> nodeKinds) {

        static Constraint empty() {
            return new Constraint(null, null, Set.of(), Set.of(), Set.of());
        }

        /** Whether anything was actually said. A shape may name a path and constrain nothing. */
        boolean isEmpty() {
            return minCount == null
                    && maxCount == null
                    && dataTypes.isEmpty()
                    && valueClasses.isEmpty()
                    && nodeKinds.isEmpty();
        }
    }

    /**
     * What several documents require together, and which of them requires each thing.
     *
     * <p>A graph's constraints are the conjunction of its enabled documents, so this is the only
     * honest right-hand side for the comparison. Official constraints arrive split across files —
     * the CGMES 3.0 DiagramLayout file states 11 of its property shapes itself and defers 41 to the
     * shared IdentifiedObject file — and reading one of them alone reports its neighbours' coverage
     * as missing.
     *
     * <p>{@code statedIn} exists so a finding can name the file to open, which a merged view would
     * otherwise have thrown away.
     */
    record Asserted(Map<Key, Constraint> constraints, Map<Key, List<String>> statedIn) {

        static Asserted empty() {
            return new Asserted(Map.of(), Map.of());
        }
    }

    private EffectiveConstraints() {}

    /** Reads several documents as one set of constraints, remembering where each came from. */
    static Asserted of(Map<String, Graph> documents) {
        var merged = new LinkedHashMap<Key, Constraint>();
        var statedIn = new LinkedHashMap<Key, List<String>>();
        documents.forEach(
                (name, graph) ->
                        of(graph)
                                .forEach(
                                        (key, constraint) -> {
                                            merged.merge(
                                                    key,
                                                    constraint,
                                                    EffectiveConstraints::conjunction);
                                            statedIn.computeIfAbsent(
                                                            key, ignored -> new ArrayList<>())
                                                    .add(name);
                                        }));
        return new Asserted(merged, statedIn);
    }

    /** Reads a shapes graph into one constraint per class and property. */
    static Map<Key, Constraint> of(Graph shapes) {
        var merged = new LinkedHashMap<Key, Constraint>();
        shapes.stream(Node.ANY, Shacl.TARGET_CLASS, Node.ANY)
                .filter(triple -> triple.getObject().isURI())
                .forEach(
                        triple ->
                                collect(
                                        shapes,
                                        triple.getSubject(),
                                        triple.getObject().getURI(),
                                        merged));
        merged.values().removeIf(Constraint::isEmpty);
        return merged;
    }

    private static void collect(
            Graph shapes, Node nodeShape, String targetClass, Map<Key, Constraint> merged) {
        shapes.stream(nodeShape, Shacl.PROPERTY, Node.ANY)
                .map(triple -> triple.getObject())
                .forEach(
                        property -> {
                            var path = uri(shapes, property, Shacl.PATH);
                            if (path == null) {
                                // A path expression rather than a property; nothing to compare it
                                // with on the other side.
                                return;
                            }
                            var key = new Key(targetClass, path);
                            merged.merge(
                                    key, read(shapes, property), EffectiveConstraints::conjunction);
                        });
    }

    private static Constraint read(Graph shapes, Node property) {
        return new Constraint(
                integer(shapes, property, Shacl.MIN_COUNT),
                integer(shapes, property, Shacl.MAX_COUNT),
                uris(shapes, property, Shacl.DATATYPE),
                uris(shapes, property, Shacl.CLASS),
                uris(shapes, property, Shacl.NODE_KIND));
    }

    /** Both shapes apply, so the strictest bound of each wins and the sets are unioned. */
    private static Constraint conjunction(Constraint left, Constraint right) {
        return new Constraint(
                pick(left.minCount(), right.minCount(), true),
                pick(left.maxCount(), right.maxCount(), false),
                union(left.dataTypes(), right.dataTypes()),
                union(left.valueClasses(), right.valueClasses()),
                union(left.nodeKinds(), right.nodeKinds()));
    }

    private static Integer pick(Integer left, Integer right, boolean larger) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return larger ? Math.max(left, right) : Math.min(left, right);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        var all = new LinkedHashSet<>(left);
        all.addAll(right);
        return Set.copyOf(all);
    }

    private static String uri(Graph shapes, Node subject, Node predicate) {
        return shapes.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .filter(Node::isURI)
                .map(Node::getURI)
                .findFirst()
                .orElse(null);
    }

    private static Set<String> uris(Graph shapes, Node subject, Node predicate) {
        return shapes.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .filter(Node::isURI)
                .map(Node::getURI)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Integer integer(Graph shapes, Node subject, Node predicate) {
        return shapes.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .filter(Node::isLiteral)
                .map(Node::getLiteralLexicalForm)
                .map(
                        lexical -> {
                            try {
                                return Integer.valueOf(lexical.trim());
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
