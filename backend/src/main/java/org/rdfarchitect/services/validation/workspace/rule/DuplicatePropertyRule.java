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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAssociation;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.rdfarchitect.services.validation.workspace.index.Occurrence;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DuplicatePropertyRule implements WorkspaceValidationRule {

    private static final String ID = "duplicate-property";

    private static final Severity DUPLICATE_SEVERITY = Severity.WARNING;

    private static final Severity DEFINITION_DIFFERS_SEVERITY = Severity.ERROR;

    private static final Severity MULTIPLICITY_DIFFERS_SEVERITY = Severity.WARNING;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var index = context.getIndex();
        var attributes = withoutDatatypeAttributes(index.sharedAttributes(), issues);
        var associations = index.sharedAssociations();

        reportDuplicates(attributes, "Attribute is defined in multiple schemas.", issues);
        reportDuplicates(associations, "Association is defined in multiple schemas.", issues);

        ConsistencyCheck.<ICIMAttribute>of(this, issues)
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Attribute belongs to different classes in different schemas.",
                        attribute -> attribute.getDomain().getUri().toString())
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Attribute has different data types in different schemas.",
                        attribute -> attribute.getDataType().getUri().toString())
                .aspect(
                        MULTIPLICITY_DIFFERS_SEVERITY,
                        "Attribute has different multiplicities in different schemas.",
                        attribute -> Multiplicities.normalize(attribute.getMultiplicity()))
                .run(attributes);

        ConsistencyCheck.<ICIMAssociation>of(this, issues)
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Association belongs to different classes in different schemas.",
                        association -> association.getDomain().getUri().toString())
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Association points to different classes in different schemas.",
                        association -> association.getRange().getUri().toString())
                .aspect(
                        MULTIPLICITY_DIFFERS_SEVERITY,
                        "Association has different multiplicities in different schemas.",
                        association -> Multiplicities.normalize(association.getMultiplicity()))
                .run(associations);
    }

    private Map<String, List<Occurrence<ICIMAttribute>>> withoutDatatypeAttributes(
            Map<String, List<Occurrence<ICIMAttribute>>> attributes, IssueCollector issues) {
        var datatypeAttributes =
                ConsistencyCheck.whereAny(
                        this, issues, attributes, DatatypeStereotypes::isDatatypeAttribute);
        var result = new LinkedHashMap<>(attributes);
        result.keySet().removeAll(datatypeAttributes.keySet());
        return result;
    }

    private <T extends ICIMResource> void reportDuplicates(
            Map<String, List<Occurrence<T>>> candidates, String message, IssueCollector issues) {
        candidates.forEach(
                (uri, occurrences) ->
                        issues.report(
                                ID,
                                DUPLICATE_SEVERITY,
                                uri,
                                message,
                                occurrences.stream()
                                        .map(occurrence -> issues.occurrence(occurrence, null))
                                        .toList()));
    }
}
