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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

@Component
public class DatatypeConsistencyRule implements WorkspaceValidationRule {

    private static final String ID = "datatype-consistency";

    private static final Severity KIND_DIFFERS_SEVERITY = Severity.ERROR;

    private static final String CLASS_KIND = "class";

    private static final String CONCRETE_SUFFIX = " (concrete)";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        ConsistencyCheck.<ICIMClass>of(this, issues)
                .aspect(
                        KIND_DIFFERS_SEVERITY,
                        "Datatype is defined as a different kind of class in another schema.",
                        DatatypeConsistencyRule::kindOf)
                .run(
                        ConsistencyCheck.whereAny(
                                this,
                                issues,
                                context.getIndex().sharedClasses(),
                                DatatypeStereotypes::isDatatype));
    }

    private static String kindOf(ICIMClass cimClass) {
        var kinds =
                DatatypeStereotypes.datatypeStereotypesOf(cimClass).stream()
                        .map(stereotype -> stereotype.substring(stereotype.lastIndexOf('#') + 1))
                        .toList();
        var kind = kinds.isEmpty() ? CLASS_KIND : String.join(", ", kinds);
        return DatatypeStereotypes.isConcrete(cimClass) ? kind + CONCRETE_SUFFIX : kind;
    }
}
