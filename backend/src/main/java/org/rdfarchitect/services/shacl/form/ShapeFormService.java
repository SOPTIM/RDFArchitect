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

import org.apache.jena.shared.PrefixMapping;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;

import java.util.ArrayList;
import java.util.List;

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
        return ShapesForm.builder().shapes(ShapeModelReader.read(parsed.graph())).build();
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
        return write(turtle, request.getShape(), prefixes);
    }

    private ShapeEditResult write(String turtle, NodeShapeModel shape, PrefixMapping prefixes) {
        if (shape == null || shape.getIri() == null || shape.getIri().isBlank()) {
            throw new ResourceConflictException("A shape needs an IRI before it can be written.");
        }
        var replacement = ShapeModelWriter.write(shape, prefixes);
        var existing = ShapeBlockLocator.locate(turtle, shape.getIri(), prefixes);
        if (existing.isEmpty()) {
            return ShapeEditResult.builder()
                    .turtle(append(turtle, replacement))
                    .warnings(List.of())
                    .build();
        }
        var statement = existing.get();
        var warnings = warningsFor(turtle.substring(statement.start(), statement.end()));
        return ShapeEditResult.builder()
                .turtle(ShapeBlockLocator.replace(turtle, statement, replacement))
                .warnings(warnings)
                .build();
    }

    private ShapeEditResult remove(String turtle, String iri, PrefixMapping prefixes) {
        var existing = ShapeBlockLocator.locate(turtle, iri, prefixes);
        if (existing.isEmpty()) {
            return ShapeEditResult.builder().turtle(turtle).warnings(List.of()).build();
        }
        var statement = existing.get();
        // Take the blank line the statement left behind with it, so removing shapes one by one
        // does not slowly fill the document with gaps.
        var after = turtle.substring(statement.end()).replaceFirst("^\\n\\n", "\n");
        return ShapeEditResult.builder()
                .turtle(turtle.substring(0, statement.start()) + after.stripLeading())
                .warnings(List.of())
                .build();
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
        if (replaced.contains("#")) {
            warnings.add(
                    "Comments written inside this shape were removed, because the shape was"
                            + " rewritten from the form. The rest of the document is unchanged.");
        }
        return List.copyOf(warnings);
    }
}
