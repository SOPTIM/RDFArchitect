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

import org.rdfarchitect.api.dto.validation.IssueKind;
import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationReportDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;
import org.rdfarchitect.services.validation.workspace.index.ReadFailure;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects what the rules find, keeps the findings that concern the schema in scope and hands them
 * over as a report.
 */
public class IssueCollector {

    private static final ValidationSeverity INFRASTRUCTURE_SEVERITY = ValidationSeverity.WARNING;

    private static final String UNREADABLE_LABEL = "Unreadable resource";

    private static final String UNREADABLE_MESSAGE = "Resource could not be read: ";

    private static final Comparator<ValidationIssueDTO> ISSUE_ORDER =
            Comparator.comparing(ValidationIssueDTO::getSeverity)
                    .thenComparing(
                            ValidationIssueDTO::getRuleId,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                            ValidationIssueDTO::getResourceUri,
                            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final IssueOccurrences occurrences;

    private final String scopeGraphUri;

    private final List<ValidationIssueDTO> issues = new ArrayList<>();

    private final Set<String> reportedUnreadable = new HashSet<>();

    /**
     * @param occurrences builds the occurrences of a finding
     * @param scopeGraphUri the schema the report is about, or null for the whole workspace
     */
    public IssueCollector(IssueOccurrences occurrences, String scopeGraphUri) {
        this.occurrences = occurrences;
        this.scopeGraphUri = scopeGraphUri;
    }

    /**
     * Reports one finding.
     *
     * @param rule the rule that found it
     * @param resourceUri the resource it is about, or null when it is about the workspace itself
     * @param issueOccurrences one entry per schema involved, with the value found there
     */
    public void report(
            WorkspaceValidationRule rule,
            ValidationSeverity severity,
            String resourceUri,
            String message,
            List<IssueOccurrenceDTO> issueOccurrences) {
        add(
                IssueKind.RULE,
                rule.id(),
                rule.label(),
                severity,
                resourceUri,
                message,
                issueOccurrences);
    }

    /**
     * Reports a resource that could not be read, once per rule, resource and schema.
     *
     * @param e what went wrong while reading it
     */
    public void reportUnreadable(
            WorkspaceValidationRule rule,
            String resourceUri,
            Occurrence<?> occurrence,
            RuntimeException e) {
        var key = rule.id() + "|" + resourceUri + "|" + occurrence.profile().graphUri();
        if (reportedUnreadable.add(key)) {
            add(
                    IssueKind.INFRASTRUCTURE,
                    rule.id(),
                    rule.label(),
                    INFRASTRUCTURE_SEVERITY,
                    resourceUri,
                    UNREADABLE_MESSAGE + e.getMessage(),
                    List.of(occurrences.of(occurrence, null)));
        }
    }

    /** Reports a resource that could not be read while the workspace was being indexed. */
    void reportReadFailure(ReadFailure failure) {
        add(
                IssueKind.INFRASTRUCTURE,
                null,
                UNREADABLE_LABEL,
                INFRASTRUCTURE_SEVERITY,
                null,
                UNREADABLE_MESSAGE + failure.message(),
                List.of(occurrences.of(failure.profile(), failure.uuid(), null)));
    }

    /** Reports a rule that threw instead of returning its findings. */
    void reportRuleFailure(WorkspaceValidationRule rule, RuntimeException e) {
        add(
                IssueKind.INFRASTRUCTURE,
                rule.id(),
                rule.label(),
                INFRASTRUCTURE_SEVERITY,
                null,
                "Validation rule could not be executed: "
                        + e.getClass().getSimpleName()
                        + ": "
                        + e.getMessage(),
                List.of());
    }

    ValidationReportDTO toReport() {
        var scopedIssues = issues.stream().filter(this::isInScope).sorted(ISSUE_ORDER).toList();
        var hasErrors =
                scopedIssues.stream()
                        .anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
        return ValidationReportDTO.builder().valid(!hasErrors).issues(scopedIssues).build();
    }

    private void add(
            IssueKind kind,
            String ruleId,
            String ruleLabel,
            ValidationSeverity severity,
            String resourceUri,
            String message,
            List<IssueOccurrenceDTO> issueOccurrences) {
        issues.add(
                ValidationIssueDTO.builder()
                        .kind(kind)
                        .ruleId(ruleId)
                        .ruleLabel(ruleLabel)
                        .severity(severity)
                        .resourceUri(resourceUri)
                        .message(message)
                        .occurrences(List.copyOf(issueOccurrences))
                        .build());
    }

    private boolean isInScope(ValidationIssueDTO issue) {
        if (scopeGraphUri == null || issue.getOccurrences().isEmpty()) {
            return true;
        }
        return issue.getOccurrences().stream()
                .anyMatch(occurrence -> scopeGraphUri.equals(occurrence.getGraphUri()));
    }
}
