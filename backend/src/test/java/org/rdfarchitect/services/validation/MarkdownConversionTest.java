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

package org.rdfarchitect.services.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.SchemaValidationReportDTO;

import java.util.List;

public class MarkdownConversionTest extends SchemaValidationTestBase {

    @Test
    void convertToMarkdown_emptyIssues_reportsNoIssues() {
        var report = SchemaValidationReportDTO.builder().valid(true).issues(List.of()).build();
        var title = "Schema Validation Report";

        var markdown = markdownService.convertToMarkdown(report, title);

        assertThat(markdown)
                .contains("# Schema Validation Report")
                .contains("**Status:** Valid")
                .contains("No issues found.");
    }

    @Test
    void convertToMarkdown_onlyInfoIssues_omitsThemAndReportsNoErrorsOrWarnings() {
        var title = "Schema Validation Report";
        var report =
                SchemaValidationReportDTO.builder()
                        .valid(true)
                        .issues(
                                List.of(
                                        SchemaValidationIssueDTO.builder()
                                                .severity(SchemaValidationIssueDTO.Severity.INFO)
                                                .resourceUri(NS + "Ontology")
                                                .message(
                                                        "Optional profile header field is not set: <x>")
                                                .build()))
                        .build();

        var markdown = markdownService.convertToMarkdown(report, title);

        assertThat(markdown)
                .contains("No errors or warnings found.")
                .doesNotContain("Optional profile header field is not set");
    }

    @Test
    void convertToMarkdown_errorAndWarning_rendersBothSections() {
        var title = "Schema Validation Report";
        var report =
                SchemaValidationReportDTO.builder()
                        .valid(false)
                        .issues(
                                List.of(
                                        SchemaValidationIssueDTO.builder()
                                                .severity(SchemaValidationIssueDTO.Severity.ERROR)
                                                .resourceUri(NS + "ClassA")
                                                .message("Class is missing rdfs:label.")
                                                .build(),
                                        SchemaValidationIssueDTO.builder()
                                                .severity(SchemaValidationIssueDTO.Severity.WARNING)
                                                .resourceUri(NS + "ClassB")
                                                .message("Class is missing rdfs:comment.")
                                                .build(),
                                        SchemaValidationIssueDTO.builder()
                                                .severity(SchemaValidationIssueDTO.Severity.INFO)
                                                .resourceUri(NS + "Ontology")
                                                .message(
                                                        "Optional profile header field is not set: <x>")
                                                .build()))
                        .build();

        var markdown = markdownService.convertToMarkdown(report, title);

        assertThat(markdown)
                .contains("**Status:** Invalid")
                .contains("**Errors:** 1 | **Warnings:** 1")
                .contains("## Errors")
                .contains("## Warnings")
                .contains("Class is missing rdfs:label.")
                .contains("Class is missing rdfs:comment.")
                .doesNotContain("Optional profile header field is not set"); // INFO must not appear
    }

    @Test
    void convertToMarkdown_pipeInMessage_isKeptAsIs() {
        var title = "Schema Validation Report";
        var report =
                SchemaValidationReportDTO.builder()
                        .valid(false)
                        .issues(
                                List.of(
                                        SchemaValidationIssueDTO.builder()
                                                .severity(SchemaValidationIssueDTO.Severity.ERROR)
                                                .resourceUri(NS + "ClassA")
                                                .message("value a | value b")
                                                .build()))
                        .build();

        var markdown = markdownService.convertToMarkdown(report, title);

        // No table is used anymore, so pipes no longer need escaping.
        assertThat(markdown).contains("value a | value b");
    }
}
