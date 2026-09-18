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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.validation.workspace.rule.CimDatatypeDefinitionRule;

class CimDatatypeDefinitionRuleTest extends WorkspaceValidationTestBase {

    private final CimDatatypeDefinitionRule rule = new CimDatatypeDefinitionRule();

    private Resource addActivePower(Model model, String unit) {
        var activePower = addDatatype(model, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(model, "ActivePower.value", activePower, XSD_FLOAT, "M:0..1");
        addAttribute(model, "ActivePower.unit", activePower)
                .addProperty(CIMS.isFixed, model.createLiteral(unit));
        return activePower;
    }

    @Test
    void validate_identicalDatatypes_reportsNothing() {
        var eq = model();
        addActivePower(eq, "W");
        var ssh = model();
        addActivePower(ssh, "W");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_differentFixedUnit_reportsError() {
        var eq = model();
        addActivePower(eq, "W");
        var ssh = model();
        addActivePower(ssh, "VA");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "ActivePower.unit");
                            assertThat(issue.getMessage()).contains("fixed values");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("W", "VA");
                        });
    }

    @Test
    void validate_differentValueDataType_reportsError() {
        var eq = model();
        addActivePower(eq, "W");
        var ssh = model();
        var activePower = addDatatype(ssh, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(ssh, "ActivePower.value", activePower, XSD_STRING, "M:0..1");
        addAttribute(ssh, "ActivePower.unit", activePower)
                .addProperty(CIMS.isFixed, ssh.createLiteral("W"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .extracting(ValidationIssueDTO::getMessage)
                .asString()
                .contains("data types");
    }

    @Test
    void validate_missingAttribute_reportsError() {
        var eq = model();
        addActivePower(eq, "W");
        var ssh = model();
        var activePower = addDatatype(ssh, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(ssh, "ActivePower.value", activePower, XSD_FLOAT, "M:0..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getResourceUri()).isEqualTo(NS + "ActivePower");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(
                                            "ActivePower.unit, ActivePower.value",
                                            "ActivePower.value");
                        });
    }

    @Test
    void validate_differentComment_reportsInfo() {
        var eq = model();
        addActivePower(eq, "W").addProperty(RDFS.comment, "Active power.");
        var ssh = model();
        addActivePower(ssh, "W").addProperty(RDFS.comment, "Product of voltage and current.");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR)).isEmpty();
        assertThat(issues(report, ValidationSeverity.INFO))
                .singleElement()
                .extracting(ValidationIssueDTO::getMessage)
                .asString()
                .contains("comments");
    }

    @Test
    void validate_differentDefaultValue_reportsError() {
        var eq = model();
        addActivePower(eq, "W");
        eq.getResource(NS + "ActivePower.value").addProperty(CIMS.isDefault, eq.createLiteral("0"));
        var ssh = model();
        addActivePower(ssh, "W");
        ssh.getResource(NS + "ActivePower.value")
                .addProperty(CIMS.isDefault, ssh.createLiteral("1"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage()).contains("default values");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("0", "1");
                        });
    }

    @Test
    void validate_differentAttributeMultiplicity_reportsError() {
        var eq = model();
        addActivePower(eq, "W");
        var ssh = model();
        var activePower = addDatatype(ssh, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(ssh, "ActivePower.value", activePower, XSD_FLOAT, "M:1..1");
        addAttribute(ssh, "ActivePower.unit", activePower)
                .addProperty(CIMS.isFixed, ssh.createLiteral("W"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage())
                                    .isEqualTo(
                                            "Datatype attribute has different multiplicities in different schemas.");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("0..1", "1..1");
                        });
    }

    @Test
    void validate_differentAttributeComment_reportsInfo() {
        var eq = model();
        addActivePower(eq, "W");
        eq.getResource(NS + "ActivePower.value")
                .addProperty(RDFS.comment, "The value of the power.");
        var ssh = model();
        addActivePower(ssh, "W");
        ssh.getResource(NS + "ActivePower.value").addProperty(RDFS.comment, "The power value.");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR)).isEmpty();
        assertThat(issues(report, ValidationSeverity.INFO))
                .singleElement()
                .extracting(ValidationIssueDTO::getMessage)
                .isEqualTo("Datatype attribute has different comments in different schemas.");
    }

    @Test
    void validate_differentAttributeOnNonDatatype_isIgnored() {
        var eq = model();
        addAttribute(eq, "Breaker.open", addClass(eq, "Breaker"), XSD_STRING, "M:0..1");
        var ssh = model();
        addAttribute(ssh, "Breaker.open", addClass(ssh, "Breaker"), XSD_FLOAT, "M:1..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }
}
