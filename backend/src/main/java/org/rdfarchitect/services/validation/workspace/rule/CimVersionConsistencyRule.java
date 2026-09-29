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

import org.rdfarchitect.api.dto.validation.IssueOccurrenceDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
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

    private static final ValidationSeverity MIXED_IN_SCHEMA_SEVERITY = ValidationSeverity.ERROR;

    private static final ValidationSeverity MIXED_IN_WORKSPACE_SEVERITY = ValidationSeverity.ERROR;

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
    public String label() {
        return "CIM version";
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var namespacesByProfile = cimNamespacesByProfile(context);

        namespacesByProfile.forEach(
                (profile, namespaces) -> {
                    if (namespaces.size() > 1) {
                        issues.report(
                                this,
                                MIXED_IN_SCHEMA_SEVERITY,
                                null,
                                "Schema uses classes of multiple CIM versions.",
                                List.of(context.getOccurrences().of(profile, namespaces)));
                    }
                });

        if (useDifferentVersions(namespacesByProfile)) {
            var occurrences = new ArrayList<IssueOccurrenceDTO>();
            namespacesByProfile.forEach(
                    (profile, namespaces) ->
                            occurrences.add(context.getOccurrences().of(profile, namespaces)));
            issues.report(
                    this,
                    MIXED_IN_WORKSPACE_SEVERITY,
                    null,
                    "Schemas of the workspace use different CIM versions.",
                    occurrences);
        }
    }

    /**
     * Whether the schemas disagree about the CIM version. A single schema that mixes versions is
     * reported on its own, so it takes two schemas that do not use the same versions for the
     * workspace as a whole to be at odds.
     *
     * @return true when the schemas do not all use the same CIM versions
     */
    private static boolean useDifferentVersions(
            Map<CIMProfileModel, Set<String>> namespacesByProfile) {
        return namespacesByProfile.values().stream().distinct().count() > 1;
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
