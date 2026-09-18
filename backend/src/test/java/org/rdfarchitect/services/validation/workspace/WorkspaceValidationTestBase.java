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

package org.rdfarchitect.services.validation.workspace;

import static org.mockito.Mockito.mock;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationIssueDTO.Severity;
import org.rdfarchitect.api.dto.validation.workspace.WorkspaceValidationReportDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.validation.workspace.rule.WorkspaceValidationRule;

import java.util.List;

abstract class WorkspaceValidationTestBase {

    protected static final String NS = "http://example.org#";
    protected static final String CIMS_NS =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    protected static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    protected static final String XSD_FLOAT = "http://www.w3.org/2001/XMLSchema#float";

    protected WorkspaceValidationReportDTO validate(
            WorkspaceValidationRule rule, CIMProfileModel... profiles) {
        return validate(List.of(rule), null, profiles);
    }

    protected WorkspaceValidationReportDTO validate(
            List<WorkspaceValidationRule> rules,
            String scopeGraphUri,
            CIMProfileModel... profiles) {
        var service = new WorkspaceValidationService(mock(DatabasePort.class), rules);
        return service.validate(List.of(profiles), scopeGraphUri);
    }

    protected CIMProfileModel profile(String keyword, Model model) {
        var graphUri = "http://example.org/graph/" + keyword;
        GraphUtils.enhanceWithUUIDs(model.getGraph());
        return new CIMProfileModel(graphUri, null, keyword, new CIMModelFacade(graphUri, model));
    }

    protected List<WorkspaceValidationIssueDTO> issues(
            WorkspaceValidationReportDTO report, Severity severity) {
        return report.getIssues().stream()
                .filter(issue -> issue.getSeverity() == severity)
                .toList();
    }

    protected Model model() {
        return ModelFactory.createDefaultModel();
    }

    protected Resource addClass(Model model, String localName) {
        return addClass(model, NS, localName);
    }

    protected Resource addClass(Model model, String namespace, String localName) {
        var cimClass = model.createResource(namespace + localName);
        cimClass.addProperty(RDF.type, RDFS.Class);
        cimClass.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        return cimClass;
    }

    protected Resource addDatatype(Model model, String localName, String stereotype) {
        var datatype = addClass(model, localName);
        datatype.addProperty(CIMS.stereotype, stereotype);
        return datatype;
    }

    protected Resource addEnumeration(Model model, String localName) {
        var enumeration = addClass(model, localName);
        enumeration.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        return enumeration;
    }

    protected void addConcrete(Resource cimClass) {
        cimClass.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
    }

    protected void addSuperClass(Resource cimClass, Resource superClass) {
        cimClass.addProperty(RDFS.subClassOf, superClass);
    }

    protected Resource addAttribute(
            Model model, String localName, Resource domain, String dataType, String multiplicity) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + multiplicity));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(CIMS.datatype, model.createResource(dataType));
        return attribute;
    }

    protected Resource addAttribute(Model model, String localName, Resource domain) {
        return addAttribute(model, localName, domain, XSD_STRING, "M:0..1");
    }

    protected Resource addAssociation(
            Model model, String localName, Resource domain, Resource range, String inverse) {
        var association = model.createResource(NS + localName);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        association.addProperty(RDFS.domain, domain);
        association.addProperty(RDFS.range, range);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_NS + "M:0..1"));
        association.addProperty(CIMS.associationUsed, "Yes");
        association.addProperty(CIMS.inverseRoleName, model.createResource(NS + inverse));
        return association;
    }

    protected Resource addEnumEntry(Model model, String localName, Resource enumeration) {
        var entry = model.createResource(NS + localName);
        entry.addProperty(RDF.type, enumeration);
        entry.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        return entry;
    }

    /** Adds the class category that marks a graph as a CGMES file header profile. */
    protected void addFileHeaderPackage(Model model) {
        var fileHeader = model.createResource(NS + "Package_FileHeaderProfile");
        fileHeader.addProperty(RDF.type, CIMS.classCategory);
        fileHeader.addProperty(RDFS.label, model.createLiteral("FileHeaderProfile", "en"));
    }

    protected void addHeader(Model model, String keyword, String versionIri) {
        var ontology = model.createResource(NS + keyword + "-Ontology");
        ontology.addProperty(RDF.type, OWL2.Ontology);
        ontology.addProperty(OWL2.versionIRI, model.createResource(versionIri));
        ontology.addProperty(
                model.createProperty(KnownOntologyFields.DCAT_KEYWORD.getIri()), keyword);
    }
}
