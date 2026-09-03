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

import java.util.ArrayList;
import java.util.List;

/**
 * What a form edit leaves alone.
 *
 * <p>The promise is "import, edit in the form, export, and the only difference is the edit". This
 * is where it is held to: every test changes one thing and asserts the document character for
 * character, rather than asserting that the change is present and hoping the rest survived.
 *
 * <p>The clauses the form has no field for get most of the attention, because those are what used
 * to make a shape read-only rather than being carried through.
 */
class ShapeClausePreservationTest {

    /** Deliberately full of things the form cannot spell: a decimal order, a range, a query. */
    private static final String SHAPES =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment , cim:Conductor ;
                    sh:group       ex:LineGroup ;
                    sh:sparql [ sh:select \"""SELECT $this WHERE { }\""" ] ;
                    sh:property [
                        # the resistance, agreed with the TSO
                        sh:path         cim:ACLineSegment.r ;
                        sh:datatype     xsd:float ;
                        sh:minCount     1 ;
                        sh:minInclusive 0 ;
                        sh:order        0.1 ;
                    ] ;
                    sh:property [
                        sh:path     cim:ACLineSegment.x ;
                        sh:maxCount 1 ;
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

    private static PropertyShapeModel ruleAt(NodeShapeModel shape, int sourceIndex) {
        return shape.getProperties().stream()
                .filter(rule -> Integer.valueOf(sourceIndex).equals(rule.getSourceIndex()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rule written at " + sourceIndex));
    }

    // -------------------------------------------------------------------------
    // What the form keeps as written
    // -------------------------------------------------------------------------

    @Test
    void aShapeFullOfThingsTheFormCannotSpellIsStillEditable() {
        var shape = shapeIn(SHAPES);

        assertThat(shape.getEditable()).isTrue();
        assertThat(shape.getReadOnlyReason()).isNull();
        assertThat(shape.getRetained())
                .extracting("predicate", "value", "field")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "http://www.w3.org/ns/shacl#group", "ex:LineGroup", null),
                        org.assertj.core.groups.Tuple.tuple(
                                "http://www.w3.org/ns/shacl#sparql",
                                "[ sh:select \"\"\"SELECT $this WHERE { }\"\"\" ]",
                                null));
    }

    @Test
    void aValueTheFormCannotSpellNamesTheFieldItWouldHaveFilled() {
        // sh:order 0.1 is an xsd:decimal and the form holds an integer. That one clause used to
        // make its whole shape — and every shape referencing it — read-only.
        var rule = ruleAt(shapeIn(SHAPES), 0);

        assertThat(rule.getOrder()).isNull();
        assertThat(rule.getRetained())
                .extracting("predicate", "value", "field")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "http://www.w3.org/ns/shacl#minInclusive", "0", null),
                        org.assertj.core.groups.Tuple.tuple(
                                "http://www.w3.org/ns/shacl#order", "0.1", "order"));
        assertThat(rule.getRetained().get(1).getReason())
                .contains("cannot write this value back unchanged");
    }

    @Test
    void changingAFieldTheFormKeepsAsWrittenIsRefusedAndSaysWhich() {
        var shape = shapeIn(SHAPES);
        ruleAt(shape, 0).setOrder(3);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SHAPES, shape)))
                .withMessageContaining("does not change order on this rule");
    }

    @Test
    void anEmbeddedQueryAndAValueRangeSurviveAnEditToTheSameRule() {
        var shape = shapeIn(SHAPES);
        ruleAt(shape, 0).setMinCount(2);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(SHAPES.replace("sh:minCount     1", "sh:minCount     2"));
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void aCommentInsideARuleSurvivesAnEditToThatRule() {
        var shape = shapeIn(SHAPES);
        ruleAt(shape, 0).setMessage("Out of range");

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle()).contains("# the resistance, agreed with the TSO");
        assertThat(result.getTurtle()).contains("sh:message \"Out of range\"");
    }

    // -------------------------------------------------------------------------
    // Clauses added, changed and cleared
    // -------------------------------------------------------------------------

    @Test
    void clearingAFieldTakesItsLineAndLeavesTheOthersWhereTheyWere() {
        var shape = shapeIn(SHAPES);
        ruleAt(shape, 0).setDataType(null);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(SHAPES.replace("            sh:datatype     xsd:float ;\n", ""));
    }

    @Test
    void aNewClauseIsWrittenWhereTheShapesOwnClausesAreAndNoEarlierOne() {
        // At the end, so nothing the document already says moves. That is the whole reason the
        // form does not sort the clauses it writes.
        var shape = shapeIn(SHAPES);
        shape.setMessage("Checked");

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        SHAPES.replace(
                                "        ] .", "        ] ;\n        sh:message \"Checked\" ."));
    }

    @Test
    void aShapeWrittenOnOneLineKeepsItsOneLine() {
        var inline =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:S a sh:NodeShape ; sh:targetClass cim:Terminal .
                """;
        var shape = shapeIn(inline);
        shape.setMessage("hi");

        var result = service.apply(edit(inline, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        inline.replace(
                                "sh:targetClass cim:Terminal .",
                                "sh:targetClass cim:Terminal ; sh:message \"hi\" ."));
    }

    @Test
    void clearingTheOnlyThingAShapeSaysIsRefusedRatherThanLeavingItSayingNothing() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:S sh:targetClass cim:Terminal .
                """;
        var shape = shapeIn(turtle);
        shape.setTargetClasses(List.of());

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(turtle, shape)))
                .withMessageContaining("a shape cannot say nothing");
    }

    @Test
    void aCommentTheRemovalOfAClauseTakesWithItIsReportedRatherThanLostQuietly() {
        // The one thing clause-level editing still cannot keep: a comment sitting between the
        // clause before and the clause being removed goes with the text that joined them. Saying
        // so is the point — it is the only case left where an edit costs something extra.
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:S a sh:NodeShape ;
                     sh:targetClass cim:Terminal ;
                     # only for the boundary
                     sh:message "x" .
                """;
        var shape = shapeIn(turtle);
        shape.setMessage(null);

        var result = service.apply(edit(turtle, shape));

        assertThat(result.getTurtle()).doesNotContain("sh:message");
        assertThat(result.getWarnings())
                .singleElement()
                .asString()
                .contains("A comment written in the part of this shape the change removed");
    }

    // -------------------------------------------------------------------------
    // Target classes, of which there may be several
    // -------------------------------------------------------------------------

    @Test
    void severalTargetClassesAreReadInTheOrderTheDocumentWritesThem() {
        // Not the graph's order, which has none: showing them the other way round would reorder
        // the clause the moment anything else about the shape changed.
        assertThat(shapeIn(SHAPES).getTargetClasses())
                .containsExactly(
                        "http://iec.ch/TC57/CIM100#ACLineSegment",
                        "http://iec.ch/TC57/CIM100#Conductor");
    }

    @Test
    void aTargetClassCanBeAddedToTheOnesAlreadyThere() {
        var shape = shapeIn(SHAPES);
        var classes = new ArrayList<>(shape.getTargetClasses());
        classes.add("http://iec.ch/TC57/CIM100#Terminal");
        shape.setTargetClasses(classes);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        SHAPES.replace(
                                "cim:ACLineSegment , cim:Conductor",
                                "cim:ACLineSegment , cim:Conductor , cim:Terminal"));
    }

    @Test
    void removingOneTargetClassKeepsTheOther() {
        var shape = shapeIn(SHAPES);
        shape.setTargetClasses(List.of("http://iec.ch/TC57/CIM100#Conductor"));

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(SHAPES.replace("cim:ACLineSegment , cim:Conductor", "cim:Conductor"));
    }

    // -------------------------------------------------------------------------
    // Rules
    // -------------------------------------------------------------------------

    @Test
    void addingARuleLeavesTheRulesAlreadyThereCharacterForCharacter() {
        var shape = shapeIn(SHAPES);
        var rules = new ArrayList<>(shape.getProperties());
        rules.add(
                PropertyShapeModel.builder()
                        .path("http://iec.ch/TC57/CIM100#ACLineSegment.bch")
                        .minCount(1)
                        .build());
        shape.setProperties(rules);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        SHAPES.replace(
                                "        ] .",
                                "        ] ;\n"
                                        + "        sh:property [\n"
                                        + "            sh:path cim:ACLineSegment.bch ;\n"
                                        + "            sh:minCount 1 ;\n"
                                        + "        ] ."));
    }

    @Test
    void removingARuleTakesItsClauseAndNothingElse() {
        var shape = shapeIn(SHAPES);
        var rules = new ArrayList<>(shape.getProperties());
        rules.removeIf(rule -> Integer.valueOf(1).equals(rule.getSourceIndex()));
        shape.setProperties(rules);

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        SHAPES.replace(
                                " ;\n"
                                        + "        sh:property [\n"
                                        + "            sh:path     cim:ACLineSegment.x ;\n"
                                        + "            sh:maxCount 1 ;\n"
                                        + "        ] .",
                                " ."));
    }

    @Test
    void aShapeSentBackWithNoRuleListAtAllIsNotAnAskToDeleteThemAll() {
        var shape = shapeIn(SHAPES);
        shape.setProperties(null);
        shape.setMessage("Checked");

        var result = service.apply(edit(SHAPES, shape));

        assertThat(result.getTurtle()).contains("cim:ACLineSegment.r");
        assertThat(result.getTurtle()).contains("cim:ACLineSegment.x");
    }

    @Test
    void aRuleTheDocumentNoLongerHasIsRefusedRatherThanWrittenSomewhere() {
        var shape = shapeIn(SHAPES);
        var rules = new ArrayList<>(shape.getProperties());
        rules.add(
                PropertyShapeModel.builder()
                        .sourceIndex(7)
                        .path("http://iec.ch/TC57/CIM100#ACLineSegment.bch")
                        .build());
        shape.setProperties(rules);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(SHAPES, shape)))
                .withMessageContaining("a rule the document no longer has");
    }

    /**
     * Generated profiles write the same rule twice. With two identical {@code sh:property} clauses
     * there is no telling which of them an edit belongs in, so neither is offered — but the shape
     * around them is, which is the difference rule-level locking makes: 207 shapes of the official
     * {@code -AllowedProperties} families used to lose every field they had over this.
     */
    private static final String TWICE =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix cim: <http://iec.ch/TC57/CIM100#> .
            @prefix ex:  <http://example.org/> .

            ex:S a sh:NodeShape ;
                 sh:targetClass cim:ACLineSegment ;
                 sh:property [ sh:path cim:IdentifiedObject.name ] ;
                 sh:property [ sh:path cim:IdentifiedObject.name ] .
            """;

    @Test
    void twoRulesThatSayTheSameThingLockThemselvesAndNotTheirShape() {
        var shape = shapeIn(TWICE);

        assertThat(shape.getEditable()).isTrue();
        assertThat(shape.getProperties())
                .allSatisfy(rule -> assertThat(rule.getEditable()).isFalse())
                .allSatisfy(
                        rule -> assertThat(rule.getReadOnlyReason()).contains("cannot tell apart"));
    }

    @Test
    void theShapeAroundTwoRulesAlikeIsStillEditable() {
        var shape = shapeIn(TWICE);
        shape.setMessage("Checked");

        var edited = service.apply(edit(TWICE, shape)).getTurtle();

        assertThat(edited).contains("sh:message \"Checked\"");
        assertThat(edited.replace(" ;\n     sh:message \"Checked\"", "")).isEqualTo(TWICE);
    }

    @Test
    void oneOfTwoRulesAlikeIsNotChanged() {
        var shape = shapeIn(TWICE);
        shape.getProperties().get(0).setMinCount(1);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(TWICE, shape)))
                .withMessageContaining("cannot tell apart");
    }

    @Test
    void oneOfTwoRulesAlikeIsNotRemoved() {
        var shape = shapeIn(TWICE);
        shape.setProperties(List.of(shape.getProperties().get(0)));

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(TWICE, shape)))
                .withMessageContaining("cannot tell apart");
    }

    /**
     * An inline rule about a path expression: shown, locked, and no longer its shape's problem.
     *
     * <p>Neither the graph nor the text can spell a sequence path the same way, so the rule is
     * matched to its text by what it states instead — which is enough to place it, and so enough to
     * leave the rules around it editable.
     */
    @Test
    void anInlineRuleAboutAPathExpressionLocksItselfAndNotItsShape() {
        var turtle =
                """
                @prefix sh:  <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex:  <http://example.org/> .

                ex:S a sh:NodeShape ;
                     sh:targetClass cim:ACLineSegment ;
                     sh:property [ sh:path ( cim:Terminal.ConductingEquipment cim:IdentifiedObject.name ) ;
                                   sh:minCount 1 ] ;
                     sh:property [ sh:path cim:ACLineSegment.r ; sh:maxCount 1 ] .
                """;
        var shape = shapeIn(turtle);

        assertThat(shape.getEditable()).isTrue();
        var expression =
                shape.getProperties().stream().filter(rule -> rule.getPath() == null).findFirst();
        assertThat(expression).isPresent();
        assertThat(expression.get().getRetained())
                .anySatisfy(
                        clause ->
                                assertThat(clause.getValue())
                                        .contains("cim:Terminal.ConductingEquipment"));

        var plain =
                shape.getProperties().stream()
                        .filter(rule -> rule.getPath() != null)
                        .findFirst()
                        .orElseThrow();
        plain.setMaxCount(3);
        var edited = service.apply(edit(turtle, shape)).getTurtle();

        assertThat(edited).isEqualTo(turtle.replace("sh:maxCount 1", "sh:maxCount 3"));
    }

    // -------------------------------------------------------------------------
    // Rules written as their own shapes
    // -------------------------------------------------------------------------

    /** How the official {@code -Con-Simple-} profiles are composed: all rules are named. */
    private static final String REFERENCED =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix cim: <http://iec.ch/TC57/CIM100#> .
            @prefix ex:  <http://example.org/shapes#> .

            ex:TerminalShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property    ex:SequenceNumberRule .

            ex:SequenceNumberRule
                    a               sh:PropertyShape ;
                    sh:path         cim:Terminal.sequenceNumber ;
                    sh:minCount     1 ;
                    sh:minInclusive 1 .
            """;

    @Test
    void aReferencedRulesOwnClausesDoNotLockTheShapeThatReferencesIt() {
        // Rewriting ex:TerminalShape only re-emits the reference and cannot lose anything, but the
        // shape used to be read-only because ex:SequenceNumberRule carries an sh:minInclusive.
        var shape = shapeIn(REFERENCED);

        assertThat(shape.getEditable()).isTrue();
        assertThat(shape.getProperties())
                .singleElement()
                .satisfies(
                        rule -> {
                            assertThat(rule.getIri())
                                    .isEqualTo("http://example.org/shapes#SequenceNumberRule");
                            assertThat(rule.getMinCount()).isEqualTo(1);
                            // Read from the rule's own statement, so a shared rule is shown with
                            // what it actually says rather than as an opaque reference.
                            assertThat(rule.getRetained())
                                    .extracting("predicate")
                                    .containsExactly("http://www.w3.org/ns/shacl#minInclusive");
                        });
    }

    @Test
    void aSharedRulesFieldsAreNotEditedFromTheShapeThatReferencesIt() {
        var shape = shapeIn(REFERENCED);
        shape.getProperties().get(0).setMinCount(2);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(REFERENCED, shape)))
                .withMessageContaining("every shape that references it");
    }

    @Test
    void editingTheShapeThatReferencesARuleLeavesTheRulesStatementAlone() {
        var shape = shapeIn(REFERENCED);
        shape.setMessage("Checked");

        var result = service.apply(edit(REFERENCED, shape));

        assertThat(result.getTurtle())
                .isEqualTo(
                        REFERENCED.replace(
                                "sh:property    ex:SequenceNumberRule .",
                                "sh:property    ex:SequenceNumberRule ;\n"
                                        + "        sh:message \"Checked\" ."));
    }
}
