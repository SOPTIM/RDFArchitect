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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        private static Constraint empty() {
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
     * <p>A schema's constraints are the conjunction of its enabled documents, so this is the only
     * honest right-hand side for the comparison. Official constraints arrive split across files —
     * the simple and the complex constraints of a CGMES profile are separate files — and reading
     * one of them alone reports its neighbours' coverage as missing.
     *
     * <p>{@code statedIn} exists so a finding can name the file to open, which a merged view would
     * otherwise have thrown away.
     *
     * @param advisory what the documents state at a severity that does not fail validation
     */
    public record Asserted(
            Map<Key, Constraint> constraints,
            Map<Key, Constraint> advisory,
            Map<Key, List<String>> statedIn) {}

    /**
     * What documents require, split by whether it can fail validation, and which documents name the
     * rules each requirement comes from.
     */
    private record Read(
            Map<Key, Constraint> enforced,
            Map<Key, Constraint> advisory,
            Map<Key, Set<Graph>> namedIn) {

        Read() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private EffectiveConstraints() {}

    /**
     * Reads several documents as one set of constraints, remembering where each came from.
     *
     * <p>Every enabled document applies, so a shape can name a rule another document defines, and a
     * document can list rules for a shape another document targets. A requirement counts as stated
     * by the document naming the rule it comes from.
     */
    public static Asserted of(Map<String, Graph> documents) {
        return of(documents, ClassHierarchy.NONE);
    }

    /**
     * As {@link #of(Map)}, with each rule also filed under every class deriving from its target.
     *
     * <p>{@code sh:targetClass C} targets the instances of {@code C} and of its subclasses, so a
     * rule written on an abstract class constrains the classes data is typed with, and joins
     * whatever their own shapes require.
     *
     * @param hierarchy the classes deriving from each target
     */
    public static Asserted of(Map<String, Graph> documents, ClassHierarchy hierarchy) {
        var read = new Read();
        documents
                .values()
                .forEach(document -> readTargets(document, documents.values(), hierarchy, read));
        var statedIn = new LinkedHashMap<Key, List<String>>();
        read.namedIn()
                .forEach(
                        (key, namers) ->
                                statedIn.put(
                                        key,
                                        documents.entrySet().stream()
                                                .filter(entry -> namers.contains(entry.getValue()))
                                                .map(Map.Entry::getKey)
                                                .toList()));
        return new Asserted(read.enforced(), read.advisory(), statedIn);
    }

    /** Reads a shapes graph into one enforced constraint per class and property. */
    public static Map<Key, Constraint> of(Graph shapes) {
        var read = new Read();
        readTargets(shapes, List.of(shapes), ClassHierarchy.NONE, read);
        return read.enforced();
    }

    /**
     * Adds what the shapes targeting a class in {@code shapes} require, finding their rules in
     * {@code documents}.
     */
    private static void readTargets(
            Graph shapes, Collection<Graph> documents, ClassHierarchy hierarchy, Read into) {
        targets(shapes)
                .forEach(
                        target ->
                                collect(
                                        shapes,
                                        documents,
                                        target.shape(),
                                        hierarchy.withSubClasses(target.targetClass()),
                                        into));
    }

    /** A shape, and a class it targets. */
    private record Target(Node shape, String targetClass) {}

    /**
     * The class targets of the shapes in {@code shapes} that are switched on: {@code
     * sh:targetClass}, and a shape that is a class itself, which targets that class.
     */
    private static List<Target> targets(Graph shapes) {
        var targets = new ArrayList<Target>();
        shapes.stream(Node.ANY, SHACL.targetClass, Node.ANY)
                .filter(triple -> triple.getObject().isURI())
                .forEach(
                        triple ->
                                targets.add(
                                        new Target(
                                                triple.getSubject(), triple.getObject().getURI())));
        shapes.stream(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                .map(triple -> triple.getSubject())
                .filter(Node::isURI)
                .filter(
                        subject ->
                                shapes.contains(subject, RDF.type.asNode(), SHACL.NodeShape)
                                        || shapes.contains(
                                                subject, RDF.type.asNode(), SHACL.PropertyShape))
                .forEach(subject -> targets.add(new Target(subject, subject.getURI())));
        return targets.stream().filter(target -> !isDeactivated(shapes, target.shape())).toList();
    }

    /** The classes the shapes in {@code shapes} target, as the reader finds them. */
    public static Set<String> targetClasses(Graph shapes) {
        return targets(shapes).stream()
                .map(Target::targetClass)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static void collect(
            Graph shapes,
            Collection<Graph> documents,
            Node shape,
            Set<String> targetClasses,
            Read into) {
        for (var rule : rulesOf(shape, shapes, documents).toList()) {
            var property = rule.node();
            var definedIn = definedIn(property, rule.namedIn(), documents);
            // Both the document naming a rule and the one defining it can switch it off or lower
            // its severity: validation reads them together.
            if (isDeactivated(rule.namedIn(), property) || isDeactivated(definedIn, property)) {
                continue;
            }
            // Usually one path. A path expression rather than a property yields none: there is
            // nothing to compare it with on the other side.
            var paths = propertiesOf(definedIn, property);
            if (paths.isEmpty()) {
                continue;
            }
            var constraint = read(definedIn, property);
            if (constraint.isEmpty()) {
                continue;
            }
            var advisory =
                    rule.reportedBy() != null
                            ? isAdvisory(rule.reportedBy().namedIn(), rule.reportedBy().node())
                            : isAdvisory(rule.namedIn(), property)
                                    || isAdvisory(definedIn, property);
            var merged = advisory ? into.advisory() : into.enforced();
            for (var targetClass : targetClasses) {
                for (var path : paths) {
                    var key = new Key(targetClass, path);
                    merged.merge(key, constraint, EffectiveConstraints::conjunction);
                    into.namedIn()
                            .computeIfAbsent(
                                    key,
                                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                            .add(rule.namedIn());
                }
            }
        }
    }

    /**
     * The rules a targeted shape applies.
     *
     * <p>A property shape can carry the target itself, with no node shape around it. What it nests
     * is then about the values of its path, and not about the target class. A node shape's rules
     * can be listed by any document, not only by the one targeting it.
     */
    private static Stream<NamedRule> rulesOf(
            Node shape, Graph shapes, Collection<Graph> documents) {
        if (definedIn(shape, shapes, documents).contains(shape, SHACL.path, Node.ANY)) {
            return Stream.of(new NamedRule(shape, shapes, null));
        }
        var rules = new ArrayList<NamedRule>();
        nodeShapeRules(shape, documents, null, new HashSet<>(), rules);
        return rules.stream();
    }

    /**
     * A node shape's rules: those any document lists with {@code sh:property}, and those of the
     * shapes its focus node has to conform to, through {@code sh:node} and every member of {@code
     * sh:and}.
     *
     * <p>A shape conforms to {@code sh:and} and {@code sh:node} as one constraint of its own, so
     * what they require is reported at the severity of the shape holding them, not of the shapes
     * they name.
     */
    private static void nodeShapeRules(
            Node shape,
            Collection<Graph> documents,
            NamedRule reportedBy,
            Set<Node> visited,
            List<NamedRule> into) {
        if (!visited.add(shape)) {
            return;
        }
        for (var document : documents) {
            document.stream(shape, SHACL.property, Node.ANY)
                    .forEach(
                            triple ->
                                    into.add(
                                            new NamedRule(
                                                    triple.getObject(), document, reportedBy)));
        }
        for (var document : documents) {
            var holder = reportedBy != null ? reportedBy : new NamedRule(shape, document, null);
            for (var member : conformedTo(document, shape)) {
                if (isDeactivated(document, member)) {
                    continue;
                }
                if (definedIn(member, document, documents).contains(member, SHACL.path, Node.ANY)) {
                    into.add(new NamedRule(member, document, holder));
                } else {
                    nodeShapeRules(member, documents, holder, visited, into);
                }
            }
        }
    }

    /** The shapes {@code shape} requires its focus node to conform to, as {@code document} says. */
    private static List<Node> conformedTo(Graph document, Node shape) {
        var members = new ArrayList<Node>();
        document.stream(shape, SHACL.node, Node.ANY)
                .forEach(triple -> members.add(triple.getObject()));
        document.stream(shape, SHACL.and, Node.ANY)
                .forEach(
                        triple -> {
                            var list = list(document, triple.getObject());
                            if (list != null) {
                                members.addAll(list);
                            }
                        });
        return members;
    }

    /**
     * A rule, the document listing it as one of a shape's, and, for a rule reached through {@code
     * sh:node} or {@code sh:and}, the shape whose severity it is reported at.
     */
    private record NamedRule(Node node, Graph namedIn, NamedRule reportedBy) {}

    /** Where a rule is written: the naming document if it says itself, else whichever does. */
    private static Graph definedIn(Node rule, Graph namedIn, Collection<Graph> documents) {
        if (!rule.isURI() || namedIn.contains(rule, SHACL.path, Node.ANY)) {
            return namedIn;
        }
        return documents.stream()
                .filter(document -> document.contains(rule, SHACL.path, Node.ANY))
                .findFirst()
                .orElse(namedIn);
    }

    /** Targets other than a class, which say nothing about the class of what they select. */
    private static final List<Node> OTHER_TARGETS =
            List.of(SHACL.targetSubjectsOf, SHACL.targetObjectsOf, SHACL.targetNode);

    /** Combinations of shapes no reading per class and property can follow. */
    private static final List<Node> COMBINATIONS = List.of(SHACL.or, SHACL.xone, SHACL.not);

    /**
     * What {@link #of(Map)} leaves unread in each document, by the SHACL term that makes it: how
     * many shapes target nodes other than by class ({@code sh:targetSubjectsOf}, {@code
     * sh:targetObjectsOf}, {@code sh:targetNode}), and how many of the shapes it does read combine
     * rules with {@code sh:or}, {@code sh:xone} or {@code sh:not}.
     *
     * <p>Path expressions are not counted: what a profile states about inverse and nested paths has
     * nothing to line up with on a schema's side.
     *
     * @return per document name, in the order given, the number of shapes per term's IRI; a
     *     document leaving nothing unread is not listed
     */
    public static Map<String, Map<String, Integer>> unread(Map<String, Graph> documents) {
        var reached = new HashSet<Node>();
        for (var document : documents.values()) {
            for (var target : targets(document)) {
                reached.add(target.shape());
                var visited = new HashSet<Node>();
                if (definedIn(target.shape(), document, documents.values())
                        .contains(target.shape(), SHACL.path, Node.ANY)) {
                    continue;
                }
                var rules = new ArrayList<NamedRule>();
                nodeShapeRules(target.shape(), documents.values(), null, visited, rules);
                reached.addAll(visited);
                rules.forEach(rule -> reached.add(rule.node()));
            }
        }
        var unread = new LinkedHashMap<String, Map<String, Integer>>();
        documents.forEach(
                (name, document) -> {
                    var counts = new LinkedHashMap<String, Integer>();
                    for (var term : OTHER_TARGETS) {
                        count(
                                counts,
                                term,
                                document.stream(Node.ANY, term, Node.ANY)
                                        .map(triple -> triple.getSubject())
                                        .filter(shape -> !isDeactivated(document, shape))
                                        .distinct()
                                        .count());
                    }
                    for (var term : COMBINATIONS) {
                        count(
                                counts,
                                term,
                                document.stream(Node.ANY, term, Node.ANY)
                                        .map(triple -> triple.getSubject())
                                        .filter(reached::contains)
                                        .filter(shape -> !isDeactivated(document, shape))
                                        .distinct()
                                        .count());
                    }
                    if (!counts.isEmpty()) {
                        unread.put(name, counts);
                    }
                });
        return unread;
    }

    private static void count(Map<String, Integer> counts, Node term, long shapes) {
        if (shapes > 0) {
            counts.put(term.getURI(), (int) shapes);
        }
    }

    /**
     * Whether a shape is switched off by {@code sh:deactivated true}. Shared with the conflict
     * analysis, so that a shape nobody validates with is ignored the same way everywhere.
     */
    public static boolean isDeactivated(Graph shapes, Node shape) {
        return shapes.stream(shape, SHACL.deactivated, Node.ANY)
                .map(triple -> triple.getObject())
                .anyMatch(
                        value ->
                                value.isLiteral()
                                        // Reading the value of an ill-typed literal throws.
                                        && value.getLiteral().isWellFormed()
                                        && Boolean.TRUE.equals(value.getLiteralValue()));
    }

    /** Whether a shape reports at a severity other than {@code sh:Violation}, the default. */
    private static boolean isAdvisory(Graph shapes, Node shape) {
        return shapes.stream(shape, SHACL.severity, Node.ANY)
                .map(triple -> triple.getObject())
                .anyMatch(severity -> !SHACL.Violation.equals(severity));
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

    private static Constraint read(Graph shapes, Node property) {
        var in = listOf(shapes, property, SHACL.in);
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
                    uris(shapes, property, SHACL.nodeKind),
                    null);
        }
        return new Constraint(
                integer(shapes, property, SHACL.minCount),
                integer(shapes, property, SHACL.maxCount),
                uris(shapes, property, SHACL.datatype),
                uris(shapes, property, SHACL.class_),
                null,
                uris(shapes, property, SHACL.nodeKind),
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
     * one shape with both paths. Each path gets what the shape says.
     */
    private static List<String> propertiesOf(Graph shapes, Node property) {
        var properties = new ArrayList<String>();
        shapes.stream(property, SHACL.path, Node.ANY)
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
        return shapes.stream(property, SHACL.path, Node.ANY)
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
