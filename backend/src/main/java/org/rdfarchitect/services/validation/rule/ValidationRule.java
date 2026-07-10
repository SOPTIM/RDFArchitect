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

import java.util.List;

public interface ValidationRule {

    void validate(Model model, List<SchemaValidationIssueDTO> issues, CGMESVersion cgmesVersion);

    default boolean hasNoNamespacePrefix(Model model, String namespace) {
        return !model.getNsPrefixMap().containsValue(namespace);
    }

    default void validateRDFSLabel(
            Resource property, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!property.hasProperty(RDFS.label)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(SchemaValidationIssueDTO.Severity.ERROR)
                            .resourceUri(uri)
                            .message(
                                    property.getProperty(RDF.type)
                                                    .getObject()
                                                    .asResource()
                                                    .getLocalName()
                                            + " is missing rdfs:label.")
                            .build());
        }
    }

    default void validateNamespace(
            Model model, Resource property, List<SchemaValidationIssueDTO> issues, String uri) {
        if (hasNoNamespacePrefix(model, property.getNameSpace())) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(SchemaValidationIssueDTO.Severity.INFO)
                            .resourceUri(uri)
                            .message(
                                    property.getProperty(RDF.type)
                                                    .getObject()
                                                    .asResource()
                                                    .getLocalName()
                                            + "Class namespace is not defined in the schema's prefix mapping: <"
                                            + property.getNameSpace()
                                            + ">")
                            .build());
        }
    }
}
