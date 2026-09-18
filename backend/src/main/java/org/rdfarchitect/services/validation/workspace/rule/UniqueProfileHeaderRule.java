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

import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class UniqueProfileHeaderRule implements WorkspaceValidationRule {

    private static final String ID = "unique-profile-header";

    private static final Severity DUPLICATE_KEYWORD_SEVERITY = Severity.ERROR;

    private static final Severity DUPLICATE_VERSION_IRI_SEVERITY = Severity.ERROR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var profilesByKeyword = new TreeMap<String, List<CIMProfileModel>>();
        var profilesByVersionIri = new TreeMap<String, List<CIMProfileModel>>();
        for (var profile : context.getProfiles()) {
            var header = context.getHeader(profile);
            if (!header.isPresent()) {
                continue;
            }
            if (header.getKeyword() != null) {
                profilesByKeyword
                        .computeIfAbsent(header.getKeyword(), _ -> new ArrayList<>())
                        .add(profile);
            }
            for (var versionIri : header.getVersionIris()) {
                profilesByVersionIri
                        .computeIfAbsent(versionIri, _ -> new ArrayList<>())
                        .add(profile);
            }
        }

        reportDuplicates(
                profilesByKeyword,
                DUPLICATE_KEYWORD_SEVERITY,
                "Keyword is used by multiple schemas: ",
                issues);
        reportDuplicates(
                profilesByVersionIri,
                DUPLICATE_VERSION_IRI_SEVERITY,
                "Version IRI is used by multiple schemas: ",
                issues);
    }

    private void reportDuplicates(
            Map<String, List<CIMProfileModel>> profilesByValue,
            Severity severity,
            String message,
            IssueCollector issues) {
        profilesByValue.forEach(
                (value, profiles) -> {
                    if (profiles.size() > 1) {
                        issues.report(
                                ID,
                                severity,
                                null,
                                message + value,
                                profiles.stream()
                                        .map(profile -> issues.occurrence(profile, value))
                                        .toList());
                    }
                });
    }
}
