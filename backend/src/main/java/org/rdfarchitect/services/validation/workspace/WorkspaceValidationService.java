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

import org.rdfarchitect.api.dto.validation.ValidationReportDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.select.ListGraphsUseCase;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceValidationService implements WorkspaceValidationUseCase {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceValidationService.class);

    private final DatabasePort databasePort;

    private final ListGraphsUseCase listGraphsUseCase;

    private final List<WorkspaceValidationRule> rules;

    @Override
    public ValidationReportDTO validateWorkspace(String datasetName) {
        return validate(loadProfiles(datasetName), null);
    }

    @Override
    public ValidationReportDTO validateSchemaInWorkspace(GraphIdentifier graphIdentifier) {
        return validate(loadProfiles(graphIdentifier.datasetName()), graphIdentifier.graphUri());
    }

    ValidationReportDTO validate(List<CIMProfileModel> profiles, String scopeGraphUri) {
        var context = new WorkspaceValidationContext(profiles, scopeGraphUri);
        var issues = new IssueCollector(context.getOccurrences(), scopeGraphUri);
        rules.forEach(rule -> runRule(rule, context, issues));
        context.getBuiltIndex()
                .ifPresent(index -> index.readFailures().forEach(issues::reportReadFailure));
        return issues.toReport();
    }

    private void runRule(
            WorkspaceValidationRule rule,
            WorkspaceValidationContext context,
            IssueCollector issues) {
        try {
            rule.validate(context, issues);
        } catch (RuntimeException e) {
            logger.warn("Workspace validation rule \"{}\" failed.", rule.id(), e);
            issues.reportRuleFailure(rule, e);
        }
    }

    private List<CIMProfileModel> loadProfiles(String datasetName) {
        var keywords = CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName);
        return CIMProfileModels.loadAll(databasePort, keywords, datasetName, null);
    }
}
