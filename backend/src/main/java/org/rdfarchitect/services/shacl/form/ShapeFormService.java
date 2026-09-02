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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;

import java.util.List;

/**
 * The form view of a constraints document, and the edits made through it.
 *
 * <p>Resolves the tension between two things this feature wants at once: the document's verbatim
 * text is the source of truth, and people who do not read Turtle must be able to change it. An edit
 * therefore changes the clauses it was made on — one field, one clause — and copies every other
 * character of the file through untouched, comments and clause order included. What the form has no
 * field for is not in its way: it stays as written and is reported so the form can say so.
 */
public class ShapeFormService implements ShapeFormUseCase {

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
        var source = ShapeSource.of(text, parsed.graph().getPrefixMapping());
        return ShapesForm.builder().shapes(ShapeModelReader.read(parsed.graph(), source)).build();
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
        var shape = request.getShape();
        if (shape == null || shape.getIri() == null || shape.getIri().isBlank()) {
            throw new ResourceConflictException("A shape needs an IRI before it can be written.");
        }
        assertRulesNameAProperty(shape);

        var source = ShapeSource.of(turtle, prefixes);
        var stored =
                ShapeModelReader.read(parsed.graph(), source).stream()
                        .filter(known -> shape.getIri().equals(known.getIri()))
                        .findFirst();
        if (stored.isEmpty()) {
            return append(
                    turtle,
                    shape,
                    parsed.graph()
                            .contains(NodeFactory.createURI(shape.getIri()), Node.ANY, Node.ANY),
                    prefixes);
        }
        // Judged against the document as stored, not against what was posted: a request claiming a
        // shape is editable is exactly the request not to trust.
        if (!Boolean.TRUE.equals(stored.get().getEditable())) {
            throw new ResourceConflictException(
                    stored.get().getReadOnlyReason() != null
                            ? stored.get().getReadOnlyReason()
                            : "This shape cannot be written back from the form.");
        }
        var written = ShapeClauseWriter.rewrite(turtle, stored.get(), shape, prefixes);
        return ShapeEditResult.builder()
                .turtle(written.turtle())
                .warnings(written.warnings())
                .build();
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
        if (shape.getProperties() == null) {
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
     * Adds a shape the document does not hold yet.
     *
     * <p>The ordinary case is a shape added through the form, which is written out in full and
     * appended. The other one is a subject the document says something about in a form the reader
     * does not read as a shape, where appending would define it a second time.
     */
    private ShapeEditResult append(
            String turtle, NodeShapeModel shape, boolean alreadyMentioned, PrefixMapping prefixes) {
        if (alreadyMentioned) {
            throw new ResourceConflictException(
                    "The document already says something about this subject in a form the form"
                            + " view does not read as a shape, so writing it here would define it"
                            + " twice. Edit it in the Turtle view.");
        }
        var statement = ShapeModelWriter.write(shape, prefixes);
        var separator =
                turtle.isEmpty() || turtle.endsWith("\n\n")
                        ? ""
                        : turtle.endsWith("\n") ? "\n" : "\n\n";
        return ShapeEditResult.builder()
                .turtle(turtle + separator + statement + "\n")
                .warnings(List.of())
                .build();
    }

    /**
     * Removes a shape, however many statements it is written as.
     *
     * <p>All of them, unlike an edit: deleting a shape is a request about the shape, not about one
     * statement, and leaving the others behind would leave half a shape in the document. Removed
     * from the back so the earlier statements' offsets still hold.
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
}
