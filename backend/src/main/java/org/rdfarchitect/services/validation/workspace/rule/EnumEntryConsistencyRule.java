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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMEnumEntry;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

@Component
public class EnumEntryConsistencyRule implements WorkspaceValidationRule {

    private static final String ID = "enum-entry-consistency";

    private static final Severity ENUMERATION_DIFFERS_SEVERITY = Severity.ERROR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        ConsistencyCheck.<ICIMEnumEntry>of(this, issues)
                .aspect(
                        ENUMERATION_DIFFERS_SEVERITY,
                        "Enum entry belongs to different enumerations in different schemas.",
                        enumEntry -> enumEntry.getDomain().getUri().toString())
                .run(context.getIndex().sharedEnumEntries());
    }
}
