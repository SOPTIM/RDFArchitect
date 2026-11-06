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
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.validation.workspace.rule.DuplicatePropertyRule;

class DuplicatePropertyRuleTest extends WorkspaceValidationTestBase {

    private final DuplicatePropertyRule rule = new DuplicatePropertyRule();

    @Test
    void validate_attributeInOneSchema_reportsNothing() {
        var eq = model();
        addAttribute(eq, "IdentifiedObject.name", addClass(eq, "IdentifiedObject"));
        var ssh = model();
        addClass(ssh, "IdentifiedObject");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_identicalAttributeInTwoSchemas_reportsOnlyDuplicateWarning() {
        var eq = model();
        addAttribute(eq, "IdentifiedObject.name", addClass(eq, "IdentifiedObject"));
        var ssh = model();
        addAttribute(ssh, "IdentifiedObject.name", addClass(ssh, "IdentifiedObject"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues())
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getSeverity()).isEqualTo(ValidationSeverity.WARNING);
                            assertThat(issue.getResourceUri())
                                    .isEqualTo(NS + "IdentifiedObject.name");
                            assertThat(issue.getMessage()).contains("multiple schemas");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getKeyword)
                                    .containsExactly("EQ", "SSH");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getUuid)
                                    .doesNotContainNull();
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getClassUUID)
                                    .doesNotContainNull()
                                    .doesNotContainAnyElementsOf(
                                            issue.getOccurrences().stream()
                                                    .map(IssueOccurrenceDTO::getUuid)
                                                    .toList());
                        });
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void validate_attributeOfDatatype_isIgnored() {
        var eq = model();
        var eqFloat = addDatatype(eq, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(eq, "ActivePower.value", eqFloat, XSD_FLOAT, "M:0..1");
        var ssh = model();
        var sshFloat = addDatatype(ssh, "ActivePower", CIMStereotypes.cimDatatypeString);
        addAttribute(ssh, "ActivePower.value", sshFloat, XSD_STRING, "M:1..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_differentDataType_reportsError() {
        var eq = model();
        addAttribute(eq, "Switch.open", addClass(eq, "Switch"), XSD_STRING, "M:0..1");
        var ssh = model();
        addAttribute(ssh, "Switch.open", addClass(ssh, "Switch"), XSD_FLOAT, "M:0..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage()).contains("data types");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(XSD_STRING, XSD_FLOAT);
                        });
        assertThat(report.isValid()).isFalse();
    }

    @Test
    void validate_equivalentMultiplicityNotation_reportsNoMultiplicityWarning() {
        var eq = model();
        addAttribute(eq, "Region.name", addClass(eq, "Region"), XSD_STRING, "M:1");
        var ssh = model();
        addAttribute(ssh, "Region.name", addClass(ssh, "Region"), XSD_STRING, "M:1..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(report.getIssues())
                .extracting(ValidationIssueDTO::getMessage)
                .noneMatch(message -> message.contains("multiplicities"));
    }

    @Test
    void validate_differentMultiplicity_reportsWarning() {
        var eq = model();
        addAttribute(eq, "Region.name", addClass(eq, "Region"), XSD_STRING, "M:0..1");
        var ssh = model();
        addAttribute(ssh, "Region.name", addClass(ssh, "Region"), XSD_STRING, "M:1..1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .filteredOn(issue -> issue.getMessage().contains("multiplicities"))
                .singleElement()
                .extracting(
                        issue ->
                                issue.getOccurrences().stream()
                                        .map(IssueOccurrenceDTO::getValue)
                                        .toList())
                .asList()
                .containsExactly("0..1", "1..1");
    }

    @Test
    void validate_attributeWithDifferentDomain_reportsError() {
        var eq = model();
        addAttribute(eq, "Common.name", addClass(eq, "Switch"));
        var ssh = model();
        addAttribute(ssh, "Common.name", addClass(ssh, "Breaker"));

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage())
                                    .isEqualTo(
                                            "Attribute belongs to different classes in different schemas.");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly(NS + "Switch", NS + "Breaker");
                        });
    }

    @Test
    void validate_associationWithDifferentDomain_reportsError() {
        var eq = model();
        addAssociation(
                eq, "Terminal.Node", addClass(eq, "TerminalA"), addClass(eq, "Node"), "Node.T");
        var tp = model();
        addAssociation(
                tp, "Terminal.Node", addClass(tp, "TerminalB"), addClass(tp, "Node"), "Node.T");

        var report = validate(rule, profile("EQ", eq), profile("TP", tp));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .extracting(ValidationIssueDTO::getMessage)
                .contains("Association belongs to different classes in different schemas.");
    }

    @Test
    void validate_associationWithDifferentMultiplicity_reportsWarning() {
        var eq = model();
        addAssociation(
                eq, "Terminal.Node", addClass(eq, "Terminal"), addClass(eq, "Node"), "Node.T");
        var tp = model();
        var association =
                addAssociation(
                        tp,
                        "Terminal.Node",
                        addClass(tp, "Terminal"),
                        addClass(tp, "Node"),
                        "Node.T");
        association.removeAll(CIMS.multiplicity);
        association.addProperty(CIMS.multiplicity, tp.createResource(CIMS_NS + "M:1..1"));

        var report = validate(rule, profile("EQ", eq), profile("TP", tp));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .filteredOn(issue -> issue.getMessage().contains("multiplicities"))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage())
                                    .isEqualTo(
                                            "Association has different multiplicities in different schemas.");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getValue)
                                    .containsExactly("0..1", "1..1");
                        });
    }

    @Test
    void validate_unreadableAttribute_reportsWarning() {
        var eq = model();
        addAttribute(eq, "Switch.open", addClass(eq, "Switch"));
        var ssh = model();
        addAttribute(ssh, "Switch.open", addClass(ssh, "Switch")).removeAll(CIMS.datatype);

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .filteredOn(issue -> issue.getMessage().startsWith("Resource could not be read"))
                .singleElement()
                .satisfies(
                        issue ->
                                assertThat(issue.getOccurrences())
                                        .extracting(IssueOccurrenceDTO::getKeyword)
                                        .containsExactly("SSH"));
    }

    @Test
    void validate_associationWithDifferentRange_reportsError() {
        var eq = model();
        addAssociation(
                eq,
                "Terminal.Node",
                addClass(eq, "Terminal"),
                addClass(eq, "NodeA"),
                "Node.Terminal");
        var tp = model();
        addAssociation(
                tp,
                "Terminal.Node",
                addClass(tp, "Terminal"),
                addClass(tp, "NodeB"),
                "Node.Terminal");

        var report = validate(rule, profile("EQ", eq), profile("TP", tp));

        assertThat(issues(report, ValidationSeverity.WARNING))
                .extracting(ValidationIssueDTO::getMessage)
                .containsExactly("Association is defined in multiple schemas.");
        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .extracting(ValidationIssueDTO::getMessage)
                .asString()
                .contains("points to different classes");
    }
}
