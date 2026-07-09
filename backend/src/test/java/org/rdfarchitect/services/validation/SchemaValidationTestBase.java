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

import static org.mockito.Mockito.mock;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
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

abstract class SchemaValidationTestBase {

    protected static final String NS = "http://example.org#";
    protected static final String CIMS_NS =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    protected static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    protected SchemaValidationUseCase service;
    protected SchemaValidationReportToMarkdownUseCase markdownService;

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
    protected void addValidOntologyHeader(Model model) {
        var ontology = model.createResource(NS + "Ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(
                model.createProperty(OWL2.versionIRI.getURI()), model.createResource(NS + "v1"));
        ontology.addProperty(
                model.createProperty(KnownOntologyFields.DCAT_KEYWORD.getIri()), "cim");
    }

    protected Resource addPackage(Model model) {
        var pkg = model.createResource(NS + "package");
        pkg.addProperty(RDF.type, CIMS.classCategory);
        pkg.addProperty(RDFS.label, model.createLiteral("package", "en"));
        return pkg;
    }

    protected Resource addClass(Model model, String localName, Resource pkg) {
        var clazz = model.createResource(NS + localName);
        clazz.addProperty(RDF.type, RDFS.Class);
        clazz.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        clazz.addProperty(CIMS.belongsToCategory, pkg);
        return clazz;
    }

    /** Creates a valid XSD-typed attribute belonging to {@code domain}. */
    protected void addAttribute(Model model, String localName, Resource domain) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(CIMS.datatype, model.createResource(XSD_STRING));
    }

    protected void addAssociation(Model model, String localName, Resource domain, Resource target) {
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
    protected Model validSchema() {
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

    protected List<SchemaValidationIssueDTO> errorsOf(SchemaValidationReportDTO report) {
        return report.getIssues().stream()
                .filter(i -> i.getSeverity() == SchemaValidationIssueDTO.Severity.ERROR)
                .toList();
    }

    protected boolean hasIssue(
            SchemaValidationReportDTO report,
            SchemaValidationIssueDTO.Severity severity,
            String uri,
            String messagePart) {
        return report.getIssues().stream()
                .anyMatch(
                        i ->
                                i.getSeverity() == severity
                                        && Objects.equals(i.getResourceUri(), uri)
                                        && i.getMessage().contains(messagePart));
    }
}
