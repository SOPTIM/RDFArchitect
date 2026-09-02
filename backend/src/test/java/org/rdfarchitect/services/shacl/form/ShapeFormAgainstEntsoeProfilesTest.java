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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.RetainedClause;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The promise the form view rests on, tested against the files it actually has to keep intact.
 *
 * <p>Official ENTSO-E constraints files carry a licence header, section comments and a deliberate
 * ordering that people expect to get back byte for byte. If a form edit reformatted them, nobody
 * could use the form on a real profile — so this is the test that says the feature works.
 */
class ShapeFormAgainstEntsoeProfilesTest {

    private static final String CONSTRAINTS =
            "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/SHACL/TTL/";

    /** A large official file with comments, embedded SPARQL and many shapes. */
    private static final String FILE = "61970-452_Equipment-AP-Con-Complex-SHACL.ttl";

    private final ShapeFormService service = new ShapeFormService();

    private static String read() {
        try {
            return Files.readString(Path.of(CONSTRAINTS, FILE));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read "
                            + FILE
                            + " — is the entsoe-application-profiles-library submodule initialised?",
                    e);
        }
    }

    private NodeShapeModel firstEditableShape(String turtle) {
        return service.parse(turtle).getShapes().stream()
                .filter(NodeShapeModel::getEditable)
                .filter(shape -> !shape.getProperties().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No editable shape in " + FILE));
    }

    @Test
    void anOfficialFileIsReadableAsAForm() {
        var form = service.parse(read());

        assertThat(form.getParseError()).isNull();
        assertThat(form.getShapes()).isNotEmpty();
        assertThat(form.getShapes())
                .anySatisfy(shape -> assertThat(shape.getTargetClasses()).isNotEmpty());
    }

    @Test
    void editingOneShapeLeavesEveryOtherByteOfTheFileUntouched() {
        var original = read();
        // Edited on the node shape itself: this file's rules are named property shapes, which a
        // form edit deliberately keeps as references rather than inlining.
        var shape = firstEditableShape(original);
        shape.setMessage("Checked by RDFArchitect");

        var edited = service.apply(request(original, shape)).getTurtle();

        // Everything before and after the edited statement has to survive character for character.
        var statement =
                ShapeBlockLocator.locate(
                                original,
                                shape.getIri(),
                                org.apache.jena.riot.RDFParser.fromString(
                                                original, org.apache.jena.riot.Lang.TURTLE)
                                        .toGraph()
                                        .getPrefixMapping())
                        .orElseThrow();
        assertThat(edited).startsWith(original.substring(0, statement.start()));
        assertThat(edited).endsWith(original.substring(statement.end()));
        assertThat(edited).contains("Checked by RDFArchitect");
        assertThat(edited).isNotEqualTo(original);
    }

    @Test
    void theEditedFileStillParsesAndKeepsItsOtherShapes() {
        var original = read();
        var before = service.parse(original).getShapes();
        var shape = firstEditableShape(original);
        shape.setMessage("Checked by RDFArchitect");

        var edited = service.apply(request(original, shape)).getTurtle();
        var after = service.parse(edited);

        assertThat(after.getParseError()).isNull();
        assertThat(after.getShapes()).hasSameSizeAs(before);
        assertThat(after.getShapes())
                .extracting(NodeShapeModel::getIri)
                .containsExactlyElementsOf(before.stream().map(NodeShapeModel::getIri).toList());
    }

    @Test
    void aShapeWithAnEmbeddedQueryIsEditableAndKeepsTheQuery() {
        // Official files use sh:sparql heavily, and it used to lock every shape whose rules carry
        // one. Now the query is a clause nobody touches, and the shape is edited around it.
        var original = read();
        var withQuery =
                service.parse(original).getShapes().stream()
                        .filter(NodeShapeModel::getEditable)
                        .filter(shape -> query(shape) != null)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No editable sh:sparql shape"));
        var query = query(withQuery);
        withQuery.setMessage("Checked by RDFArchitect");

        var edited = service.apply(request(original, withQuery)).getTurtle();

        assertThat(edited).contains(query);
        assertThat(edited).contains("Checked by RDFArchitect");
        assertThat(service.parse(edited).getParseError()).isNull();
    }

    /** The embedded query one of a shape's rules carries, as the file writes it. */
    private static String query(NodeShapeModel shape) {
        return shape.getProperties().stream()
                .flatMap(rule -> rule.getRetained().stream())
                .filter(clause -> clause.getPredicate().endsWith("#sparql"))
                .map(RetainedClause::getValue)
                .findFirst()
                .orElse(null);
    }

    @Test
    void repeatedNoOpEditsDoNotKeepChangingTheFile() {
        // A form that rewrote the shape differently every time would produce noise in every diff.
        var original = read();
        var shape = firstEditableShape(original);

        var once = service.apply(request(original, shape)).getTurtle();
        var twice = service.apply(request(once, firstEditableShape(once))).getTurtle();

        assertThat(twice).isEqualTo(once);
    }

    private static ShapeEditRequest request(String turtle, NodeShapeModel shape) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setShape(shape);
        return request;
    }

    /**
     * The whole official library, as the measure of how far the form reaches.
     *
     * <p>314 of 2959 before clause-preserving edits; 2721 after, because what the form can offer is
     * no longer decided by whether it understands every last thing a shape says. The 238 left over
     * are honest: 207 are shapes of the generated {@code -AllowedProperties} families, which write
     * the same rule twice, so there is no telling which of two identical {@code sh:property}
     * clauses an edit belongs in; 31 are subjects written as more than one statement. The floor is
     * asserted rather than the exact number, because a new ENTSO-E release moves it — but a change
     * that quietly locks shapes the form used to offer fails here.
     */
    @Test
    void theFormReachesMostOfTheWholeLibrary() throws IOException {
        var editable = 0;
        var shapes = 0;
        for (Path file : constraintsFiles()) {
            var form = service.parse(Files.readString(file));
            if (form.getParseError() != null) {
                continue;
            }
            shapes += form.getShapes().size();
            editable +=
                    (int)
                            form.getShapes().stream()
                                    .filter(shape -> Boolean.TRUE.equals(shape.getEditable()))
                                    .count();
        }

        assertThat(shapes).isGreaterThanOrEqualTo(2959);
        assertThat(editable).isGreaterThanOrEqualTo(2600);
    }

    /**
     * No shape is offered for editing that the writer cannot put an edit back into.
     *
     * <p>The invariant behind the split-subject refusal: a shape written as two statements gives
     * the writer no way to say which of them a new clause belongs in. If this ever fails, the form
     * is offering an edit it cannot place.
     */
    @Test
    void everyEditableShapeIsWrittenAsExactlyOneStatement() throws IOException {
        for (Path file : constraintsFiles()) {
            var turtle = Files.readString(file);
            var form = service.parse(turtle);
            if (form.getParseError() != null) {
                continue;
            }
            var prefixes = RDFParser.fromString(turtle, Lang.TURTLE).toGraph().getPrefixMapping();
            var source = ShapeSource.of(turtle, prefixes);
            assertThat(form.getShapes())
                    .filteredOn(shape -> Boolean.TRUE.equals(shape.getEditable()))
                    .allSatisfy(
                            shape ->
                                    assertThat(source.forSubject(shape.getIri()).statements())
                                            .describedAs(
                                                    "%s in %s", shape.getIri(), file.getFileName())
                                            .hasSize(1));
        }
    }

    /**
     * A no-op edit over every editable shape in the library leaves every file byte for byte.
     *
     * <p>The strongest form of the promise the form makes, and the one a user checks by looking at
     * the diff: applying a shape nobody changed is an ordinary event — the form applies whole
     * shapes, not fields — and it has to cost nothing. Cheap enough to run over all 104 files
     * because a no-op produces no text edits at all.
     */
    @Test
    void applyingEveryShapeUnchangedLeavesTheWholeLibraryUntouched() throws IOException {
        for (Path file : constraintsFiles()) {
            var original = Files.readString(file);
            var form = service.parse(original);
            if (form.getParseError() != null) {
                continue;
            }
            var turtle = original;
            for (NodeShapeModel shape : form.getShapes()) {
                if (Boolean.TRUE.equals(shape.getEditable())) {
                    turtle = service.apply(request(turtle, shape)).getTurtle();
                }
            }
            assertThat(turtle).describedAs("%s", file.getFileName()).isEqualTo(original);
        }
    }

    /**
     * The profile family the form could do nothing at all with, and the reason 3.2 exists.
     *
     * <p>Every rule in a {@code -Con-Simple-} file is a named property shape referenced from the
     * node shapes, and each of those rules carries something the form has no field for. Two of the
     * file's 145 shapes were editable; all 145 are now, because a referenced rule's own clauses are
     * no longer the referencing shape's problem.
     */
    @Test
    void theProfileFamilyTheFormCouldNotTouchIsNowEditableThroughout() throws IOException {
        var turtle =
                Files.readString(
                        Path.of(CONSTRAINTS, "61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl"));

        var shapes = service.parse(turtle).getShapes();

        assertThat(shapes).hasSizeGreaterThanOrEqualTo(145);
        assertThat(shapes).allSatisfy(shape -> assertThat(shape.getEditable()).isTrue());
    }

    /** Every official constraints file in the submodule, CGMES and the NC profiles alike. */
    private static List<Path> constraintsFiles() throws IOException {
        var roots =
                List.of(
                        Path.of(CONSTRAINTS),
                        Path.of(
                                "../external/entsoe-application-profiles-library/NCP/CurrentRelease/SHACL"));
        var files = new ArrayList<Path>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var listed = Files.list(root)) {
                listed.filter(path -> path.toString().endsWith(".ttl"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        assertThat(files)
                .describedAs("is the entsoe-application-profiles-library submodule initialised?")
                .isNotEmpty();
        return files;
    }

    @Test
    void everyShapeInTheFileSurvivesAnEditToEveryOneOfThem() {
        var original = read();
        var editable =
                service.parse(original).getShapes().stream()
                        .filter(NodeShapeModel::getEditable)
                        .toList();
        assertThat(editable).isNotEmpty();

        var turtle = original;
        for (NodeShapeModel shape : editable) {
            shape.setMessage("Checked by RDFArchitect");
            turtle = service.apply(request(turtle, shape)).getTurtle();
        }

        var after = service.parse(turtle);
        assertThat(after.getParseError()).isNull();
        assertThat(after.getShapes()).hasSameSizeAs(service.parse(original).getShapes());
        assertThat(List.copyOf(after.getShapes()))
                .filteredOn(NodeShapeModel::getEditable)
                .extracting(NodeShapeModel::getTargetClasses)
                .containsExactlyElementsOf(
                        editable.stream().map(NodeShapeModel::getTargetClasses).toList());
    }
}
