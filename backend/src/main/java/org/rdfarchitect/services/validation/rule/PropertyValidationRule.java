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

package org.rdfarchitect.services.validation.rule;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO.Severity;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.properties.CIMAttributeUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PropertyValidationRule implements ValidationRule {

    @Override
    public void validate(
            Model model, List<SchemaValidationIssueDTO> issues, CGMESVersion cgmesVersion) {
        model.listSubjectsWithProperty(RDF.type, RDF.Property)
                .forEach(property -> validateProperty(model, property, issues));
    }

    private void validateProperty(
            Model model, Resource property, List<SchemaValidationIssueDTO> issues) {
        var uri = property.getURI();

        validateRDFSLabel(property, issues, uri);

        validateNamespace(model, property, issues, uri);

        validateRDFSDomain(property, issues, uri);

        validateCIMSMultiplicity(property, issues, uri);

        var isAttribute = CIMPropertyUtils.isAttribute(property);
        var isAssociation = CIMPropertyUtils.isAssociation(property);

        if (isAttribute) {
            validateAttribute(property, issues);
        }
        if (isAssociation) {
            validateAssociation(property, issues);
        }
        validateIsAttributeOrAssociation(issues, isAttribute, isAssociation, uri);
    }

    private void validateRDFSDomain(
            Resource property, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!property.hasProperty(RDFS.domain)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Property is missing rdfs:domain.")
                            .build());
        }
    }

    private void validateCIMSMultiplicity(
            Resource property, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!property.hasProperty(CIMS.multiplicity)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Property is missing cims:multiplicity.")
                            .build());
        }
    }

    private void validateIsAttributeOrAssociation(
            List<SchemaValidationIssueDTO> issues,
            boolean isAttribute,
            boolean isAssociation,
            String uri) {
        if (!isAttribute && !isAssociation) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.WARNING)
                            .resourceUri(uri)
                            .message(
                                    "Property is neither an attribute nor an association. It may be"
                                            + " missing required properties.")
                            .build());
        }
    }

    private void validateAttribute(Resource attribute, List<SchemaValidationIssueDTO> issues) {
        var uri = attribute.getURI();
        if (validateAttributeHasDatatype(attribute, issues, uri)) {
            return;
        }
        validateAttributeDatatype(attribute, issues);
    }

    private static boolean validateAttributeHasDatatype(
            Resource attribute, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!attribute.hasProperty(CIMS.datatype) && !attribute.hasProperty(RDFS.range)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Attribute is missing cims:dataType or rdfs:range.")
                            .build());
            return true;
        }
        return false;
    }

    private void validateAttributeDatatype(
            Resource attribute, List<SchemaValidationIssueDTO> issues) {
        var uri = attribute.getURI();

        boolean datatypeExists =
                CIMAttributeUtils.hasXSDDatatype(attribute)
                        || CIMAttributeUtils.hasPrimitiveDatatype(attribute)
                        || CIMAttributeUtils.hasCIMDatatype(attribute)
                        || hasEnumDatatype(attribute);

        if (!datatypeExists) {
            var referencedDatatype = resolveReferencedDatatypeUri(attribute);
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message(
                                    "Attribute references a datatype that does not exist or is not a"
                                            + " known XSD datatype"
                                            + (referencedDatatype != null
                                                    ? ": <" + referencedDatatype + ">"
                                                    : "."))
                            .build());
        }
    }

    private boolean hasEnumDatatype(Resource attribute) {
        if (!attribute.hasProperty(RDFS.range)
                || !attribute.getProperty(RDFS.range).getObject().isResource()) {
            return false;
        }
        return CIMAttributeUtils.hasEnumAttribute(attribute);
    }

    private String resolveReferencedDatatypeUri(Resource attribute) {
        if (attribute.hasProperty(CIMS.datatype)
                && attribute.getProperty(CIMS.datatype).getObject().isResource()) {
            return attribute.getProperty(CIMS.datatype).getResource().getURI();
        }
        if (attribute.hasProperty(RDFS.range)
                && attribute.getProperty(RDFS.range).getObject().isResource()) {
            return attribute.getProperty(RDFS.range).getResource().getURI();
        }
        return null;
    }

    private void validateAssociation(Resource association, List<SchemaValidationIssueDTO> issues) {
        var uri = association.getURI();

        if (!association.hasProperty(RDFS.range)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Association is missing rdfs:range (target class).")
                            .build());
        } else {
            validateAssociationTargetExists(association, issues);
        }
    }

    private void validateAssociationTargetExists(
            Resource association, List<SchemaValidationIssueDTO> issues) {
        var uri = association.getURI();
        var rangeObject = association.getProperty(RDFS.range).getObject();

        if (!rangeObject.isResource()) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Association target (rdfs:range) is not a resource.")
                            .build());
            return;
        }

        var targetClass = rangeObject.asResource();
        if (!targetClass.hasProperty(RDF.type, RDFS.Class)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message(
                                    "Association target class does not exist: <"
                                            + targetClass.getURI()
                                            + ">")
                            .build());
        }
    }
}
