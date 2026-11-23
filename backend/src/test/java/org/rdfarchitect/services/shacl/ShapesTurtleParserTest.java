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

import org.junit.jupiter.api.Test;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

/** Text that cannot be read is reported as findings, because validation runs while it is typed. */
class ShapesTurtleParserTest {

    private static final String PREFIXES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            """;

    @Test
    void readableTextHasNoFindings() {
        var result = ShapesTurtleParser.parse(PREFIXES + "ex:Shape a sh:NodeShape .\n");

        assertThat(result.failed()).isFalse();
        assertThat(result.findings()).isEmpty();
        assertThat(result.graph().size()).isEqualTo(1);
    }

    @Test
    void aSyntaxErrorIsAFindingAtTheLineThatHasIt() {
        var result = ShapesTurtleParser.parse(PREFIXES + "ex:Shape a sh:NodeShape .\nex:Broken a");

        assertThat(result.failed()).isTrue();
        assertThat(result.findings()).hasSize(1);
        var finding = result.findings().getFirst();
        assertThat(finding.getSeverity()).isEqualTo(ShapesValidationFinding.Severity.ERROR);
        assertThat(finding.getSource()).isEqualTo(ShapesValidationFinding.Source.SYNTAX);
        assertThat(finding.getCode()).isEqualTo(ShapesTurtleParser.PARSE_ERROR_CODE);
        assertThat(finding.getLine()).isEqualTo(5);
        assertThat(finding.getColumn()).isPositive();
    }

    @Test
    void anUndefinedPrefixIsAFinding() {
        var result = ShapesTurtleParser.parse("nope:Shape a nope:NodeShape .\n");

        assertThat(result.failed()).isTrue();
        assertThat(result.findings().getFirst().getMessage()).contains("nope");
        assertThat(result.findings().getFirst().getLine()).isEqualTo(1);
    }

    @Test
    void whatTheParserOnlyWarnsAboutDoesNotStopIt() {
        var result =
                ShapesTurtleParser.parse(
                        PREFIXES + "ex:Shape sh:minCount \"many\"^^xsd:integer .\n");

        assertThat(result.failed()).isFalse();
        assertThat(result.findings())
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getSeverity())
                                    .isEqualTo(ShapesValidationFinding.Severity.WARNING);
                            assertThat(finding.getLine()).isEqualTo(4);
                        });
        assertThat(result.graph().size()).isEqualTo(1);
    }

    @Test
    void textNestedTooDeeplyToParseIsAFindingRatherThanACrash() {
        var depth = 200_000;
        var result =
                ShapesTurtleParser.parse(
                        PREFIXES
                                + "ex:Shape ex:p "
                                + "[ ex:p ".repeat(depth)
                                + "1"
                                + " ]".repeat(depth)
                                + " .\n");

        assertThat(result.failed()).isTrue();
        assertThat(result.findings())
                .singleElement()
                .satisfies(
                        finding -> {
                            assertThat(finding.getMessage()).isEqualTo(ShapesTurtleParser.TOO_DEEP);
                            assertThat(finding.getLine()).isNull();
                        });
    }

    @Test
    void whatWasReadBeforeAFailureIsKept() {
        var result = ShapesTurtleParser.parse(PREFIXES + "ex:Shape a sh:NodeShape .\nex:Broken a");

        assertThat(result.failed()).isTrue();
        assertThat(result.graph().size()).isEqualTo(1);
    }
}
