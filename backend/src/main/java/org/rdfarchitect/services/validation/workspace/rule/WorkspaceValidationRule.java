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

package org.rdfarchitect.services.validation.workspace.rule;

import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;

public interface WorkspaceValidationRule {

    /**
     * The id this rule reports its findings under, kebab-case, for instance {@code
     * inheritance-consistency}. It groups the findings in the UI and stays stable.
     *
     * @return the id of the rule
     */
    String id();

    /**
     * Checks the schemas of the workspace and reports what it finds.
     *
     * @param context the schemas of the workspace, their headers and the index over them
     * @param issues where to report the findings
     */
    void validate(WorkspaceValidationContext context, IssueCollector issues);
}
