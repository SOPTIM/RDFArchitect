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
import org.rdfarchitect.services.validation.workspace.rule.UniqueProfileHeaderRule;

class UniqueProfileHeaderRuleTest extends WorkspaceValidationTestBase {

    private final UniqueProfileHeaderRule rule = new UniqueProfileHeaderRule();

    @Test
    void validate_uniqueHeaders_reportsNothing() {
        var eq = model();
        addHeader(eq, "EQ", "http://example.org/EQ/1");
        var ssh = model();
        addHeader(ssh, "SSH", "http://example.org/SSH/1");
        var withoutHeader = model();
        addClass(withoutHeader, "Breaker");

        var report =
                validate(
                        rule,
                        profile("EQ", eq),
                        profile("SSH", ssh),
                        profile("NOHEADER", withoutHeader));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_fileHeaderProfileWithoutOntology_reportsNothing() {
        var fileHeader = model();
        addFileHeaderPackage(fileHeader);
        var eq = model();
        addHeader(eq, "EQ", "http://example.org/EQ/1");

        var profile = profile("FH", fileHeader);
        var header = profile.model().getHeader();

        assertThat(header.isPresent()).isTrue();
        assertThat(header.getVersionIris()).isEmpty();
        assertThat(header.getVersionInfo()).isNull();

        var report = validate(rule, profile, profile("EQ", eq));

        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void validate_duplicateKeyword_reportsError() {
        var first = model();
        addHeader(first, "EQ", "http://example.org/EQ/1");
        var second = model();
        addHeader(second, "EQ", "http://example.org/EQ/2");

        var report = validate(rule, profile("first", first), profile("second", second));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .singleElement()
                .satisfies(
                        issue -> {
                            assertThat(issue.getMessage())
                                    .isEqualTo("Keyword is used by multiple schemas: EQ");
                            assertThat(issue.getOccurrences())
                                    .extracting(IssueOccurrenceDTO::getGraphUri)
                                    .containsExactly(
                                            "http://example.org/graph/first",
                                            "http://example.org/graph/second");
                        });
    }

    @Test
    void validate_duplicateVersionIri_reportsError() {
        var eq = model();
        addHeader(eq, "EQ", "http://example.org/shared/1");
        var ssh = model();
        addHeader(ssh, "SSH", "http://example.org/shared/1");

        var report = validate(rule, profile("EQ", eq), profile("SSH", ssh));

        assertThat(issues(report, ValidationSeverity.ERROR))
                .extracting(ValidationIssueDTO::getMessage)
                .containsExactly(
                        "Version IRI is used by multiple schemas: http://example.org/shared/1");
    }
}
