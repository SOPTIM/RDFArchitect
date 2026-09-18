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
import org.rdfarchitect.models.cim.data.dto.facade.ICIMAttribute;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMResource;
import org.rdfarchitect.models.cim.data.dto.relations.AttributeValueNode;
import org.rdfarchitect.services.validation.workspace.IssueCollector;
import org.rdfarchitect.services.validation.workspace.WorkspaceValidationContext;
import org.springframework.stereotype.Component;

import java.util.TreeSet;

@Component
public class CimDatatypeDefinitionRule implements WorkspaceValidationRule {

    private static final String ID = "cim-datatype-definition";

    private static final ValidationSeverity DEFINITION_DIFFERS_SEVERITY = ValidationSeverity.ERROR;

    private static final ValidationSeverity DOCUMENTATION_DIFFERS_SEVERITY =
            ValidationSeverity.INFO;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "CIMDatatype definition";
    }

    @Override
    public void validate(WorkspaceValidationContext context, IssueCollector issues) {
        var index = context.getIndex();

        ConsistencyCheck.<ICIMClass>of(this, context, issues)
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Datatype has different attributes in different schemas.",
                        CimDatatypeDefinitionRule::attributeNames)
                .aspect(
                        DOCUMENTATION_DIFFERS_SEVERITY,
                        "Datatype has different comments in different schemas.",
                        CimDatatypeDefinitionRule::comment)
                .run(
                        ConsistencyCheck.whereAny(
                                this,
                                issues,
                                index.sharedClasses(),
                                DatatypeStereotypes::isDatatype));

        ConsistencyCheck.<ICIMAttribute>of(this, context, issues)
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Datatype attribute has different data types in different schemas.",
                        attribute -> attribute.getDataType().getUri().toString())
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Datatype attribute has different fixed values in different schemas.",
                        attribute -> value(attribute.getFixed()))
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Datatype attribute has different default values in different schemas.",
                        attribute -> value(attribute.getDefault()))
                .aspect(
                        DEFINITION_DIFFERS_SEVERITY,
                        "Datatype attribute has different multiplicities in different schemas.",
                        attribute -> Multiplicities.normalize(attribute.getMultiplicity()))
                .aspect(
                        DOCUMENTATION_DIFFERS_SEVERITY,
                        "Datatype attribute has different comments in different schemas.",
                        CimDatatypeDefinitionRule::comment)
                .run(
                        ConsistencyCheck.whereAny(
                                this,
                                issues,
                                index.sharedAttributes(),
                                DatatypeStereotypes::isDatatypeAttribute));
    }

    private static TreeSet<String> attributeNames(ICIMClass cimClass) {
        var names = new TreeSet<String>();
        for (var attribute : cimClass.getAttributes()) {
            names.add(attribute.getUri().getSuffix());
        }
        return names;
    }

    private static String comment(ICIMResource resource) {
        var comment = resource.getComment();
        return comment == null ? null : comment.getValue();
    }

    private static String value(AttributeValueNode valueNode) {
        return valueNode == null ? null : valueNode.getValue();
    }
}
