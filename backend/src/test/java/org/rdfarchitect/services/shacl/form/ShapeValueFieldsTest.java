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
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.util.List;

/**
 * The fields the form carried but never showed, and the ones it did not model at all.
 *
 * <p>Most of these are ordinary — one more predicate, one more box. The value ranges are not, and
 * they are what this class is mostly about: the official library writes every one of its 1282 of
 * them as {@code "0.0"^^xsd:float}, so a form holding a {@code double} would have written back a
 * bare decimal and changed a datatype nobody touched. The number is therefore edited *inside* the
 * literal the document already wrote, and these tests pin that the rest of it survives.
 */
class ShapeValueFieldsTest {

    private static final String SHAPES =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property [
                        sh:path         cim:ACLineSegment.r ;
                        sh:datatype     xsd:float ;
                        sh:minInclusive "0.0"^^xsd:float ;
                        sh:maxExclusive "100.0"^^xsd:float ;
                        sh:order        0.1 ;
                    ] .
            """;

    private final ShapeFormService service = new ShapeFormService();

    private static ShapeEditRequest edit(String turtle, NodeShapeModel shape) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setShape(shape);
        return request;
    }

    private NodeShapeModel shapeIn(String turtle) {
        return service.parse(turtle).getShapes().get(0);
    }

    private PropertyShapeModel ruleIn(String turtle) {
        return shapeIn(turtle).getProperties().get(0);
    }

    // -------------------------------------------------------------------------
    // Value ranges
    // -------------------------------------------------------------------------

    @Test
    void aValueRangeIsReadAsTheNumberItHolds() {
        var rule = ruleIn(SHAPES);

        assertThat(rule.getMinInclusive()).isEqualTo("0.0");
        assertThat(rule.getMaxExclusive()).isEqualTo("100.0");
        assertThat(rule.getRetained())
                .extracting(clause -> clause.getField())
                .doesNotContain("minInclusive", "maxExclusive");
    }

    @Test
    void changingAValueRangeKeepsTheDatatypeTheAuthorGaveIt() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setMinInclusive("1.5");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).isEqualTo(SHAPES.replace("\"0.0\"^^xsd:float", "\"1.5\"^^xsd:float"));
    }

    @Test
    void clearingAValueRangeTakesTheWholeClause() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setMaxExclusive(null);

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).doesNotContain("sh:maxExclusive");
        assertThat(edited).contains("\"0.0\"^^xsd:float");
        assertThat(service.parse(edited).getParseError()).isNull();
    }

    @Test
    void addingAValueRangeWritesItAsAPlainNumber() {
        // Nothing to keep the spelling of: a clause the document does not have yet gets the
        // plainest thing that reads as a number.
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setMaxInclusive("42");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).contains("sh:maxInclusive 42");
        assertThat(service.parse(edited).getParseError()).isNull();
    }

    @Test
    void aValueRangeThatIsNotANumberIsKeptAsWrittenInstead() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S a sh:NodeShape ;
                     sh:property [ sh:path cim:X.at ;
                                   sh:minInclusive "2020-01-01"^^xsd:date ] .
                """;
        var rule = ruleIn(turtle);

        assertThat(rule.getRetained())
                .anySatisfy(
                        clause -> {
                            assertThat(clause.getField()).isEqualTo("minInclusive");
                            assertThat(clause.getValue()).isEqualTo("\"2020-01-01\"^^xsd:date");
                        });
    }

    @Test
    void aNumberFieldIsNotGivenSomethingThatIsNotANumber() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setMinInclusive("quite small");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SHAPES, shape)))
                .withMessageContaining("is not a number");
    }

    // -------------------------------------------------------------------------
    // sh:order
    // -------------------------------------------------------------------------

    @Test
    void aDecimalOrderIsAFieldNowRatherThanSomethingToWorkAround() {
        // ido:IdentifiedObject.mRID-cardinality carries sh:order 0.1, and that one decimal used to
        // make every node shape referencing it read-only.
        var rule = ruleIn(SHAPES);

        assertThat(rule.getOrder()).isEqualTo("0.1");
        assertThat(rule.getRetained())
                .extracting(clause -> clause.getField())
                .doesNotContain("order");
    }

    @Test
    void changingADecimalOrderChangesOnlyTheNumber() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setOrder("0.2");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).isEqualTo(SHAPES.replace("sh:order        0.1", "sh:order        0.2"));
    }

    @Test
    void rulesAreOrderedByWhatTheOrderMeansRatherThanBySpelling() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S a sh:NodeShape ;
                     sh:property [ sh:path cim:X.c ; sh:order 10 ] ;
                     sh:property [ sh:path cim:X.b ; sh:order 2 ] ;
                     sh:property [ sh:path cim:X.a ; sh:order 0.1 ] .
                """;

        assertThat(shapeIn(turtle).getProperties())
                .extracting(PropertyShapeModel::getOrder)
                .containsExactly("0.1", "2", "10");
    }

    // -------------------------------------------------------------------------
    // The rest of the rule card
    // -------------------------------------------------------------------------

    @Test
    void lengthsPatternAndFlagsAreWrittenAndRead() {
        var shape = shapeIn(SHAPES);
        var rule = shape.getProperties().get(0);
        rule.setMinLength(2);
        rule.setMaxLength(64);
        rule.setPattern("^[A-Z]");
        rule.setFlags("i");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();
        var back = ruleIn(edited);

        assertThat(back.getMinLength()).isEqualTo(2);
        assertThat(back.getMaxLength()).isEqualTo(64);
        assertThat(back.getPattern()).isEqualTo("^[A-Z]");
        assertThat(back.getFlags()).isEqualTo("i");
    }

    @Test
    void oneAllowedValueIsWrittenAsATermWhenItIsOne() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setHasValue("http://iec.ch/TC57/CIM100#UnitSymbol.W");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).contains("sh:hasValue cim:UnitSymbol.W");
        assertThat(ruleIn(edited).getHasValue())
                .isEqualTo("http://iec.ch/TC57/CIM100#UnitSymbol.W");
    }

    @Test
    void oneAllowedValueIsWrittenAsAStringWhenItIsNotATerm() {
        var shape = shapeIn(SHAPES);
        shape.getProperties().get(0).setHasValue("kV");

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).contains("sh:hasValue \"kV\"");
        assertThat(ruleIn(edited).getHasValue()).isEqualTo("kV");
    }

    @Test
    void theListOfAllowedValuesIsEdited() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/shapes#> .

                ex:S a sh:NodeShape ;
                     sh:property [ sh:path cim:X.kind ; sh:in ( cim:Kind.a cim:Kind.b ) ] .
                """;
        var shape = shapeIn(turtle);
        assertThat(shape.getProperties().get(0).getAllowedValues())
                .containsExactly(
                        "http://iec.ch/TC57/CIM100#Kind.a", "http://iec.ch/TC57/CIM100#Kind.b");

        shape.getProperties()
                .get(0)
                .setAllowedValues(
                        List.of(
                                "http://iec.ch/TC57/CIM100#Kind.a",
                                "http://iec.ch/TC57/CIM100#Kind.c"));
        var edited = service.apply(edit(turtle, shape)).getTurtle();

        assertThat(edited).isEqualTo(turtle.replace("cim:Kind.b )", "cim:Kind.c )"));
    }

    // -------------------------------------------------------------------------
    // Targets other than a class
    // -------------------------------------------------------------------------

    private static final String TARGETS =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix cim: <http://iec.ch/TC57/CIM100#> .
            @prefix ex:  <http://example.org/shapes#> .

            ex:S a sh:NodeShape ;
                 sh:targetSubjectsOf rdf:type ;
                 sh:targetObjectsOf cim:Terminal.ConductingEquipment ;
                 sh:targetNode ex:TheOne ;
                 sh:name "Counted" .
            """;

    @Test
    void everyKindOfTargetIsReadAsItsOwnList() {
        var shape = shapeIn(TARGETS);

        assertThat(shape.getTargetClasses()).isEmpty();
        assertThat(shape.getTargetSubjectsOf())
                .containsExactly("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        assertThat(shape.getTargetObjectsOf())
                .containsExactly("http://iec.ch/TC57/CIM100#Terminal.ConductingEquipment");
        assertThat(shape.getTargetNodes()).containsExactly("http://example.org/shapes#TheOne");
        assertThat(shape.getEditable()).isTrue();
    }

    @Test
    void aTargetIsChangedWithoutDisturbingTheOthers() {
        var shape = shapeIn(TARGETS);
        shape.setTargetNodes(List.of("http://example.org/shapes#TheOther"));

        var edited = service.apply(edit(TARGETS, shape)).getTurtle();

        assertThat(edited).isEqualTo(TARGETS.replace("ex:TheOne", "ex:TheOther"));
    }

    @Test
    void aShapeCanBeGivenAKindOfTargetItDidNotHave() {
        var shape = shapeIn(SHAPES);
        shape.setTargetSubjectsOf(List.of("http://iec.ch/TC57/CIM100#ACLineSegment.r"));

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();

        assertThat(edited).contains("sh:targetSubjectsOf cim:ACLineSegment.r");
        assertThat(shapeIn(edited).getTargetClasses())
                .containsExactly("http://iec.ch/TC57/CIM100#ACLineSegment");
    }

    // -------------------------------------------------------------------------
    // The node shape's own words
    // -------------------------------------------------------------------------

    @Test
    void aShapesOwnMetadataIsWrittenBack() {
        var shape = shapeIn(SHAPES);
        shape.setName("Line segment");
        shape.setDescription("What a line segment has to look like");
        shape.setMessage("This line segment is not valid");
        shape.setSeverity("http://www.w3.org/ns/shacl#Warning");
        shape.setClosed(true);
        shape.setIgnoredProperties(List.of("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

        var edited = service.apply(edit(SHAPES, shape)).getTurtle();
        var back = shapeIn(edited);

        assertThat(back.getName()).isEqualTo("Line segment");
        assertThat(back.getDescription()).isEqualTo("What a line segment has to look like");
        assertThat(back.getMessage()).isEqualTo("This line segment is not valid");
        assertThat(back.getSeverity()).isEqualTo("http://www.w3.org/ns/shacl#Warning");
        assertThat(back.getClosed()).isTrue();
        assertThat(back.getIgnoredProperties())
                .containsExactly("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        // The rule underneath it is untouched, spelling and all.
        assertThat(edited).contains("sh:minInclusive \"0.0\"^^xsd:float");
    }
}
