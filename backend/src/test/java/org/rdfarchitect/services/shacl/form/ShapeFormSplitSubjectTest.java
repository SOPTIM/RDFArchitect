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

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * A shape whose subject is written as more than one statement.
 *
 * <p>Turtle allows it and people write it — a shape declared in one place and given another rule
 * further down — and it used to corrupt the document. The form read every triple of the subject
 * whichever statement it came from, and the writer put all of them into the first statement while
 * the others stayed where they were: the rules in them came back twice, and grew by one on every
 * further edit. The form now says so on the card instead, and refuses the write.
 */
class ShapeFormSplitSubjectTest {

    private static final String SPLIT =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment .

            # The length rule came later, in its own statement.
            ex:ACLineSegmentShape
                    sh:property [ sh:path cim:ACLineSegment.length ; sh:minCount 1 ] .

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
                .filter(shape -> iri.equals(shape.getIri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shape " + iri));
    }

    private static long triples(String turtle) {
        return RDFParser.fromString(turtle, Lang.TURTLE).toGraph().size();
    }

    @Test
    void theShapeIsShownWithEverythingItSaysAcrossItsStatements() {
        var shape = shapeNamed(SPLIT, "http://example.org/shapes#ACLineSegmentShape");

        assertThat(shape.getTargetClass()).isEqualTo("http://iec.ch/TC57/CIM100#ACLineSegment");
        assertThat(shape.getProperties()).hasSize(1);
    }

    @Test
    void butItIsNotOfferedForEditing() {
        var shape = shapeNamed(SPLIT, "http://example.org/shapes#ACLineSegmentShape");

        assertThat(shape.getEditable()).isFalse();
        assertThat(shape.getReadOnlyReason()).contains("2 separate statements");
    }

    @Test
    void aSubjectWrittenOnceIsStillEditable() {
        var shape = shapeNamed(SPLIT, "http://example.org/shapes#TerminalShape");

        assertThat(shape.getEditable()).isTrue();
        assertThat(shape.getReadOnlyReason()).isNull();
    }

    @Test
    void writingItIsRefusedRatherThanDuplicatingItsRules() {
        var shape = shapeNamed(SPLIT, "http://example.org/shapes#ACLineSegmentShape");
        shape.setMessage("edited");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SPLIT, shape)))
                .withMessageContaining("2 separate statements");
        // The mechanism the refusal rests on: the writer would have replaced one of the two and
        // left the other, so the rule in it came back twice and the document grew on every edit.
        assertThat(
                        ShapeBlockLocator.locateAll(
                                SPLIT,
                                "http://example.org/shapes#ACLineSegmentShape",
                                RDFParser.fromString(SPLIT, Lang.TURTLE)
                                        .toGraph()
                                        .getPrefixMapping()))
                .hasSize(2);
    }

    @Test
    void removingItTakesEveryStatementItIsWrittenAs() {
        var request = new ShapeEditRequest();
        request.setTurtle(SPLIT);
        request.setRemoveShapeIri("http://example.org/shapes#ACLineSegmentShape");

        var after = service.apply(request).getTurtle();

        assertThat(after).doesNotContain("ACLineSegmentShape");
        assertThat(after).contains("ex:TerminalShape");
        assertThat(service.parse(after).getParseError()).isNull();
    }

    @Test
    void aShapeThatIsNotItsOwnStatementIsNeitherEditableNorAppended() {
        // Written against a base, which the scanner cannot resolve, so it cannot find the
        // statement to replace. Appending the rewrite would define the shape a second time.
        var relative =
                """
                @base <http://example.org/shapes/> .
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .

                <ACLineSegmentShape> a sh:NodeShape ; sh:targetClass cim:ACLineSegment .
                """;
        var shape = shapeNamed(relative, "http://example.org/shapes/ACLineSegmentShape");

        assertThat(shape.getEditable()).isFalse();
        assertThat(shape.getReadOnlyReason()).contains("not written as a statement of its own");

        shape.setEditable(true);
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(relative, shape)));
    }

    @Test
    void aRuleWithNoPropertyIsRefusedRatherThanSilentlyDropped() {
        var shape = shapeNamed(SPLIT, "http://example.org/shapes#TerminalShape");
        var properties = new ArrayList<PropertyShapeModel>(shape.getProperties());
        properties.add(PropertyShapeModel.builder().minCount(1).build());
        shape.setProperties(properties);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SPLIT, shape)))
                .withMessageContaining("which property");
    }

    @Test
    void aRuleThatIsOnlyAReferenceIsNotMistakenForAnEmptyOne() {
        var referencing =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:TerminalShape
                        a              sh:NodeShape ;
                        sh:targetClass cim:Terminal ;
                        sh:property    ex:SharedRule .

                ex:SharedRule
                        a       sh:PropertyShape ;
                        sh:path cim:Terminal.sequenceNumber .
                """;
        var shape = shapeNamed(referencing, "http://example.org/shapes#TerminalShape");
        shape.setMessage("edited");

        var after = service.apply(edit(referencing, shape)).getTurtle();

        assertThat(after).contains("sh:property ex:SharedRule");
        assertThat(after).contains("ex:SharedRule");
        assertThat(service.parse(after).getParseError()).isNull();
        assertThat(triples(after)).isEqualTo(triples(referencing) + 1);
    }

    @Test
    void aNewShapeIsStillAppended() {
        var shape =
                NodeShapeModel.builder()
                        .iri("http://example.org/shapes#NewShape")
                        .targetClass("http://iec.ch/TC57/CIM100#Breaker")
                        .properties(List.of())
                        .build();

        var after = service.apply(edit(SPLIT, shape)).getTurtle();

        assertThat(after).startsWith(SPLIT);
        assertThat(shapeNamed(after, "http://example.org/shapes#NewShape").getEditable()).isTrue();
    }
}
