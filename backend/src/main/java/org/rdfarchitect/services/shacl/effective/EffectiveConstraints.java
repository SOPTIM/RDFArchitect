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

package org.rdfarchitect.services.shacl.effective;

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * see it as one. Two {@code sh:in} lists are intersected: a value has to be in both.
 *
 * <p>Only what can fail validation is a requirement. A deactivated shape asks nothing, and a shape
 * whose {@code sh:severity} is not {@code sh:Violation} only reports — both are kept apart from the
 * enforced constraints, the second as {@linkplain Asserted#advisory() advisory}.
 */
public final class EffectiveConstraints {

    private static final Node SEVERITY = NodeFactory.createURI(Shacl.NS + "severity");

    private static final Node VIOLATION = NodeFactory.createURI(Shacl.NS + "Violation");

    /** One property of one class — the only identity a cross-file comparison can rely on. */
    public record Key(String targetClass, String path) {}

    /**
     * Everything a shapes graph asks of that property, merged.
     *
     * @param valueClasses the classes a value must be an instance of, from {@code sh:class}
     * @param valueTypes the classes a value may be typed as, from a value-type list {@code sh:path
     *     (p rdf:type) ; sh:in (…)} — how generated SHACL and the CGMES constraints state what NC
     *     files state with {@code sh:class} — or {@code null} when no such list is stated
     * @param allowedValues the values {@code sh:in} permits, or {@code null} when nothing limits
     *     them
     */
    public record Constraint(
            Integer minCount,
            Integer maxCount,
            Set<String> dataTypes,
            Set<String> valueClasses,
            Set<String> valueTypes,
            Set<String> nodeKinds,
            Set<Node> allowedValues) {

        public static Constraint empty() {
            return new Constraint(null, null, Set.of(), Set.of(), null, Set.of(), null);
        }

        /** Whether anything was actually said. A shape may name a path and constrain nothing. */
        public boolean isEmpty() {
            return minCount == null
                    && maxCount == null
                    && dataTypes.isEmpty()
                    && valueClasses.isEmpty()
                    && valueTypes == null
                    && nodeKinds.isEmpty()
                    && allowedValues == null;
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
     *
     * @param advisory what the documents state at a severity that does not fail validation
     */
    public record Asserted(
            Map<Key, Constraint> constraints,
            Map<Key, Constraint> advisory,
            Map<Key, List<String>> statedIn) {

        public static Asserted empty() {
            return new Asserted(Map.of(), Map.of(), Map.of());
        }
    }

    /** One graph's constraints, split by whether they can fail validation. */
    private record Read(Map<Key, Constraint> enforced, Map<Key, Constraint> advisory) {}

    private EffectiveConstraints() {}

    /** Reads several documents as one set of constraints, remembering where each came from. */
    public static Asserted of(Map<String, Graph> documents) {
        var enforced = new LinkedHashMap<Key, Constraint>();
        var advisory = new LinkedHashMap<Key, Constraint>();
        var statedIn = new LinkedHashMap<Key, List<String>>();
        documents.forEach(
                (name, graph) -> {
                    var read = read(graph);
                    read.enforced()
                            .forEach(
                                    (key, constraint) ->
                                            enforced.merge(
                                                    key,
                                                    constraint,
                                                    EffectiveConstraints::conjunction));
                    read.advisory()
                            .forEach(
                                    (key, constraint) ->
                                            advisory.merge(
                                                    key,
                                                    constraint,
                                                    EffectiveConstraints::conjunction));
                    var keys = new LinkedHashSet<>(read.enforced().keySet());
                    keys.addAll(read.advisory().keySet());
                    keys.forEach(
                            key ->
                                    statedIn.computeIfAbsent(key, ignored -> new ArrayList<>())
                                            .add(name));
                });
        return new Asserted(enforced, advisory, statedIn);
    }

    /** Reads a shapes graph into one enforced constraint per class and property. */
    public static Map<Key, Constraint> of(Graph shapes) {
        return read(shapes).enforced();
    }

    private static Read read(Graph shapes) {
        var enforced = new LinkedHashMap<Key, Constraint>();
        var advisory = new LinkedHashMap<Key, Constraint>();
        shapes.stream(Node.ANY, Shacl.TARGET_CLASS, Node.ANY)
                .filter(triple -> triple.getObject().isURI())
                .filter(triple -> !isDeactivated(shapes, triple.getSubject()))
                .forEach(
                        triple ->
                                collect(
                                        shapes,
                                        triple.getSubject(),
                                        triple.getObject().getURI(),
                                        enforced,
                                        advisory));
        enforced.values().removeIf(Constraint::isEmpty);
        advisory.values().removeIf(Constraint::isEmpty);
        return new Read(enforced, advisory);
    }

    private static void collect(
            Graph shapes,
            Node nodeShape,
            String targetClass,
            Map<Key, Constraint> enforced,
            Map<Key, Constraint> advisory) {
        shapes.stream(nodeShape, Shacl.PROPERTY, Node.ANY)
                .map(triple -> triple.getObject())
                .filter(property -> !isDeactivated(shapes, property))
                .forEach(
                        property -> {
                            // Usually one path. A path expression rather than a property yields
                            // none: there is nothing to compare it with on the other side.
                            var paths = propertiesOf(shapes, property);
                            if (paths.isEmpty()) {
                                return;
                            }
                            var into = isAdvisory(shapes, property) ? advisory : enforced;
                            var constraint = read(shapes, property);
                            paths.forEach(
                                    path ->
                                            into.merge(
                                                    new Key(targetClass, path),
                                                    constraint,
                                                    EffectiveConstraints::conjunction));
                        });
    }

    /**
     * Whether a shape is switched off by {@code sh:deactivated true}. Shared with the conflict
     * analysis, so that a shape nobody validates with is ignored the same way everywhere.
     */
    public static boolean isDeactivated(Graph shapes, Node shape) {
        return shapes.stream(shape, Shacl.DEACTIVATED, Node.ANY)
                .map(triple -> triple.getObject())
                .anyMatch(
                        value -> value.isLiteral() && Boolean.TRUE.equals(value.getLiteralValue()));
    }

    /** Whether a shape reports at a severity other than {@code sh:Violation}, the default. */
    public static boolean isAdvisory(Graph shapes, Node shape) {
        return shapes.stream(shape, SEVERITY, Node.ANY)
                .map(triple -> triple.getObject())
                .anyMatch(severity -> !VIOLATION.equals(severity));
    }

    /**
     * What a chosen set of property shapes requires between them.
     *
     * <p>Used where the shapes are already known — the constraints on one property of one class, as
     * the class dialog lists them — rather than discovered by walking {@code sh:targetClass}. The
     * merge is the same conjunction: every shape applies, except those that cannot fail validation.
     */
    public static Constraint readAll(Graph shapes, Collection<Node> propertyShapes) {
        return propertyShapes.stream()
                .filter(shape -> !isDeactivated(shapes, shape) && !isAdvisory(shapes, shape))
                .map(shape -> read(shapes, shape))
                .reduce(EffectiveConstraints::conjunction)
                .orElseGet(Constraint::empty);
    }

    /**
     * A constraint in words, listing only what was actually stated.
     *
     * <p>Lives here rather than beside either caller so that "0..1, xsd:float" means the same thing
     * in the conformance report and in the class dialog.
     */
    public static String describe(Constraint constraint, PrefixMapping prefixes) {
        return describe(constraint, prefixes, ClassHierarchy.NONE);
    }

    /**
     * As {@link #describe(Constraint, PrefixMapping)}, naming a value type by its most general
     * classes: a generated list of a range and every class deriving from it reads as the range.
     */
    public static String describe(
            Constraint constraint, PrefixMapping prefixes, ClassHierarchy hierarchy) {
        var parts = new ArrayList<String>();
        if (constraint.minCount() != null || constraint.maxCount() != null) {
            parts.add(
                    "%s..%s"
                            .formatted(
                                    constraint.minCount() == null ? "0" : constraint.minCount(),
                                    constraint.maxCount() == null ? "n" : constraint.maxCount()));
        }
        if (!constraint.dataTypes().isEmpty()) {
            parts.add(terms(constraint.dataTypes(), prefixes));
        }
        var classes = new LinkedHashSet<>(constraint.valueClasses());
        if (constraint.valueTypes() != null) {
            classes.addAll(constraint.valueTypes());
        }
        if (!classes.isEmpty()) {
            parts.add("of class " + terms(hierarchy.roots(classes), prefixes));
        }
        if (!constraint.nodeKinds().isEmpty()) {
            parts.add(terms(constraint.nodeKinds(), prefixes));
        }
        if (constraint.allowedValues() != null) {
            parts.add("one of " + values(constraint.allowedValues(), prefixes));
        }
        return String.join(", ", parts);
    }

    /** The terms of a set, shortest form first, joined as prose. */
    public static String terms(Set<String> iris, PrefixMapping prefixes) {
        return iris.stream()
                .map(iri -> term(iri, prefixes))
                .sorted()
                .reduce((a, b) -> a + " and " + b)
                .orElse("");
    }

    /** The values of an {@code sh:in} list as a reader would write them, sorted. */
    public static String values(Set<Node> values, PrefixMapping prefixes) {
        if (values.isEmpty()) {
            return "nothing";
        }
        return values.stream()
                .map(value -> value.isURI() ? term(value.getURI(), prefixes) : value.toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String term(String iri, PrefixMapping prefixes) {
        var shortened = prefixes.shortForm(iri);
        return shortened.equals(iri) ? "<" + iri + ">" : shortened;
    }

    private static Constraint read(Graph shapes, Node property) {
        var in = listOf(shapes, property, Shacl.IN);
        if (valueTypeOf(shapes, property) != null) {
            // sh:path (p rdf:type) ; sh:in (C …) — the value of p must be typed as one of C. The
            // path is p's for comparison, and so is the node kind that comes with it.
            Set<String> types =
                    in == null
                            ? null
                            : in.stream()
                                    .filter(Node::isURI)
                                    .map(Node::getURI)
                                    .collect(Collectors.toUnmodifiableSet());
            return new Constraint(
                    null,
                    null,
                    Set.of(),
                    Set.of(),
                    types,
                    uris(shapes, property, Shacl.NODE_KIND),
                    null);
        }
        return new Constraint(
                integer(shapes, property, Shacl.MIN_COUNT),
                integer(shapes, property, Shacl.MAX_COUNT),
                uris(shapes, property, Shacl.DATATYPE),
                uris(shapes, property, Shacl.CLASS),
                null,
                uris(shapes, property, Shacl.NODE_KIND),
                in == null ? null : Set.copyOf(in));
    }

    /**
     * Both shapes apply, so the strictest bound of each wins and the sets are unioned — except the
     * lists of permitted values and types, where a value has to be in both.
     */
    private static Constraint conjunction(Constraint left, Constraint right) {
        return new Constraint(
                pick(left.minCount(), right.minCount(), true),
                pick(left.maxCount(), right.maxCount(), false),
                union(left.dataTypes(), right.dataTypes()),
                union(left.valueClasses(), right.valueClasses()),
                intersection(left.valueTypes(), right.valueTypes()),
                union(left.nodeKinds(), right.nodeKinds()),
                intersection(left.allowedValues(), right.allowedValues()));
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

    private static <T> Set<T> union(Set<T> left, Set<T> right) {
        var all = new LinkedHashSet<>(left);
        all.addAll(right);
        return Set.copyOf(all);
    }

    /** Values both lists permit; a missing list permits everything. */
    private static <T> Set<T> intersection(Set<T> left, Set<T> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        var shared = new HashSet<>(left);
        shared.retainAll(right);
        return Set.copyOf(shared);
    }

    /**
     * The properties a shape constrains: its plain path, or {@code p} of a value-type path.
     *
     * <p>A shape has one {@code sh:path}, but generated SHACL names shapes by the property's local
     * name, so two properties spelled alike in different namespaces — {@code CIM100#Equipment
     * .inService} next to the CIM 18 one, in a workspace holding CGMES and NC profiles — end up as
     * one shape with both paths. Reading only the first gave the other a constraint with nothing in
     * it; each path gets what the shape says instead.
     */
    private static List<String> propertiesOf(Graph shapes, Node property) {
        var properties = new ArrayList<String>();
        shapes.stream(property, Shacl.PATH, Node.ANY)
                .map(triple -> triple.getObject())
                .forEach(
                        path -> {
                            if (path.isURI()) {
                                properties.add(path.getURI());
                            } else {
                                var valueType = valueTypeStep(shapes, path);
                                if (valueType != null) {
                                    properties.add(valueType.getURI());
                                }
                            }
                        });
        return properties;
    }

    /**
     * {@code p} when the shape's path is exactly {@code (p rdf:type)}, the form generated SHACL and
     * the CGMES constraints use for an association's value type.
     */
    private static Node valueTypeOf(Graph shapes, Node property) {
        return shapes.stream(property, Shacl.PATH, Node.ANY)
                .map(triple -> valueTypeStep(shapes, triple.getObject()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Node valueTypeStep(Graph shapes, Node path) {
        if (path.isURI()) {
            return null;
        }
        var steps = list(shapes, path);
        if (steps == null
                || steps.size() != 2
                || !steps.get(0).isURI()
                || !RDF.type.asNode().equals(steps.get(1))) {
            return null;
        }
        return steps.get(0);
    }

    private static List<Node> listOf(Graph shapes, Node subject, Node predicate) {
        var head = object(shapes, subject, predicate);
        return head == null ? null : list(shapes, head);
    }

    /** The members of an RDF list, or {@code null} when {@code head} does not start one. */
    private static List<Node> list(Graph shapes, Node head) {
        var members = new ArrayList<Node>();
        var visited = new HashSet<Node>();
        var current = head;
        while (!RDF.nil.asNode().equals(current)) {
            if (!visited.add(current)) {
                return null;
            }
            var first = object(shapes, current, RDF.first.asNode());
            var rest = object(shapes, current, RDF.rest.asNode());
            if (first == null || rest == null) {
                return null;
            }
            members.add(first);
            current = rest;
        }
        return members;
    }

    private static Node object(Graph shapes, Node subject, Node predicate) {
        return shapes.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .findFirst()
                .orElse(null);
    }

    private static Set<String> uris(Graph shapes, Node subject, Node predicate) {
        return shapes.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .filter(Node::isURI)
                .map(Node::getURI)
                .collect(Collectors.toUnmodifiableSet());
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
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
