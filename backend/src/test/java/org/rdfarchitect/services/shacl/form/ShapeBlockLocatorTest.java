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

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.Test;

/**
 * Splitting a Turtle document into the statements a form edit can replace.
 *
 * <p>These are the tests that make surgical editing safe: everything else in the form path assumes
 * that a statement's span is exactly right, and getting it wrong would corrupt a file rather than
 * merely reformat it.
 */
class ShapeBlockLocatorTest {

    private static final String SHACL = "http://www.w3.org/ns/shacl#";
    private static final String EX = "http://example.org/";

    private static PrefixMapping prefixes() {
        return new PrefixMappingImpl().setNsPrefix("sh", SHACL).setNsPrefix("ex", EX);
    }

    private static String textOf(String turtle, ShapeBlockLocator.Statement statement) {
        return turtle.substring(statement.start(), statement.end());
    }

    @Test
    void findsAStatementAndSpansItThroughItsFullStop() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:First a sh:NodeShape .

                ex:Second
                    a sh:NodeShape ;
                    sh:targetClass ex:Thing .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Second", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement))
                .isEqualTo("ex:Second\n    a sh:NodeShape ;\n    sh:targetClass ex:Thing .");
    }

    @Test
    void doesNotEndAStatementOnTheDotInsideAPrefixedName() {
        // cim:ACLineSegment.length is one name; only a dot followed by space ends a statement.
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Shape sh:path ex:ACLineSegment.length ;
                    sh:minCount 1 .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).endsWith("sh:minCount 1 .");
        assertThat(textOf(turtle, statement)).contains("ex:ACLineSegment.length");
    }

    @Test
    void ignoresFullStopsInsideEmbeddedSparql() {
        // The SPARQL in a sh:select is full of dots, and it lives in a triple-quoted string.
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Shape
                    sh:sparql [
                        sh:select \"""
                            SELECT $this WHERE { $this ex:p ?o . ?o ex:q ?r . }
                        \""" ;
                    ] .

                ex:After a sh:NodeShape .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).endsWith("] .");
        assertThat(textOf(turtle, statement)).contains("SELECT $this");
        assertThat(ShapeBlockLocator.locate(turtle, EX + "After", prefixes())).isPresent();
    }

    @Test
    void ignoresFullStopsInCommentsAndStringLiterals() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                # a comment. with a full stop.
                ex:Shape
                    sh:message "Values must be positive. Always." ;
                    sh:minCount 1 .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).startsWith("ex:Shape");
        assertThat(textOf(turtle, statement)).endsWith("sh:minCount 1 .");
    }

    @Test
    void keepsNestedBracketsTogether() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Shape
                    sh:property [
                        sh:path ex:name ;
                        sh:in ( "a" "b" ) ;
                    ] ;
                    sh:property [ sh:path ex:other ] .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).endsWith("sh:property [ sh:path ex:other ] .");
    }

    @Test
    void copesWithSparqlStylePrefixesThatHaveNoFullStop() {
        // Turtle 1.1 allows PREFIX without a terminator; merging it into the next statement would
        // make the first shape unfindable.
        var turtle =
                """
                PREFIX sh: <http://www.w3.org/ns/shacl#>
                PREFIX ex: <http://example.org/>

                ex:Shape a sh:NodeShape .
                """;

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).isEqualTo("ex:Shape a sh:NodeShape .");
    }

    @Test
    void findsAShapeWrittenAsAnAbsoluteIri() {
        var turtle = "<http://example.org/Shape> a <" + SHACL + "NodeShape> .\n";

        var statement = ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow();

        assertThat(textOf(turtle, statement)).isEqualTo(turtle.strip());
    }

    @Test
    void reportsNothingForAShapeThatIsNotItsOwnStatement() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Other a sh:NodeShape .
                """;

        assertThat(ShapeBlockLocator.locate(turtle, EX + "Missing", prefixes())).isEmpty();
    }

    @Test
    void leavesEveryOtherByteAloneWhenReplacing() {
        var turtle =
                """
                @prefix ex: <http://example.org/> .

                # keep this comment
                ex:First a ex:Thing .

                # and this one
                ex:Second a ex:Thing .
                """;
        var statement = ShapeBlockLocator.locate(turtle, EX + "First", prefixes()).orElseThrow();

        var edited = ShapeBlockLocator.replace(turtle, statement, "ex:First a ex:Other .");

        assertThat(edited)
                .isEqualTo(
                        """
                        @prefix ex: <http://example.org/> .

                        # keep this comment
                        ex:First a ex:Other .

                        # and this one
                        ex:Second a ex:Thing .
                        """);
    }

    @Test
    void listsOnlyStatementsAndNotDirectives() {
        var turtle =
                """
                @prefix ex: <http://example.org/> .
                @base <http://example.org/> .
                PREFIX sh: <http://www.w3.org/ns/shacl#>

                ex:One a sh:NodeShape .
                ex:Two a sh:NodeShape .
                """;

        assertThat(ShapeBlockLocator.statements(turtle))
                .extracting(ShapeBlockLocator.Statement::subjectToken)
                .containsExactly("ex:One", "ex:Two");
    }

    @Test
    void copesWithAnEmptyOrUnterminatedDocument() {
        assertThat(ShapeBlockLocator.statements("")).isEmpty();
        assertThat(ShapeBlockLocator.statements(null)).isEmpty();

        var unterminated = "@prefix ex: <http://example.org/> .\n\nex:Shape a ex:Thing ;\n";
        assertThat(ShapeBlockLocator.locate(unterminated, EX + "Shape", prefixes())).isPresent();
    }

    @Test
    void findsEveryStatementASubjectIsWrittenAs() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Shape a sh:NodeShape .

                ex:Other a sh:NodeShape .

                ex:Shape sh:targetClass ex:Thing .
                """;

        var found = ShapeBlockLocator.locateAll(turtle, EX + "Shape", prefixes());

        assertThat(found).hasSize(2);
        assertThat(textOf(turtle, found.get(0))).isEqualTo("ex:Shape a sh:NodeShape .");
        assertThat(textOf(turtle, found.get(1))).isEqualTo("ex:Shape sh:targetClass ex:Thing .");
        // `locate` is the first of them, which is what a caller replacing one statement gets.
        assertThat(ShapeBlockLocator.locate(turtle, EX + "Shape", prefixes()).orElseThrow())
                .isEqualTo(found.get(0));
    }

    @Test
    void everyStatementOfASubjectIsGatheredUnderIt() {
        // How many statements a subject is written as decides whether the form may edit it, and
        // ShapeSource is where that is asked now: one scan for the whole document rather than one
        // per subject, which on a file with thousands of them was quadratic in its length.
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                ex:Shape a sh:NodeShape .
                ex:Shape sh:targetClass ex:Thing .
                ex:Other a sh:NodeShape .
                """;

        var source = ShapeSource.of(turtle, prefixes());

        assertThat(source.forSubject(EX + "Shape").statements()).hasSize(2);
        assertThat(source.forSubject(EX + "Other").statements()).hasSize(1);
        assertThat(source.forSubject(EX + "Missing")).isNull();
        assertThat(ShapeSource.of("", prefixes()).forSubject(EX + "Shape")).isNull();
    }
}
