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
import org.rdfarchitect.shacl.dto.PropertyShapeSplit;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules the document writes as shapes of their own, which several node shapes then share.
 *
 * <p>How the official {@code -Con-Simple-} profiles are composed, and the reason the form could
 * show those files but change nothing in them: the constraints are all in named property shapes,
 * and a named rule was displayed read-only under every shape that referenced it. It is now edited
 * on itself — which is also what makes the question this raises answerable, because a rule editing
 * itself knows how many shapes rely on it, and can offer one of them a copy instead.
 */
class ShapeSharedRuleTest {

    /** Two classes sharing one cardinality rule, and one keeping a rule of its own. */
    private static final String SHARED =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            ex:NameCardinality
                    a           sh:PropertyShape ;
                    # every identified object is named
                    sh:path     cim:IdentifiedObject.name ;
                    sh:minCount 1 ;
                    sh:maxCount 1 .

            ex:ACLineSegmentShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:ACLineSegment ;
                    sh:property    ex:NameCardinality ;
                    sh:property    [ sh:path cim:ACLineSegment.r ; sh:datatype xsd:float ] .

            ex:TerminalShape
                    a              sh:NodeShape ;
                    sh:targetClass cim:Terminal ;
                    sh:property    ex:NameCardinality .
            """;

    private static final String RULE = "http://example.org/shapes#NameCardinality";
    private static final String LINE_SHAPE = "http://example.org/shapes#ACLineSegmentShape";
    private static final String TERMINAL_SHAPE = "http://example.org/shapes#TerminalShape";

    private final ShapeFormService service = new ShapeFormService();

    private PropertyShapeModel namedRule(String turtle, String iri) {
        return service.parse(turtle).getPropertyShapes().stream()
                .filter(rule -> iri.equals(rule.getIri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No named rule " + iri));
    }

    private NodeShapeModel shape(String turtle, String iri) {
        return service.parse(turtle).getShapes().stream()
                .filter(shape -> iri.equals(shape.getIri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shape " + iri));
    }

    private static ShapeEditRequest ruleEdit(String turtle, PropertyShapeModel rule) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setPropertyShape(rule);
        return request;
    }

    private static ShapeEditRequest shapeEdit(String turtle, NodeShapeModel shape) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setShape(shape);
        return request;
    }

    private static ShapeEditRequest splitEdit(
            String turtle, PropertyShapeModel rule, String newIri, String nodeShape, Integer at) {
        var request = ruleEdit(turtle, rule);
        var split = new PropertyShapeSplit();
        split.setNewIri(newIri);
        split.setNodeShapeIri(nodeShape);
        split.setSourceIndex(at);
        request.setSplit(split);
        return request;
    }

    // -------------------------------------------------------------------------
    // Reading them
    // -------------------------------------------------------------------------

    @Test
    void aNamedRuleIsACardOfItsOwnWithTheShapesThatUseIt() {
        var rule = namedRule(SHARED, RULE);

        assertThat(rule.getPath()).isEqualTo("http://iec.ch/TC57/CIM100#IdentifiedObject.name");
        assertThat(rule.getMinCount()).isEqualTo(1);
        assertThat(rule.getEditable()).isTrue();
        assertThat(rule.getUsedBy()).containsExactly(LINE_SHAPE, TERMINAL_SHAPE);
    }

    @Test
    void theSameRuleIsAlsoShownUnderEveryShapeThatReferencesIt() {
        var inline =
                shape(SHARED, TERMINAL_SHAPE).getProperties().stream()
                        .filter(rule -> RULE.equals(rule.getIri()))
                        .findFirst()
                        .orElseThrow();

        assertThat(inline.getMaxCount()).isEqualTo(1);
        assertThat(inline.getUsedBy()).hasSize(2);
        assertThat(inline.getSourceIndex()).isNotNull();
    }

    @Test
    void aRuleWrittenInsideAShapeIsNotOneOfTheDocumentsOwnRules() {
        // Only shapes the document writes under a name of their own are shared; an inline rule
        // belongs to the one shape it is written in, and listing it would suggest otherwise.
        assertThat(service.parse(SHARED).getPropertyShapes())
                .extracting(PropertyShapeModel::getIri)
                .containsExactly(RULE);
    }

    @Test
    void aRuleReferencedButNeverWrittenIsNotOfferedAsACard() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/shapes#> .

                ex:Shape a sh:NodeShape ; sh:property ex:Missing .
                """;

        assertThat(service.parse(turtle).getPropertyShapes()).isEmpty();
    }

    @Test
    void aRuleReferencedButNeverWrittenIsNotEditableUnderTheShapeEither() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/shapes#> .

                ex:Shape a sh:NodeShape ; sh:property ex:Missing .
                """;
        var rule = shape(turtle, "http://example.org/shapes#Shape").getProperties().get(0);

        assertThat(rule.getEditable()).isFalse();
        assertThat(rule.getReadOnlyReason()).contains("not written as a statement of its own");
    }

    // -------------------------------------------------------------------------
    // Changing them
    // -------------------------------------------------------------------------

    @Test
    void changingANamedRuleChangesItsOwnStatementAndNothingElse() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        var edited = service.apply(ruleEdit(SHARED, rule)).getTurtle();

        assertThat(edited).isEqualTo(SHARED.replace("sh:minCount 1", "sh:minCount 0"));
    }

    @Test
    void changingANamedRuleKeepsWhatTheFormHasNoFieldFor() {
        var rule = namedRule(SHARED, RULE);
        rule.setMessage("A name is required");

        var edited = service.apply(ruleEdit(SHARED, rule)).getTurtle();

        assertThat(edited).contains("# every identified object is named");
        assertThat(edited).contains("a           sh:PropertyShape ;");
        assertThat(service.parse(edited).getParseError()).isNull();
    }

    @Test
    void aNamedRuleIsNotChangedThroughAShapeThatMerelyReferencesIt() {
        // The form asks first, because the answer changes what is written: this shape gets a copy,
        // or every shape using the rule gets the change. Neither is a shape edit.
        var shape = shape(SHARED, LINE_SHAPE);
        shape.getProperties().stream()
                .filter(rule -> RULE.equals(rule.getIri()))
                .findFirst()
                .orElseThrow()
                .setMinCount(0);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(shapeEdit(SHARED, shape)))
                .withMessageContaining("Change it on the rule itself");
    }

    @Test
    void aRuleTheDocumentDoesNotWriteAsItsOwnShapeIsNotChangedThisWay() {
        var rule = new PropertyShapeModel();
        rule.setIri("http://example.org/shapes#Unknown");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(ruleEdit(SHARED, rule)))
                .withMessageContaining("does not write this rule as a shape of its own");
    }

    @Test
    void anInlineRuleIsNotChangedAsIfItHadAName() {
        var rule = new PropertyShapeModel();

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(ruleEdit(SHARED, rule)))
                .withMessageContaining("changed with that shape");
    }

    // -------------------------------------------------------------------------
    // Splitting them
    // -------------------------------------------------------------------------

    /** The reference the split moves: which of the node shape's rules names the shared one. */
    private static int referenceIn(NodeShapeModel shape) {
        return shape.getProperties().stream()
                .filter(rule -> RULE.equals(rule.getIri()))
                .findFirst()
                .orElseThrow()
                .getSourceIndex();
    }

    @Test
    void aSplitGivesOneShapeACopyAndLeavesTheOthersOnTheOriginal() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);
        var at = referenceIn(shape(SHARED, LINE_SHAPE));

        var edited =
                service.apply(
                                splitEdit(
                                        SHARED,
                                        rule,
                                        "http://example.org/shapes#LineNameCardinality",
                                        LINE_SHAPE,
                                        at))
                        .getTurtle();
        var after = service.parse(edited);

        assertThat(after.getParseError()).isNull();
        // The original still says what it said, and the shape that did not ask still uses it.
        assertThat(edited)
                .contains("ex:NameCardinality\n" + "        a           sh:PropertyShape ;");
        assertThat(namedRule(edited, RULE).getMinCount()).isEqualTo(1);
        assertThat(namedRule(edited, RULE).getUsedBy()).containsExactly(TERMINAL_SHAPE);
        // The copy carries the edit, and only the shape that made it.
        var copy = namedRule(edited, "http://example.org/shapes#LineNameCardinality");
        assertThat(copy.getMinCount()).isZero();
        assertThat(copy.getMaxCount()).isEqualTo(1);
        assertThat(copy.getUsedBy()).containsExactly(LINE_SHAPE);
    }

    @Test
    void theCopyIsTakenFromTheRulesTextRatherThanWrittenOutAgain() {
        // Writing the copy from the model would drop everything the form has no field for, which
        // on a real profile is most of what the rule says.
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        var edited =
                service.apply(
                                splitEdit(
                                        SHARED,
                                        rule,
                                        "http://example.org/shapes#LineNameCardinality",
                                        LINE_SHAPE,
                                        referenceIn(shape(SHARED, LINE_SHAPE))))
                        .getTurtle();

        // Twice, deliberately: the copy is the original's text, so it carries the comment too.
        assertThat(edited).contains("# every identified object is named");
        assertThat(edited.split("# every identified object is named", -1)).hasSize(3);
        assertThat(edited)
                .contains(
                        "ex:LineNameCardinality\n"
                                + "        a           sh:PropertyShape ;\n"
                                + "        # every identified object is named\n"
                                + "        sh:path     cim:IdentifiedObject.name ;\n"
                                + "        sh:minCount 0 ;\n"
                                + "        sh:maxCount 1 .");
    }

    @Test
    void everythingTheSplitDidNotTouchIsUntouched() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        var edited =
                service.apply(
                                splitEdit(
                                        SHARED,
                                        rule,
                                        "http://example.org/shapes#LineNameCardinality",
                                        LINE_SHAPE,
                                        referenceIn(shape(SHARED, LINE_SHAPE))))
                        .getTurtle();

        assertThat(edited).startsWith(SHARED.substring(0, SHARED.indexOf("ex:ACLineSegmentShape")));
        assertThat(edited).contains("sh:property    [ sh:path cim:ACLineSegment.r ;");
        assertThat(edited).contains("ex:TerminalShape\n");
        assertThat(edited).contains("sh:property    ex:LineNameCardinality ;");
    }

    @Test
    void aCopyIsNotWrittenOverSomethingTheDocumentAlreadySays() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.apply(
                                        splitEdit(
                                                SHARED,
                                                rule,
                                                TERMINAL_SHAPE,
                                                LINE_SHAPE,
                                                referenceIn(shape(SHARED, LINE_SHAPE)))))
                .withMessageContaining("already says something about");
    }

    @Test
    void aCopyNeedsANameTheDocumentCanWrite() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.apply(
                                        splitEdit(
                                                SHARED,
                                                rule,
                                                "a name with spaces",
                                                LINE_SHAPE,
                                                referenceIn(shape(SHARED, LINE_SHAPE)))))
                .withMessageContaining("not a name this document can write");
    }

    @Test
    void aSplitIsRefusedWhenTheShapeNoLongerUsesTheRuleThere() {
        var rule = namedRule(SHARED, RULE);
        rule.setMinCount(0);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(
                        () ->
                                service.apply(
                                        splitEdit(
                                                SHARED,
                                                rule,
                                                "http://example.org/shapes#Copy",
                                                LINE_SHAPE,
                                                1)))
                .withMessageContaining("no longer uses that rule");
    }

    // -------------------------------------------------------------------------
    // References to them
    // -------------------------------------------------------------------------

    @Test
    void aShapeCanBeGivenAReferenceToARuleTheDocumentAlreadyWrites() {
        var shape = shape(SHARED, LINE_SHAPE);
        var reference = new PropertyShapeModel();
        reference.setIri(RULE);
        var rules = new ArrayList<>(shape.getProperties());
        rules.add(reference);
        shape.setProperties(rules);

        var edited = service.apply(shapeEdit(SHARED, shape)).getTurtle();

        assertThat(service.parse(edited).getParseError()).isNull();
        assertThat(namedRule(edited, RULE).getUsedBy()).containsExactly(LINE_SHAPE, TERMINAL_SHAPE);
        assertThat(edited).contains("sh:property ex:NameCardinality");
        // The rule itself is referenced, never inlined: inlining would orphan its statement.
        assertThat(edited).containsOnlyOnce("sh:path     cim:IdentifiedObject.name");
    }

    @Test
    void removingAReferenceLeavesTheRuleItselfInTheDocument() {
        var shape = shape(SHARED, TERMINAL_SHAPE);
        shape.setProperties(List.of());

        var edited = service.apply(shapeEdit(SHARED, shape)).getTurtle();

        assertThat(service.parse(edited).getParseError()).isNull();
        assertThat(namedRule(edited, RULE).getUsedBy()).containsExactly(LINE_SHAPE);
        assertThat(edited).contains("ex:NameCardinality");
    }

    @Test
    void aRuleWrittenAsTwoStatementsIsShownButNotChanged() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix cim: <http://iec.ch/TC57/CIM100#> .
                @prefix ex: <http://example.org/shapes#> .

                ex:Rule sh:path cim:IdentifiedObject.name .
                ex:Rule sh:minCount 1 .

                ex:Shape a sh:NodeShape ; sh:property ex:Rule .
                """;
        var rule = namedRule(turtle, "http://example.org/shapes#Rule");

        assertThat(rule.getMinCount()).isEqualTo(1);
        assertThat(rule.getEditable()).isFalse();
        assertThat(rule.getReadOnlyReason()).contains("2 separate statements");

        rule.setMinCount(0);
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(ruleEdit(turtle, rule)))
                .withMessageContaining("2 separate statements");
    }
}
