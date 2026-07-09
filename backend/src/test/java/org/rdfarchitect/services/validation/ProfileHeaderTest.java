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

package org.rdfarchitect.services.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;

public class ProfileHeaderTest extends SchemaValidationTestBase {

    @Test
    void validateSchema_missingOntologyHeader_reportsWarning() {
        var model = ModelFactory.createDefaultModel();
        addPackage(model);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(report.isValid()).isTrue();
        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.WARNING,
                                null,
                                "Profile header is missing"))
                .isTrue();
    }

    @Test
    void validateSchema_missingRequiredVersionIri_reportsWarning() {
        var model = ModelFactory.createDefaultModel();
        var ontology = model.createResource(NS + "Ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        // only DCAT keyword, versionIRI missing
        ontology.addProperty(
                model.createProperty(KnownOntologyFields.DCAT_KEYWORD.getIri()), "cim");

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(report.isValid()).isTrue();
        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.WARNING,
                                NS + "Ontology",
                                KnownOntologyFields.OWL_VERSION_IRI.getIri()))
                .isTrue();
    }

    @Test
    void validateSchema_missingRequiredKeyword_reportsWarning() {
        var model = ModelFactory.createDefaultModel();
        var ontology = model.createResource(NS + "Ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(
                model.createProperty(OWL2.versionIRI.getURI()), model.createResource(NS + "v1"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.WARNING,
                                NS + "Ontology",
                                KnownOntologyFields.DCAT_KEYWORD.getIri()))
                .isTrue();
    }

    @Test
    void validateSchema_bothRequiredFieldsPresent_producesInfoForOptionalFields() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        // No required-field errors from the header itself.
        assertThat(
                        report.getIssues().stream()
                                .filter(
                                        i ->
                                                i.getSeverity()
                                                        == SchemaValidationIssueDTO.Severity.ERROR)
                                .anyMatch(i -> i.getMessage().contains("profile header field")))
                .isFalse();
        // But INFO issues for all other known but unset optional fields.
        assertThat(report.getIssues())
                .anyMatch(
                        i ->
                                i.getSeverity() == SchemaValidationIssueDTO.Severity.INFO
                                        && i.getMessage()
                                                .contains(
                                                        "Optional profile header field is not set"));
    }
}
