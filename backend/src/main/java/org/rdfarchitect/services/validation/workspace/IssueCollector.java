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

import org.rdfarchitect.api.dto.validation.workspace.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationReportDTO;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class IssueCollector {

    private static final Severity UNREADABLE_SEVERITY = Severity.WARNING;

    private static final Comparator<WorkspaceValidationIssueDTO> ISSUE_ORDER =
            Comparator.comparing(WorkspaceValidationIssueDTO::getSeverity)
                    .thenComparing(WorkspaceValidationIssueDTO::getRuleId)
                    .thenComparing(
                            WorkspaceValidationIssueDTO::getResourceUri,
                            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final WorkspaceValidationContext context;

    private final List<WorkspaceValidationIssueDTO> issues = new ArrayList<>();

    private final Set<String> reportedUnreadable = new HashSet<>();

    /**
     * @param context the workspace being validated, used to name the schemas of a finding
     */
    public IssueCollector(WorkspaceValidationContext context) {
        this.context = context;
    }

    /**
     * Reports one finding.
     *
     * @param ruleId the rule that found it
     * @param resourceUri the resource it is about, or null when it is about the workspace itself
     * @param occurrences one entry per schema involved, with the value found there
     */
    public void report(
            String ruleId,
            Severity severity,
            String resourceUri,
            String message,
            List<IssueOccurrenceDTO> occurrences) {
        issues.add(
                WorkspaceValidationIssueDTO.builder()
                        .ruleId(ruleId)
                        .severity(severity)
                        .resourceUri(resourceUri)
                        .message(message)
                        .occurrences(List.copyOf(occurrences))
                        .build());
    }

    /**
     * Reports a resource that could not be read, once per rule, resource and schema.
     *
     * @param e what went wrong while reading it
     */
    public void reportUnreadable(
            String ruleId, String resourceUri, Occurrence<?> occurrence, RuntimeException e) {
        var key = ruleId + "|" + resourceUri + "|" + occurrence.profile().graphUri();
        if (reportedUnreadable.add(key)) {
            report(
                    ruleId,
                    UNREADABLE_SEVERITY,
                    resourceUri,
                    "Resource could not be read: " + e.getMessage(),
                    List.of(occurrence(occurrence, null)));
        }
    }

    /**
     * Describes a finding in one schema, with the class it belongs to so that the UI can open it.
     *
     * @param value what the schema says, null when the finding has no value to show
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO occurrence(Occurrence<?> occurrence, Object value) {
        var ownerClass = occurrence.ownerClass();
        return occurrenceBuilder(occurrence.profile(), occurrence.resource().getUuid(), value)
                .classUUID(ownerClass.getUuid())
                .packageUUID(packageUuidOf(ownerClass))
                .build();
    }

    /**
     * Describes a finding that is about a schema as a whole, such as a duplicate keyword.
     *
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO occurrence(CIMProfileModel profile, Object value) {
        return occurrence(profile, null, value);
    }

    /**
     * Describes a finding about one resource of a schema, without the class around it.
     *
     * @param uuid the resource in that schema, or null when there is none
     * @return the occurrence for a finding
     */
    public IssueOccurrenceDTO occurrence(CIMProfileModel profile, UUID uuid, Object value) {
        return occurrenceBuilder(profile, uuid, value).build();
    }

    private IssueOccurrenceDTO.IssueOccurrenceDTOBuilder occurrenceBuilder(
            CIMProfileModel profile, UUID uuid, Object value) {
        return IssueOccurrenceDTO.builder()
                .graphUri(profile.graphUri())
                .keyword(context.getKeyword(profile))
                .uuid(uuid)
                .value(format(value));
    }

    private static UUID packageUuidOf(ICIMClass cimClass) {
        try {
            var category = cimClass.getBelongsToCategory();
            return category == null ? null : category.getUuid();
        } catch (RuntimeException _) {
            return null;
        }
    }

    WorkspaceValidationReportDTO toReport(String scopeGraphUri) {
        var scopedIssues =
                issues.stream()
                        .filter(issue -> isInScope(issue, scopeGraphUri))
                        .sorted(ISSUE_ORDER)
                        .toList();
        var hasErrors =
                scopedIssues.stream().anyMatch(issue -> issue.getSeverity() == Severity.ERROR);
        return WorkspaceValidationReportDTO.builder()
                .valid(!hasErrors)
                .issues(scopedIssues)
                .build();
    }

    private static boolean isInScope(WorkspaceValidationIssueDTO issue, String scopeGraphUri) {
        if (scopeGraphUri == null || issue.getOccurrences().isEmpty()) {
            return true;
        }
        return issue.getOccurrences().stream()
                .anyMatch(occurrence -> scopeGraphUri.equals(occurrence.getGraphUri()));
    }

    private static String format(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty()
                    ? null
                    : values.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }
}
