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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
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
import org.rdfarchitect.models.cim.relations.model.properties.CIMAttributeUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaValidationService
        implements SchemaValidationUseCase, SchemaValidationReportToMarkdownUseCase {

    private static final String NEW_LINE = "\n";

    private static final Set<OntologyField> REQUIRED_ONTOLOGY_FIELDS =
            Set.of(KnownOntologyFields.OWL_VERSION_IRI, KnownOntologyFields.DCAT_KEYWORD);

    private final DatabasePort databasePort;

    @Override
    public SchemaValidationReport validateSchema(GraphIdentifier graphIdentifier) {
        SchemaValidationReport report;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            report = validateModel(model);
        }
        return report;
    }

    @Override
    public SchemaValidationReport validateSchema(Graph graph) {
        return validateModel(ModelFactory.createModelForGraph(graph));
    }

    private SchemaValidationReport validateModel(Model model) {
        var issues = new ArrayList<SchemaValidationIssue>();

        validateProfileHeader(model, issues);
        validatePackages(model, issues);
        validateClasses(model, issues);
        validateProperties(model, issues);

        var hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == Severity.ERROR);

        return SchemaValidationReport.builder().valid(!hasErrors).issues(issues).build();
    }

    // ─── Profile Header ───────────────────────────────────────────────────────

    private void validateProfileHeader(Model model, List<SchemaValidationIssue> issues) {
        var ontologyDTO = OntologyFactory.createOntologyDTO(model);

        if (ontologyDTO == null || ontologyDTO.getNamespace() == null) {
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .message("Profile header is missing: no owl:Ontology entry found.")
                            .build());
            return;
        }

        var presentIris =
                ontologyDTO.getEntries().stream()
                        .map(OntologyEntry::getIri)
                        .collect(java.util.stream.Collectors.toSet());

        for (var requiredField : REQUIRED_ONTOLOGY_FIELDS) {
            if (!presentIris.contains(requiredField.getIri())) {
                issues.add(
                        SchemaValidationIssue.builder()
                                .severity(Severity.ERROR)
                                .resourceUri(ontologyDTO.getNamespace() + "Ontology")
                                .message(
                                        "Required profile header field is missing: <"
                                                + requiredField.getIri()
                                                + ">")
                                .build());
            }
        }

        var allKnownIris =
                KnownOntologyFields.getAllFields().stream()
                        .map(OntologyField::getIri)
                        .collect(java.util.stream.Collectors.toSet());
        for (var knownIri : allKnownIris) {
            if (!presentIris.contains(knownIri)
                    && REQUIRED_ONTOLOGY_FIELDS.stream()
                            .noneMatch(f -> f.getIri().equals(knownIri))) {
                issues.add(
                        SchemaValidationIssue.builder()
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

    // ─── Packages ──────────────────────────────────────────────────────────────

    private void validatePackages(Model model, List<SchemaValidationIssue> issues) {
        model.listSubjectsWithProperty(RDF.type, CIMS.classCategory)
                .forEach(
                        packageResource -> {
                            var uri = packageResource.getURI();

                            // rdfs:label required
                            if (!packageResource.hasProperty(RDFS.label)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Package is missing rdfs:label.")
                                                .build());
                            }

                            // namespace required
                            if (packageResource.getNameSpace() == null
                                    || packageResource.getNameSpace().isEmpty()) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Package is missing a namespace.")
                                                .build());
                            } else {
                                if (hasNoNamespacePrefix(model, packageResource.getNameSpace())) {
                                    issues.add(
                                            SchemaValidationIssue.builder()
                                                    .severity(Severity.WARNING)
                                                    .resourceUri(uri)
                                                    .message(
                                                            "Package namespace is not defined in the schema's prefix mapping: <"
                                                                    + packageResource.getNameSpace()
                                                                    + ">")
                                                    .build());
                                }
                            }
                        });
    }

    // ─── Classes ──────────────────────────────────────────────────────────────

    private void validateClasses(Model model, List<SchemaValidationIssue> issues) {
        model.listSubjectsWithProperty(RDF.type, RDFS.Class)
                .forEach(
                        classResource -> {
                            var uri = classResource.getURI();

                            // rdfs:label required
                            if (!classResource.hasProperty(RDFS.label)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Class is missing rdfs:label.")
                                                .build());
                            }

                            // namespace required
                            if (classResource.getNameSpace() == null
                                    || classResource.getNameSpace().isEmpty()) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Class is missing a namespace.")
                                                .build());
                            } else {
                                if (hasNoNamespacePrefix(model, classResource.getNameSpace())) {
                                    issues.add(
                                            SchemaValidationIssue.builder()
                                                    .severity(Severity.WARNING)
                                                    .resourceUri(uri)
                                                    .message(
                                                            "Class namespace is not defined in the schema's prefix mapping: <"
                                                                    + classResource.getNameSpace()
                                                                    + ">")
                                                    .build());
                                }
                            }

                            // cims:belongsToCategory required
                            if (!classResource.hasProperty(CIMS.belongsToCategory)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message(
                                                        "Class is not assigned to a package (cims:belongsToCategory is missing).")
                                                .build());
                            }
                        });
    }

    // ─── Properties (attributes & associations) ───────────────────────────────

    private void validateProperties(Model model, List<SchemaValidationIssue> issues) {
        model.listSubjectsWithProperty(RDF.type, RDF.Property)
                .forEach(
                        property -> {
                            var uri = property.getURI();

                            // rdfs:label required
                            if (!property.hasProperty(RDFS.label)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Property is missing rdfs:label.")
                                                .build());
                            }

                            // namespace required
                            if (property.getNameSpace() == null
                                    || property.getNameSpace().isEmpty()) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Property is missing a namespace.")
                                                .build());
                            } else {
                                if (hasNoNamespacePrefix(model, property.getNameSpace())) {
                                    issues.add(
                                            SchemaValidationIssue.builder()
                                                    .severity(Severity.WARNING)
                                                    .resourceUri(uri)
                                                    .message(
                                                            "Property namespace is not defined in the schema's prefix mapping: <"
                                                                    + property.getNameSpace()
                                                                    + ">")
                                                    .build());
                                }
                            }

                            // rdfs:domain required
                            if (!property.hasProperty(RDFS.domain)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Property is missing rdfs:domain.")
                                                .build());
                            }

                            // cims:multiplicity required
                            if (!property.hasProperty(CIMS.multiplicity)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.ERROR)
                                                .resourceUri(uri)
                                                .message("Property is missing cims:multiplicity.")
                                                .build());
                            }

                            if (CIMPropertyUtils.isAttribute(property)) {
                                validateAttribute(property, issues);
                            }

                            // For associations: verify rdfs:range, cims:associationUsed and
                            // cims:inverseRoleName
                            if (CIMPropertyUtils.isAssociation(property)) {
                                validateAssociation(property, issues);
                            }

                            if (!CIMPropertyUtils.isAttribute(property)
                                    && !CIMPropertyUtils.isAssociation(property)) {
                                issues.add(
                                        SchemaValidationIssue.builder()
                                                .severity(Severity.WARNING)
                                                .resourceUri(uri)
                                                .message(
                                                        "Property is neither an attribute nor an association. It may be missing required properties.")
                                                .build());
                            }
                        });
    }

    private void validateAttribute(Resource attribute, List<SchemaValidationIssue> issues) {
        var uri = attribute.getURI();
        if (!attribute.hasProperty(CIMS.datatype) && !attribute.hasProperty(RDFS.range)) {
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Attribute is missing cims:dataType or rdfs:range.")
                            .build());
            return;
        }
        validateAttributeDatatype(attribute, issues);
    }

    /**
     * Verifies that the datatype referenced by an attribute exists, i.e. it is either a known XSD
     * datatype, a primitive datatype, a CIM datatype, or an enumeration defined in the graph.
     */
    private void validateAttributeDatatype(Resource attribute, List<SchemaValidationIssue> issues) {
        var uri = attribute.getURI();

        boolean datatypeExists =
                CIMAttributeUtils.hasXSDDatatype(attribute)
                        || CIMAttributeUtils.hasPrimitiveDatatype(attribute)
                        || CIMAttributeUtils.hasCIMDatatype(attribute)
                        || hasEnumDatatype(attribute);

        if (!datatypeExists) {
            var referencedDatatype = resolveReferencedDatatypeUri(attribute);
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message(
                                    "Attribute references a datatype that does not exist or is not a"
                                            + " known XSD datatype"
                                            + (referencedDatatype != null
                                                    ? ": <" + referencedDatatype + ">"
                                                    : "."))
                            .build());
        }
    }

    /**
     * Null-safe wrapper around {@link CIMAttributeUtils#hasEnumAttribute(Resource)}.
     *
     * <p>{@code hasEnumAttribute} dereferences {@code rdfs:range} without checking that it is
     * actually present (it only checks that either {@code rdfs:range} or {@code cims:datatype}
     * exists), which causes a {@link NullPointerException} for attributes that only define {@code
     * cims:datatype}. This guard ensures {@code rdfs:range} is present and a resource before
     * delegating.
     */
    private boolean hasEnumDatatype(Resource attribute) {
        if (!attribute.hasProperty(RDFS.range)
                || !attribute.getProperty(RDFS.range).getObject().isResource()) {
            return false;
        }
        return CIMAttributeUtils.hasEnumAttribute(attribute);
    }

    /**
     * Returns the URI of the datatype referenced by the attribute via {@code cims:dataType} or
     * {@code rdfs:range}, or {@code null} if none is set, or it is not a resource.
     */
    private String resolveReferencedDatatypeUri(Resource attribute) {
        if (attribute.hasProperty(CIMS.datatype)
                && attribute.getProperty(CIMS.datatype).getObject().isResource()) {
            return attribute.getProperty(CIMS.datatype).getResource().getURI();
        }
        if (attribute.hasProperty(RDFS.range)
                && attribute.getProperty(RDFS.range).getObject().isResource()) {
            return attribute.getProperty(RDFS.range).getResource().getURI();
        }
        return null;
    }

    /**
     * Verifies that an association defines the required fields {@code rdfs:range} (the target
     * class), {@code cims:associationUsed} and {@code cims:inverseRoleName}, and that the
     * referenced target class actually exists in the graph.
     */
    private void validateAssociation(Resource association, List<SchemaValidationIssue> issues) {
        var uri = association.getURI();

        // rdfs:range required (target class of the association)
        if (!association.hasProperty(RDFS.range)) {
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Association is missing rdfs:range (target class).")
                            .build());
        } else {
            validateAssociationTargetExists(association, issues);
        }
    }

    /**
     * Verifies that the target class referenced by an association via {@code rdfs:range} exists in
     * the graph, i.e. it is a resource that is typed as {@code rdfs:Class}.
     */
    private void validateAssociationTargetExists(
            Resource association, List<SchemaValidationIssue> issues) {
        var uri = association.getURI();
        var rangeObject = association.getProperty(RDFS.range).getObject();

        if (!rangeObject.isResource()) {
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message("Association target (rdfs:range) is not a resource.")
                            .build());
            return;
        }

        var targetClass = rangeObject.asResource();
        if (!targetClass.hasProperty(RDF.type, RDFS.Class)) {
            issues.add(
                    SchemaValidationIssue.builder()
                            .severity(Severity.ERROR)
                            .resourceUri(uri)
                            .message(
                                    "Association target class does not exist: <"
                                            + targetClass.getURI()
                                            + ">")
                            .build());
        }
    }

    private boolean hasNoNamespacePrefix(Model model, String namespace) {
        return !model.getNsPrefixMap().containsValue(namespace);
    }

    @Override
    public String convertToMarkdown(SchemaValidationReport report) {
        var markdown = new StringBuilder();

        markdown.append("# Schema Validation Report").append(NEW_LINE).append(NEW_LINE);

        var status = report.isValid() ? "Valid" : "Invalid";
        markdown.append("**Status:** ").append(status).append(NEW_LINE).append(NEW_LINE);

        var issues = report.getIssues();
        if (issues == null || issues.isEmpty()) {
            markdown.append("No issues found.").append(NEW_LINE);
            return markdown.toString();
        }

        // Only ERROR and WARNING issues are reported; INFO is omitted.
        var relevantIssues =
                issues.stream()
                        .filter(
                                issue ->
                                        issue.getSeverity() == Severity.ERROR
                                                || issue.getSeverity() == Severity.WARNING)
                        .collect(Collectors.groupingBy(SchemaValidationIssue::getSeverity));

        var errors = relevantIssues.getOrDefault(Severity.ERROR, List.of());
        var warnings = relevantIssues.getOrDefault(Severity.WARNING, List.of());

        if (errors.isEmpty() && warnings.isEmpty()) {
            markdown.append("No errors or warnings found.").append(NEW_LINE);
            return markdown.toString();
        }

        markdown.append("**Errors:** ")
                .append(errors.size())
                .append(" | **Warnings:** ")
                .append(warnings.size())
                .append(NEW_LINE)
                .append(NEW_LINE);

        appendSection(markdown, "Errors", errors);
        appendSection(markdown, "Warnings", warnings);

        return markdown.toString();
    }

    private void appendSection(
            StringBuilder markdown, String title, List<SchemaValidationIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }

        markdown.append("## ").append(title).append(NEW_LINE).append(NEW_LINE);
        markdown.append("| Resource | Message |").append(NEW_LINE);
        markdown.append("| --- | --- |").append(NEW_LINE);

        for (var issue : issues) {
            var resource = issue.getResourceUri() == null ? "—" : issue.getResourceUri();
            markdown.append("| ")
                    .append(escapeCell(resource))
                    .append(" | ")
                    .append(escapeCell(issue.getMessage()))
                    .append(" |")
                    .append(NEW_LINE);
        }

        markdown.append(NEW_LINE);
    }

    /** Escapes the pipe character so it does not break the Markdown table layout. */
    private String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|");
    }
}
