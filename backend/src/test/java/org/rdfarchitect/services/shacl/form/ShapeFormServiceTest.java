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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.util.List;

/**
 * Reading a constraints document as shapes, and writing a form edit back into its text.
 *
 * <p>The behaviour these pin down is the one the whole form view rests on: an edit rewrites the
 * shape it edited and nothing else. Everything about comments, ordering and untouched neighbours is
 * a promise to the user, not an implementation detail.
 */
class ShapeFormServiceTest {

    private static final String SHAPES =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            # The line segment rules, as agreed with the TSO.
            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path     cim:ACLineSegment.r ;
                        sh:datatype xsd:float ;
                        sh:minCount 1 ;
                        sh:maxCount 1 ;
                    ] .

            ex:TerminalShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:Terminal .
            """;

    private final ShapeFormService service = new ShapeFormService();

    private static ShapeEditRequest edit(String turtle, NodeShapeModel shape) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setShape(shape);
        return request;
    }

    private NodeShapeModel shapeNamed(String turtle, String iri) {
        return service.parse(turtle).getShapes().stream()
                .filter(shape -> shape.getIri().equals(iri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shape " + iri));
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    @Test
    void readsShapesWithTheirPropertyConstraints() {
        var form = service.parse(SHAPES);

        assertThat(form.getShapes()).hasSize(2);
        var segment = shapeNamed(SHAPES, "http://example.org/shapes#ACLineSegmentShape");
        assertThat(segment.getTargetClass()).isEqualTo("http://iec.ch/TC57/CIM100#ACLineSegment");
        assertThat(segment.getEditable()).isTrue();
        assertThat(segment.getProperties())
                .singleElement()
                .satisfies(
                        property -> {
                            assertThat(property.getPath())
                                    .isEqualTo("http://iec.ch/TC57/CIM100#ACLineSegment.r");
                            assertThat(property.getDataType())
                                    .isEqualTo("http://www.w3.org/2001/XMLSchema#float");
                            assertThat(property.getMinCount()).isEqualTo(1);
                            assertThat(property.getMaxCount()).isEqualTo(1);
                        });
    }

    @Test
    void marksAShapeTheFormCannotFullyRepresentAsNotEditable() {
        // Writing this back from the form would silently drop the sh:sparql constraint.
        var withQuery =
                SHAPES.replace(
                        "sh:targetClass cim:Terminal .",
                        """
                        sh:targetClass cim:Terminal ;
                                sh:sparql [ sh:select \"""SELECT $this WHERE { }\""" ] .""");

        var shape = shapeNamed(withQuery, "http://example.org/shapes#TerminalShape");

        assertThat(shape.getEditable()).isFalse();
        assertThat(shape.getUnsupported()).contains("http://www.w3.org/ns/shacl#sparql");
    }

    @Test
    void reportsASyntaxErrorInsteadOfAnEmptyForm() {
        var form = service.parse("@prefix sh: <http://www.w3.org/ns/shacl#> .\nex:Broken a sh:X .");

        assertThat(form.getShapes()).isEmpty();
        assertThat(form.getParseError()).isNotNull();
        assertThat(form.getParseError().getLine()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    @Test
    void changingOneConstraintLeavesEveryOtherByteAlone() {
        var shape = shapeNamed(SHAPES, "http://example.org/shapes#ACLineSegmentShape");
        shape.getProperties().get(0).setMinCount(0);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle()).contains("sh:minCount 0");
        // The header, the comment and the neighbouring shape are untouched.
        assertThat(result.getTurtle())
                .contains("# The line segment rules, as agreed with the TSO.");
        assertThat(result.getTurtle())
                .contains(
                        """
                        ex:TerminalShape
                                a              sh:NodeShape ;
                                sh:targetClass cim:Terminal .""");
        assertThat(result.getTurtle()).startsWith("@prefix sh:   <http://www.w3.org/ns/shacl#> .");
    }

    @Test
    void writesTermsWithTheDocumentsOwnPrefixes() {
        var shape = shapeNamed(SHAPES, "http://example.org/shapes#TerminalShape");
        shape.setTargetClass("http://iec.ch/TC57/CIM100#ConnectivityNode");

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle()).contains("sh:targetClass cim:ConnectivityNode");
        assertThat(result.getTurtle())
                .doesNotContain("<http://iec.ch/TC57/CIM100#ConnectivityNode>");
    }

    @Test
    void fallsBackToAnAbsoluteIriRatherThanInventingAPrefix() {
        var shape = shapeNamed(SHAPES, "http://example.org/shapes#TerminalShape");
        shape.setTargetClass("http://other.example/Thing");

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle()).contains("sh:targetClass <http://other.example/Thing>");
    }

    @Test
    void addingAShapeAppendsItAndKeepsTheDocument() {
        var added =
                NodeShapeModel.builder()
                        .iri("http://example.org/shapes#NewShape")
                        .targetClass("http://iec.ch/TC57/CIM100#Breaker")
                        .properties(
                                List.of(
                                        PropertyShapeModel.builder()
                                                .path("http://iec.ch/TC57/CIM100#Switch.normalOpen")
                                                .minCount(1)
                                                .build()))
                        .build();

        var result = service.apply(edit(SHAPES, added));

        assertThat(result.getTurtle()).startsWith(SHAPES.stripTrailing());
        assertThat(result.getTurtle()).contains("ex:NewShape");
        assertThat(result.getTurtle()).contains("sh:path cim:Switch.normalOpen");
        assertThat(service.parse(result.getTurtle()).getShapes()).hasSize(3);
    }

    @Test
    void aPropertyShapeWithItsOwnIriStaysAReference() {
        // Inlining it would orphan the statement defining it and duplicate its constraints.
        var referencing =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:ACLineSegmentShape
                        a              sh:NodeShape ;
                        sh:targetClass cim:ACLineSegment ;
                        sh:property    ex:rRule .

                ex:rRule
                        sh:path     cim:ACLineSegment.r ;
                        sh:minCount 1 .
                """;
        var shape = shapeNamed(referencing, "http://example.org/shapes#ACLineSegmentShape");
        shape.setMessage("Edited through the form");

        var result = service.apply(edit(referencing, shape));

        assertThat(result.getTurtle()).contains("sh:property ex:rRule");
        assertThat(result.getTurtle()).contains("Edited through the form");
        // The rule's own statement is untouched, and its constraints are not duplicated inline.
        assertThat(result.getTurtle()).contains("sh:minCount 1 .");
        assertThat(result.getTurtle()).doesNotContain("sh:property [");
    }

    @Test
    void removingAShapeTakesItsStatementAndNothingElse() {
        var request = new ShapeEditRequest();
        request.setTurtle(SHAPES);
        request.setRemoveShapeIri("http://example.org/shapes#TerminalShape");

        var result = service.apply(request);

        assertThat(result.getTurtle()).doesNotContain("ex:TerminalShape");
        assertThat(result.getTurtle()).contains("ex:ACLineSegmentShape");
        assertThat(result.getTurtle()).contains("# The line segment rules");
    }

    @Test
    void saysWhenARewriteCostACommentInsideTheShape() {
        // The one thing surgical replacement cannot preserve is a comment written inside the shape
        // being rewritten. Everything outside its statement is copied through untouched.
        var withComment =
                SHAPES.replace(
                        "sh:targetClass cim:Terminal .",
                        "# only terminals of the boundary\n        sh:targetClass cim:Terminal .");
        var shape = shapeNamed(withComment, "http://example.org/shapes#TerminalShape");
        shape.setMessage("Now with a message");

        var result = service.apply(edit(withComment, shape));

        assertThat(result.getTurtle()).doesNotContain("# only terminals of the boundary");
        assertThat(result.getWarnings())
                .singleElement()
                .asString()
                .contains("Comments written inside this shape were removed");
    }

    @Test
    void aCommentAfterTheStatementIsNotTouched() {
        var withTrailing =
                SHAPES.replace(
                        "sh:targetClass cim:Terminal .",
                        "sh:targetClass cim:Terminal . # kept, it is not inside the shape");
        var shape = shapeNamed(withTrailing, "http://example.org/shapes#TerminalShape");
        shape.setMessage("Now with a message");

        var result = service.apply(edit(withTrailing, shape));

        assertThat(result.getTurtle()).contains("# kept, it is not inside the shape");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void refusesToEditATextThatDoesNotParse() {
        var shape = shapeNamed(SHAPES, "http://example.org/shapes#TerminalShape");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit("ex:Broken a sh:NodeShape .", shape)));
    }

    @Test
    void refusesAShapeWithoutAnIri() {
        var nameless = NodeShapeModel.builder().targetClass("http://example.org/A").build();

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SHAPES, nameless)));
    }

    @Test
    void aReadWriteRoundTripWithNoChangeKeepsTheShapeMeaningTheSame() {
        var before = shapeNamed(SHAPES, "http://example.org/shapes#ACLineSegmentShape");

        var result = service.apply(edit(SHAPES, before));
        var after = shapeNamed(result.getTurtle(), "http://example.org/shapes#ACLineSegmentShape");

        assertThat(after.getTargetClass()).isEqualTo(before.getTargetClass());
        assertThat(after.getProperties()).isEqualTo(before.getProperties());
    }

    // -------------------------------------------------------------------------
    // What a rewrite must never quietly lose
    // -------------------------------------------------------------------------

    @Test
    void anIriThePrefixesCannotShortenLegallyIsWrittenInFull() {
        // ex: matches by namespace, but "a/b" is not a legal local name. Shortening on the
        // namespace alone produced "ex:a/b" and a document that no longer parsed.
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                <http://example.org/shapes/S> a sh:NodeShape ;
                    sh:targetClass <http://example.org/a/b> .
                """;
        var shape = shapeNamed(turtle, "http://example.org/shapes/S");

        var result = service.apply(edit(turtle, shape));

        assertThat(result.getTurtle()).contains("<http://example.org/a/b>");
        assertThat(ShapesTurtleParser.parse(result.getTurtle()).failed()).isFalse();
    }

    @Test
    void aSecondValueForAOneFieldPredicateMakesTheShapeReadOnly() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S  a sh:NodeShape ;
                      sh:targetClass cim:ACLineSegment ;
                      sh:targetClass cim:Conductor .
                """;

        var shape = shapeNamed(turtle, "http://example.org/shapes#S");

        // The form holds one target class, so offering this shape as editable would drop the other.
        assertThat(shape.getEditable()).isFalse();
        assertThat(shape.getUnsupported()).contains("http://www.w3.org/ns/shacl#targetClass");
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(turtle, shape)));
    }

    @Test
    void aLanguageTaggedMessageMakesTheShapeReadOnly() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S  a sh:NodeShape ;
                      sh:targetClass cim:ACLineSegment ;
                      sh:message "Broken"@en .
                """;

        // The writer has no way to spell a language tag, so it used to strip it silently.
        assertThat(shapeNamed(turtle, "http://example.org/shapes#S").getEditable()).isFalse();
    }

    @Test
    void aShapeNotTypedAsANodeShapeIsShownButNotEdited() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:Implied  sh:targetClass cim:ACLineSegment .
                """;

        var form = service.parse(turtle);

        assertThat(form.getShapes())
                .extracting(NodeShapeModel::getIri)
                .containsExactly("http://example.org/shapes#Implied");
        assertThat(form.getShapes().get(0).getEditable()).isFalse();
        assertThat(form.getShapes().get(0).getReadOnlyReason()).contains("sh:NodeShape");
    }

    @Test
    void anExplicitPropertyShapeTypeSurvivesARewrite() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S  a sh:NodeShape ;
                      sh:targetClass cim:ACLineSegment ;
                      sh:property [
                          a sh:PropertyShape ;
                          sh:path cim:ACLineSegment.r ;
                          sh:datatype xsd:float ;
                      ] .
                """;
        var shape = shapeNamed(turtle, "http://example.org/shapes#S");
        assertThat(shape.getEditable()).isTrue();

        var result = service.apply(edit(turtle, shape));

        assertThat(result.getTurtle()).contains("a sh:PropertyShape");
    }

    @Test
    void aBooleanThatIsNeitherTrueNorFalseIsNotInvented() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S  a sh:NodeShape ;
                      sh:targetClass cim:ACLineSegment ;
                      sh:closed "yes" .
                """;

        // Reading this as sh:closed false and writing it back would state a rule nobody wrote.
        assertThat(shapeNamed(turtle, "http://example.org/shapes#S").getEditable()).isFalse();
    }
}
