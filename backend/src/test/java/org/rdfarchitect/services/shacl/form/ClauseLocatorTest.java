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

import java.util.List;

/**
 * Splitting a statement into the clauses a form edit lands in.
 *
 * <p>Everything here is a way the separator can be hidden. Get one of them wrong and an edit is
 * written into the middle of somebody's SPARQL query, so each has a test of its own rather than
 * being covered incidentally by a shape that happens to contain it.
 */
class ClauseLocatorTest {

    /** Each clause as {@code predicate → objects}, with the objects exactly as written. */
    private static List<String> clausesOf(String turtle) {
        var statement = ShapeBlockLocator.statements(turtle).get(0);
        return ClauseLocator.of(turtle, statement).stream()
                .map(
                        clause ->
                                clause.predicateToken()
                                        + " -> "
                                        + turtle.substring(
                                                clause.objectsStart(), clause.objectsEnd()))
                .toList();
    }

    @Test
    void splitsAStatementAtItsSemicolons() {
        var turtle = "ex:S a sh:NodeShape ; sh:targetClass cim:Terminal ; sh:minCount 1 .";

        assertThat(clausesOf(turtle))
                .containsExactly(
                        "a -> sh:NodeShape", "sh:targetClass -> cim:Terminal", "sh:minCount -> 1");
    }

    @Test
    void aCommaSeparatedObjectListStaysOneClause() {
        // What 462 official shapes look like, and what made every one of them read-only: the form
        // has to see this as one clause with two objects, not as two clauses.
        var turtle = "ex:S sh:targetClass cim:AsynchronousMachine , cim:SynchronousMachine .";

        var clause = ClauseLocator.of(turtle, ShapeBlockLocator.statements(turtle).get(0)).get(0);

        assertThat(clause.objects()).hasSize(2);
        assertThat(turtle.substring(clause.objectsStart(), clause.objectsEnd()))
                .isEqualTo("cim:AsynchronousMachine , cim:SynchronousMachine");
    }

    @Test
    void aSemicolonInsideALiteralIsNotASeparator() {
        var turtle = "ex:S sh:message \"a ; b\" ; sh:minCount 1 .";

        assertThat(clausesOf(turtle))
                .containsExactly("sh:message -> \"a ; b\"", "sh:minCount -> 1");
    }

    @Test
    void aSemicolonInsideAnEmbeddedQueryIsNotASeparator() {
        // sh:sparql carries a triple-quoted SPARQL query, and SPARQL is full of semicolons.
        var turtle =
                """
                ex:S sh:sparql [ sh:select \"""
                        PREFIX cim: <http://iec.ch/TC57/CIM100#>
                        SELECT $this WHERE { $this cim:a ?x ; cim:b ?y }\"""
                     ] ;
                     sh:minCount 1 .
                """;

        assertThat(clausesOf(turtle)).hasSize(2).last().asString().isEqualTo("sh:minCount -> 1");
    }

    @Test
    void aBlankNodeIsOneObjectWithClausesOfItsOwn() {
        var turtle = "ex:S sh:property [ sh:path cim:Terminal.name ; sh:minCount 1 ] .";

        var clause = ClauseLocator.of(turtle, ShapeBlockLocator.statements(turtle).get(0)).get(0);
        var nested = clause.objects().get(0).nested();

        assertThat(nested)
                .extracting(ClauseLocator.Clause::predicateToken)
                .containsExactly("sh:path", "sh:minCount");
        assertThat(turtle.substring(nested.get(1).objectsStart(), nested.get(1).objectsEnd()))
                .isEqualTo("1");
    }

    @Test
    void aNestedBlankNodeDoesNotEndTheOuterOne() {
        var turtle = "ex:S sh:property [ sh:path [ sh:inversePath cim:a ] ; sh:minCount 1 ] .";

        var clause = ClauseLocator.of(turtle, ShapeBlockLocator.statements(turtle).get(0)).get(0);

        assertThat(clause.objects().get(0).nested())
                .extracting(ClauseLocator.Clause::predicateToken)
                .containsExactly("sh:path", "sh:minCount");
    }

    @Test
    void aCollectionIsOneObject() {
        var turtle = "ex:S sh:in ( \"a\" \"b\" ) ; sh:minCount 1 .";

        assertThat(clausesOf(turtle))
                .containsExactly("sh:in -> ( \"a\" \"b\" )", "sh:minCount -> 1");
    }

    @Test
    void aDatatypeOrLanguageBelongsToItsLiteral() {
        var turtle = "ex:S sh:message \"Broken\"@en ; sh:order \"1\"^^xsd:int ; sh:minCount 1 .";

        assertThat(clausesOf(turtle))
                .containsExactly(
                        "sh:message -> \"Broken\"@en",
                        "sh:order -> \"1\"^^xsd:int",
                        "sh:minCount -> 1");
    }

    @Test
    void aCommentBetweenClausesIsSkippedRatherThanReadAsOne() {
        var turtle =
                """
                ex:S a sh:NodeShape ;
                     # why this shape exists
                     sh:targetClass cim:Terminal .
                """;

        assertThat(clausesOf(turtle))
                .containsExactly("a -> sh:NodeShape", "sh:targetClass -> cim:Terminal");
    }

    @Test
    void aHashInsideAnAbsoluteIriIsNotAComment() {
        // Every CIM term ends in one, so treating it as a comment loses the rest of the statement.
        var turtle =
                "<http://example.org/S#s> sh:targetClass <http://iec.ch/TC57/CIM100#Terminal> ;"
                        + " sh:minCount 1 .";

        assertThat(clausesOf(turtle))
                .containsExactly(
                        "sh:targetClass -> <http://iec.ch/TC57/CIM100#Terminal>",
                        "sh:minCount -> 1");
    }

    @Test
    void theTrailingSemicolonManyFilesWriteIsNotAnEmptyClause() {
        var turtle = "ex:S sh:property [ sh:path cim:a ; sh:minCount 1 ; ] ; .";

        var clause = ClauseLocator.of(turtle, ShapeBlockLocator.statements(turtle).get(0)).get(0);

        assertThat(clause.objects().get(0).nested()).hasSize(2);
        assertThat(clausesOf(turtle)).hasSize(1);
    }

    @Test
    void aNameWithADotInItIsOneObjectAndTheFullStopIsLeftOut() {
        // cim:ACLineSegment.length is one prefixed name; the statement's own full stop is not part
        // of the last clause, so a caller may write a new clause after it.
        var turtle = "ex:S sh:path cim:ACLineSegment.length .";

        var clause = ClauseLocator.of(turtle, ShapeBlockLocator.statements(turtle).get(0)).get(0);

        assertThat(turtle.substring(clause.objectsStart(), clause.objectsEnd()))
                .isEqualTo("cim:ACLineSegment.length");
        assertThat(turtle.charAt(clause.end())).isEqualTo(' ');
    }

    @Test
    void aDecimalKeepsItsDot() {
        var turtle = "ex:S sh:order 0.1 ; sh:minCount 1 .";

        assertThat(clausesOf(turtle)).containsExactly("sh:order -> 0.1", "sh:minCount -> 1");
    }
}
