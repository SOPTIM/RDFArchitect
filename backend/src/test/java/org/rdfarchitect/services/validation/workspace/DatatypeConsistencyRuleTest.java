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
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.validation.workspace.rule.DatatypeConsistencyRule;

class DatatypeConsistencyRuleTest extends WorkspaceValidationTestBase {

    private final DatatypeConsistencyRule rule = new DatatypeConsistencyRule();

    @Test
    void validate_sameDatatypeInAllSchemas_reportsNothing() {
        var eq = model();
        addDatatype(eq, "Float", CIMStereotypes.primitiveString);
        var ssh = model();
        addDatatype(ssh, "Float", CIMStereotypes.primitiveString);

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_datatypeIsConcreteClassInOtherSchema_reportsError() {
        var eq = model();
        addDatatype(eq, "ActivePower", CIMStereotypes.cimDatatypeString);
        var ssh = model();
        addConcrete(addClass(ssh, "ActivePower"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "ActivePower");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("CIMDatatype", "class (concrete)");
                        });
        assertThat(report.isValid()).isFalse();
    }

    @Test
    void validate_enumerationIsCompoundInOtherSchema_reportsError() {
        var eq = model();
        addEnumeration(eq, "Kind");
        var ssh = model();
        addDatatype(ssh, "Kind", CIMStereotypes.compoundString);

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .extracting(
                        issue ->
                                issue.getOccurrences().stream()
                                        .map(IssueOccurrenceDTO::getValue)
                                        .toList())
                .asList()
                .containsExactly("enumeration", "Compound");
    }

    @Test
    void validate_datatypeConcreteInOneSchemaOnly_reportsNothing() {
        var eq = model();
        addConcrete(addEnumeration(eq, "Kind"));
        var ssh = model();
        addEnumeration(ssh, "Kind");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_classWithoutDatatypeStereotypeInAllSchemas_reportsNothing() {
        var eq = model();
        addConcrete(addClass(eq, "Breaker"));
        var ssh = model();
        addClass(ssh, "Breaker");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }
}
