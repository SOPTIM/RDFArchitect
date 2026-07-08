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
import static org.mockito.Mockito.mock;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO.Severity;
import org.rdfarchitect.api.dto.validation.SchemaValidationReportDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.validation.rule.ClassValidationRule;
import org.rdfarchitect.services.validation.rule.PackageValidationRule;
import org.rdfarchitect.services.validation.rule.ProfileHeaderValidationRule;
import org.rdfarchitect.services.validation.rule.PropertyValidationRule;

import java.util.List;
import java.util.Objects;

class SchemaValidationServiceTest {

    private static final String NS = "http://example.org#";
    private static final String CIMS_NS = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    private SchemaValidationUseCase service;
    private SchemaValidationReportToMarkdownUseCase markdownService;

    @BeforeEach
    void setUp() {
        service =
                new SchemaValidationService(
                        mock(DatabasePort.class),
                        List.of(
                                new ProfileHeaderValidationRule(),
                                new PackageValidationRule(),
                                new ClassValidationRule(),
                                new PropertyValidationRule()));
        markdownService = new SchemaValidationReportToMarkdownService();
    }

    // ─── Helper builders ──────────────────────────────────────────────────────

    /** Adds a minimal, valid owl:Ontology header (with both required fields) to the model. */
    private void addValidOntologyHeader(Model model) {
        var ontology = model.createResource(NS + "Ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(
                model.createProperty(OWL2.versionIRI.getURI()), model.createResource(NS + "v1"));
        ontology.addProperty(
                model.createProperty(KnownOntologyFields.DCAT_KEYWORD.getIri()), "cim");
    }

    private Resource addPackage(Model model) {
        var pkg = model.createResource(NS + "package");
        pkg.addProperty(RDF.type, CIMS.classCategory);
        pkg.addProperty(RDFS.label, model.createLiteral("package", "en"));
        return pkg;
    }

    private Resource addClass(Model model, String localName, Resource pkg) {
        var clazz = model.createResource(NS + localName);
        clazz.addProperty(RDF.type, RDFS.Class);
        clazz.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        clazz.addProperty(CIMS.belongsToCategory, pkg);
        return clazz;
    }

    /** Creates a valid XSD-typed attribute belonging to {@code domain}. */
    private void addAttribute(Model model, String localName, Resource domain) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(CIMS.datatype, model.createResource(XSD_STRING));
    }

    private void addAssociation(Model model, String localName, Resource domain, Resource target) {
        var association = model.createResource(NS + localName);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        association.addProperty(RDFS.domain, domain);
        association.addProperty(RDFS.range, target);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        association.addProperty(CIMS.associationUsed, "Yes");
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(NS + localName + ".inverse"));
    }

    /** A fully valid schema: header + package + class + attribute + association. */
    private Model validSchema() {
        var model = ModelFactory.createDefaultModel();
        addValidOntologyHeader(model);
        var pkg = addPackage(model);
        var classA = addClass(model, "ClassA", pkg);
        var classB = addClass(model, "ClassB", pkg);
        addAttribute(model, "ClassA.name", classA);
        addAssociation(model, "ClassA.classB", classA, classB);
        model.setNsPrefix("NS", NS);
        return model;
    }

    private List<SchemaValidationIssueDTO> errorsOf(SchemaValidationReportDTO report) {
        return report.getIssues().stream().filter(i -> i.getSeverity() == Severity.ERROR).toList();
    }

    private boolean hasIssue(
            SchemaValidationReportDTO report, Severity severity, String uri, String messagePart) {
        return report.getIssues().stream()
                .anyMatch(
                        i ->
                                i.getSeverity() == severity
                                        && Objects.equals(i.getResourceUri(), uri)
                                        && i.getMessage().contains(messagePart));
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Nested
    class ValidSchemaTests {

        @Test
        void validateSchema_fullyValidSchema_reportsValidWithoutErrors() {
            var report = service.validateSchema(validSchema().getGraph(), CGMESVersion.V3_0);

            assertThat(report.isValid()).isTrue();
            assertThat(errorsOf(report)).isEmpty();
        }

        @Test
        void validateSchema_fullyValidSchema_onlyEmitsInfoForMissingOptionalFields() {
            var report = service.validateSchema(validSchema().getGraph(), CGMESVersion.V3_0);

            // Everything that is not an ERROR here must be an INFO about optional header fields.
            assertThat(report.getIssues())
                    .allMatch(i -> i.getSeverity() == Severity.INFO)
                    .allMatch(
                            i ->
                                    i.getMessage()
                                            .contains("Optional profile header field is not set"));
        }
    }

    // ─── Profile header ───────────────────────────────────────────────────────

    @Nested
    class ProfileHeaderTests {

        @Test
        void validateSchema_missingOntologyHeader_reportsWarning() {
            var model = ModelFactory.createDefaultModel();
            addPackage(model);

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(report.isValid()).isTrue();
            assertThat(hasIssue(report, Severity.WARNING, null, "Profile header is missing"))
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
                                    Severity.WARNING,
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
                    model.createProperty(OWL2.versionIRI.getURI()),
                    model.createResource(NS + "v1"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.WARNING,
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
                                    .filter(i -> i.getSeverity() == Severity.ERROR)
                                    .anyMatch(i -> i.getMessage().contains("profile header field")))
                    .isFalse();
            // But INFO issues for all other known but unset optional fields.
            assertThat(report.getIssues())
                    .anyMatch(
                            i ->
                                    i.getSeverity() == Severity.INFO
                                            && i.getMessage()
                                                    .contains(
                                                            "Optional profile header field is not set"));
        }
    }

    // ─── Packages ─────────────────────────────────────────────────────────────

    @Nested
    class PackageTests {

        @Test
        void validateSchema_packageWithoutLabel_reportsError() {
            var model = ModelFactory.createDefaultModel();
            addValidOntologyHeader(model);
            var pkg = model.createResource(NS + "package");
            pkg.addProperty(RDF.type, CIMS.classCategory);

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(hasIssue(report, Severity.ERROR, NS + "package", "missing rdfs:label"))
                    .isTrue();
        }
    }

    // ─── Classes ──────────────────────────────────────────────────────────────

    @Nested
    class ClassTests {

        @Test
        void validateSchema_classWithoutLabel_reportsError() {
            var model = ModelFactory.createDefaultModel();
            addValidOntologyHeader(model);
            var pkg = addPackage(model);
            var clazz = model.createResource(NS + "ClassA");
            clazz.addProperty(RDF.type, RDFS.Class);
            clazz.addProperty(CIMS.belongsToCategory, pkg);

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.ERROR,
                                    NS + "ClassA",
                                    "Class is missing rdfs:label"))
                    .isTrue();
        }

        @Test
        void validateSchema_classWithoutBelongsToCategory_reportsWarning() {
            var model = ModelFactory.createDefaultModel();
            addValidOntologyHeader(model);
            var clazz = model.createResource(NS + "ClassA");
            clazz.addProperty(RDF.type, RDFS.Class);
            clazz.addProperty(RDFS.label, model.createLiteral("ClassA", "en"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.WARNING,
                                    NS + "ClassA",
                                    "cims:belongsToCategory is missing"))
                    .isTrue();
        }
    }

    // ─── Properties (generic) ─────────────────────────────────────────────────

    @Nested
    class PropertyTests {

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

            assertThat(hasIssue(report, Severity.ERROR, NS + "ClassA.attr", "missing rdfs:domain"))
                    .isTrue();
            assertThat(
                            hasIssue(
                                    report,
                                    Severity.ERROR,
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
                                    Severity.ERROR,
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
                                    Severity.WARNING,
                                    NS + "ClassA.orphan",
                                    "neither an attribute nor an association"))
                    .isTrue();
        }
    }

    // ─── Attributes ───────────────────────────────────────────────────────────

    @Nested
    class AttributeTests {

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
                                    .anyMatch(
                                            i -> i.getMessage().contains("references a datatype")))
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
                                    Severity.ERROR,
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
                                    Severity.ERROR,
                                    NS + "ClassA.attr",
                                    "does not exist or is not a"))
                    .isTrue();
            // The offending datatype URI is included in the message.
            assertThat(
                            report.getIssues().stream()
                                    .anyMatch(
                                            i -> i.getMessage().contains(NS + "NotAKnownDatatype")))
                    .isTrue();
        }

        @Test
        void validateSchema_attributeWithPrimitiveDatatype_reportsNoDatatypeError() {
            var model = modelWithClass();
            var primitive = model.createResource(NS + "Float");
            primitive.addProperty(RDF.type, RDFS.Class);
            primitive.addProperty(
                    CIMS.stereotype, model.createLiteral(CIMStereotypes.primitiveString));
            baseAttribute(model).addProperty(CIMS.datatype, primitive);

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            report.getIssues().stream()
                                    .anyMatch(
                                            i -> i.getMessage().contains("references a datatype")))
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
                                    .anyMatch(
                                            i -> i.getMessage().contains("references a datatype")))
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
                                    .anyMatch(
                                            i -> i.getMessage().contains("references a datatype")))
                    .isFalse();
        }

        /**
         * Regression: an attribute that only defines {@code cims:dataType} (no {@code rdfs:range})
         * must not trigger a {@link NullPointerException} in the enum-detection path.
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
                                    Severity.ERROR,
                                    NS + "ClassA.attr",
                                    "does not exist or is not a"))
                    .isTrue();
        }
    }

    // ─── Associations ─────────────────────────────────────────────────────────

    @Nested
    class AssociationTests {

        private Model modelWithTwoClasses() {
            var model = ModelFactory.createDefaultModel();
            addValidOntologyHeader(model);
            var pkg = addPackage(model);
            addClass(model, "ClassA", pkg);
            addClass(model, "ClassB", pkg);
            return model;
        }

        @Test
        void validateSchema_validAssociationWithExistingTarget_reportsNoAssociationError() {
            var model = modelWithTwoClasses();
            addAssociation(
                    model,
                    "ClassA.classB",
                    model.getResource(NS + "ClassA"),
                    model.getResource(NS + "ClassB"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            report.getIssues().stream()
                                    .anyMatch(i -> i.getMessage().contains("Association")))
                    .isFalse();
        }

        @Test
        void validateSchema_associationTargetClassMissing_reportsError() {
            var model = modelWithTwoClasses();
            // Target ClassC is referenced but never declared as rdfs:Class.
            addAssociation(
                    model,
                    "ClassA.classC",
                    model.getResource(NS + "ClassA"),
                    model.createResource(NS + "ClassC"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.ERROR,
                                    NS + "ClassA.classC",
                                    "Association target class does not exist"))
                    .isTrue();
        }

        @Test
        void validateSchema_associationRangeIsLiteral_reportsError() {
            var model = modelWithTwoClasses();
            var association = model.createResource(NS + "ClassA.broken");
            association.addProperty(RDF.type, RDF.Property);
            association.addProperty(RDFS.label, model.createLiteral("broken", "en"));
            association.addProperty(RDFS.domain, model.getResource(NS + "ClassA"));
            association.addProperty(RDFS.range, model.createLiteral("not-a-resource"));
            association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
            association.addProperty(CIMS.associationUsed, "Yes");
            association.addProperty(
                    CIMS.inverseRoleName, model.createResource(NS + "ClassA.broken.inverse"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.ERROR,
                                    NS + "ClassA.broken",
                                    "target (rdfs:range) is not a resource"))
                    .isTrue();
        }

        @Test
        void validateSchema_associationMissingRange_reportsError() {
            var model = modelWithTwoClasses();
            // isAssociation requires inverseRoleName + associationUsed; range is what we omit.
            var association = model.createResource(NS + "ClassA.noRange");
            association.addProperty(RDF.type, RDF.Property);
            association.addProperty(RDFS.label, model.createLiteral("noRange", "en"));
            association.addProperty(RDFS.domain, model.getResource(NS + "ClassA"));
            association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
            association.addProperty(CIMS.associationUsed, "Yes");
            association.addProperty(
                    CIMS.inverseRoleName, model.createResource(NS + "ClassA.noRange.inverse"));

            var report = service.validateSchema(model.getGraph(), CGMESVersion.V3_0);

            assertThat(
                            hasIssue(
                                    report,
                                    Severity.ERROR,
                                    NS + "ClassA.noRange",
                                    "missing rdfs:range (target class)"))
                    .isTrue();
        }
    }

    // ─── Markdown conversion ──────────────────────────────────────────────────

    @Nested
    class MarkdownConversionTests {

        @Test
        void convertToMarkdown_emptyIssues_reportsNoIssues() {
            var report = SchemaValidationReportDTO.builder().valid(true).issues(List.of()).build();

            var markdown = markdownService.convertToMarkdown(report);

            assertThat(markdown).contains("# Schema Validation Report");
            assertThat(markdown).contains("**Status:** Valid");
            assertThat(markdown).contains("No issues found.");
        }

        @Test
        void convertToMarkdown_onlyInfoIssues_omitsThemAndReportsNoErrorsOrWarnings() {
            var report =
                    SchemaValidationReportDTO.builder()
                            .valid(true)
                            .issues(
                                    List.of(
                                            SchemaValidationIssueDTO.builder()
                                                    .severity(Severity.INFO)
                                                    .resourceUri(NS + "Ontology")
                                                    .message(
                                                            "Optional profile header field is not set: <x>")
                                                    .build()))
                            .build();

            var markdown = markdownService.convertToMarkdown(report);

            assertThat(markdown).contains("No errors or warnings found.");
            assertThat(markdown).doesNotContain("Optional profile header field is not set");
        }

        @Test
        void convertToMarkdown_errorAndWarning_rendersBothSections() {
            var report =
                    SchemaValidationReportDTO.builder()
                            .valid(false)
                            .issues(
                                    List.of(
                                            SchemaValidationIssueDTO.builder()
                                                    .severity(Severity.ERROR)
                                                    .resourceUri(NS + "ClassA")
                                                    .message("Class is missing rdfs:label.")
                                                    .build(),
                                            SchemaValidationIssueDTO.builder()
                                                    .severity(Severity.WARNING)
                                                    .resourceUri(NS + "ClassB")
                                                    .message("Class is missing rdfs:comment.")
                                                    .build(),
                                            SchemaValidationIssueDTO.builder()
                                                    .severity(Severity.INFO)
                                                    .resourceUri(NS + "Ontology")
                                                    .message(
                                                            "Optional profile header field is not set: <x>")
                                                    .build()))
                            .build();

            var markdown = markdownService.convertToMarkdown(report);

            assertThat(markdown).contains("**Status:** Invalid");
            assertThat(markdown).contains("**Errors:** 1 | **Warnings:** 1");
            assertThat(markdown).contains("## Errors");
            assertThat(markdown).contains("## Warnings");
            assertThat(markdown).contains("Class is missing rdfs:label.");
            assertThat(markdown).contains("Class is missing rdfs:comment.");
            // INFO must not appear.
            assertThat(markdown).doesNotContain("Optional profile header field is not set");
        }

        @Test
        void convertToMarkdown_pipeInMessage_isKeptAsIs() {
            var report =
                    SchemaValidationReportDTO.builder()
                            .valid(false)
                            .issues(
                                    List.of(
                                            SchemaValidationIssueDTO.builder()
                                                    .severity(Severity.ERROR)
                                                    .resourceUri(NS + "ClassA")
                                                    .message("value a | value b")
                                                    .build()))
                            .build();

            var markdown = markdownService.convertToMarkdown(report);

            // No table is used anymore, so pipes no longer need escaping.
            assertThat(markdown).contains("value a | value b");
        }
    }
}
