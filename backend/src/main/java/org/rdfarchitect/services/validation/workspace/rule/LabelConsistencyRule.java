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

import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAssociation;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

@Component
public class LabelConsistencyRule implements WorkspaceValidationRule {

    private static final String ID = "label-consistency";

    private static final ValidationSeverity LABEL_DIFFERS_SEVERITY = ValidationSeverity.INFO;

    private static final String MESSAGE = "Label differs between schemas.";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Label";
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var index = context.getIndex();
        ConsistencyCheck.<ICIMClass>of(this, context, issues)
                .aspect(LABEL_DIFFERS_SEVERITY, MESSAGE, LabelConsistencyRule::label)
                .run(index.sharedClasses());
        ConsistencyCheck.<ICIMAttribute>of(this, context, issues)
                .aspect(LABEL_DIFFERS_SEVERITY, MESSAGE, LabelConsistencyRule::label)
                .run(index.sharedAttributes());
        ConsistencyCheck.<ICIMAssociation>of(this, context, issues)
                .aspect(LABEL_DIFFERS_SEVERITY, MESSAGE, LabelConsistencyRule::label)
                .run(index.sharedAssociations());
        ConsistencyCheck.<ICIMEnumEntry>of(this, context, issues)
                .aspect(LABEL_DIFFERS_SEVERITY, MESSAGE, LabelConsistencyRule::label)
                .run(index.sharedEnumEntries());
    }

    private static String label(ICIMResource resource) {
        var label = resource.getLabelOrNull();
        return label == null ? null : label.getValue();
    }
}
