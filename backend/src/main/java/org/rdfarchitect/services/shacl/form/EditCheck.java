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

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks what a form edit produced before anybody sees it.
 *
 * <p>Every edit is text surgery guided by a scanner, and a scanner that misreads one construct does
 * not fail — it writes somewhere slightly wrong. Without a check that is silent damage in a file
 * the user trusted the form with. With one it is a refusal: the result is parsed, everything
 * outside the subjects the edit was about has to come back triple for triple, and the edited
 * subject has to read back as what the form asked for.
 */
final class EditCheck {

    private static final String TURTLE_VIEW =
            " It was not applied. Make the change in the Turtle view.";

    /** Predicates a form field or the form's structure accounts for. */
    private static final Set<Node> MODELLED =
            Stream.of(
                            ShapeModelReader.NODE_FIELDS.keySet().stream(),
                            ShapeModelReader.PROPERTY_FIELDS.keySet().stream(),
                            Stream.of(
                                    RDF.type.asNode(),
                                    RDF.first.asNode(),
                                    RDF.rest.asNode(),
                                    Shacl.PROPERTY))
                    .flatMap(Function.identity())
                    .collect(Collectors.toUnmodifiableSet());

    private EditCheck() {}

    /**
     * The graph {@code after} parses to, once it is known to differ from {@code before} only in
     * what {@code subjects} say.
     *
     * <p>A subject's say includes the blank nodes it reaches — its inline rules and lists — which
     * is where most edits land.
     */
    static Graph parsed(String after, Graph before, Collection<String> subjects) {
        var parsed = ShapesTurtleParser.parse(after);
        if (parsed.failed()) {
            var reason =
                    parsed.findings().isEmpty()
                            ? ""
                            : " (" + parsed.findings().get(0).getMessage() + ")";
            throw new ResourceConflictException(
                    "The edit would leave a document that would no longer parse"
                            + reason
                            + "."
                            + TURTLE_VIEW);
        }
        var nodes = subjects.stream().map(NodeFactory::createURI).toList();
        if (!outside(before, nodes).equals(outside(parsed.graph(), nodes))) {
            throw new ResourceConflictException(
                    "The edit would have changed the document outside what it was made on."
                            + TURTLE_VIEW);
        }
        return parsed.graph();
    }

    /**
     * Refuses a result in which {@code subject} states something the form has no field for
     * differently than before.
     *
     * <p>The form cannot compare those clauses through its model, so they are compared as triples.
     * Not for an edit that removes a rule: that takes whatever the rule said with it.
     */
    static void assertKeepsUnmodelled(Graph before, Graph after, String subject) {
        var node = List.of(NodeFactory.createURI(subject));
        if (!unmodelled(before, node).equals(unmodelled(after, node))) {
            throw new ResourceConflictException(
                    "The edit would have changed a part of this shape the form has no field for."
                            + TURTLE_VIEW);
        }
    }

    /** Refuses a shape that reads back as something other than what was asked for. */
    static void assertReadsAs(NodeShapeModel asked, NodeShapeModel got) {
        compare(SHAPE_VALUES, asked, got);
        if (asked.getProperties() == null) {
            return;
        }
        var wanted = rules(asked.getProperties());
        var written = rules(got.getProperties());
        if (!wanted.equals(written)) {
            throw new ResourceConflictException(
                    "The edit did not come out as the form asked: the shape's rules would read"
                            + " differently from what was sent."
                            + TURTLE_VIEW);
        }
    }

    /** The same, for a rule written as a shape of its own. */
    static void assertReadsAs(PropertyShapeModel asked, PropertyShapeModel got) {
        compare(RULE_VALUES, asked, got);
    }

    // -------------------------------------------------------------------------
    // The model
    // -------------------------------------------------------------------------

    private static final Map<String, Function<NodeShapeModel, Object>> SHAPE_VALUES =
            fields(
                    Map.entry("targetClasses", shape -> set(shape.getTargetClasses())),
                    Map.entry("targetSubjectsOf", shape -> set(shape.getTargetSubjectsOf())),
                    Map.entry("targetObjectsOf", shape -> set(shape.getTargetObjectsOf())),
                    Map.entry("targetNodes", shape -> set(shape.getTargetNodes())),
                    Map.entry("name", shape -> text(shape.getName())),
                    Map.entry("description", shape -> text(shape.getDescription())),
                    Map.entry("message", shape -> text(shape.getMessage())),
                    Map.entry("severity", shape -> blank(shape.getSeverity())),
                    Map.entry("closed", NodeShapeModel::getClosed),
                    Map.entry("deactivated", NodeShapeModel::getDeactivated),
                    Map.entry("ignoredProperties", shape -> list(shape.getIgnoredProperties())));

    private static final Map<String, Function<PropertyShapeModel, Object>> RULE_VALUES =
            fields(
                    Map.entry("iri", rule -> blank(rule.getIri())),
                    Map.entry("path", rule -> blank(rule.getPath())),
                    Map.entry("name", rule -> text(rule.getName())),
                    Map.entry("description", rule -> text(rule.getDescription())),
                    Map.entry("dataType", rule -> blank(rule.getDataType())),
                    Map.entry("classIri", rule -> blank(rule.getClassIri())),
                    Map.entry("nodeKind", rule -> blank(rule.getNodeKind())),
                    Map.entry("minCount", PropertyShapeModel::getMinCount),
                    Map.entry("maxCount", PropertyShapeModel::getMaxCount),
                    Map.entry("allowedValues", rule -> list(rule.getAllowedValues())),
                    Map.entry("hasValue", rule -> text(rule.getHasValue())),
                    Map.entry("minInclusive", rule -> blank(rule.getMinInclusive())),
                    Map.entry("maxInclusive", rule -> blank(rule.getMaxInclusive())),
                    Map.entry("minExclusive", rule -> blank(rule.getMinExclusive())),
                    Map.entry("maxExclusive", rule -> blank(rule.getMaxExclusive())),
                    Map.entry("minLength", PropertyShapeModel::getMinLength),
                    Map.entry("maxLength", PropertyShapeModel::getMaxLength),
                    Map.entry("pattern", rule -> text(rule.getPattern())),
                    Map.entry("flags", rule -> text(rule.getFlags())),
                    Map.entry("severity", rule -> blank(rule.getSeverity())),
                    Map.entry("message", rule -> text(rule.getMessage())),
                    Map.entry("order", rule -> blank(rule.getOrder())),
                    Map.entry("group", rule -> blank(rule.getGroup())),
                    Map.entry("deactivated", PropertyShapeModel::getDeactivated));

    @SafeVarargs
    private static <T> Map<String, Function<T, Object>> fields(
            Map.Entry<String, Function<T, Object>>... fields) {
        var ordered = new LinkedHashMap<String, Function<T, Object>>();
        for (Map.Entry<String, Function<T, Object>> field : fields) {
            ordered.put(field.getKey(), field.getValue());
        }
        return ordered;
    }

    private static <T> void compare(Map<String, Function<T, Object>> fields, T asked, T got) {
        fields.forEach(
                (name, value) -> {
                    if (!Objects.equals(value.apply(asked), value.apply(got))) {
                        throw new ResourceConflictException(
                                "The edit did not come out as the form asked: "
                                        + name
                                        + " would read "
                                        + value.apply(got)
                                        + " rather than "
                                        + value.apply(asked)
                                        + "."
                                        + TURTLE_VIEW);
                    }
                });
    }

    /** A shape's rules as a multiset of what each of them says, in no particular order. */
    private static Map<List<Object>, Integer> rules(List<PropertyShapeModel> rules) {
        var counted = new HashMap<List<Object>, Integer>();
        for (PropertyShapeModel rule : rules == null ? List.<PropertyShapeModel>of() : rules) {
            // A reference says only which rule it is; what that rule holds is its own statement's,
            // and a shape given a new reference sends nothing else about it.
            var said =
                    rule.getIri() != null
                            ? List.<Object>of(rule.getIri())
                            : RULE_VALUES.values().stream()
                                    .map(value -> value.apply(rule))
                                    .toList();
            if (rule.getIri() != null) {
                // Two references to one rule are one triple, so they read back as one.
                counted.put(said, 1);
            } else {
                counted.merge(said, 1, Integer::sum);
            }
        }
        return counted;
    }

    /** The form writes blank and absent alike, so they compare alike. */
    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<String> list(List<String> values) {
        return values == null
                ? List.of()
                : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static Set<String> set(List<String> values) {
        return new HashSet<>(list(values));
    }

    // -------------------------------------------------------------------------
    // The graph
    // -------------------------------------------------------------------------

    /** Every triple not about {@code subjects}, counted, with blank nodes told apart by nothing. */
    private static Map<String, Integer> outside(Graph graph, List<Node> subjects) {
        var inside = closure(graph, subjects);
        return counted(graph.stream().filter(triple -> !inside.contains(triple)));
    }

    private static Map<String, Integer> unmodelled(Graph graph, List<Node> subjects) {
        return counted(
                closure(graph, subjects).stream()
                        .filter(Predicate.not(triple -> MODELLED.contains(triple.getPredicate()))));
    }

    /** The triples {@code subjects} state, and those of every blank node they reach. */
    private static Set<Triple> closure(Graph graph, List<Node> subjects) {
        var triples = new HashSet<Triple>();
        var visited = new LinkedHashSet<Node>(subjects);
        var pending = new ArrayDeque<Node>(subjects);
        while (!pending.isEmpty()) {
            var subject = pending.poll();
            graph.find(subject, Node.ANY, Node.ANY)
                    .forEachRemaining(
                            triple -> {
                                triples.add(triple);
                                var object = triple.getObject();
                                if (object.isBlank() && visited.add(object)) {
                                    pending.add(object);
                                }
                            });
        }
        return triples;
    }

    /**
     * Triples as text, counted.
     *
     * <p>Two parses of one document label its blank nodes differently, so a blank node is written
     * as nothing at all. That loses which blank node a triple is about, which a full isomorphism
     * check would keep — at a cost per edit that grows with the document rather than the edit.
     */
    private static Map<String, Integer> counted(Stream<Triple> triples) {
        var counted = new HashMap<String, Integer>();
        triples.forEach(
                triple ->
                        counted.merge(
                                label(triple.getSubject())
                                        + ' '
                                        + label(triple.getPredicate())
                                        + ' '
                                        + label(triple.getObject()),
                                1,
                                Integer::sum));
        return counted;
    }

    private static String label(Node node) {
        return node.isBlank() ? "_" : node.toString();
    }
}
