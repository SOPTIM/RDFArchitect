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
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClassValidationRule implements ValidationRule {

    @Override
    public void validate(
            Model model, List<SchemaValidationIssueDTO> issues, CGMESVersion cgmesVersion) {
        model.listSubjectsWithProperty(RDF.type, RDFS.Class)
                .forEach(classResource -> validateClass(model, classResource, issues));
    }

    private void validateClass(
            Model model, Resource classResource, List<SchemaValidationIssueDTO> issues) {
        var uri = classResource.getURI();

        validateRDFSLabel(classResource, issues, uri);

        validateNamespace(model, classResource, issues, uri);

        validateCIMSBelongsToCategory(classResource, issues, uri);
    }

    private void validateRDFSLabel(
            Resource classResource, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!classResource.hasProperty(RDFS.label)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Class is missing rdfs:label.")
                            .build());
        }
    }

    private void validateNamespace(
            Model model,
            Resource classResource,
            List<SchemaValidationIssueDTO> issues,
            String uri) {
        if (hasNoNamespacePrefix(model, classResource.getNameSpace())) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.WARNING)
                            .resourceUri(uri)
                            .message(
                                    "Class namespace is not defined in the schema's prefix mapping: <"
                                            + classResource.getNameSpace()
                                            + ">")
                            .build());
        }
    }

    private void validateCIMSBelongsToCategory(
            Resource classResource, List<SchemaValidationIssueDTO> issues, String uri) {
        if (!classResource.hasProperty(CIMS.belongsToCategory)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.WARNING)
                            .resourceUri(uri)
                            .message(
                                    "Class is not assigned to a package (cims:belongsToCategory is missing).")
                            .build());
        }
    }
}
