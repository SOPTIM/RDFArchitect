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

import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.services.validation.workspace.rule.LabelConsistencyRule;

class LabelConsistencyRuleTest extends WorkspaceValidationTestBase {

    private final LabelConsistencyRule rule = new LabelConsistencyRule();

    @Test
    void validate_sameLabels_reportsNothing() {
        var eq = model();
        addAttribute(eq, "Breaker.open", addClass(eq, "Breaker"));
        var ssh = model();
        addAttribute(ssh, "Breaker.open", addClass(ssh, "Breaker"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_differentClassLabel_reportsInfo() {
        var eq = model();
        addClass(eq, "Breaker");
        var ssh = model();
        var sshBreaker = addClass(ssh, "Breaker");
        sshBreaker.removeAll(RDFS.label);
        sshBreaker.addProperty(RDFS.label, ssh.createLiteral("CircuitBreaker", "en"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.INFO))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "Breaker");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("Breaker", "CircuitBreaker");
                        });
    }

    @Test
    void validate_missingAttributeLabel_reportsInfoInsteadOfFailing() {
        var eq = model();
        addAttribute(eq, "Breaker.open", addClass(eq, "Breaker"));
        var ssh = model();
        addAttribute(ssh, "Breaker.open", addClass(ssh, "Breaker")).removeAll(RDFS.label);

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues())
                .singleElement()
                .extracting(ValidationIssueDTO::getSeverity)
                .isEqualTo(ValidationSeverity.INFO);
        assertThat(report.getIssues().getFirst().getOccurrences())
                .extracting(IssueOccurrenceDTO::getValue)
                .containsExactly("Breaker.open", null);
    }
}
