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
import org.rdfarchitect.services.validation.workspace.rule.EnumEntryConsistencyRule;

class EnumEntryConsistencyRuleTest extends WorkspaceValidationTestBase {

    private final EnumEntryConsistencyRule rule = new EnumEntryConsistencyRule();

    @Test
    void validate_subsetOfEntries_reportsNothing() {
        var eq = model();
        var eqKind = addEnumeration(eq, "Kind");
        addEnumEntry(eq, "Kind.a", eqKind);
        addEnumEntry(eq, "Kind.b", eqKind);
        var ssh = model();
        addEnumEntry(ssh, "Kind.a", addEnumeration(ssh, "Kind"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_entryInDifferentEnumerations_reportsError() {
        var eq = model();
        addEnumEntry(eq, "Kind.a", addEnumeration(eq, "KindA"));
        var ssh = model();
        addEnumEntry(ssh, "Kind.a", addEnumeration(ssh, "KindB"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "Kind.a");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(NS + "KindA", NS + "KindB");
                        });
    }
}
