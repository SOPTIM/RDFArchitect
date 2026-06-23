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

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ontology.OntologyEntry;
import org.rdfarchitect.api.dto.ontology.OntologyField;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssue;
import org.rdfarchitect.api.dto.validation.SchemaValidationIssue.Severity;
import org.rdfarchitect.api.dto.validation.SchemaValidationReport;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.ontology.KnownOntologyFields;
import org.rdfarchitect.models.cim.ontology.OntologyFactory;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SchemaValidationService implements SchemaValidationUseCase {

    // Required fields in the profile header (based on KnownOntologyFields)
    private static final Set<OntologyField> REQUIRED_ONTOLOGY_FIELDS = Set.of(
            KnownOntologyFields.DCT_TITLE,
            KnownOntologyFields.DCT_DESCRIPTION,
            KnownOntologyFields.OWL_VERSION_IRI,
            KnownOntologyFields.OWL_VERSION_INFO,
            KnownOntologyFields.DCT_ISSUED,
            KnownOntologyFields.DCT_LANGUAGE
    );

    private final DatabasePort databasePort;

    @Override
    public SchemaValidationReport validateSchema(GraphIdentifier graphIdentifier) {
        var issues = new ArrayList<SchemaValidationIssue>();

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            validateProfileHeader(model, issues);
            validateClasses(model, issues);
            validateProperties(model, issues);
        }

        var hasErrors = issues.stream()
                .anyMatch(i -> i.getSeverity() == Severity.ERROR);

        return SchemaValidationReport.builder()
                .valid(!hasErrors)
                .issues(issues)
                .build();
    }

    // ─── Profile Header ───────────────────────────────────────────────────────

    private void validateProfileHeader(Model model, List<SchemaValidationIssue> issues) {
        var ontologyDTO = OntologyFactory.createOntologyDTO(model);

        if (ontologyDTO == null || ontologyDTO.getNamespace() == null) {
            issues.add(SchemaValidationIssue.builder()
                    .severity(Severity.ERROR)
                    .message("Profile header is missing: no owl:Ontology entry found.")
                    .build());
            return;
        }

        var presentIris = ontologyDTO.getEntries().stream()
                .map(OntologyEntry::getIri)
                .collect(java.util.stream.Collectors.toSet());

        for (var requiredField : REQUIRED_ONTOLOGY_FIELDS) {
            if (!presentIris.contains(requiredField.getIri())) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(ontologyDTO.getNamespace() + "Ontology")
                        .message("Required profile header field is missing: <" + requiredField.getIri() + ">")
                        .build());
            }
        }

        // Warnings for optional known fields
        var allKnownIris = KnownOntologyFields.getAllFields().stream()
                .map(OntologyField::getIri)
                .collect(java.util.stream.Collectors.toSet());
        for (var knownIri : allKnownIris) {
            if (!presentIris.contains(knownIri)
                    && REQUIRED_ONTOLOGY_FIELDS.stream().noneMatch(f -> f.getIri().equals(knownIri))) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.WARNING)
                        .resourceUri(ontologyDTO.getNamespace() + "Ontology")
                        .message("Optional profile header field is not set: <" + knownIri + ">")
                        .build());
            }
        }
    }

    // ─── Classes ──────────────────────────────────────────────────────────────

    private void validateClasses(Model model, List<SchemaValidationIssue> issues) {
        model.listSubjectsWithProperty(RDF.type, RDFS.Class).forEach(classResource -> {
            var uri = classResource.getURI();

            // rdfs:label required
            if (!classResource.hasProperty(RDFS.label)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(uri)
                        .message("Class is missing rdfs:label.")
                        .build());
            }

            // cims:belongsToCategory recommended
            if (!classResource.hasProperty(CIMS.belongsToCategory)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.WARNING)
                        .resourceUri(uri)
                        .message("Class is not assigned to a package (cims:belongsToCategory is missing).")
                        .build());
            }

            // rdfs:comment recommended
            if (!classResource.hasProperty(RDFS.comment)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.WARNING)
                        .resourceUri(uri)
                        .message("Class is missing rdfs:comment.")
                        .build());
            }
        });
    }

    // ─── Properties (attributes & associations) ───────────────────────────────

    private void validateProperties(Model model, List<SchemaValidationIssue> issues) {
        model.listSubjectsWithProperty(RDF.type, RDF.Property).forEach(property -> {
            var uri = property.getURI();

            // rdfs:label required
            if (!property.hasProperty(RDFS.label)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(uri)
                        .message("Property is missing rdfs:label.")
                        .build());
            }

            // rdfs:domain required
            if (!property.hasProperty(RDFS.domain)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(uri)
                        .message("Property is missing rdfs:domain.")
                        .build());
            }

            // cims:multiplicity required
            if (!property.hasProperty(CIMS.multiplicity)) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(uri)
                        .message("Property is missing cims:multiplicity.")
                        .build());
            }

            // rdfs:range or cims:dataType required
            boolean hasRange = property.hasProperty(RDFS.range);
            boolean hasDatatype = property.hasProperty(CIMS.datatype);
            if (!hasRange && !hasDatatype) {
                issues.add(SchemaValidationIssue.builder()
                        .severity(Severity.ERROR)
                        .resourceUri(uri)
                        .message("Property has neither rdfs:range nor cims:dataType.")
                        .build());
            }
        });
    }
}
