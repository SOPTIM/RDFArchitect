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

import de.soptim.opencgmes.cimvocabcheck.core.shacl.Shacl;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Contradictions between the shapes a graph holds, across all of its enabled documents.
 *
 * <h2>Why these are reported rather than resolved</h2>
 *
 * <p>SHACL is conjunctive and has no precedence: two shapes on one focus node both apply, and
 * adding a shape can only make validation stricter. So there is no correct way to let one document
 * override another — a rule like "the later document wins" would make RDFArchitect disagree with
 * the file it exports and with every real engine, and a constraint the user believed overridden
 * would still fail their data. Contradictions are therefore surfaced here and left in force.
 *
 * <p>Most overlaps between documents are not contradictions at all: {@code minCount 1} in one file
 * and {@code datatype xsd:string} in another are complementary, and a repeated constraint is merely
 * redundant. Only two situations are genuinely wrong, and only those are reported: a combination no
 * data can satisfy, and one shape IRI defined in two documents.
 *
 * <h2>What is deliberately not looked at</h2>
 *
 * <p>Only property shapes attached directly through {@code sh:property} to a node shape with an
 * explicit {@code sh:targetClass} are compared, and only when {@code sh:path} is a plain IRI. A
 * property shape nested inside {@code sh:or}, {@code sh:not} or {@code sh:qualifiedValueShape}
 * applies conditionally, so two such shapes disagreeing is not a contradiction — and those shapes
 * are not reached by this rule, because the node shape enclosing them carries no target. Narrowing
 * this way trades finding every contradiction for never inventing one.
 */
final class ShapesConflictAnalyzer {

    static final String DUPLICATE_SHAPE_CODE = "DUPLICATE_SHAPE_IRI";
    static final String UNSATISFIABLE_CARDINALITY_CODE = "UNSATISFIABLE_CARDINALITY";
    static final String CONFLICTING_DATATYPE_CODE = "CONFLICTING_DATATYPE";

    private static final Node NODE_SHAPE = NodeFactory.createURI(Shacl.NS + "NodeShape");
    private static final Node PROPERTY_SHAPE = NodeFactory.createURI(Shacl.NS + "PropertyShape");

    /** One document as this analyzer needs to see it. */
    record Document(UUID id, String name, Graph graph, String rawText) {}

    private ShapesConflictAnalyzer() {}

    /**
     * Findings per document id. A contradiction is reported against every document taking part in
     * it, so the user sees it wherever they happen to be looking.
     */
    static Map<UUID, List<ShapesValidationFinding>> analyze(List<Document> documents) {
        Map<UUID, List<ShapesValidationFinding>> findings = new LinkedHashMap<>();
        documents.forEach(document -> findings.put(document.id(), new ArrayList<>()));
        if (documents.size() > 1) {
            reportDuplicateShapeIris(documents, findings);
        }
        reportUnsatisfiableProperties(documents, findings);
        return findings;
    }

    // -------------------------------------------------------------------------
    // One shape IRI, two documents
    // -------------------------------------------------------------------------

    private static void reportDuplicateShapeIris(
            List<Document> documents, Map<UUID, List<ShapesValidationFinding>> findings) {
        Map<Node, List<Document>> owners = new LinkedHashMap<>();
        for (Document document : documents) {
            for (Node shape : declaredShapes(document.graph())) {
                owners.computeIfAbsent(shape, key -> new ArrayList<>()).add(document);
            }
        }
        owners.forEach(
                (shape, holders) -> {
                    if (holders.size() < 2) {
                        return;
                    }
                    var names = holders.stream().map(Document::name).toList();
                    var template =
                            "Shape <%s> is also defined in %s. Both definitions apply, so neither"
                                    + " replaces the other.";
                    holders.forEach(
                            document -> {
                                var message =
                                        template.formatted(
                                                shape.getURI(),
                                                String.join(", ", others(names, document.name())));
                                findings.get(document.id())
                                        .add(
                                                finding(
                                                        DUPLICATE_SHAPE_CODE,
                                                        message,
                                                        shape,
                                                        document,
                                                        null));
                            });
                });
    }

    /** URI subjects the document explicitly types as a shape; implicit shapes are not compared. */
    private static LinkedHashSet<Node> declaredShapes(Graph graph) {
        var shapes = new LinkedHashSet<Node>();
        for (Node type : List.of(NODE_SHAPE, PROPERTY_SHAPE)) {
            var it = graph.find(Node.ANY, RDF.type.asNode(), type);
            while (it.hasNext()) {
                var subject = it.next().getSubject();
                if (subject.isURI()) {
                    shapes.add(subject);
                }
            }
        }
        return shapes;
    }

    private static List<String> others(List<String> names, String self) {
        return names.stream().filter(name -> !name.equals(self)).toList();
    }

    // -------------------------------------------------------------------------
    // Constraints no data can satisfy
    // -------------------------------------------------------------------------

    /** A constraint on one path of one target class, and where it was written. */
    private record PropertyConstraint(
            Document document, Node targetClass, Node path, Node shape, Node value) {}

    private static void reportUnsatisfiableProperties(
            List<Document> documents, Map<UUID, List<ShapesValidationFinding>> findings) {
        Map<PathKey, List<PropertyConstraint>> minCounts = new LinkedHashMap<>();
        Map<PathKey, List<PropertyConstraint>> maxCounts = new LinkedHashMap<>();
        Map<PathKey, List<PropertyConstraint>> datatypes = new LinkedHashMap<>();

        for (Document document : documents) {
            collect(document, minCounts, maxCounts, datatypes);
        }

        minCounts.forEach(
                (key, mins) -> {
                    var maxes = maxCounts.get(key);
                    if (maxes == null) {
                        return;
                    }
                    var strictestMin =
                            mins.stream().max(ShapesConflictAnalyzer::byIntValue).orElseThrow();
                    var strictestMax =
                            maxes.stream().min(ShapesConflictAnalyzer::byIntValue).orElseThrow();
                    int min = intValue(strictestMin.value()).orElse(Integer.MIN_VALUE);
                    int max = intValue(strictestMax.value()).orElse(Integer.MAX_VALUE);
                    if (min <= max) {
                        return;
                    }
                    var template =
                            "No value can satisfy both sh:minCount %d (%s) and sh:maxCount %d (%s)"
                                    + " for %s on %s.";
                    var message =
                            template.formatted(
                                    min,
                                    strictestMin.document().name(),
                                    max,
                                    strictestMax.document().name(),
                                    shortForm(key.path()),
                                    shortForm(key.targetClass()));
                    addTo(findings, UNSATISFIABLE_CARDINALITY_CODE, message, strictestMin);
                    addTo(findings, UNSATISFIABLE_CARDINALITY_CODE, message, strictestMax);
                });

        datatypes.forEach(
                (key, declarations) -> {
                    var distinct =
                            declarations.stream()
                                    .map(PropertyConstraint::value)
                                    .distinct()
                                    .toList();
                    if (distinct.size() < 2) {
                        return;
                    }
                    var template =
                            "%s on %s is constrained to more than one sh:datatype (%s); every"
                                    + " constraint applies, so no value can satisfy them all.";
                    var message =
                            template.formatted(
                                    shortForm(key.path()),
                                    shortForm(key.targetClass()),
                                    String.join(
                                            ", ",
                                            distinct.stream()
                                                    .map(ShapesConflictAnalyzer::shortForm)
                                                    .toList()));
                    declarations.forEach(
                            declaration ->
                                    addTo(
                                            findings,
                                            CONFLICTING_DATATYPE_CODE,
                                            message,
                                            declaration));
                });
    }

    /** Target class plus path, which together identify what a set of constraints is about. */
    private record PathKey(Node targetClass, Node path) {}

    private static void collect(
            Document document,
            Map<PathKey, List<PropertyConstraint>> minCounts,
            Map<PathKey, List<PropertyConstraint>> maxCounts,
            Map<PathKey, List<PropertyConstraint>> datatypes) {
        var graph = document.graph();
        var targeted = graph.find(Node.ANY, Shacl.TARGET_CLASS, Node.ANY);
        while (targeted.hasNext()) {
            var triple = targeted.next();
            var nodeShape = triple.getSubject();
            var targetClass = triple.getObject();
            if (!targetClass.isURI()) {
                continue;
            }
            var properties = graph.find(nodeShape, Shacl.PROPERTY, Node.ANY);
            while (properties.hasNext()) {
                var propertyShape = properties.next().getObject();
                var path = singleObject(graph, propertyShape, Shacl.PATH);
                if (path == null || !path.isURI() || isDeactivated(graph, propertyShape)) {
                    continue;
                }
                var key = new PathKey(targetClass, path);
                add(
                        minCounts,
                        key,
                        document,
                        targetClass,
                        path,
                        nodeShape,
                        graph,
                        propertyShape,
                        Shacl.MIN_COUNT);
                add(
                        maxCounts,
                        key,
                        document,
                        targetClass,
                        path,
                        nodeShape,
                        graph,
                        propertyShape,
                        Shacl.MAX_COUNT);
                add(
                        datatypes,
                        key,
                        document,
                        targetClass,
                        path,
                        nodeShape,
                        graph,
                        propertyShape,
                        Shacl.DATATYPE);
            }
        }
    }

    private static void add(
            Map<PathKey, List<PropertyConstraint>> into,
            PathKey key,
            Document document,
            Node targetClass,
            Node path,
            Node nodeShape,
            Graph graph,
            Node propertyShape,
            Node predicate) {
        var value = singleObject(graph, propertyShape, predicate);
        if (value == null) {
            return;
        }
        // The node shape, not the property shape, anchors the finding: a property shape is usually
        // a blank node and so cannot be found in the source text.
        into.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new PropertyConstraint(document, targetClass, path, nodeShape, value));
    }

    private static boolean isDeactivated(Graph graph, Node shape) {
        var deactivated = singleObject(graph, shape, Shacl.DEACTIVATED);
        return deactivated != null
                && deactivated.isLiteral()
                && Boolean.TRUE.equals(deactivated.getLiteralValue());
    }

    private static Node singleObject(Graph graph, Node subject, Node predicate) {
        var it = graph.find(subject, predicate, Node.ANY);
        return it.hasNext() ? it.next().getObject() : null;
    }

    private static OptionalInt intValue(Node node) {
        if (node == null || !node.isLiteral()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(node.getLiteralLexicalForm().trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static int byIntValue(PropertyConstraint left, PropertyConstraint right) {
        return Integer.compare(
                intValue(left.value()).orElse(Integer.MIN_VALUE),
                intValue(right.value()).orElse(Integer.MIN_VALUE));
    }

    private static void addTo(
            Map<UUID, List<ShapesValidationFinding>> findings,
            String code,
            String message,
            PropertyConstraint constraint) {
        findings.get(constraint.document().id())
                .add(
                        finding(
                                code,
                                message,
                                constraint.path(),
                                constraint.document(),
                                constraint.shape()));
    }

    private static ShapesValidationFinding finding(
            String code, String message, Node term, Document document, Node hint) {
        var location = SourcePositions.locate(document.rawText(), document.graph(), term, hint);
        return ShapesValidationFinding.builder()
                .severity(ShapesValidationFinding.Severity.ERROR)
                .source(ShapesValidationFinding.Source.CONFLICT)
                .code(code)
                .message(message)
                .line(location.line())
                .column(location.column())
                .term(term != null && term.isURI() ? term.getURI() : null)
                .foundInProfiles(List.of())
                .build();
    }

    private static String shortForm(Node node) {
        if (node == null) {
            return "?";
        }
        return node.isURI() ? "<" + node.getURI() + ">" : node.toString();
    }
}
