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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.SchemaValidationReportDTO;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaValidationReportToMarkdownService
        implements SchemaValidationReportToMarkdownUseCase {

    private static final String NEW_LINE = "\n";
    private static final String NO_RESOURCE_LABEL = "General";

    @Override
    public String convertToMarkdown(SchemaValidationReportDTO report) {
        var sb = new StringBuilder();

        sb.append("# Schema Validation Report").append(NEW_LINE).append(NEW_LINE);
        sb.append("Errors and warnings found during schema validation, grouped by affected ")
                .append("resource. Issues with severity INFO are omitted.")
                .append(NEW_LINE)
                .append(NEW_LINE);

        var status = report.isValid() ? "Valid" : "Invalid";
        sb.append("**Status:** ").append(status).append(NEW_LINE).append(NEW_LINE);

        var issues = report.getIssues();
        if (issues == null || issues.isEmpty()) {
            sb.append("No issues found.").append(NEW_LINE);
            return sb.toString();
        }

        // Only ERROR and WARNING issues are reported; INFO is omitted.
        var relevantIssues =
                issues.stream()
                        .filter(
                                issue ->
                                        issue.getSeverity()
                                                        == SchemaValidationIssueDTO.Severity.ERROR
                                                || issue.getSeverity()
                                                        == SchemaValidationIssueDTO.Severity
                                                                .WARNING)
                        .collect(Collectors.groupingBy(SchemaValidationIssueDTO::getSeverity));

        var errors =
                relevantIssues.getOrDefault(SchemaValidationIssueDTO.Severity.ERROR, List.of());
        var warnings =
                relevantIssues.getOrDefault(SchemaValidationIssueDTO.Severity.WARNING, List.of());

        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("No errors or warnings found.").append(NEW_LINE);
            return sb.toString();
        }

        sb.append("**Errors:** ")
                .append(errors.size())
                .append(" | **Warnings:** ")
                .append(warnings.size())
                .append(NEW_LINE)
                .append(NEW_LINE);

        appendSection(sb, "Errors", errors);
        appendSection(sb, "Warnings", warnings);

        return sb.toString();
    }

    private void appendSection(
            StringBuilder sb, String title, List<SchemaValidationIssueDTO> issues) {
        if (issues.isEmpty()) {
            return;
        }

        sb.append("## ").append(title).append(NEW_LINE).append(NEW_LINE);

        var byResource =
                issues.stream()
                        .collect(Collectors.groupingBy(SchemaValidationIssueDTO::getResourceUri));

        var sortedResourceUris =
                byResource.keySet().stream()
                        .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
                        .toList();

        for (var resourceUri : sortedResourceUris) {
            sb.append("### ")
                    .append(formatResourceHeading(resourceUri))
                    .append(NEW_LINE)
                    .append(NEW_LINE);
            for (var issue : byResource.get(resourceUri)) {
                sb.append("- ").append(issue.getMessage()).append(NEW_LINE);
            }
            sb.append(NEW_LINE);
        }
    }

    private String formatResourceHeading(String resourceUri) {
        if (resourceUri == null) {
            return NO_RESOURCE_LABEL;
        }
        return "`" + shorten(resourceUri) + "`";
    }

    /** Shortens a resource IRI to its local name; falls back to the raw value if invalid. */
    private String shorten(String value) {
        try {
            return new URI(value).getSuffix();
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
