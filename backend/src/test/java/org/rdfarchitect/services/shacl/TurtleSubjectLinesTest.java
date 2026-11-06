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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class TurtleSubjectLinesTest {

    private static final String SHACL = "http://www.w3.org/ns/shacl#";
    private static final String EX = "http://example.org/";

    private static PrefixMapping prefixes() {
        return new PrefixMappingImpl().setNsPrefix("sh", SHACL).setNsPrefix("ex", EX);
    }

    private static Optional<TurtleSubjectLines.Statement> locate(String turtle, String iri) {
        return TurtleSubjectLines.statements(turtle).stream()
                .filter(s -> iri.equals(TurtleSubjectLines.expand(s.subjectToken(), prefixes())))
                .findFirst();
    }

    private static String textOf(String turtle, TurtleSubjectLines.Statement statement) {
        return turtle.substring(statement.start(), statement.end());
    }

    @Test
    void aFullStopRightBeforeTheNextStatementEndsThisOne() {
        var turtle =
                "@prefix ex: <http://example.org/> .\n"
                        + "ex:First ex:p <urn:o>.ex:Second ex:p \"x\".<urn:Third> ex:p [ ex:q 1 ].ex:Fourth ex:p 1 .\n";

        assertThat(TurtleSubjectLines.statements(turtle).stream().map(s -> textOf(turtle, s)))
                .contains(
                        "ex:First ex:p <urn:o>.",
                        "ex:Second ex:p \"x\".",
                        "<urn:Third> ex:p [ ex:q 1 ].",
                        "ex:Fourth ex:p 1 .");
    }

    @Test
    void aDotInsideAPrefixedNameBeforeANameStaysInIt() {
        var turtle = "@prefix ex: <http://example.org/> .\nex:S ex:p ex:a.b .\n";

        assertThat(TurtleSubjectLines.statements(turtle).stream().map(s -> textOf(turtle, s)))
                .contains("ex:S ex:p ex:a.b .");
    }

    @Test
    void aSubjectEndsWhereACommentOrAnIriStarts() {
        var commented = "@prefix ex: <http://example.org/> .\nex:S# comment\n ex:p ex:o .\n";
        var glued = "@prefix ex: <http://example.org/> .\nex:S<urn:p> <urn:o> .\n";

        assertThat(locate(commented, EX + "S")).isPresent();
        assertThat(locate(glued, EX + "S")).isPresent();
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

        var statement = locate(turtle, EX + "Second").orElseThrow();

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

        var statement = locate(turtle, EX + "Shape").orElseThrow();

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

        var statement = locate(turtle, EX + "Shape").orElseThrow();

        assertThat(textOf(turtle, statement)).endsWith("] .");
        assertThat(textOf(turtle, statement)).contains("SELECT $this");
        assertThat(locate(turtle, EX + "After")).isPresent();
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

        var statement = locate(turtle, EX + "Shape").orElseThrow();

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

        var statement = locate(turtle, EX + "Shape").orElseThrow();

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

        var statement = locate(turtle, EX + "Shape").orElseThrow();

        assertThat(textOf(turtle, statement)).isEqualTo("ex:Shape a sh:NodeShape .");
    }

    @Test
    void findsAShapeWrittenAsAnAbsoluteIri() {
        var turtle = "<http://example.org/Shape> a <" + SHACL + "NodeShape> .\n";

        var statement = locate(turtle, EX + "Shape").orElseThrow();

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

        assertThat(locate(turtle, EX + "Missing")).isEmpty();
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

        assertThat(TurtleSubjectLines.statements(turtle))
                .extracting(TurtleSubjectLines.Statement::subjectToken)
                .containsExactly("ex:One", "ex:Two");
    }

    @Test
    void copesWithAnEmptyOrUnterminatedDocument() {
        assertThat(TurtleSubjectLines.statements("")).isEmpty();
        assertThat(TurtleSubjectLines.statements(null)).isEmpty();

        var unterminated = "@prefix ex: <http://example.org/> .\n\nex:Shape a ex:Thing ;\n";
        assertThat(locate(unterminated, EX + "Shape")).isPresent();
    }

    @Test
    void aLongStringEndingInAnEscapedQuoteDoesNotEndBeforeItsDelimiter() {
        // `\"` right before the closing `"""` is part of the string, so the delimiter is the three
        // quotes after it. Taking the first three quotes as the end reads the rest as Turtle.
        for (var quote : List.of("\"", "'")) {
            var delimiter = quote.repeat(3);
            var turtle =
                    "ex:Shape sh:description "
                            + delimiter
                            + "said \\"
                            + quote
                            + "no\\"
                            + quote
                            + delimiter
                            + " ; sh:name \"n\" .\nex:Other a sh:NodeShape .\n";

            var statements = TurtleSubjectLines.statements(turtle);

            assertThat(statements)
                    .extracting(TurtleSubjectLines.Statement::subjectToken)
                    .containsExactly("ex:Shape", "ex:Other");
            assertThat(textOf(turtle, statements.get(0))).endsWith("sh:name \"n\" .");
        }
    }

    @Test
    void anEscapedCharacterInALocalNameIsPartOfTheName() {
        // `\.` does not end the statement and `\#` does not start a comment: both are characters
        // of the name, and the name means the unescaped IRI.
        var turtle =
                """
                ex:Shape sh:targetClass ex:A\\. ;
                    sh:name ex:B\\#c .
                ex:Other a sh:NodeShape .
                """;

        var statements = TurtleSubjectLines.statements(turtle);

        assertThat(statements)
                .extracting(TurtleSubjectLines.Statement::subjectToken)
                .containsExactly("ex:Shape", "ex:Other");
        assertThat(TurtleSubjectLines.expand("ex:A\\.", prefixes())).isEqualTo(EX + "A.");
    }

    // -------------------------------------------------------------------------
    // The line each subject starts on
    // -------------------------------------------------------------------------

    @Test
    void reportsTheLineEachSubjectStartsOn() {
        var turtle =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .

                # A comment
                ex:First a sh:NodeShape ;
                    sh:select \"""
                        SELECT ?this WHERE { ?this a ?type }
                    \""" .

                ex:Second a sh:NodeShape .
                """;

        var lines = TurtleSubjectLines.linesBySubject(turtle, prefixes());

        // Counted past the newlines inside the triple-quoted literal, which is where the embedded
        // SPARQL of a real constraints file lives.
        assertThat(lines).containsEntry(EX + "First", 5).containsEntry(EX + "Second", 10);
    }

    @Test
    void theFirstStatementOfASubjectWrittenTwiceGivesItsLine() {
        var turtle =
                """
                ex:Shape a sh:NodeShape .
                ex:Other a sh:NodeShape .
                ex:Shape sh:targetClass ex:Thing .
                """;

        assertThat(TurtleSubjectLines.linesBySubject(turtle, prefixes()))
                .containsEntry(EX + "Shape", 1)
                .containsEntry(EX + "Other", 2);
    }

    @Test
    void aBlankNodeSubjectHasNoLine() {
        var turtle = "_:b sh:path ex:p .\n[] sh:path ex:q .\n";

        assertThat(TurtleSubjectLines.linesBySubject(turtle, prefixes())).isEmpty();
    }

    @Test
    void hasNothingToSayAboutTextThatIsNotThere() {
        assertThat(TurtleSubjectLines.linesBySubject(null, prefixes())).isEmpty();
        assertThat(TurtleSubjectLines.linesBySubject("", prefixes())).isEmpty();
    }
}
