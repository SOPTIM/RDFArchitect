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
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Reads a shapes graph into the structured model the form edits.
 *
 * <p>Only what the form can show is read, and anything else a shape says is reported rather than
 * dropped: a shape carrying {@code sh:or} or an embedded query is handed back marked un-editable,
 * so the form can show it without ever offering to write it back and lose it.
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

    /** Everything the form models on a node shape. Anything else makes the shape read-only. */
    private static final Set<Node> KNOWN_NODE_PREDICATES =
            Set.of(
                    RDF.type.asNode(),
                    Shacl.TARGET_CLASS,
                    Shacl.PROPERTY,
                    Shacl.IGNORED_PROPERTIES,
                    Shacl.DEACTIVATED,
                    CLOSED,
                    NAME,
                    DESCRIPTION,
                    MESSAGE,
                    SEVERITY);

    /** Everything the form models on a property shape. */
    private static final Set<Node> KNOWN_PROPERTY_PREDICATES =
            Set.of(
                    RDF.type.asNode(),
                    Shacl.PATH,
                    Shacl.DATATYPE,
                    Shacl.CLASS,
                    Shacl.NODE_KIND,
                    Shacl.MIN_COUNT,
                    Shacl.MAX_COUNT,
                    Shacl.IN,
                    Shacl.DEACTIVATED,
                    PATTERN,
                    NAME,
                    DESCRIPTION,
                    MESSAGE,
                    SEVERITY,
                    ORDER,
                    GROUP);

    private ShapeModelReader() {}

    /** Every node shape the document declares under its own IRI, in IRI order. */
    static List<NodeShapeModel> read(Graph graph) {
        var shapes = new ArrayList<NodeShapeModel>();
        graph.stream(Node.ANY, RDF.type.asNode(), NODE_SHAPE)
                .map(triple -> triple.getSubject())
                .filter(Node::isURI)
                .distinct()
                .sorted(Comparator.comparing(Node::getURI))
                .forEach(shape -> shapes.add(readShape(graph, shape)));
        return shapes;
    }

    private static NodeShapeModel readShape(Graph graph, Node shape) {
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
                .editable(unsupported.isEmpty())
                .build();
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

    private static List<String> unsupportedPredicates(Graph graph, Node subject, Set<Node> known) {
        return graph.stream(subject, Node.ANY, Node.ANY)
                .map(triple -> triple.getPredicate())
                .filter(predicate -> !known.contains(predicate))
                .map(Node::getURI)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
        try {
            return lexical == null ? null : Integer.valueOf(lexical.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Graph graph, Node subject, Node predicate) {
        var lexical = string(graph, subject, predicate);
        return lexical == null ? null : Boolean.valueOf("true".equalsIgnoreCase(lexical.trim()));
    }
}
