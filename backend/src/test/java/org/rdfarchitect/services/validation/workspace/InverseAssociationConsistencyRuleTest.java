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

package org.rdfarchitect.services.validation.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.services.validation.workspace.rule.InverseAssociationConsistencyRule;

class InverseAssociationConsistencyRuleTest extends WorkspaceValidationTestBase {

    private final InverseAssociationConsistencyRule rule = new InverseAssociationConsistencyRule();

    @Test
    void validate_sameInverse_reportsNothing() {
        var eq = model();
        addAssociation(
                eq,
                "Terminal.Node",
                addClass(eq, "Terminal"),
                addClass(eq, "Node"),
                "Node.Terminals");
        var tp = model();
        addAssociation(
                tp,
                "Terminal.Node",
                addClass(tp, "Terminal"),
                addClass(tp, "Node"),
                "Node.Terminals");

        var report = validate(rule, profile("EQ", eq), profile("TP", tp));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_differentInverse_reportsError() {
        var eq = model();
        addAssociation(
                eq,
                "Terminal.Node",
                addClass(eq, "Terminal"),
                addClass(eq, "Node"),
                "Node.Terminals");
        var tp = model();
        addAssociation(
                tp, "Terminal.Node", addClass(tp, "Terminal"), addClass(tp, "Node"), "Node.Ends");

        var report = validate(rule, profile("EQ", eq), profile("TP", tp));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "Terminal.Node");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(NS + "Node.Terminals", NS + "Node.Ends");
                        });
    }
}
