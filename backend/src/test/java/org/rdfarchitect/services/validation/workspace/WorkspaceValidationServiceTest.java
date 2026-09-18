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
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.rule.DuplicatePropertyRule;
import org.rdfarchitect.services.validation.workspace.rule.InheritanceConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;

import java.util.List;
import java.util.UUID;

class WorkspaceValidationServiceTest extends WorkspaceValidationTestBase {

    private static final String EQ_GRAPH = "http://example.org/graph/EQ";

    private final WorkspaceValidationRule failingRule =
            new WorkspaceValidationRule() {
                @Override
                public String id() {
                    return "failing";
                }

                @Override
                public void validate(WorkspaceValidationContext context, IssueCollector issues) {
                    throw new IllegalStateException("boom");
                }
            };

    @Test
    void validate_unreadableSchema_reportsReadFailure() {
        var eq = model();
        addAttribute(eq, "IdentifiedObject.name", addClass(eq, "IdentifiedObject"));
        var profile = profile("EQ", eq);
        eq.getResource(NS + "IdentifiedObject")
                .addProperty(RDFA.uuid, UUID.randomUUID().toString());

        var report = validate(List.of(new DuplicatePropertyRule()), null, profile);

        assertThat(report.getIssues())
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getRuleId()).isEqualTo("workspace-index");
                            assertThat(issue.getSeverity()).isEqualTo(Severity.WARNING);
                            assertThat(issue.getMessage()).startsWith("Resource could not be read");
                        });
    }

    @Test
    void validate_failingRule_reportsWarningAndRunsOtherRules() {
        var eq = model();
        addAttribute(eq, "IdentifiedObject.name", addClass(eq, "IdentifiedObject"));
        var ssh = model();
        addAttribute(ssh, "IdentifiedObject.name", addClass(ssh, "IdentifiedObject"));

        var report =
                validate(
                        List.of(failingRule, new DuplicatePropertyRule()),
                        null,
                        profile("EQ", eq),
                        profile("SSH", ssh));

        assertThat(report.getIssues())
                .extracting(WorkspaceValidationIssueDTO::getRuleId)
                .containsExactly("duplicate-property", "failing");
        assertThat(report.getIssues().getLast().getMessage()).contains("boom");
        assertThat(report.getIssues().getLast().getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void validate_withScope_keepsOnlyIssuesInvolvingTheSchema() {
        var eq = model();
        addAttribute(eq, "IdentifiedObject.name", addClass(eq, "IdentifiedObject"));
        var ssh = model();
        addAttribute(ssh, "IdentifiedObject.name", addClass(ssh, "IdentifiedObject"));
        addSuperClass(addClass(ssh, "Switch"), addClass(ssh, "Equipment"));
        var sv = model();
        addClass(sv, "Switch");

        var rules = List.of(new DuplicatePropertyRule(), new InheritanceConsistencyRule());
        var profiles = List.of(profile("EQ", eq), profile("SSH", ssh), profile("SV", sv));

        var unscoped = validate(rules, null, profiles.toArray(CIMProfileModel[]::new));
        var scoped = validate(rules, EQ_GRAPH, profiles.toArray(CIMProfileModel[]::new));

        assertThat(unscoped.getIssues())
                .extracting(WorkspaceValidationIssueDTO::getRuleId)
                .containsExactly("duplicate-property", "inheritance-consistency");
        assertThat(scoped.getIssues())
                .extracting(WorkspaceValidationIssueDTO::getRuleId)
                .containsExactly("duplicate-property");
    }

    @Test
    void validate_issues_areSortedBySeverity() {
        var eq = model();
        addSuperClass(addClass(eq, "A"), addClass(eq, "B"));
        addSuperClass(addClass(eq, "Child"), addClass(eq, "ParentA"));
        var ssh = model();
        addSuperClass(addClass(ssh, "B"), addClass(ssh, "A"));
        addSuperClass(addClass(ssh, "Child"), addClass(ssh, "ParentB"));

        var report =
                validate(
                        List.of(new InheritanceConsistencyRule()),
                        null,
                        profile("EQ", eq),
                        profile("SSH", ssh));

        assertThat(report.getIssues())
                .extracting(WorkspaceValidationIssueDTO::getSeverity)
                .containsExactly(Severity.ERROR, Severity.ERROR, Severity.INFO, Severity.INFO);
    }
}
