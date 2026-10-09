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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.shacl.ShapesTurtleParser;
import org.rdfarchitect.shacl.dto.NodeShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeModel;
import org.rdfarchitect.shacl.dto.PropertyShapeSplit;
import org.rdfarchitect.shacl.dto.ShapeEditRequest;

import java.util.List;

/**
 * The edits the form must refuse, and the checks that stand between an edit and a damaged file.
 *
 * <p>Every edit is text surgery on a document somebody else wrote. These pin down what happens when
 * the surgery is asked to operate on the wrong text — a model read from an older version, a value
 * the literal cannot hold, a name the document cannot write — and what the form says when an edit
 * leaves something behind that the user would not otherwise see.
 */
class ShapeFormEditSafetyTest {

    private static final String PREFIXES =
            """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .
            @prefix ex:   <http://example.org/shapes#> .

            """;

    private static final String EX = "http://example.org/shapes#";
    private static final String CIM = "http://iec.ch/TC57/CIM100#";

    private final ShapeFormService service = new ShapeFormService();

    private NodeShapeModel shapeNamed(String turtle, String iri) {
        return service.parse(turtle).getShapes().stream()
                .filter(shape -> shape.getIri().equals(iri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shape " + iri));
    }

    private PropertyShapeModel namedRule(String turtle, String iri) {
        return service.parse(turtle).getPropertyShapes().stream()
                .filter(rule -> iri.equals(rule.getIri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rule " + iri));
    }

    private static PropertyShapeModel ruleOn(NodeShapeModel shape, String path) {
        return shape.getProperties().stream()
                .filter(rule -> path.equals(rule.getPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rule on " + path));
    }

    private static ShapeEditRequest edit(String turtle, NodeShapeModel shape) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setShape(shape);
        return request;
    }

    private static ShapeEditRequest removal(String turtle, String iri) {
        var request = new ShapeEditRequest();
        request.setTurtle(turtle);
        request.setRemoveShapeIri(iri);
        return request;
    }

    // -------------------------------------------------------------------------
    // A model read from other text
    // -------------------------------------------------------------------------

    private static final String TWO_RULES =
            PREFIXES
                    + """
                    ex:LineShape a sh:NodeShape ;
                        sh:targetClass cim:Line ;
                        sh:property [ sh:path cim:Line.a ; sh:lessThan cim:Line.limit ; sh:minCount 1 ] ;
                        sh:property [ sh:path cim:Line.b ; sh:minCount 1 ] .
                    """;

    @Test
    void anEditMadeOnAnOlderReadingOfTheDocumentIsRefused() {
        var model = shapeNamed(TWO_RULES, EX + "LineShape");
        var typed = TWO_RULES.replace("sh:targetClass cim:Line ;", "sh:targetClass cim:Line2 ;");
        model.setName("renamed");
        var request = edit(typed, model);
        request.setBaseTurtle(TWO_RULES);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(request))
                .withMessageContaining("changed since the form was read");
    }

    @Test
    void anEditNamingTheTextItWasReadFromIsApplied() {
        var model = shapeNamed(TWO_RULES, EX + "LineShape");
        model.setName("renamed");
        var request = edit(TWO_RULES, model);
        request.setBaseTurtle(TWO_RULES);

        assertThat(service.apply(request).getTurtle()).contains("sh:name \"renamed\"");
    }

    @Test
    void aRuleThatMovedInTheTextIsNotEditedThroughTheOldPosition() {
        // Without a base text the form cannot know the document changed, but it can see that the
        // rule it would write into is written about another property than the one it was told.
        var model = shapeNamed(TWO_RULES, EX + "LineShape");
        var swapped =
                PREFIXES
                        + """
                        ex:LineShape a sh:NodeShape ;
                            sh:targetClass cim:Line ;
                            sh:property [ sh:path cim:Line.b ; sh:minCount 1 ] ;
                            sh:property [ sh:path cim:Line.a ; sh:lessThan cim:Line.limit ; sh:minCount 1 ] .
                        """;
        ruleOn(model, CIM + "Line.a").setMaxCount(1);

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(swapped, model)))
                .withMessageContaining("Reload");
    }

    @Test
    void aRulesPathCanStillBeChanged() {
        var model = shapeNamed(TWO_RULES, EX + "LineShape");
        ruleOn(model, CIM + "Line.b").setPath(CIM + "Line.c");

        var written = service.apply(edit(TWO_RULES, model)).getTurtle();

        assertThat(written).isEqualTo(TWO_RULES.replace("cim:Line.b", "cim:Line.c"));
    }

    // -------------------------------------------------------------------------
    // Checking what an edit produced
    // -------------------------------------------------------------------------

    @Test
    void aResultThatDoesNotParseIsRefused() {
        var before = ShapesTurtleParser.parse(TWO_RULES).graph();
        var broken = TWO_RULES.replace("sh:minCount 1 ] .", "sh:minCount 1 ] ; .\n ex:Oops");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> EditCheck.parsed(broken, before, List.of(EX + "LineShape")))
                .withMessageContaining("would no longer parse");
    }

    @Test
    void aResultThatChangedAnotherSubjectIsRefused() {
        var turtle = TWO_RULES + "ex:Other a sh:NodeShape ; sh:targetClass cim:Other .\n";
        var before = ShapesTurtleParser.parse(turtle).graph();
        var drifted = turtle.replace("cim:Other .", "cim:Elsewhere .");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> EditCheck.parsed(drifted, before, List.of(EX + "LineShape")))
                .withMessageContaining("outside");
        // The subject the edit was about may change as it likes.
        var edited = turtle.replace("cim:Line ;", "cim:Line2 ;");
        assertThat(EditCheck.parsed(edited, before, List.of(EX + "LineShape"))).isNotNull();
    }

    @Test
    void aResultThatDoesNotSayWhatTheFormAskedForIsRefused() {
        var asked = shapeNamed(TWO_RULES, EX + "LineShape");
        asked.setSeverity("http://www.w3.org/ns/shacl#Violation");
        var got = shapeNamed(TWO_RULES, EX + "LineShape");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> EditCheck.assertReadsAs(asked, got))
                .withMessageContaining("severity");
    }

    // -------------------------------------------------------------------------
    // Values
    // -------------------------------------------------------------------------

    @Test
    void aNumberTheLiteralsDatatypeCannotHoldIsRefused() {
        var turtle =
                PREFIXES
                        + """
                        ex:LineShape a sh:NodeShape ; sh:targetClass cim:Line ;
                            sh:property [ sh:path cim:Line.a ; sh:minInclusive "0"^^xsd:integer ] .
                        """;
        var model = shapeNamed(turtle, EX + "LineShape");
        ruleOn(model, CIM + "Line.a").setMinInclusive("1.5");

        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(edit(turtle, model)))
                .withMessageContaining("1.5")
                .withMessageContaining("integer");

        ruleOn(model, CIM + "Line.a").setMinInclusive("2");
        assertThat(service.apply(edit(turtle, model)).getTurtle())
                .contains("sh:minInclusive \"2\"^^xsd:integer");
    }

    // -------------------------------------------------------------------------
    // Naming a copy
    // -------------------------------------------------------------------------

    private static final String SHARED =
            PREFIXES
                    + """
                    ex:Cardinality a sh:PropertyShape ; sh:path cim:Line.a ; sh:minCount 1 .
                    ex:LineShape a sh:NodeShape ; sh:targetClass cim:Line ;
                        sh:property ex:Cardinality ; sh:node ex:Elsewhere .
                    ex:OtherShape a sh:NodeShape ; sh:targetClass cim:Other ;
                        sh:property ex:Cardinality .
                    """;

    private ShapeEditRequest split(String newIri) {
        var rule = namedRule(SHARED, EX + "Cardinality");
        rule.setMinCount(0);
        var split = new PropertyShapeSplit();
        split.setNewIri(newIri);
        split.setNodeShapeIri(EX + "LineShape");
        split.setSourceIndex(0);
        var request = new ShapeEditRequest();
        request.setTurtle(SHARED);
        request.setPropertyShape(rule);
        request.setSplit(split);
        return request;
    }

    @Test
    void aCopyNamedWithTheDocumentsPrefixIsWrittenThatWay() {
        var written = service.apply(split("ex:LineCardinality")).getTurtle();

        assertThat(written).doesNotContain("<ex:LineCardinality>");
        assertThat(namedRule(written, EX + "LineCardinality").getMinCount()).isZero();
    }

    @Test
    void aCopyIsNotNamedAfterSomethingTheDocumentOnlyRefersTo() {
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(split(EX + "Elsewhere")))
                .withMessageContaining("already");
    }

    @Test
    void aCopyNamedWithAPrefixTheDocumentDoesNotBindIsRefused() {
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(split("zz:LineCardinality")))
                .withMessageContaining("zz:LineCardinality");
        assertThat(service.apply(split("urn:example:copy")).getTurtle())
                .contains("<urn:example:copy>");
    }

    // -------------------------------------------------------------------------
    // Removing a shape something else relies on
    // -------------------------------------------------------------------------

    private static final String REFERENCED =
            PREFIXES
                    + """
                    ex:Only a sh:PropertyShape ; sh:path cim:Line.a ; sh:minCount 1 .
                    ex:Both a sh:PropertyShape ; sh:path cim:Line.b ; sh:minCount 1 .
                    ex:LineShape a sh:NodeShape ; sh:targetClass cim:Line ;
                        sh:property ex:Only , ex:Both ;
                        sh:node ex:PartShape .
                    ex:PartShape a sh:NodeShape ;
                        sh:property ex:Both .
                    ex:EitherShape a sh:NodeShape ; sh:targetClass cim:Other ;
                        sh:or ( ex:PartShape [ sh:path cim:Other.x ; sh:minCount 1 ] ) .
                    """;

    @Test
    void removingAShapeOthersReferToSaysWhichOfThemDo() {
        var result = service.apply(removal(REFERENCED, EX + "PartShape"));

        assertThat(result.getTurtle()).doesNotContain("ex:PartShape a sh:NodeShape");
        assertThat(result.getWarnings())
                .anySatisfy(
                        warning ->
                                assertThat(warning)
                                        .contains("ex:LineShape")
                                        .contains("ex:EitherShape"));
    }

    @Test
    void removingTheLastShapeUsingARuleRemovesTheRuleAsWell() {
        var result = service.apply(removal(REFERENCED, EX + "LineShape"));
        var after = ShapesTurtleParser.parse(result.getTurtle()).graph();

        assertThat(after.contains(NodeFactory.createURI(EX + "Only"), Node.ANY, Node.ANY))
                .isFalse();
        // Still used by ex:PartShape, so it stays.
        assertThat(after.contains(NodeFactory.createURI(EX + "Both"), Node.ANY, Node.ANY)).isTrue();
        assertThat(result.getWarnings()).anySatisfy(w -> assertThat(w).contains("ex:Only"));
    }

    @Test
    void aRuleNothingUsesCanBeRemovedAndOneStillUsedSaysSo() {
        var unused = REFERENCED.replace("sh:property ex:Only , ex:Both ;", "sh:property ex:Both ;");

        var removed = service.apply(removal(unused, EX + "Only"));
        assertThat(removed.getTurtle()).doesNotContain("ex:Only");
        assertThat(removed.getWarnings()).isEmpty();

        var stillUsed = service.apply(removal(REFERENCED, EX + "Both"));
        assertThat(stillUsed.getWarnings())
                .anySatisfy(
                        warning ->
                                assertThat(warning)
                                        .contains("ex:LineShape")
                                        .contains("ex:PartShape"));
    }

    // -------------------------------------------------------------------------
    // Pathological input
    // -------------------------------------------------------------------------

    @Test
    void aDocumentNestedTooDeeplyToReadIsReportedRatherThanCrashingTheServer() {
        int depth = 20_000;
        var turtle =
                PREFIXES
                        + "ex:Deep a sh:NodeShape ; sh:targetClass cim:Line ; sh:and "
                        + "( ".repeat(depth)
                        + ") ".repeat(depth)
                        + ".\n";

        assertThat(service.parse(turtle).getParseError()).isNotNull();
        assertThatExceptionOfType(ResourceConflictException.class)
                .isThrownBy(() -> service.apply(removal(turtle, EX + "Deep")));
        assertThat(ShapesTurtleParser.parse(turtle).failed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // What the form is sent
    // -------------------------------------------------------------------------

    @Test
    void aSharedRuleListsItsUsersOnceRatherThanUnderEveryShape() {
        var form = service.parse(SHARED);

        assertThat(namedRule(SHARED, EX + "Cardinality").getUsedBy())
                .containsExactly(EX + "LineShape", EX + "OtherShape");
        assertThat(form.getShapes())
                .flatExtracting(NodeShapeModel::getProperties)
                .allSatisfy(inline -> assertThat(inline.getUsedBy()).isNull());
    }
}
