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

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationReportDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.validation.workspace.index.ReadFailure;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkspaceValidationService implements WorkspaceValidationUseCase {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceValidationService.class);

    private static final String INDEX_ID = "workspace-index";

    private static final Severity RULE_FAILURE_SEVERITY = Severity.WARNING;

    private static final Severity READ_FAILURE_SEVERITY = Severity.WARNING;

    private final DatabasePort databasePort;

    private final List<WorkspaceValidationRule> rules;

    @Override
    public WorkspaceValidationReportDTO validateWorkspace(String datasetName) {
        return validate(loadProfiles(datasetName), null);
    }

    @Override
    public WorkspaceValidationReportDTO validateSchemaInWorkspace(GraphIdentifier graphIdentifier) {
        return validate(loadProfiles(graphIdentifier.datasetName()), graphIdentifier.graphUri());
    }

    WorkspaceValidationReportDTO validate(List<CIMProfileModel> profiles, String scopeGraphUri) {
        var context = new WorkspaceValidationContext(profiles);
        var issues = new IssueCollector(context);
        rules.forEach(rule -> runRule(rule, context, issues));
        context.getBuiltIndex()
                .ifPresent(
                        index -> index.readFailures().forEach(f -> reportReadFailure(f, issues)));
        return issues.toReport(scopeGraphUri);
    }

    private void runRule(
            WorkspaceValidationRule rule,
            WorkspaceValidationContext context,
            IssueCollector issues) {
        try {
            rule.validate(context, issues);
        } catch (RuntimeException e) {
            logger.warn("Workspace validation rule \"{}\" failed.", rule.id(), e);
            issues.report(
                    rule.id(),
                    RULE_FAILURE_SEVERITY,
                    null,
                    "Validation rule could not be executed: "
                            + e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage(),
                    List.of());
        }
    }

    private void reportReadFailure(ReadFailure failure, IssueCollector issues) {
        issues.report(
                INDEX_ID,
                READ_FAILURE_SEVERITY,
                null,
                "Resource could not be read: " + failure.message(),
                List.of(issues.occurrence(failure.profile(), failure.uuid(), null)));
    }

    private List<CIMProfileModel> loadProfiles(String datasetName) {
        return CIMProfileModels.loadAll(databasePort, Map.of(), datasetName, null);
    }
}
