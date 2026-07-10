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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

public class AttributeTest extends SchemaValidationTestBase {

    private Model modelWithClass() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        addClass(model, "ClassA", pkg);
        return model;
    }

    private Resource baseAttribute(Model model) {
        var attribute = model.createResource(NS + "ClassA.attr");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral("attr", "en"));
        attribute.addProperty(RDFS.domain, model.createResource(NS + "ClassA"));
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        return attribute;
    }

    @Test
    void validateSchema_attributeWithValidXsdDatatype_reportsNoDatatypeError() {
        var model = modelWithClass();
        baseAttribute(model).addProperty(CIMS.datatype, model.createResource(XSD_STRING));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains("references a datatype")))
                .isFalse();
    }

    @Test
    void validateSchema_attributeWithoutDatatypeOrRange_reportsError() {
        var model = modelWithClass();
        baseAttribute(model); // neither cims:dataType nor rdfs:range

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "missing cims:dataType or rdfs:range"))
                .isTrue();
    }

    @Test
    void validateSchema_attributeWithUnknownDatatype_reportsError() {
        var model = modelWithClass();
        baseAttribute(model)
                .addProperty(CIMS.datatype, model.createResource(NS + "NotAKnownDatatype"));

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "does not exist or is not a"))
                .isTrue();
        // The offending datatype URI is included in the message.
        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains(NS + "NotAKnownDatatype")))
                .isTrue();
    }

    @Test
    void validateSchema_attributeWithPrimitiveDatatype_reportsNoDatatypeError() {
        var model = modelWithClass();
        var primitive = model.createResource(NS + "Float");
        primitive.addProperty(RDF.type, RDFS.Class);
        primitive.addProperty(CIMS.stereotype, model.createLiteral(CIMStereotypes.primitiveString));
        baseAttribute(model).addProperty(CIMS.datatype, primitive);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains("references a datatype")))
                .isFalse();
    }

    @Test
    void validateSchema_attributeWithCimDatatype_reportsNoDatatypeError() {
        var model = modelWithClass();
        var cimDatatype = model.createResource(NS + "Voltage");
        cimDatatype.addProperty(RDF.type, RDFS.Class);
        cimDatatype.addProperty(
                CIMS.stereotype, model.createLiteral(CIMStereotypes.cimDatatypeString));
        baseAttribute(model).addProperty(CIMS.datatype, cimDatatype);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains("references a datatype")))
                .isFalse();
    }

    @Test
    void validateSchema_attributeWithEnumRange_reportsNoDatatypeError() {
        var model = modelWithClass();
        var enumeration = model.createResource(NS + "Color");
        enumeration.addProperty(RDF.type, RDFS.Class);
        enumeration.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        baseAttribute(model).addProperty(RDFS.range, enumeration);

        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(
                        report.getIssues().stream()
                                .anyMatch(i -> i.getMessage().contains("references a datatype")))
                .isFalse();
    }

    /**
     * Regression: an attribute that only defines {@code cims:dataType} (no {@code rdfs:range}) must
     * not trigger a {@link NullPointerException} in the enum-detection path.
     */
    @Test
    void validateSchema_attributeWithOnlyDatatypeNoRange_doesNotThrow() {
        var model = modelWithClass();
        baseAttribute(model)
                .addProperty(CIMS.datatype, model.createResource(NS + "NotAKnownDatatype"));

        // Must complete without throwing and still flag the unknown datatype.
        var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

        assertThat(report).isNotNull();
        assertThat(
                        hasIssue(
                                report,
                                SchemaValidationIssueDTO.Severity.ERROR,
                                NS + "ClassA.attr",
                                "does not exist or is not a"))
                .isTrue();
    }
}
