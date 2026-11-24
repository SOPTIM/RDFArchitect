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

import org.apache.jena.irix.IRIs;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TurtleSubjectLinesTest {

    private static final String SHACL = "http://www.w3.org/ns/shacl#";
    private static final String EX = "http://example.org/";
    private static final String PREFIXES =
            "@prefix sh: <http://www.w3.org/ns/shacl#> .\n@prefix ex: <http://example.org/> .\n";

    private static PrefixMapping prefixes() {
        return new PrefixMappingImpl().setNsPrefix("sh", SHACL).setNsPrefix("ex", EX);
    }

    private static Map<String, Integer> lines(String turtle) {
        return TurtleSubjectLines.linesBySubject(turtle, IRIs.getBaseStr(), prefixes());
    }

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

                ex:Second
                    a sh:NodeShape ;
                    sh:targetClass ex:Thing .
                """;

        // Counted past the newlines inside the triple-quoted literal, which is where the embedded
        // SPARQL of a constraints file lives.
        assertThat(lines(turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "First", 5, EX + "Second", 10));
    }

    @Test
    void theFirstStatementOfASubjectWrittenTwiceGivesItsLine() {
        var turtle =
                """
                ex:Shape a sh:NodeShape .
                ex:Other a sh:NodeShape .
                ex:Shape sh:targetClass ex:Thing .
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 3, EX + "Other", 4));
    }

    @Test
    void aBlankNodeSubjectHasNoLine() {
        var turtle = PREFIXES + "_:b sh:path ex:p .\n[] sh:path ex:q .\n[ sh:path ex:r ] .\n";

        assertThat(lines(turtle)).isEmpty();
    }

    @Test
    void hasNothingToSayAboutTextThatIsNotThere() {
        assertThat(lines(null)).isEmpty();
        assertThat(lines("")).isEmpty();
    }

    @Test
    void directivesAreNotSubjects() {
        var turtle =
                """
                @prefix ex: <http://example.org/> .
                @base <http://example.org/> .
                PREFIX sh: <http://www.w3.org/ns/shacl#>
                BASE <http://example.org/>

                ex:One a sh:NodeShape .
                """;

        assertThat(lines(turtle)).containsExactlyInAnyOrderEntriesOf(Map.of(EX + "One", 6));
    }

    @Test
    void aVersionDirectiveIsNotASubject() {
        // Right before `@prefix`, the version string and the directive read as one literal with a
        // language tag.
        var turtle =
                """
                VERSION "1.2"
                @prefix ex: <http://v.example/> .
                ex:S a ex:T .
                @version "1.2" .
                ex:U a ex:T .
                """;

        assertThat(lines(turtle))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("http://v.example/S", 3, "http://v.example/U", 5));
    }

    @Test
    void sparqlStyleDirectivesNeedNoFullStop() {
        var turtle = "PREFIX ex:<http://example.org/>ex:S a ex:T.\nprefix sh: <" + SHACL + ">\n";

        assertThat(lines(turtle + "ex:U a sh:NodeShape .\n"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 1, EX + "U", 3));
    }

    @Test
    void findsASubjectWrittenAsAnAbsoluteIriWithEscapes() {
        var turtle =
                "<http://example.org/Shape> a <"
                        + SHACL
                        + "NodeShape> .\n<http://example.org/\\u0053> a 1 .";

        assertThat(lines(turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 1, EX + "S", 2));
    }

    @Test
    void aFullStopRightBeforeTheNextStatementEndsThisOne() {
        var turtle =
                "ex:First ex:p <urn:o>.ex:Second ex:p \"x\".<urn:Third> ex:p [ ex:q 1 ].ex:Fourth"
                        + " ex:p 1 .\n";

        assertThat(lines(PREFIXES + turtle))
                .containsOnlyKeys(EX + "First", EX + "Second", "urn:Third", EX + "Fourth");
    }

    @Test
    void aFullStopRightAfterALanguageTagOrASemicolonEndsTheStatement() {
        var turtle =
                """
                ex:a ex:p "x"@en.ex:b ex:p ex:o .
                ex:c ex:p ex:o;.ex:d ex:p ex:o.
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(EX + "a", 3, EX + "b", 3, EX + "c", 4, EX + "d", 4));
    }

    @Test
    void aFullStopInsideAPrefixedNameOrANumberStaysInIt() {
        var turtle =
                """
                ex:S sh:path ex:ACLineSegment.length ; ex:q 1.5 , .5 ;
                    sh:minCount 1 .
                ex:T a sh:NodeShape .
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 3, EX + "T", 5));
    }

    @Test
    void aSubjectEndsWhereACommentOrAnIriStarts() {
        var turtle = "ex:S# comment\n ex:p ex:o .\nex:T<urn:p> <urn:o> .\n";

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 3, EX + "T", 5));
    }

    @Test
    void ignoresFullStopsInCommentsStringsAndEmbeddedSparql() {
        var turtle =
                """
                # a comment. with a full stop. ex:NotASubject a sh:NodeShape .
                ex:Shape
                    sh:message "Values must be positive. Always." , "it's . # no" , 'say "hi . ' ;
                    sh:sparql [
                        sh:select \"""
                            SELECT $this WHERE { $this ex:p ?o . ?o ex:q "a." . }
                        \""" ;
                    ] .
                ex:After a sh:NodeShape .
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 4, EX + "After", 11));
    }

    @Test
    void aStringEndingInAnEscapeEndsAtItsDelimiter() {
        for (var quote : List.of("\"", "'")) {
            var delimiter = quote.repeat(3);
            var turtle =
                    PREFIXES
                            + "ex:Shape sh:description "
                            + delimiter
                            + "said \\"
                            + quote
                            + "no\\"
                            + quote
                            + delimiter
                            + " ; sh:name \"a\\\\\" .\nex:Other a sh:NodeShape .\n";

            assertThat(lines(turtle))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 3, EX + "Other", 4));
        }
    }

    @Test
    void keepsNestedBracketsTogether() {
        var turtle =
                """
                ex:Shape sh:property [
                        sh:path ex:name ;
                        sh:in ( "a" [ ex:r "]." ] ) ;
                    ] ;
                    sh:property [ sh:path ex:other ] .
                ex:After a sh:NodeShape .
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 3, EX + "After", 8));
    }

    @Test
    void anEscapedCharacterInALocalNameIsPartOfTheName() {
        var turtle =
                """
                ex:A\\. sh:name ex:B\\#c .
                ex:d\\~e a sh:NodeShape .
                ex:%41 a sh:NodeShape .
                """;

        assertThat(lines(PREFIXES + turtle))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(EX + "A.", 3, EX + "d~e", 4, EX + "%41", 5));
    }

    @Test
    void aRelativeIriResolvesAgainstTheBaseInForce() {
        var turtle =
                """
                @base <http://example.org/> .
                <S> a <T> .
                <#U> a <T> .
                BASE <http://example.org/nested/>
                <V> a <T> .
                """;

        assertThat(lines(turtle))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(EX + "S", 2, EX + "#U", 3, EX + "nested/V", 5));
    }

    @Test
    void aPrefixBoundAgainMeansWhatItMeansWhereItIsUsed() {
        var turtle =
                """
                @prefix ex: <http://a.example/> .
                ex:S a ex:T .
                @prefix ex: <http://b.example/> .

                ex:S a ex:T .
                """;

        // The graph's own prefixes hold the last binding only.
        var bound = new PrefixMappingImpl().setNsPrefix("ex", "http://b.example/");

        assertThat(TurtleSubjectLines.linesBySubject(turtle, IRIs.getBaseStr(), bound))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("http://a.example/S", 2, "http://b.example/S", 5));
    }

    @Test
    void aPrefixTheTextDoesNotDeclareComesFromTheGivenPrefixes() {
        assertThat(lines("ex:S a sh:NodeShape .\n"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 1));
    }

    @Test
    void aLoneCarriageReturnEndsALineAndAComment() {
        var turtle = "@prefix ex: <http://example.org/> .\r# c\rex:S a ex:T .\rex:U a ex:T .\r";

        assertThat(lines(turtle))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 3, EX + "U", 4));
        assertThat(lines(turtle.replace("\r", "\r\n")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "S", 3, EX + "U", 4));
    }

    @Test
    void anUnterminatedLastStatementStillHasItsLine() {
        assertThat(lines(PREFIXES + "\nex:Shape a ex:Thing ;\n"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(EX + "Shape", 4));
    }

    @Test
    void subjectsAfterALexicalErrorHaveNoLine() {
        // The parser stops at the same place, so the graph holds nothing after it either.
        var turtle = "ex:Before a ex:T .\nex:Broken ex:p \"no end\nex:After a ex:T .\n";

        assertThat(lines(PREFIXES + turtle))
                .containsEntry(EX + "Before", 3)
                .doesNotContainKey(EX + "After");
    }
}
