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

import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationReportDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.rule.CimDatatypeDefinitionRule;
import org.rdfarchitect.services.validation.workspace.rule.CimVersionConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.DatatypeConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.DuplicatePropertyRule;
import org.rdfarchitect.services.validation.workspace.rule.EnumEntryConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.InheritanceConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.InverseAssociationConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.LabelConsistencyRule;
import org.rdfarchitect.services.validation.workspace.rule.UniqueProfileHeaderRule;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class CgmesWorkspaceValidationTest extends WorkspaceValidationTestBase {

    private static final Path CGMES_PATH =
            Path.of("../external/entsoe-application-profiles-library/CGMES");

    private static final Path CGMES_3_PATH = CGMES_PATH.resolve("CurrentRelease/RDFS");

    private static final Path CGMES_2_PATH = CGMES_PATH.resolve("PastReleases/v2-4/Original/RDFS");

    private static final List<String> CGMES_2_FILES =
            List.of(
                    "DiagramLayoutProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "DynamicsProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "EquipmentBoundaryProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "EquipmentProfileCoreOperationShortCircuitRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "GeographicalLocationProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "StateVariableProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "SteadyStateHypothesisProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "TopologyBoundaryProfileRDFSAugmented-v2_4_15-4Sep2020.rdf",
                    "TopologyProfileRDFSAugmented-v2_4_15-4Sep2020.rdf");

    private final List<WorkspaceValidationRule> rules =
            List.of(
                    new InheritanceConsistencyRule(),
                    new DatatypeConsistencyRule(),
                    new CimDatatypeDefinitionRule(),
                    new DuplicatePropertyRule(),
                    new CimVersionConsistencyRule(),
                    new UniqueProfileHeaderRule(),
                    new InverseAssociationConsistencyRule(),
                    new EnumEntryConsistencyRule(),
                    new LabelConsistencyRule());

    private CIMProfileModel load(Path file) {
        var name = file.getFileName().toString();
        return profile(name, RDFDataMgr.loadModel(file.toString()));
    }

    private List<CIMProfileModel> cgmes3Profiles() throws IOException {
        try (Stream<Path> files = Files.list(CGMES_3_PATH)) {
            return files.filter(file -> file.toString().endsWith("-AP-Voc-RDFS2020.rdf"))
                    .sorted()
                    .map(this::load)
                    .toList();
        }
    }

    private ValidationReportDTO validate(List<CIMProfileModel> profiles) {
        return validate(rules, null, profiles.toArray(CIMProfileModel[]::new));
    }

    private static long count(ValidationReportDTO report, String messagePart) {
        return report.getIssues().stream()
                .map(ValidationIssueDTO::getMessage)
                .filter(message -> message.contains(messagePart))
                .count();
    }

    @Test
    void validate_cgmes3_hasNoErrors() throws IOException {
        var report = validate(cgmes3Profiles());

        assertThat(issues(report, ValidationSeverity.ERROR)).isEmpty();
        assertThat(report.isValid()).isTrue();
        assertThat(count(report, "none in others")).isEqualTo(8);
        assertThat(count(report, "different superclasses")).isZero();
        assertThat(count(report, "could not be read")).isZero();
        assertThat(report.getIssues())
                .filteredOn(issue -> issue.getMessage().contains("multiplicities"))
                .extracting(ValidationIssueDTO::getResourceUri)
                .containsExactly("http://iec.ch/TC57/CIM100#IdentifiedObject.name");
        assertThat(report.getIssues())
                .filteredOn(issue -> issue.getMessage().contains("multiple schemas"))
                .extracting(ValidationIssueDTO::getResourceUri)
                .contains("http://iec.ch/TC57/CIM100#IdentifiedObject.name")
                .doesNotContain("http://iec.ch/TC57/CIM100#ActivePower.value");
    }

    @Test
    void getHeader_cgmes3_readsKeywordAndVersionIri() {
        var equipment = load(CGMES_3_PATH.resolve("61970-600-2_Equipment-AP-Voc-RDFS2020.rdf"));

        var header = equipment.model().getHeader();

        assertThat(header.isPresent()).isTrue();
        assertThat(header.getKeyword()).isEqualTo("EQ");
        assertThat(header.getVersionIris())
                .containsExactly("http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0");
    }

    @Test
    void validate_cgmes2_hasNoErrors() {
        var profiles = CGMES_2_FILES.stream().map(CGMES_2_PATH::resolve).map(this::load).toList();

        var report = validate(profiles);

        assertThat(issues(report, ValidationSeverity.ERROR)).isEmpty();
        assertThat(count(report, "none in others")).isEqualTo(9);
        assertThat(count(report, "could not be read")).isZero();
    }

    @Test
    void getHeader_cgmes2_readsKeywordAndTrimmedVersionIris() {
        var ssh =
                load(
                        CGMES_2_PATH.resolve(
                                "SteadyStateHypothesisProfileRDFSAugmented-v2_4_15-4Sep2020.rdf"));

        var header = ssh.model().getHeader();

        assertThat(header.getKeyword()).isEqualTo("SSH");
        assertThat(header.getVersionIris())
                .containsExactly(
                        "http://entsoe.eu/CIM/SteadyStateHypothesis/1/1",
                        "http://iec.ch/TC57/2013/61970-456/SteadyStateHypothesis/1");
    }

    @Test
    void validate_cgmes2AndCgmes3Mixed_reportsCimVersionError() throws IOException {
        var profiles =
                List.of(
                        load(CGMES_2_PATH.resolve(CGMES_2_FILES.get(6))),
                        cgmes3Profiles().stream()
                                .filter(profile -> profile.graphUri().contains("Equipment-AP"))
                                .findFirst()
                                .orElseThrow());

        var report = validate(profiles);

        assertThat(issues(report, ValidationSeverity.ERROR))
                .extracting(ValidationIssueDTO::getRuleId)
                .contains("cim-version-consistency");
    }

    @Test
    void getHeader_graphWithoutHeader_isMissing() {
        var header = profile("empty", model()).model().getHeader();

        assertThat(header.isPresent()).isFalse();
        assertThat(header.getKeyword()).isNull();
        assertThat(header.getVersionIris()).isEmpty();
    }
}
