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
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.services.validation.workspace.rule.InheritanceConsistencyRule;

class InheritanceConsistencyRuleTest extends WorkspaceValidationTestBase {

    private final InheritanceConsistencyRule rule = new InheritanceConsistencyRule();

    @Test
    void validate_sameSuperClassInAllSchemas_reportsNothing() {
        var eq = model();
        addSuperClass(addClass(eq, "Child"), addClass(eq, "Parent"));
        var ssh = model();
        addSuperClass(addClass(ssh, "Child"), addClass(ssh, "Parent"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void validate_differentSuperClasses_reportsError() {
        var eq = model();
        addSuperClass(addClass(eq, "Child"), addClass(eq, "ParentA"));
        var ssh = model();
        addSuperClass(addClass(ssh, "Child"), addClass(ssh, "ParentB"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "Child");
                            assertThat(issue.getMessage()).contains("different superclasses");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getKeyword)
                                    .containsExactly("EQ", "SSH");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(NS + "ParentA", NS + "ParentB");
                        });
        assertThat(report.isValid()).isFalse();
    }

    @Test
    void validate_superClassMissingInOneSchema_reportsInfo() {
        var eq = model();
        addSuperClass(addClass(eq, "Child"), addClass(eq, "Parent"));
        var sv = model();
        addClass(sv, "Child");

        var report = validate(rule, profile("EQ", eq), profile("SV", sv));

        assertThat(issues(report, ValidationSeverity.INFO))
                .singleElement()
                .extracting(ValidationIssueDTO::getMessage)
                .asString()
                .contains("none in others");
    }

    @Test
    void validate_multipleSuperClassesInOneSchema_reportsWarning() {
        var eq = model();
        var child = addClass(eq, "Child");
        addSuperClass(child, addClass(eq, "ParentA"));
        addSuperClass(child, addClass(eq, "ParentB"));

        var report = validate(rule, profile("EQ", eq));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage()).contains("more than one superclass");
                            assertThat(issue.getOccurrences().getFirst().getValue())
                                    .isEqualTo(NS + "ParentA, " + NS + "ParentB");
                        });
    }

    @Test
    void validate_multipleSuperClassesInSeveralSchemas_reportsOneWarningForAllOfThem() {
        var eq = model();
        var eqChild = addClass(eq, "Child");
        addSuperClass(eqChild, addClass(eq, "ParentA"));
        addSuperClass(eqChild, addClass(eq, "ParentB"));
        var ssh = model();
        var sshChild = addClass(ssh, "Child");
        addSuperClass(sshChild, addClass(ssh, "ParentA"));
        addSuperClass(sshChild, addClass(ssh, "ParentB"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "Child");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getKeyword)
                                    .containsExactly("EQ", "SSH");
                        });
    }

    @Test
    void validate_cycleAcrossSchemas_reportsError() {
        var eq = model();
        addSuperClass(addClass(eq, "A"), addClass(eq, "B"));
        var ssh = model();
        addSuperClass(addClass(ssh, "B"), addClass(ssh, "A"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage())
                                    .isEqualTo(
                                            "Inheritance cycle: "
                                                    + NS
                                                    + "A -> "
                                                    + NS
                                                    + "B -> "
                                                    + NS
                                                    + "A");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getKeyword)
                                    .containsExactly("EQ", "SSH");
                        });
        assertThat(report.isValid()).isFalse();
    }
}
