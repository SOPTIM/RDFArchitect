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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

public class PropertyTest extends SchemaValidationTestBase {

    @Test
    void validateSchema_propertyMissingDomainAndMultiplicity_reportsErrors() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        addClass(model, "ClassA", pkg);
        var property = model.createResource(NS + "ClassA.attr");
        property.addProperty(RDF.type, RDF.Property);
        property.addProperty(RDFS.label, model.createLiteral("attr", "en"));
        property.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        property.addProperty(CIMS.datatype, model.createResource(XSD_STRING));
        // deliberately no domain, no multiplicity

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "missing rdfs:domain"))
                .isTrue();
        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "missing cims:multiplicity"))
                .isTrue();
    }

    @Test
    void validateSchema_propertyWithoutLabel_reportsError() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        var classA = addClass(model, "ClassA", pkg);
        var property = model.createResource(NS + "ClassA.attr");
        property.addProperty(RDF.type, RDF.Property);
        property.addProperty(RDFS.domain, classA);
        property.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        property.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        property.addProperty(CIMS.datatype, model.createResource(XSD_STRING));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "Property is missing rdfs:label"))
                .isTrue();
    }

    @Test
    void validateSchema_propertyNeitherAttributeNorAssociation_reportsWarning() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        var classA = addClass(model, "ClassA", pkg);
        var property = model.createResource(NS + "ClassA.orphan");
        property.addProperty(RDF.type, RDF.Property);
        property.addProperty(RDFS.label, model.createLiteral("orphan", "en"));
        property.addProperty(RDFS.domain, classA);
        property.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        // no attribute stereotype, no association fields

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.WARNING,
                                NS + "ClassA.orphan",
                                "neither an attribute nor an association"))
                .isTrue();
    }
}
