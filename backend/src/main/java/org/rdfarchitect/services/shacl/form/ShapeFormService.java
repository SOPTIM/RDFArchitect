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
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeSplit;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The form view of a constraints document, and the edits made through it.
 *
 * <p>Resolves the tension between two things this feature wants at once: the document's verbatim
 * text is the source of truth, and people who do not read Turtle must be able to change it. An edit
 * therefore changes the clauses it was made on — one field, one clause — and copies every other
 * character of the file through untouched, comments and clause order included. What the form has no
 * field for is not in its way: it stays as written and is reported so the form can say so.
 *
 * <p>Every result is parsed and read back before it is returned, and refused if it does not say
 * what the form asked for or says anything else differently. The writer is text surgery guided by a
 * scanner; the check is what turns a construct the scanner misreads into a refusal rather than a
 * damaged file.
 */
public class ShapeFormService implements ShapeFormUseCase {

    private static final String STALE =
            "The document changed since the form was read. Reload the form and make the change"
                    + " again.";

    @Override
    public ShapesForm parse(String turtle) {
        var text = turtle == null ? "" : turtle;
        var parsed = ShapesTurtleParser.parse(text);
        if (parsed.failed()) {
            // The form has nothing to show for text that does not parse; the editor shows why.
            return ShapesForm.builder()
                    .shapes(List.of())
                    .parseError(parsed.findings().isEmpty() ? null : parsed.findings().get(0))
                    .build();
        }
        var source = sourceOf(text, parsed.graph());
        var shapes = ShapeModelReader.read(parsed.graph(), source);
        return ShapesForm.builder()
                .shapes(shapes.nodeShapes())
                .propertyShapes(shapes.propertyShapes())
                .build();
    }

    private static ShapeSource sourceOf(String turtle, Graph graph) {
        return ShapeSource.of(
                turtle, ShapeBlockLocator.documentPrefixes(turtle, graph.getPrefixMapping()));
    }

    @Override
    public ShapeEditResult apply(ShapeEditRequest request) {
        var turtle = request.getTurtle() == null ? "" : request.getTurtle();
        // The edit is a diff between the model and the text. Against text the model was not read
        // from, it reverts whatever changed in between.
        if (request.getBaseTurtle() != null && !request.getBaseTurtle().equals(turtle)) {
            throw new ResourceConflictException(STALE);
        }
        var parsed = ShapesTurtleParser.parse(turtle);
        if (parsed.failed()) {
            throw new ResourceConflictException(
                    "The document cannot be edited as a form while its Turtle does not parse.");
        }
        var graph = parsed.graph();
        var prefixes = ShapeBlockLocator.documentPrefixes(turtle, graph.getPrefixMapping());

        if (request.getRemoveShapeIri() != null) {
            return remove(turtle, graph, request.getRemoveShapeIri(), prefixes);
        }
        if (request.getPropertyShape() != null) {
            return applyRule(
                    turtle, graph, request.getPropertyShape(), request.getSplit(), prefixes);
        }
        var shape = request.getShape();
        if (shape == null || shape.getIri() == null || shape.getIri().isBlank()) {
            throw new ResourceConflictException("A shape needs an IRI before it can be written.");
        }
        assertRulesNameAProperty(shape);

        var source = ShapeSource.of(turtle, prefixes);
        var stored =
                ShapeModelReader.read(graph, source).nodeShapes().stream()
                        .filter(known -> shape.getIri().equals(known.getIri()))
                        .findFirst();
        if (stored.isEmpty()) {
            var written =
                    append(
                            turtle,
                            shape,
                            graph.contains(
                                    NodeFactory.createURI(shape.getIri()), Node.ANY, Node.ANY),
                            prefixes);
            assertShapeWritten(graph, written, shape);
            return ShapeEditResult.builder().turtle(written).warnings(List.of()).build();
        }
        // Judged against the document as stored, not against what was posted: a request claiming a
        // shape is editable is exactly the request not to trust.
        if (!Boolean.TRUE.equals(stored.get().getEditable())) {
            throw new ResourceConflictException(
                    stored.get().getReadOnlyReason() != null
                            ? stored.get().getReadOnlyReason()
                            : "This shape cannot be written back from the form.");
        }
        if (request.getBaseTurtle() == null) {
            assertRulesStillInPlace(stored.get(), shape);
        }
        var written = ShapeClauseWriter.rewrite(turtle, stored.get(), shape, prefixes);
        // Nothing written is nothing to check, which is what every unchanged save is.
        if (!written.turtle().equals(turtle)) {
            var after = assertShapeWritten(graph, written.turtle(), shape);
            if (!removesARule(stored.get(), shape)) {
                EditCheck.assertKeepsUnmodelled(graph, after, shape.getIri());
            }
        }
        return ShapeEditResult.builder()
                .turtle(written.turtle())
                .warnings(written.warnings())
                .build();
    }

    /** The graph of {@code written}, once it is known to say {@code shape} and nothing else new. */
    private static Graph assertShapeWritten(Graph before, String written, NodeShapeModel shape) {
        var after = EditCheck.parsed(written, before, List.of(shape.getIri()));
        var reread =
                ShapeModelReader.readNodeShape(after, sourceOf(written, after), shape.getIri())
                        .orElseThrow(() -> new ResourceConflictException(LOST));
        EditCheck.assertReadsAs(shape, reread);
        return after;
    }

    private static final String LOST =
            "The edit would have left the document without the shape it was made on. It was not"
                    + " applied. Make the change in the Turtle view.";

    private static boolean removesARule(NodeShapeModel stored, NodeShapeModel incoming) {
        if (incoming.getProperties() == null) {
            return false;
        }
        var kept = new HashSet<Integer>();
        incoming.getProperties().forEach(rule -> kept.add(rule.getSourceIndex()));
        return stored.getProperties().stream()
                .anyMatch(rule -> !kept.contains(rule.getSourceIndex()));
    }

    /**
     * Refuses rules the document now writes somewhere else than the model was read from.
     *
     * <p>A rule is addressed by its position, so a model read before two rules were swapped in the
     * Turtle view would write one rule's edit into the other — and a clause the form has no field
     * for, such as {@code sh:lessThan}, would end up on the wrong property. Where the client does
     * not say which text it read, the one tell is a rule arriving at a position whose rule is about
     * another property that the shape also writes elsewhere. A path edited to a property no other
     * rule of the shape states is an ordinary edit and is not refused.
     */
    private static void assertRulesStillInPlace(NodeShapeModel stored, NodeShapeModel incoming) {
        if (incoming.getProperties() == null) {
            return;
        }
        var byIndex = new HashMap<Integer, PropertyShapeModel>();
        var indicesByPath = new HashMap<String, Set<Integer>>();
        for (PropertyShapeModel rule : stored.getProperties()) {
            byIndex.put(rule.getSourceIndex(), rule);
            if (rule.getPath() != null) {
                indicesByPath
                        .computeIfAbsent(rule.getPath(), ignored -> new HashSet<>())
                        .add(rule.getSourceIndex());
            }
        }
        for (PropertyShapeModel rule : incoming.getProperties()) {
            var was = rule.getSourceIndex() == null ? null : byIndex.get(rule.getSourceIndex());
            if (was == null) {
                continue;
            }
            var moved =
                    !Objects.equals(was.getIri(), rule.getIri())
                            || (was.getPath() != null
                                    && rule.getPath() != null
                                    && !was.getPath().equals(rule.getPath())
                                    && indicesByPath.getOrDefault(rule.getPath(), Set.of()).stream()
                                            .anyMatch(
                                                    index -> !index.equals(rule.getSourceIndex())));
            if (moved) {
                throw new ResourceConflictException(STALE);
            }
        }
    }

    /**
     * Changes a rule the document writes as a shape of its own, or gives one shape a copy of it.
     *
     * <p>Its own request rather than part of the shape that references it, because it is its own
     * decision: a named rule in a {@code -Con-Simple-} profile carries the cardinality of dozens of
     * classes, and the form has to say so — and offer the copy — before the change reaches them
     * all. A split is the same edit with the copying done first, so either answer to that question
     * is one round trip.
     */
    private ShapeEditResult applyRule(
            String turtle,
            Graph graph,
            PropertyShapeModel rule,
            PropertyShapeSplit split,
            PrefixMapping prefixes) {
        if (rule.getIri() == null || rule.getIri().isBlank()) {
            throw new ResourceConflictException(
                    "Only a rule the document writes as a shape of its own can be changed on its"
                            + " own. A rule written inside a shape is changed with that shape.");
        }
        var source = ShapeSource.of(turtle, prefixes);
        var stored =
                ShapeModelReader.read(graph, source).propertyShapes().stream()
                        .filter(known -> rule.getIri().equals(known.getIri()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResourceConflictException(
                                                "The document does not write this rule as a shape"
                                                        + " of its own. Reload the document and"
                                                        + " make the change again."));
        // Judged against the document as stored, for the same reason a node shape is: a request
        // claiming a rule is editable is exactly the request not to trust.
        if (!Boolean.TRUE.equals(stored.getEditable())) {
            throw new ResourceConflictException(
                    stored.getReadOnlyReason() != null
                            ? stored.getReadOnlyReason()
                            : "This rule cannot be written back from the form.");
        }
        if (split == null) {
            var written = ShapeClauseWriter.rewriteRule(turtle, stored, rule, prefixes);
            if (!written.turtle().equals(turtle)) {
                var after =
                        assertRuleWritten(graph, written.turtle(), rule, List.of(rule.getIri()));
                EditCheck.assertKeepsUnmodelled(graph, after, rule.getIri());
            }
            return ShapeEditResult.builder()
                    .turtle(written.turtle())
                    .warnings(written.warnings())
                    .build();
        }
        var newIri = assertNameIsFree(graph, split, prefixes);
        var written =
                ShapeClauseWriter.splitRule(
                        turtle,
                        stored,
                        rule,
                        newIri,
                        split.getNodeShapeIri(),
                        assertSplitNamesARule(split),
                        prefixes);
        var copy = new PropertyShapeModel();
        copyFields(rule, copy);
        copy.setIri(newIri);
        assertRuleWritten(graph, written.turtle(), copy, List.of(newIri, split.getNodeShapeIri()));
        return ShapeEditResult.builder()
                .turtle(written.turtle())
                .warnings(written.warnings())
                .build();
    }

    private static Graph assertRuleWritten(
            Graph before, String written, PropertyShapeModel rule, List<String> touched) {
        var after = EditCheck.parsed(written, before, touched);
        var reread =
                ShapeModelReader.readRule(after, sourceOf(written, after), rule.getIri())
                        .orElseThrow(() -> new ResourceConflictException(LOST));
        EditCheck.assertReadsAs(rule, reread);
        return after;
    }

    /**
     * The fields {@link EditCheck} compares, so the posted rule is not renamed under its caller.
     */
    private static void copyFields(PropertyShapeModel from, PropertyShapeModel to) {
        to.setPath(from.getPath());
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setDataType(from.getDataType());
        to.setClassIri(from.getClassIri());
        to.setNodeKind(from.getNodeKind());
        to.setMinCount(from.getMinCount());
        to.setMaxCount(from.getMaxCount());
        to.setAllowedValues(from.getAllowedValues());
        to.setHasValue(from.getHasValue());
        to.setMinInclusive(from.getMinInclusive());
        to.setMaxInclusive(from.getMaxInclusive());
        to.setMinExclusive(from.getMinExclusive());
        to.setMaxExclusive(from.getMaxExclusive());
        to.setMinLength(from.getMinLength());
        to.setMaxLength(from.getMaxLength());
        to.setPattern(from.getPattern());
        to.setFlags(from.getFlags());
        to.setSeverity(from.getSeverity());
        to.setMessage(from.getMessage());
        to.setOrder(from.getOrder());
        to.setGroup(from.getGroup());
        to.setDeactivated(from.getDeactivated());
    }

    /**
     * The IRI a split's copy is written under, once it is known to be free.
     *
     * <p>Writing the copy under a name the document already uses — as a subject, or only as a
     * reference, which is how a {@code sh:node} target it has not written yet looks — would merge
     * it into whatever that name stands for, which is the one way this operation could lose a
     * constraint.
     */
    private static String assertNameIsFree(
            Graph graph, PropertyShapeSplit split, PrefixMapping prefixes) {
        var typed = split.getNewIri() == null ? "" : split.getNewIri().strip();
        if (typed.isEmpty()) {
            throw new ResourceConflictException("The copy of a rule needs a name of its own.");
        }
        var iri = nameFor(typed, prefixes);
        if (iri == null || !isWritable(iri)) {
            throw new ResourceConflictException(
                    "\""
                            + typed
                            + "\" is not a name this document can write. Use a prefixed name"
                            + " the document binds, or an absolute IRI.");
        }
        var node = NodeFactory.createURI(iri);
        if (graph.contains(node, Node.ANY, Node.ANY)
                || graph.contains(Node.ANY, node, Node.ANY)
                || graph.contains(Node.ANY, Node.ANY, node)) {
            throw new ResourceConflictException(
                    "The document already says something about \""
                            + typed
                            + "\". Give the copy a name of its own.");
        }
        return iri;
    }

    /**
     * The IRI a typed name stands for: a prefixed name through the document's prefixes, or an
     * absolute IRI as it is.
     *
     * <p>{@code ex:Copy} and {@code urn:x:y} have the same shape, so a name is taken as absolute
     * only where it cannot be a prefixed name — it has a hierarchical part, or is a URN.
     */
    private static String nameFor(String typed, PrefixMapping prefixes) {
        if (typed.startsWith("<") && typed.endsWith(">")) {
            return typed.substring(1, typed.length() - 1);
        }
        if (typed.contains("//") || typed.regionMatches(true, 0, "urn:", 0, 4)) {
            return typed;
        }
        return ShapeBlockLocator.expand(typed, prefixes);
    }

    /**
     * Whether an IRI can be written between angle brackets and read back as itself.
     *
     * <p>The characters Turtle forbids inside an {@code IRIREF} matter here rather than in general:
     * a name carrying one of them would be written as {@code <a>b>} and the document would stop
     * parsing on text nobody could see was wrong.
     */
    private static boolean isWritable(String iri) {
        return iri.matches("[A-Za-z][A-Za-z0-9+.\\-]*:[^\\s<>\"{}|^\\\\`]+");
    }

    private static int assertSplitNamesARule(PropertyShapeSplit split) {
        if (split.getNodeShapeIri() == null
                || split.getNodeShapeIri().isBlank()
                || split.getSourceIndex() == null) {
            throw new ResourceConflictException(
                    "A copy of a rule has to say which shape is to use it instead.");
        }
        return split.getSourceIndex();
    }

    /**
     * Refuses a rule that names no property.
     *
     * <p>There is nothing to write for such a rule — {@code sh:path} is what a property constraint
     * is about — and the writer used to leave it out, which is the worst of the three options: the
     * edit was reported as applied and the rule was gone from the card on the next read. The form
     * now keeps a rule like this as a draft and does not send it, so a request carrying one is a
     * client that has got ahead of itself, and it is told so.
     *
     * <p>A rule whose path is an expression rather than a property is not one of these: it names no
     * property in the model because the form cannot show one, but the document writes it a path all
     * the same, and it arrives as a clause the form keeps as written.
     */
    private static void assertRulesNameAProperty(NodeShapeModel shape) {
        if (shape.getProperties() == null) {
            return;
        }
        var unnamed =
                shape.getProperties().stream()
                        .filter(property -> property.getIri() == null)
                        .filter(property -> isBlank(property.getPath()))
                        .anyMatch(property -> !keepsAPath(property));
        if (unnamed) {
            throw new ResourceConflictException(
                    "A rule has to say which property it is about before it can be written."
                            + " Pick a property, or remove the rule.");
        }
    }

    /** Whether the document writes this rule a path the form shows but does not model. */
    private static boolean keepsAPath(PropertyShapeModel rule) {
        return rule.getRetained() != null
                && rule.getRetained().stream().anyMatch(clause -> "path".equals(clause.getField()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Adds a shape the document does not hold yet.
     *
     * <p>The ordinary case is a shape added through the form, which is written out in full and
     * appended. The other one is a subject the document says something about in a form the reader
     * does not read as a shape, where appending would define it a second time.
     */
    private static String append(
            String turtle, NodeShapeModel shape, boolean alreadyMentioned, PrefixMapping prefixes) {
        if (alreadyMentioned) {
            throw new ResourceConflictException(
                    "The document already says something about this subject in a form the form"
                            + " view does not read as a shape, so writing it here would define it"
                            + " twice. Edit it in the Turtle view.");
        }
        var eol = ShapeClauseWriter.lineEnding(turtle);
        var statement =
                ShapeModelWriter.write(
                        shape, ShapeBlockLocator.prefixesBefore(turtle, turtle.length()));
        return turtle
                + ShapeClauseWriter.separatorAtEnd(turtle)
                + statement.replace("\n", eol)
                + eol;
    }

    // -------------------------------------------------------------------------
    // Removing
    // -------------------------------------------------------------------------

    /**
     * Removes a shape, however many statements it is written as.
     *
     * <p>All of them, unlike an edit: deleting a shape is a request about the shape, not about one
     * statement, and leaving the others behind would leave half a shape in the document.
     *
     * <p>A rule written as a shape of its own that only this shape used goes with it, since nothing
     * could reach it any more and the form offers no other way to find it. What still refers to the
     * removed shape is left alone — the document is the user's — but named in a warning: a {@code
     * sh:node} pointing at a shape that is not there constrains nothing, which is a rule quietly
     * weakened rather than an error anybody would see.
     */
    private ShapeEditResult remove(String turtle, Graph graph, String iri, PrefixMapping prefixes) {
        var existing = ShapeBlockLocator.locateAll(turtle, iri, prefixes);
        if (existing.isEmpty()) {
            return ShapeEditResult.builder().turtle(turtle).warnings(List.of()).build();
        }
        var removed = new LinkedHashSet<>(List.of(iri));
        var statements = new ArrayList<>(existing);
        var remaining = cut(turtle, statements);
        var after = EditCheck.parsed(remaining, graph, removed);

        var warnings = new ArrayList<String>();
        for (String rule : rulesUsedBy(graph, iri)) {
            if (isReferenced(after, rule) || !isOnlyARule(graph, rule)) {
                continue;
            }
            var written = ShapeBlockLocator.locateAll(turtle, rule, prefixes);
            if (written.isEmpty()) {
                continue;
            }
            removed.add(rule);
            statements.addAll(written);
            warnings.add(
                    ShapeModelWriter.term(rule, prefixes)
                            + " was removed as well: it is a rule written as a shape of its own,"
                            + " and no other shape used it.");
        }
        if (removed.size() > 1) {
            remaining = cut(turtle, statements);
            after = EditCheck.parsed(remaining, graph, removed);
        }

        var referrers = referrers(after, NodeFactory.createURI(iri));
        if (!referrers.isEmpty()) {
            warnings.add(danglingWarning(iri, referrers, prefixes));
        }
        return ShapeEditResult.builder().turtle(remaining).warnings(List.copyOf(warnings)).build();
    }

    /** The document without {@code statements}, removed from the back so offsets still hold. */
    private static String cut(String turtle, List<ShapeBlockLocator.Statement> statements) {
        var ordered = new ArrayList<>(statements);
        ordered.sort(Comparator.comparingInt(ShapeBlockLocator.Statement::start).reversed());
        var remaining = turtle;
        for (ShapeBlockLocator.Statement statement : ordered) {
            // Take the blank line the statement left behind with it, so removing shapes one by one
            // does not slowly fill the document with gaps. What preceded the statement is kept, so
            // the shape that follows keeps the separation the removed one had in front of it.
            var after = remaining.substring(statement.end()).stripLeading();
            remaining = remaining.substring(0, statement.start()) + after;
        }
        return remaining;
    }

    /** The named rules {@code shape} states {@code sh:property} on. */
    private static List<String> rulesUsedBy(Graph graph, String shape) {
        return graph.stream(NodeFactory.createURI(shape), Shacl.PROPERTY, Node.ANY)
                .map(Triple::getObject)
                .filter(Node::isURI)
                .map(Node::getURI)
                .distinct()
                .toList();
    }

    private static boolean isReferenced(Graph graph, String iri) {
        return graph.contains(Node.ANY, Node.ANY, NodeFactory.createURI(iri));
    }

    /** A property shape and nothing else: removing it cannot take a node shape with it. */
    private static boolean isOnlyARule(Graph graph, String iri) {
        var node = NodeFactory.createURI(iri);
        return !graph.contains(node, RDF.type.asNode(), ShapeModelReader.NODE_SHAPE)
                && !graph.contains(node, Shacl.TARGET_CLASS, Node.ANY)
                && !graph.contains(node, Shacl.PROPERTY, Node.ANY);
    }

    /**
     * Which subjects still refer to {@code target}, and through which predicate.
     *
     * <p>A reference inside a list or a blank node — {@code sh:or ( ex:A ex:B )} — is reported
     * against the named subject that states it, which is what the user can find in the document.
     */
    private static Map<String, String> referrers(Graph graph, Node target) {
        var referrers = new LinkedHashMap<String, String>();
        graph.stream(Node.ANY, Node.ANY, target)
                .forEach(
                        triple -> {
                            var owner = ownerOf(graph, triple);
                            if (owner != null) {
                                referrers.putIfAbsent(
                                        owner.getSubject().getURI(), owner.getPredicate().getURI());
                            }
                        });
        return referrers;
    }

    /** The triple a named subject states that leads, through blank nodes, to {@code triple}. */
    private static Triple ownerOf(Graph graph, Triple triple) {
        var current = triple;
        var seen = new HashSet<Node>();
        while (current.getSubject().isBlank()) {
            if (!seen.add(current.getSubject())) {
                return null;
            }
            var subject = current.getSubject();
            current = graph.stream(Node.ANY, Node.ANY, subject).findFirst().orElse(null);
            if (current == null) {
                return null;
            }
        }
        return current.getSubject().isURI() ? current : null;
    }

    private static final int NAMED = 10;

    private static String danglingWarning(
            String iri, Map<String, String> referrers, PrefixMapping prefixes) {
        var named =
                referrers.entrySet().stream()
                        .limit(NAMED)
                        .map(
                                entry ->
                                        ShapeModelWriter.term(entry.getKey(), prefixes)
                                                + " ("
                                                + ShapeModelWriter.term(entry.getValue(), prefixes)
                                                + ")")
                        .toList();
        var more = referrers.size() > NAMED ? " and " + (referrers.size() - NAMED) + " more" : "";
        return String.join(", ", named)
                + more
                + (referrers.size() == 1 ? " still refers" : " still refer")
                + " to "
                + ShapeModelWriter.term(iri, prefixes)
                + ", which is no longer in the document. A reference to a shape that is not there"
                + " constrains nothing, so what it said no longer applies.";
    }
}
