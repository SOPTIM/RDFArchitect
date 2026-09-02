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
import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a shapes graph into the structured model the form edits.
 *
 * <p>Only what the form can show is read, and anything else a shape says is reported rather than
 * dropped: a shape carrying {@code sh:or} or an embedded query is handed back marked un-editable,
 * so the form can show it without ever offering to write it back and lose it.
 *
 * <p>Naming a predicate is not enough to make it safe. The form holds one value per predicate and
 * {@link ShapeModelWriter} writes plain literals, so a shape stating {@code sh:targetClass} twice,
 * or {@code sh:message "…"@en, "…"@de}, would come back with all but one value gone and the
 * language tag stripped — silently, on a shape the form had called editable. Every modelled
 * predicate therefore declares the shape of value the writer can reproduce ({@link ValueKind}), and
 * one it cannot reproduce makes the shape read-only exactly as an unknown predicate does.
 *
 * <p>The SHACL predicates come from CIMVocabCheck's {@code Shacl} rather than being redeclared,
 * except for the handful that class has no constant for.
 */
final class ShapeModelReader {

    static final Node NODE_SHAPE = NodeFactory.createURI(Shacl.NS + "NodeShape");
    static final Node NAME = NodeFactory.createURI(Shacl.NS + "name");
    static final Node DESCRIPTION = NodeFactory.createURI(Shacl.NS + "description");
    static final Node MESSAGE = NodeFactory.createURI(Shacl.NS + "message");
    static final Node SEVERITY = NodeFactory.createURI(Shacl.NS + "severity");
    static final Node ORDER = NodeFactory.createURI(Shacl.NS + "order");
    static final Node GROUP = NodeFactory.createURI(Shacl.NS + "group");
    static final Node CLOSED = NodeFactory.createURI(Shacl.NS + "closed");
    static final Node PATTERN = NodeFactory.createURI(Shacl.NS + "pattern");

    /**
     * The shape of value {@link ShapeModelWriter} can write back for a predicate.
     *
     * <p>Each case names what the writer emits, which is what decides whether reading a value and
     * writing it again returns the same triple.
     */
    private enum ValueKind {
        /** Written as a term: only a URI survives. */
        IRI,
        /** Written as a plain string literal: a language tag or a datatype would be dropped. */
        STRING,
        /** Written as a bare integer. */
        INTEGER,
        /** Written as a bare {@code true} or {@code false}. */
        BOOLEAN,
        /** Written as an RDF collection of terms and plain strings. */
        IRI_LIST,
        /** Written as an RDF collection whose members may be terms or plain strings. */
        MIXED_LIST,
        /** {@code rdf:type} on a node shape, which the writer re-states as {@code sh:NodeShape}. */
        TYPE_NODE_SHAPE,
        /** {@code rdf:type} on a property shape, which the writer re-states if it was there. */
        TYPE_PROPERTY_SHAPE,
        /** {@code sh:property}, repeatable and read as its own shape rather than as a value. */
        SHAPES
    }

    /** Everything the form models on a node shape. Anything else makes the shape read-only. */
    private static final Map<Node, ValueKind> KNOWN_NODE_PREDICATES =
            Map.ofEntries(
                    Map.entry(RDF.type.asNode(), ValueKind.TYPE_NODE_SHAPE),
                    Map.entry(Shacl.TARGET_CLASS, ValueKind.IRI),
                    Map.entry(Shacl.PROPERTY, ValueKind.SHAPES),
                    Map.entry(Shacl.IGNORED_PROPERTIES, ValueKind.IRI_LIST),
                    Map.entry(Shacl.DEACTIVATED, ValueKind.BOOLEAN),
                    Map.entry(CLOSED, ValueKind.BOOLEAN),
                    Map.entry(NAME, ValueKind.STRING),
                    Map.entry(DESCRIPTION, ValueKind.STRING),
                    Map.entry(MESSAGE, ValueKind.STRING),
                    Map.entry(SEVERITY, ValueKind.IRI));

    /** Everything the form models on a property shape. */
    private static final Map<Node, ValueKind> KNOWN_PROPERTY_PREDICATES =
            Map.ofEntries(
                    Map.entry(RDF.type.asNode(), ValueKind.TYPE_PROPERTY_SHAPE),
                    Map.entry(Shacl.PATH, ValueKind.IRI),
                    Map.entry(Shacl.DATATYPE, ValueKind.IRI),
                    Map.entry(Shacl.CLASS, ValueKind.IRI),
                    Map.entry(Shacl.NODE_KIND, ValueKind.IRI),
                    Map.entry(Shacl.MIN_COUNT, ValueKind.INTEGER),
                    Map.entry(Shacl.MAX_COUNT, ValueKind.INTEGER),
                    Map.entry(Shacl.IN, ValueKind.MIXED_LIST),
                    Map.entry(Shacl.DEACTIVATED, ValueKind.BOOLEAN),
                    Map.entry(PATTERN, ValueKind.STRING),
                    Map.entry(NAME, ValueKind.STRING),
                    Map.entry(DESCRIPTION, ValueKind.STRING),
                    Map.entry(MESSAGE, ValueKind.STRING),
                    Map.entry(SEVERITY, ValueKind.IRI),
                    Map.entry(ORDER, ValueKind.INTEGER),
                    Map.entry(GROUP, ValueKind.IRI));

    /** The one {@code rdf:type} the writer re-states on an inlined property shape. */
    private static final Node PROPERTY_SHAPE = NodeFactory.createURI(Shacl.NS + "PropertyShape");

    private ShapeModelReader() {}

    /**
     * Every node shape the document declares under its own IRI, in IRI order.
     *
     * <p>A shape SHACL infers rather than types — one carrying {@code sh:targetClass} or {@code
     * sh:property} but no {@code a sh:NodeShape} — is read too, and shown read-only. Leaving such a
     * shape out made it invisible in the form for no reason the user could see; writing one back
     * would add the {@code rdf:type} its author chose to leave implicit, which is the form
     * reformatting a document it promised not to touch.
     */
    static List<NodeShapeModel> read(Graph graph) {
        var declared =
                graph.stream(Node.ANY, RDF.type.asNode(), NODE_SHAPE)
                        .map(Triple::getSubject)
                        .filter(Node::isURI)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var implied =
                Stream.of(Shacl.TARGET_CLASS, Shacl.PROPERTY)
                        .flatMap(predicate -> graph.stream(Node.ANY, predicate, Node.ANY))
                        .map(Triple::getSubject)
                        .filter(Node::isURI)
                        .filter(subject -> !declared.contains(subject))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        return Stream.concat(
                        declared.stream().map(shape -> readShape(graph, shape, false)),
                        implied.stream().map(shape -> readShape(graph, shape, true)))
                .sorted(Comparator.comparing(NodeShapeModel::getIri))
                .toList();
    }

    private static NodeShapeModel readShape(Graph graph, Node shape, boolean implicit) {
        var unsupported = unsupportedPredicates(graph, shape, KNOWN_NODE_PREDICATES);
        var properties = new ArrayList<PropertyShapeModel>();
        graph.stream(shape, Shacl.PROPERTY, Node.ANY)
                .map(triple -> triple.getObject())
                .forEach(
                        property -> {
                            unsupported.addAll(
                                    unsupportedPredicates(
                                            graph, property, KNOWN_PROPERTY_PREDICATES));
                            properties.add(readProperty(graph, property));
                        });
        // A path that is not a plain IRI is a path expression, which the form cannot show.
        properties.stream()
                .filter(property -> property.getPath() == null)
                .findAny()
                .ifPresent(property -> unsupported.add(Shacl.PATH.getURI()));

        return NodeShapeModel.builder()
                .iri(shape.getURI())
                .targetClass(uri(graph, shape, Shacl.TARGET_CLASS))
                .closed(bool(graph, shape, CLOSED))
                .ignoredProperties(
                        RdfLists.uris(graph, object(graph, shape, Shacl.IGNORED_PROPERTIES)))
                .name(string(graph, shape, NAME))
                .description(string(graph, shape, DESCRIPTION))
                .severity(uri(graph, shape, SEVERITY))
                .message(string(graph, shape, MESSAGE))
                .deactivated(bool(graph, shape, Shacl.DEACTIVATED))
                .properties(properties.stream().sorted(byOrderThenPath()).toList())
                .unsupported(unsupported.stream().distinct().sorted().toList())
                .editable(unsupported.isEmpty() && !implicit)
                .readOnlyReason(readOnlyReason(unsupported, implicit))
                .build();
    }

    /** Why the form will not write this shape back, or {@code null} when it will. */
    private static String readOnlyReason(List<String> unsupported, boolean implicit) {
        if (implicit) {
            return "This shape is not declared as a sh:NodeShape, so writing it back from the form"
                    + " would add an rdf:type its author left out. Edit it in the Turtle view.";
        }
        if (unsupported.isEmpty()) {
            return null;
        }
        return "This shape uses something the form does not write back. Edit it in the Turtle"
                + " view.";
    }

    private static PropertyShapeModel readProperty(Graph graph, Node property) {
        return PropertyShapeModel.builder()
                .iri(property.isURI() ? property.getURI() : null)
                .path(uri(graph, property, Shacl.PATH))
                .name(string(graph, property, NAME))
                .description(string(graph, property, DESCRIPTION))
                .dataType(uri(graph, property, Shacl.DATATYPE))
                .classIri(uri(graph, property, Shacl.CLASS))
                .nodeKind(uri(graph, property, Shacl.NODE_KIND))
                .minCount(integer(graph, property, Shacl.MIN_COUNT))
                .maxCount(integer(graph, property, Shacl.MAX_COUNT))
                .allowedValues(RdfLists.values(graph, object(graph, property, Shacl.IN)))
                .pattern(string(graph, property, PATTERN))
                .severity(uri(graph, property, SEVERITY))
                .message(string(graph, property, MESSAGE))
                .order(integer(graph, property, ORDER))
                .group(uri(graph, property, GROUP))
                .deactivated(bool(graph, property, Shacl.DEACTIVATED))
                // Recorded so the writer can put it back. Restating it unconditionally would add a
                // type to the shapes that leave it implicit, which is most of them.
                .typed(graph.contains(property, RDF.type.asNode(), PROPERTY_SHAPE))
                .build();
    }

    /** Reading order in the form: whatever sh:order says, then by path so it is stable. */
    private static Comparator<PropertyShapeModel> byOrderThenPath() {
        return Comparator.comparing(
                        PropertyShapeModel::getOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        PropertyShapeModel::getPath,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * The predicates of {@code subject} the form cannot write back unchanged.
     *
     * <p>Two ways that happens, and both have to be caught here or the shape is offered as editable
     * and quietly loses something on the next save: the predicate is one the form does not model at
     * all, or it is modelled but carries a value the form cannot hold — a second value where the
     * form has one field, or a literal whose language tag or datatype the writer would drop.
     */
    private static List<String> unsupportedPredicates(
            Graph graph, Node subject, Map<Node, ValueKind> known) {
        var lossy = new LinkedHashSet<String>();
        var valuesByPredicate = new LinkedHashMap<Node, List<Node>>();
        graph.stream(subject, Node.ANY, Node.ANY)
                .forEach(
                        triple ->
                                valuesByPredicate
                                        .computeIfAbsent(
                                                triple.getPredicate(), ignored -> new ArrayList<>())
                                        .add(triple.getObject()));

        valuesByPredicate.forEach(
                (predicate, values) -> {
                    var kind = known.get(predicate);
                    if (kind == null || !faithful(graph, kind, values)) {
                        lossy.add(predicate.getURI());
                    }
                });
        return new ArrayList<>(lossy);
    }

    /** Whether every value of one predicate survives a read and a write unchanged. */
    private static boolean faithful(Graph graph, ValueKind kind, List<Node> values) {
        if (kind == ValueKind.SHAPES) {
            // Repeatable by design: each object is read as a property shape of its own.
            return true;
        }
        if (kind == ValueKind.TYPE_NODE_SHAPE || kind == ValueKind.TYPE_PROPERTY_SHAPE) {
            // The writer states the type itself rather than copying it, so it can reproduce
            // exactly the one type it knows how to state — and a shape typed as something else as
            // well would come back having lost the other type.
            var expected = kind == ValueKind.TYPE_NODE_SHAPE ? NODE_SHAPE : PROPERTY_SHAPE;
            return values.size() == 1 && expected.equals(values.get(0));
        }
        // Every other predicate is one field on the form, so a second value has nowhere to go.
        return values.size() == 1 && faithful(graph, kind, values.get(0));
    }

    private static boolean faithful(Graph graph, ValueKind kind, Node value) {
        return switch (kind) {
            case IRI -> value.isURI();
            case STRING -> isPlainString(value);
            case INTEGER -> isCanonicalInteger(value);
            case BOOLEAN -> isCanonicalBoolean(value);
            case IRI_LIST ->
                    RdfLists.nodes(graph, value)
                            .map(members -> members.stream().allMatch(Node::isURI))
                            .orElse(false);
            case MIXED_LIST ->
                    RdfLists.nodes(graph, value)
                            .map(
                                    members ->
                                            members.stream()
                                                    .allMatch(ShapeModelReader::faithfulListMember))
                            .orElse(false);
            case TYPE_NODE_SHAPE, TYPE_PROPERTY_SHAPE, SHAPES -> true;
        };
    }

    /**
     * A literal the writer reproduces by printing a bare number.
     *
     * <p>Turtle reads a bare number as {@code xsd:integer}, so only an {@code xsd:integer} written
     * the way {@code Integer.toString} writes it survives: {@code "1"^^xsd:int} would come back
     * with a different datatype, and {@code 01} with different characters.
     */
    private static boolean isCanonicalInteger(Node value) {
        if (!isTyped(value, XSD.integer.getURI())) {
            return false;
        }
        var lexical = value.getLiteralLexicalForm();
        var parsed = parseInt(lexical);
        return parsed != null && parsed.toString().equals(lexical);
    }

    /** A literal the writer reproduces by printing a bare {@code true} or {@code false}. */
    private static boolean isCanonicalBoolean(Node value) {
        return isTyped(value, XSD.xboolean.getURI())
                && parseBoolean(value.getLiteralLexicalForm()).isPresent()
                && value.getLiteralLexicalForm().equals(value.getLiteralLexicalForm().trim());
    }

    private static boolean isTyped(Node value, String datatype) {
        return value.isLiteral()
                && value.getLiteralLanguage().isEmpty()
                && datatype.equals(value.getLiteralDatatypeURI());
    }

    /**
     * Whether one {@code sh:in} member survives the round trip.
     *
     * <p>The writer decides between a term and a quoted string by looking at the value's text, so a
     * plain string that reads like an IRI would come back as an IRI. A typed or language-tagged
     * literal it writes as a plain string, losing what made it different.
     */
    private static boolean faithfulListMember(Node member) {
        if (member.isURI()) {
            return true;
        }
        return isPlainString(member) && !looksLikeIri(member.getLiteralLexicalForm());
    }

    /** Kept in step with {@code ShapeModelWriter.looksLikeIri}. */
    private static boolean looksLikeIri(String value) {
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("urn:");
    }

    /** A literal the writer can reproduce with plain quotes: no language tag, no other datatype. */
    private static boolean isPlainString(Node value) {
        if (!value.isLiteral() || !value.getLiteralLanguage().isEmpty()) {
            return false;
        }
        var datatype = value.getLiteralDatatypeURI();
        return datatype == null || XSD.xstring.getURI().equals(datatype);
    }

    // -------------------------------------------------------------------------
    // Single values
    // -------------------------------------------------------------------------

    private static Node object(Graph graph, Node subject, Node predicate) {
        return graph.stream(subject, predicate, Node.ANY)
                .map(triple -> triple.getObject())
                .findFirst()
                .orElse(null);
    }

    private static String uri(Graph graph, Node subject, Node predicate) {
        var object = object(graph, subject, predicate);
        return object != null && object.isURI() ? object.getURI() : null;
    }

    private static String string(Graph graph, Node subject, Node predicate) {
        var object = object(graph, subject, predicate);
        return object != null && object.isLiteral() ? object.getLiteralLexicalForm() : null;
    }

    private static Integer integer(Graph graph, Node subject, Node predicate) {
        var lexical = string(graph, subject, predicate);
        return lexical == null ? null : parseInt(lexical);
    }

    private static Integer parseInt(String lexical) {
        try {
            return Integer.valueOf(lexical.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Graph graph, Node subject, Node predicate) {
        var lexical = string(graph, subject, predicate);
        return lexical == null ? null : parseBoolean(lexical).orElse(null);
    }

    /**
     * {@code true} or {@code false}, or empty for anything else.
     *
     * <p>Anything-that-is-not-true used to read as {@code false}, which invented a rule: {@code
     * sh:closed "yes"} became {@code sh:closed false} on the next save. An unreadable value is
     * reported as unrepresentable instead, and the shape is left to the Turtle view.
     *
     * <p>{@code Optional} rather than a nullable {@code Boolean}: a three-valued Boolean is a
     * standing invitation to unbox one, and the two callers want different things from a value that
     * will not parse — one reports it as unrepresentable, the other treats it as absent.
     */
    private static Optional<Boolean> parseBoolean(String lexical) {
        var trimmed = lexical.trim();
        if ("true".equals(trimmed)) {
            return Optional.of(Boolean.TRUE);
        }
        return "false".equals(trimmed) ? Optional.of(Boolean.FALSE) : Optional.empty();
    }
}
