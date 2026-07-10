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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.api.dto.ontology.OntologyField;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssueDTO.Severity;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.models.cim.ontology.OntologyFactory;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProfileHeaderValidationRule implements ValidationRule {

    private static final Set<OntologyField> REQUIRED_ONTOLOGY_FIELDS =
            Set.of(KnownOntologyFields.OWL_VERSION_IRI, KnownOntologyFields.DCAT_KEYWORD);
    private static final String PACKAGE_FILE_HEADER_PROFILE = "#Package_FileHeaderProfile";

    /**
     * Local name suffix of the ENTSO-E profile version (header) class, e.g. DiagramLayoutVersion.
     */
    private static final String VERSION_SUFFIX = "Version";

    @Override
    public void validate(
            Model model, List<SchemaValidationIssueDTO> issues, CGMESVersion cgmesVersion) {
        if (cgmesVersion == CGMESVersion.V3_0) {
            validate3_0ProfileHeader(model, issues);
        } else if (cgmesVersion == CGMESVersion.V2_4_15) {
            validate2_4_15ProfileHeader(model, issues);
        }
    }

    private void validate3_0ProfileHeader(Model model, List<SchemaValidationIssueDTO> issues) {
        var ontologyDTO = OntologyFactory.createOntologyDTO(model);

        if (ontologyDoesNotExists(issues, ontologyDTO)) {
            return;
        }

        var presentIris =
                ontologyDTO.getEntries().stream()
                        .map(OntologyEntry::getIri)
                        .collect(Collectors.toSet());

        validateRequiredFields(issues, presentIris, ontologyDTO);
        validateOptionalFields(issues, presentIris, ontologyDTO);
    }

    private static void validate2_4_15ProfileHeader(
            Model model, List<SchemaValidationIssueDTO> issues) {
        var graph = model.getGraph();
        if (!isCim16HeaderProfile(graph) && !hasCim16VersionHeader(graph)) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.WARNING)
                            .message(
                                    "Profile header is missing: no Package_FileHeaderProfile"
                                            + " or ENTSO-E profile version header found.")
                            .build());
        }
    }

    private boolean ontologyDoesNotExists(
            List<SchemaValidationIssueDTO> issues, OntologyDTO ontologyDTO) {
        if (ontologyDTO == null || ontologyDTO.getNamespace() == null) {
            issues.add(
                    SchemaValidationIssueDTO.builder()
                            .severity(Severity.WARNING)
                            .message("Profile header is missing: no owl:Ontology entry found.")
                            .build());
            return true;
        }
        return false;
    }

    private void validateRequiredFields(
            List<SchemaValidationIssueDTO> issues,
            Set<String> presentIris,
            OntologyDTO ontologyDTO) {
        for (var requiredField : REQUIRED_ONTOLOGY_FIELDS) {
            if (!presentIris.contains(requiredField.getIri())) {
                issues.add(
                        SchemaValidationIssueDTO.builder()
                                .severity(Severity.WARNING)
                                .resourceUri(ontologyDTO.getNamespace() + "Ontology")
                                .message(
                                        "Required profile header field is missing: <"
                                                + requiredField.getIri()
                                                + ">")
                                .build());
            }
        }
    }

    private void validateOptionalFields(
            List<SchemaValidationIssueDTO> issues,
            Set<String> presentIris,
            OntologyDTO ontologyDTO) {
        var allKnownIris =
                KnownOntologyFields.getAllFields().stream()
                        .map(OntologyField::getIri)
                        .collect(Collectors.toSet());
        for (var knownIri : allKnownIris) {
            if (!presentIris.contains(knownIri)
                    && REQUIRED_ONTOLOGY_FIELDS.stream()
                            .noneMatch(f -> f.getIri().equals(knownIri))) {
                issues.add(
                        SchemaValidationIssueDTO.builder()
                                .severity(Severity.INFO)
                                .resourceUri(ontologyDTO.getNamespace() + "Ontology")
                                .message(
                                        "Optional profile header field is not set: <"
                                                + knownIri
                                                + ">")
                                .build());
            }
        }
    }

    public static boolean isCim16HeaderProfile(Graph graph) {
        return graph.stream(Node.ANY, RDF.type.asNode(), CIMS.classCategory.asNode())
                .anyMatch(
                        t ->
                                t.getSubject().isURI()
                                        && t.getSubject()
                                                .getURI()
                                                .endsWith(PACKAGE_FILE_HEADER_PROFILE));
    }

    /**
     * Checks for the presence of an ENTSO-E CIM16 profile version header, i.e. an {@code
     * rdfs:Class} carrying the {@code cims:stereotype} {@code Entsoe} whose local name ends with
     * {@code Version} (e.g. {@code DiagramLayoutVersion}). This is how CGMES 2.4.15 profiles such
     * as DiagramLayout declare their profile header.
     *
     * @param graph the schema graph to inspect
     * @return {@code true} if such a version header class is present
     */
    public static boolean hasCim16VersionHeader(Graph graph) {
        return graph.stream(Node.ANY, RDF.type.asNode(), RDFS.Class.asNode())
                .map(Triple::getSubject)
                .filter(Node::isURI)
                .filter(subject -> subject.getURI().endsWith(VERSION_SUFFIX))
                .anyMatch(subject -> hasEntsoeStereotype(graph, subject));
    }

    private static boolean hasEntsoeStereotype(Graph graph, Node subject) {
        return graph.contains(subject, CIMS.stereotype.asNode(), CIMStereotypes.entsoe.asNode());
    }
}
