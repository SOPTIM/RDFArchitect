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
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The form view of a constraints document, and the edits made through it.
 *
 * <p>Resolves the tension between two things this feature wants at once: the document's verbatim
 * text is the source of truth, and people who do not read Turtle must be able to change it. An edit
 * therefore rewrites exactly one statement — the shape that was edited — and copies the rest of the
 * file through untouched. A comment written inside that one shape is the only thing lost, and the
 * caller is told when that happens.
 */
public class ShapeFormService implements ShapeFormUseCase {

    @Override
    public ShapesForm parse(String turtle) {
        var parsed = ShapesTurtleParser.parse(turtle);
        if (parsed.failed()) {
            // The form has nothing to show for text that does not parse; the editor shows why.
            return ShapesForm.builder()
                    .shapes(List.of())
                    .parseError(parsed.findings().isEmpty() ? null : parsed.findings().get(0))
                    .build();
        }
        var shapes = ShapeModelReader.read(parsed.graph());
        var counts =
                ShapeBlockLocator.statementCountsBySubject(
                        turtle == null ? "" : turtle, parsed.graph().getPrefixMapping());
        shapes.forEach(shape -> markUnlocatable(shape, counts));
        return ShapesForm.builder().shapes(shapes).build();
    }

    /**
     * Locks a shape the writer would not be able to put back where it came from.
     *
     * <p>The reader works on the graph, which does not remember how the text was laid out, so this
     * is the one judgement only the text can make. Two cases, both of which used to be found out
     * the hard way: a subject written as several statements, where a rewrite of one of them
     * duplicates what the others say; and a subject the scanner cannot find at all — written
     * against a {@code @base}, say — where a rewrite is appended and the shape ends up defined
     * twice. Saying so on the card is the point: the alternative is an error after the edit.
     */
    private static void markUnlocatable(NodeShapeModel shape, Map<String, Integer> counts) {
        if (!Boolean.TRUE.equals(shape.getEditable())) {
            return;
        }
        int written = counts.getOrDefault(shape.getIri(), 0);
        if (written == 1) {
            return;
        }
        shape.setEditable(false);
        shape.setReadOnlyReason(
                written == 0
                        ? "This shape is not written as a statement of its own in the document, so"
                                + " the form cannot write it back without defining it a second"
                                + " time. Edit it in the Turtle view."
                        : "This shape is written as "
                                + written
                                + " separate statements. Rewriting one of them from the form would"
                                + " repeat what the others say, so the form leaves it alone. Edit"
                                + " it in the Turtle view, or write the shape as one statement.");
    }

    @Override
    public ShapeEditResult apply(ShapeEditRequest request) {
        var turtle = request.getTurtle() == null ? "" : request.getTurtle();
        var parsed = ShapesTurtleParser.parse(turtle);
        if (parsed.failed()) {
            throw new ResourceConflictException(
                    "The document cannot be edited as a form while its Turtle does not parse.");
        }
        var prefixes = parsed.graph().getPrefixMapping();

        if (request.getRemoveShapeIri() != null) {
            return remove(turtle, request.getRemoveShapeIri(), prefixes);
        }
        assertWritable(parsed.graph(), request.getShape());
        assertRulesNameAProperty(request.getShape());
        return write(turtle, request.getShape(), parsed.graph(), prefixes);
    }

    /**
     * Refuses a rule that names no property.
     *
     * <p>There is nothing to write for such a rule — {@code sh:path} is what a property constraint
     * is about — and the writer used to leave it out, which is the worst of the three options: the
     * edit was reported as applied and the rule was gone from the card on the next read. The form
     * now keeps a rule like this as a draft and does not send it, so a request carrying one is a
     * client that has got ahead of itself, and it is told so.
     */
    private static void assertRulesNameAProperty(NodeShapeModel shape) {
        if (shape == null || shape.getProperties() == null) {
            return;
        }
        var unnamed =
                shape.getProperties().stream()
                        .filter(property -> property.getIri() == null)
                        .anyMatch(property -> isBlank(property.getPath()));
        if (unnamed) {
            throw new ResourceConflictException(
                    "A rule has to say which property it is about before it can be written."
                            + " Pick a property, or remove the rule.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Refuses to rewrite a shape the form cannot represent.
     *
     * <p>The reader already decides this and the form already honours it, so reaching here means
     * the client asked for something its own UI does not offer. Checking again is cheap and the
     * failure it prevents is not: rewriting a shape carrying {@code sh:sparql} or a second {@code
     * sh:targetClass} would drop it, in the one place the whole feature promises not to.
     *
     * <p>Judged against the document as stored, not against what was posted — a request claiming a
     * shape is editable is exactly the request not to trust.
     */
    private static void assertWritable(Graph graph, NodeShapeModel shape) {
        if (shape == null || shape.getIri() == null) {
            return;
        }
        ShapeModelReader.read(graph).stream()
                .filter(stored -> shape.getIri().equals(stored.getIri()))
                .filter(stored -> !Boolean.TRUE.equals(stored.getEditable()))
                .findFirst()
                .ifPresent(
                        stored -> {
                            throw new ResourceConflictException(
                                    stored.getReadOnlyReason() != null
                                            ? stored.getReadOnlyReason()
                                            : "This shape cannot be written back from the form.");
                        });
    }

    private ShapeEditResult write(
            String turtle, NodeShapeModel shape, Graph graph, PrefixMapping prefixes) {
        if (shape == null || shape.getIri() == null || shape.getIri().isBlank()) {
            throw new ResourceConflictException("A shape needs an IRI before it can be written.");
        }
        var replacement = ShapeModelWriter.write(shape, prefixes);
        var existing = ShapeBlockLocator.locateAll(turtle, shape.getIri(), prefixes);
        if (existing.isEmpty()) {
            // Nothing to replace. Either the shape is new — the ordinary case, and it is appended —
            // or the document says something about that subject in a form the scanner cannot find,
            // in which case appending would define the shape twice.
            if (graph.contains(NodeFactory.createURI(shape.getIri()), Node.ANY, Node.ANY)) {
                throw new ResourceConflictException(
                        "This shape is not written as a statement of its own in the document, so"
                                + " the form cannot write it back without defining it a second"
                                + " time. Edit it in the Turtle view.");
            }
            return ShapeEditResult.builder()
                    .turtle(append(turtle, replacement))
                    .warnings(List.of())
                    .build();
        }
        if (existing.size() > 1) {
            // Rewriting one of them would repeat what the others say, and deleting the others
            // would delete text the user never edited. `parse` marks such a shape read-only, so
            // getting here means the client asked anyway.
            throw new ResourceConflictException(
                    "This shape is written as "
                            + existing.size()
                            + " separate statements. Rewriting one of them from the form would"
                            + " repeat what the others say, so the form leaves it alone. Edit it in"
                            + " the Turtle view, or write the shape as one statement.");
        }
        var statement = existing.get(0);
        var warnings = warningsFor(turtle.substring(statement.start(), statement.end()));
        return ShapeEditResult.builder()
                .turtle(ShapeBlockLocator.replace(turtle, statement, replacement))
                .warnings(warnings)
                .build();
    }

    /**
     * Removes a shape, however many statements it is written as.
     *
     * <p>All of them, unlike a rewrite: deleting a shape is a request about the shape, not about
     * one statement, and leaving the others behind would leave half a shape in the document.
     * Removed from the back so the earlier statements' offsets still hold.
     */
    private ShapeEditResult remove(String turtle, String iri, PrefixMapping prefixes) {
        var existing = ShapeBlockLocator.locateAll(turtle, iri, prefixes);
        if (existing.isEmpty()) {
            return ShapeEditResult.builder().turtle(turtle).warnings(List.of()).build();
        }
        var remaining = turtle;
        for (int i = existing.size() - 1; i >= 0; i--) {
            var statement = existing.get(i);
            // Take the blank line the statement left behind with it, so removing shapes one by one
            // does not slowly fill the document with gaps. What preceded the statement is kept, so
            // the shape that follows keeps the separation the removed one had in front of it.
            var after = remaining.substring(statement.end()).stripLeading();
            remaining = remaining.substring(0, statement.start()) + after;
        }
        return ShapeEditResult.builder().turtle(remaining).warnings(List.of()).build();
    }

    private static String append(String turtle, String replacement) {
        var separator =
                turtle.isEmpty() || turtle.endsWith("\n\n")
                        ? ""
                        : turtle.endsWith("\n") ? "\n" : "\n\n";
        return turtle + separator + replacement + "\n";
    }

    /** What the rewrite costs beyond the change itself. */
    private static List<String> warningsFor(String replaced) {
        var warnings = new ArrayList<String>();
        if (ShapeBlockLocator.containsComment(replaced)) {
            warnings.add(
                    "Comments written inside this shape were removed, because the shape was"
                            + " rewritten from the form. The rest of the document is unchanged.");
        }
        return List.copyOf(warnings);
    }
}
