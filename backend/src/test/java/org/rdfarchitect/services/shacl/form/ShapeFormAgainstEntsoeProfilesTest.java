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

import org.junit.jupiter.api.Test;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                .anySatisfy(shape -> assertThat(shape.getTargetClass()).isNotNull());
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
    void aShapeWithAnEmbeddedQueryIsShownButNeverOfferedForWriting() {
        // Official files use sh:sparql heavily. The form must show those shapes without ever
        // letting an edit drop the query.
        var shapes = service.parse(read()).getShapes();

        var withQuery =
                shapes.stream().filter(shape -> !shape.getEditable()).findFirst().orElseThrow();
        assertThat(withQuery.getUnsupported()).isNotEmpty();
        assertThat(shapes.stream().filter(NodeShapeModel::getEditable).toList()).isNotEmpty();
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

    @Test
    void everyShapeInTheFileSurvivesAReadWriteRoundTrip() {
        var original = read();
        var editable =
                service.parse(original).getShapes().stream()
                        .filter(NodeShapeModel::getEditable)
                        .toList();
        assertThat(editable).isNotEmpty();

        var turtle = original;
        for (NodeShapeModel shape : editable) {
            turtle = service.apply(request(turtle, shape)).getTurtle();
        }

        var after = service.parse(turtle);
        assertThat(after.getParseError()).isNull();
        assertThat(after.getShapes()).hasSameSizeAs(service.parse(original).getShapes());
        assertThat(List.copyOf(after.getShapes()))
                .filteredOn(NodeShapeModel::getEditable)
                .extracting(NodeShapeModel::getTargetClass)
                .containsExactlyElementsOf(
                        editable.stream().map(NodeShapeModel::getTargetClass).toList());
    }
}
