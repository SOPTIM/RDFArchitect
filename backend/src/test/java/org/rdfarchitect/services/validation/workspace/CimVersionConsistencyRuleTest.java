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
import org.rdfarchitect.api.dto.validation.workspace.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.services.validation.workspace.rule.CimVersionConsistencyRule;

class CimVersionConsistencyRuleTest extends WorkspaceValidationTestBase {

    private static final String CIM16 = "http://iec.ch/TC57/2013/CIM-schema-cim16#";
    private static final String CIM100 = "http://iec.ch/TC57/CIM100#";
    private static final String CIM100_EU = "http://iec.ch/TC57/CIM100-European#";

    private final CimVersionConsistencyRule rule = new CimVersionConsistencyRule();

    @Test
    void validate_sameCimVersion_reportsNothing() {
        var eq = model();
        addClass(eq, CIM100, "Breaker");
        addClass(eq, CIM100_EU, "BoundaryPoint");
        var ssh = model();
        addClass(ssh, CIM100, "Breaker");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_differentCimVersions_reportsError() {
        var eq = model();
        addClass(eq, CIM16, "Breaker");
        var ssh = model();
        addClass(ssh, CIM100, "Breaker");
        var dl = model();
        addClass(dl, NS, "Diagram");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh), profile("DL", dl));

        assertThat(issues(report, Severity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage()).contains("different CIM versions");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(CIM16, CIM100);
                        });
    }

    @Test
    void validate_mixedCimVersionsInOneSchema_reportsError() {
        var eq = model();
        addClass(eq, CIM16, "Breaker");
        addClass(eq, CIM100, "Switch");

        var report = validate(rule, profile("EQ", eq));

        assertThat(issues(report, Severity.ERROR))
                .extracting(WorkspaceValidationIssueDTO::getMessage)
                .containsExactlyInAnyOrder(
                        "Schema uses classes of multiple CIM versions.",
                        "Schemas of the workspace use different CIM versions.");
    }
}
