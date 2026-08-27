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

package org.rdfarchitect.services.schemamigration.artifacts;

import static org.apache.jena.rdf.model.ModelFactory.createModelForGraph;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MarkdownMigrationReportBuilder implements MigrationReportBuilder {

    private final PrefixMapping defaultPrefixes;

    public MarkdownMigrationReportBuilder(SchemaConfig schemaConfig) {
        this.defaultPrefixes = new PrefixMappingImpl().setNsPrefixes(PrefixMapping.Standard);
        schemaConfig.getNamespaces().forEach(this.defaultPrefixes::setNsPrefix);
        this.defaultPrefixes.lock();
    }

    @Override
    public String generateDetailedMigrationReport(
            List<SemanticClassChange> classChanges,
            Graph originalGraph,
            Graph updatedGraph,
            boolean ignorePrefixes) {
        var visibleChanges = ignorePrefixes ? applyPrefixRenameFilter(classChanges) : classChanges;

        var sb = new StringBuilder();
        sb.append("# Migration Report — Detailed View\n\n");

        appendStatsSummary(sb, visibleChanges);

        sb.append("All affected concrete classes are listed alphabetically. ");
        sb.append("Inherited changes are shown under each affected subclass.\n\n");

        var model = createModelForGraph(originalGraph);
        var concreteClassIRIs =
                model.listResourcesWithProperty(RDF.type, RDFS.Class)
                        .filterKeep(r -> r.hasProperty(CIMS.stereotype, CIMStereotypes.concrete))
                        .mapWith(Resource::getURI)
                        .toSet();
        model = createModelForGraph(updatedGraph);
        var updatedConcreteClassIRIs =
                model.listResourcesWithProperty(RDF.type, RDFS.Class)
                        .filterKeep(r -> r.hasProperty(CIMS.stereotype))
                        .mapWith(Resource::getURI)
                        .toSet();
        concreteClassIRIs.addAll(updatedConcreteClassIRIs);

        var sortedConcreteClassIRIs =
                concreteClassIRIs.stream()
                        .sorted(Comparator.comparing(iri -> new URI(iri).getSuffix()))
                        .toList();

        var classChangesMap =
                visibleChanges.stream()
                        .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));

        for (var cls : sortedConcreteClassIRIs) {
            appendDetailedClassSection(sb, cls, model, classChangesMap);
        }

        return sb.toString();
    }

    @Override
    public String generateSummaryMigrationReport(
            List<SemanticClassChange> classChanges, Graph updatedGraph, boolean ignorePrefixes) {
        var visibleChanges = ignorePrefixes ? applyPrefixRenameFilter(classChanges) : classChanges;

        var sb = new StringBuilder();
        sb.append("# Migration Report — Summary View\n\n");

        appendStatsSummary(sb, visibleChanges);

        sb.append(
                "Classes with direct changes. For parent classes, affected concrete subclasses are listed.\n\n");

        var directlyChanged =
                visibleChanges.stream()
                        .filter(this::hasDirectChange)
                        .sorted(Comparator.comparing(SemanticResourceChange::getLabel))
                        .toList();

        for (var classChange : directlyChanged) {
            appendClassHeader(sb, classChange);

            if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE) {
                appendFieldChangesAsSentences(sb, classChange.getChanges());
            }

            appendDirectProperties(sb, "Attributes", classChange.getAttributes());
            appendDirectProperties(sb, "Associations", classChange.getAssociations());
            appendDirectProperties(sb, "Enum Entries", classChange.getEnumEntries());

            var model = createModelForGraph(updatedGraph);
            var affectedSubclasses = findAffectedSubclasses(classChange, model);
            if (!affectedSubclasses.isEmpty()) {
                sb.append("**Affected concrete classes:**\n\n");
                for (var sub : affectedSubclasses) {
                    sb.append("- ").append(sub).append("\n");
                }
                sb.append("\n");
            }

            sb.append("---\n\n");
        }

        return sb.toString();
    }

    private void appendStatsSummary(StringBuilder sb, List<SemanticClassChange> classChanges) {
        int added = 0;
        int deleted = 0;
        int changed = 0;

        for (var change : classChanges) {
            switch (change.getSemanticResourceChangeType()) {
                case ADD -> added++;
                case DELETE -> deleted++;
                case CHANGE, RENAME -> changed++;
                default -> {
                    // changes from inheritance should not be included in the stat summary
                }
            }
        }

        sb.append("## Summary\n\n");
        sb.append("| Category | Count |\n");
        sb.append("|----------|------:|\n");
        sb.append("| Added    | ").append(added).append(" |\n");
        sb.append("| Deleted  | ").append(deleted).append(" |\n");
        sb.append("| Changed  | ").append(changed).append(" |\n");
        sb.append("\n---\n\n");
    }

    private void appendDetailedClassSection(
            StringBuilder sb,
            String classIRI,
            Model model,
            Map<String, SemanticClassChange> classChangesMap) {

        SemanticClassChange classChange;
        if (classChangesMap.containsKey(classIRI)) {
            classChange = classChangesMap.get(classIRI);
        } else {
            // If the class is not in the changes map, it means it has no direct changes.
            // We create a dummy SemanticClassChange to represent it.
            classChange = new SemanticClassChange();
            classChange.setIri(classIRI);
            classChange.setLabel(new URI(classIRI).getSuffix());
            classChange.setSemanticResourceChangeType(SemanticResourceChangeType.INHERITS_CHANGE);
        }

        // Collect changed properties from ancestor classes
        var classResource = model.getResource(classChange.getIri());
        var ancestorChangedAttributes = new ArrayList<SemanticResourceChange>();
        var ancestorChangedAssociations = new ArrayList<SemanticResourceChange>();
        var ancestorChangedEnumEntries = new ArrayList<SemanticResourceChange>();

        CIMClassUtils.listSuperClasses(classResource).stream()
                .map(r -> classChangesMap.get(r.getURI()))
                .filter(Objects::nonNull)
                .forEach(
                        superclass -> {
                            superclass.getAttributes().stream()
                                    .filter(
                                            p ->
                                                    p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE
                                                            && p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE)
                                    .forEach(ancestorChangedAttributes::add);
                            superclass.getAssociations().stream()
                                    .filter(
                                            p ->
                                                    p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE
                                                            && p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE)
                                    .forEach(ancestorChangedAssociations::add);
                            superclass.getEnumEntries().stream()
                                    .filter(
                                            p ->
                                                    p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE
                                                            && p.getSemanticResourceChangeType()
                                                            != SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE)
                                    .forEach(ancestorChangedEnumEntries::add);
                        });

        if (classChange.getSemanticResourceChangeType() == SemanticResourceChangeType.INHERITS_CHANGE &&
        ancestorChangedAttributes.isEmpty() && ancestorChangedAssociations.isEmpty() && ancestorChangedEnumEntries.isEmpty()) {
            return;
        }

        appendClassHeader(sb, classChange);

        if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE) {
            appendFieldChangesAsSentences(sb, classChange.getChanges());
        }
        appendPropertySection(sb, "Attributes", classChange.getAttributes());
        appendPropertySection(sb, "Associations", classChange.getAssociations());
        appendPropertySection(sb, "Enum Entries", classChange.getEnumEntries());

        appendPropertySection(sb, "Inherited Attribute Changes", ancestorChangedAttributes);
        appendPropertySection(sb, "Inherited Association Changes", ancestorChangedAssociations);
        appendPropertySection(sb, "Inherited Enum Entry Changes", ancestorChangedEnumEntries);

        sb.append("\n---\n\n");
    }

    private void appendClassHeader(StringBuilder sb, SemanticClassChange classChange) {
        if (classChange.getSemanticResourceChangeType() == SemanticResourceChangeType.RENAME) {
            var oldLabel = new URI(classChange.getOldIRI()).getSuffix();
            sb.append("## Renamed from ")
                    .append(oldLabel)
                    .append(" to ")
                    .append(classChange.getLabel())
                    .append("\n\n");
            sb.append("**Old IRI:** ").append(shorten(classChange.getOldIRI())).append("\n\n");
            sb.append("**IRI:** ").append(shorten(classChange.getIri())).append("\n\n");
        } else {
            sb.append("## ")
                    .append(formatChangeType(classChange.getSemanticResourceChangeType()))
                    .append(" ")
                    .append(classChange.getLabel())
                    .append("\n\n");
            sb.append("**IRI:** ").append(shorten(classChange.getIri())).append("\n\n");
        }

        if (classChange.getComment() != null && !classChange.getComment().isBlank()) {
            sb.append("> ").append(classChange.getComment()).append("\n\n");
        }
    }

    private void appendFieldChangesAsSentences(
            StringBuilder sb, List<SemanticFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        for (var change : changes) {
            sb.append("- ").append(fieldChangeToSentence(change)).append("\n");
        }
        sb.append("\n");
    }

    private String fieldChangeToSentence(SemanticFieldChange change) {
        var from = change.getFrom();
        var to = change.getTo();

        return switch (change.getSemanticFieldChangeType()) {
            case LABEL_CHANGE -> formatTransition("Label set", from, to);
            case COMMENT_CHANGE -> "Comment was updated.";
            case SUPERCLASS_CHANGE -> formatTransition("Superclass set", from, to);
            case SUPERCLASS_RENAME -> formatTransition("Superclass renamed", from, to);
            case BELONGS_TO_CATEGORY_CHANGE -> formatTransition("Package set", from, to);
            case DATATYPE_CHANGE -> formatTransition("Datatype set", from, to);
            case DATATYPE_RENAME -> formatTransition("Datatype renamed", from, to);
            case MADE_OPTIONAL -> "Was marked optional.";
            case MADE_REQUIRED -> "Was marked required.";
            case MULTIPLICITY_CHANGE -> formatTransition("Multiplicity set", from, to);
            case STEREOTYPE_ADDED -> "Stereotype added: `" + shorten(to) + "`.";
            case STEREOTYPE_REMOVED -> "Stereotype removed: " + shorten(from) + ".";
            case MADE_ABSTRACT -> "Was made abstract.";
            case DOMAIN_CHANGE -> formatTransition("Domain set", from, to);
            case DOMAIN_RENAME -> formatTransition("Domain renamed", from, to);
            case TARGET_CHANGE -> formatTransition("Target set", from, to);
            case TARGET_RENAME -> formatTransition("Target renamed", from, to);
            case ASSOCIATION_USED_CHANGE -> formatTransition("Association used set", from, to);
            case DEFAULT_VALUE_CHANGE -> formatTransition("Default value set", from, to);
            case FIXED_VALUE_CHANGE -> formatTransition("Fixed value set", from, to);
        };
    }

    private String formatTransition(String description, String from, String to) {
        var shortFrom = shorten(from);
        var shortTo = shorten(to);
        if (from == null && to != null) {
            return description + " to `" + shortTo + "`.";
        } else if (from != null && to == null) {
            return description + " (removed `" + shortFrom + "`).";
        } else if (from != null) {
            return description + " from `" + shortFrom + "` to `" + shortTo + "`.";
        }
        return description + ".";
    }

    private <T extends SemanticResourceChange> void appendPropertySection(
            StringBuilder sb, String title, List<T> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        sb.append("### ").append(title).append("\n\n");

        for (var prop : properties) {
            if (prop.getSemanticResourceChangeType() == SemanticResourceChangeType.RENAME
                    && prop.getOldIRI() != null) {
                var oldLabel = new URI(prop.getOldIRI()).getSuffix();
                sb.append("#### Renamed from ")
                        .append(oldLabel)
                        .append(" to ")
                        .append(prop.getLabel())
                        .append("\n\n");
            } else {
                sb.append("#### ")
                        .append(formatChangeType(prop.getSemanticResourceChangeType()))
                        .append(" ")
                        .append(prop.getLabel())
                        .append("\n\n");
            }

            if (prop.getComment() != null && !prop.getComment().isBlank()) {
                sb.append("> ").append(prop.getComment()).append("\n\n");
            }

            if (prop.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE
                    && prop.getSemanticResourceChangeType()
                            != SemanticResourceChangeType.DELETED_FROM_INHERITANCE) {
                appendFieldChangesAsSentences(sb, prop.getChanges());
            }
        }
    }

    private <T extends SemanticResourceChange> void appendDirectProperties(
            StringBuilder sb, String title, List<T> properties) {
        if (properties == null) {
            return;
        }

        var direct =
                properties.stream()
                        .filter(
                                p ->
                                        p.getSemanticResourceChangeType()
                                                        != SemanticResourceChangeType
                                                                .ADDED_FROM_INHERITANCE
                                                && p.getSemanticResourceChangeType()
                                                        != SemanticResourceChangeType
                                                                .DELETED_FROM_INHERITANCE)
                        .toList();
        if (direct.isEmpty()) {
            return;
        }
        appendPropertySection(sb, title, direct);
    }

    private boolean hasDirectChange(SemanticClassChange c) {
        if (!c.getChanges().isEmpty()) {
            return true;
        }
        var allProperties = new ArrayList<SemanticResourceChange>(c.getAssociations());
        allProperties.addAll(c.getAttributes());
        allProperties.addAll(c.getEnumEntries());

        if (allProperties.isEmpty()) {
            return false;
        }

        return allProperties.stream()
                .anyMatch(
                        p ->
                                p.getSemanticResourceChangeType()
                                                != SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                                        && p.getSemanticResourceChangeType()
                                                != SemanticResourceChangeType
                                                        .DELETED_FROM_INHERITANCE);
    }

    private List<URI> findAffectedSubclasses(SemanticClassChange parentChange, Model model) {
        var parentClass = model.getResource(parentChange.getIri());
        var subclasses = CIMClassUtils.findDerivingClasses(parentClass);
        return subclasses.stream()
                .filter(cls -> cls.hasProperty(CIMS.stereotype, CIMStereotypes.concrete))
                .map(cls -> new URI(cls.getURI()))
                .toList();
    }

    private String formatChangeType(SemanticResourceChangeType type) {
        return switch (type) {
            case ADD -> "[Added]";
            case DELETE -> "[Deleted]";
            case CHANGE -> "[Changed]";
            case RENAME -> "[Renamed]";
            case ADDED_FROM_INHERITANCE -> "[Added via inheritance]";
            case DELETED_FROM_INHERITANCE -> "[Deleted via inheritance]";
            case INHERITS_CHANGE -> "[Inherits changes]";
        };
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }

        var shortened = defaultPrefixes.shortForm(value);
        if (shortened.equals(value)) {
            return value;
        }
        return shortened;
    }

    private List<SemanticClassChange> applyPrefixRenameFilter(
            List<SemanticClassChange> classChanges) {
        return classChanges.stream()
                .map(this::downgradeIfPrefixOnlyRename)
                .filter(this::hasVisibleChanges)
                .toList();
    }

    private SemanticClassChange downgradeIfPrefixOnlyRename(SemanticClassChange classChange) {
        var result = classChange;
        if (isPrefixOnlyRename(classChange)) {
            var copy = new SemanticClassChange(classChange);
            copy.setSemanticResourceChangeType(SemanticResourceChangeType.CHANGE);
            copy.setOldIRI(null);

            copy.setChanges(
                    copy.getChanges().stream().filter(fc -> !isPrefixOnlyFieldRename(fc)).toList());

            result = copy;
        }

        result.setAttributes(filterProperties(result.getAttributes()));
        result.setAssociations(filterProperties(result.getAssociations()));
        result.setEnumEntries(filterProperties(result.getEnumEntries()));

        return result;
    }

    private <T extends SemanticResourceChange> List<T> filterProperties(List<T> properties) {
        if (properties == null) {
            return Collections.emptyList();
        }

        return properties.stream()
                .map(
                        p -> {
                            if (isPrefixOnlyRename(p)) {
                                var copy = p.copy();
                                copy.setSemanticResourceChangeType(
                                        SemanticResourceChangeType.CHANGE);
                                copy.setOldIRI(null);

                                copy.setChanges(
                                        copy.getChanges().stream()
                                                .filter(fc -> !isPrefixOnlyFieldRename(fc))
                                                .toList());

                                return (T) copy;
                            }
                            return p;
                        })
                .filter(
                        p ->
                                p.getSemanticResourceChangeType()
                                                != SemanticResourceChangeType.CHANGE
                                        || (p.getChanges() != null && !p.getChanges().isEmpty()))
                .toList();
    }

    private boolean hasVisibleChanges(SemanticClassChange classChange) {
        if (classChange.getSemanticResourceChangeType() == SemanticResourceChangeType.CHANGE) {
            boolean hasFieldChanges =
                    classChange.getChanges() != null && !classChange.getChanges().isEmpty();
            boolean hasProperties =
                    !classChange.getAttributes().isEmpty()
                            || !classChange.getAssociations().isEmpty()
                            || !classChange.getEnumEntries().isEmpty();
            return hasFieldChanges || hasProperties;
        }
        return true;
    }

    private boolean isPrefixOnlyRename(SemanticResourceChange change) {
        if (change.getSemanticResourceChangeType() != SemanticResourceChangeType.RENAME) {
            return false;
        }
        if (change.getOldIRI() == null || change.getIri() == null) {
            return false;
        }

        return new URI(change.getOldIRI()).getSuffix().equals(new URI(change.getIri()).getSuffix());
    }

    private boolean isPrefixOnlyFieldRename(SemanticFieldChange change) {
        if (change.getFrom() == null || change.getTo() == null) {
            return false;
        }
        if (change.getSemanticFieldChangeType() == SemanticFieldChangeType.DOMAIN_RENAME
                || change.getSemanticFieldChangeType() == SemanticFieldChangeType.SUPERCLASS_RENAME
                || change.getSemanticFieldChangeType() != SemanticFieldChangeType.TARGET_RENAME) {
            return new URI(change.getFrom())
                    .getSuffix()
                    .equals(new URI(change.getTo()).getSuffix());
        } else {
            return false;
        }
    }
}
