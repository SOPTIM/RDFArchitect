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

import de.soptim.opencgmes.cimxml.graph.CimProfile16;
import de.soptim.opencgmes.cimxml.graph.CimProfile17;
import de.soptim.opencgmes.cimxml.graph.CimProfile18;

import org.rdfarchitect.api.dto.validation.workspace.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class CimVersionConsistencyRule implements WorkspaceValidationRule {

    private static final String ID = "cim-version-consistency";

    private static final Severity MIXED_IN_SCHEMA_SEVERITY = Severity.ERROR;

    private static final Severity MIXED_IN_WORKSPACE_SEVERITY = Severity.ERROR;

    private static final Set<String> CIM_NAMESPACES =
            Set.of(
                    CimProfile16.CIM_NAMESPACE,
                    CimProfile17.CIM_NAMESPACE,
                    CimProfile18.CIM_NAMESPACE);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var namespacesByProfile = cimNamespacesByProfile(context);

        namespacesByProfile.forEach(
                (profile, namespaces) -> {
                    if (namespaces.size() > 1) {
                        issues.report(
                                ID,
                                MIXED_IN_SCHEMA_SEVERITY,
                                null,
                                "Schema uses classes of multiple CIM versions.",
                                List.of(issues.occurrence(profile, namespaces)));
                    }
                });

        var allNamespaces = new TreeSet<String>();
        namespacesByProfile.values().forEach(allNamespaces::addAll);
        if (allNamespaces.size() > 1) {
            var occurrences = new ArrayList<IssueOccurrenceDTO>();
            namespacesByProfile.forEach(
                    (profile, namespaces) ->
                            occurrences.add(issues.occurrence(profile, namespaces)));
            issues.report(
                    ID,
                    MIXED_IN_WORKSPACE_SEVERITY,
                    null,
                    "Schemas of the workspace use different CIM versions.",
                    occurrences);
        }
    }

    private static Map<CIMProfileModel, Set<String>> cimNamespacesByProfile(
            WorkspaceValidationContext context) {
        var result = new LinkedHashMap<CIMProfileModel, Set<String>>();
        context.getIndex()
                .classes()
                .values()
                .forEach(
                        occurrences ->
                                occurrences.forEach(
                                        occurrence -> {
                                            var namespace =
                                                    occurrence.resource().getUri().getPrefix();
                                            if (CIM_NAMESPACES.contains(namespace)) {
                                                result.computeIfAbsent(
                                                                occurrence.profile(),
                                                                _ -> new TreeSet<>())
                                                        .add(namespace);
                                            }
                                        }));
        return result;
    }
}
