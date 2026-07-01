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

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MarkdownMigrationReportBuilder implements MigrationReportBuilder {

    @Override
    public String generateDetailedMigrationReport(List<SemanticClassChange> classChanges, Graph originalGraph) {
        var sb = new StringBuilder();
        sb.append("# Migration Report — Detailed View\n\n");
        sb.append("All affected concrete classes are listed alphabetically. ");
        sb.append("Inherited changes are shown under each affected subclass.\n\n");

        var model = ModelFactory.createModelForGraph(originalGraph);
        var concreteClassIRIs = model.listResourcesWithProperty(RDF.type, RDFS.Class)
                .filterKeep(r -> r.hasProperty(CIMS.stereotype, CIMStereotypes.concrete))
                .filterDrop(r -> r.hasProperty(CIMS.stereotype, CIMStereotypes.enumeration))
                .filterDrop(r -> r.hasProperty(CIMS.stereotype, CIMStereotypes.cimDataType))
                .mapWith(Resource::getURI)
                .toList();

        var concreteChanges = classChanges.stream()
                .filter(c -> concreteClassIRIs.contains(c.getIri()))
                .sorted(Comparator.comparing(SemanticResourceChange::getLabel))
                .toList();

        for (var classChange : concreteChanges) {
            appendClassSection(sb, classChange);
        }

        return sb.toString();
    }

    private void appendClassSection(StringBuilder sb, SemanticClassChange classChange) {
        sb.append("## ").append(formatChangeType(classChange.getSemanticResourceChangeType()))
                .append(" `").append(classChange.getLabel()).append("`\n\n");
        sb.append("**IRI:** `").append(classChange.getIri()).append("`\n\n");

        if (classChange.getOldIRI() != null) {
            sb.append("**Previous IRI:** `").append(classChange.getOldIRI()).append("`\n\n");
        }

        appendFieldChangesTable(sb, classChange.getChanges());
        appendPropertySection(sb, "Attributes", classChange.getAttributes());
        appendPropertySection(sb, "Associations", classChange.getAssociations());
        appendPropertySection(sb, "Enum Entries", classChange.getEnumEntries());
        sb.append("\n---\n\n");
    }

    @Override
    public String generateSummaryMigrationReport(List<SemanticClassChange> classChanges, Graph originalGraph) {
        var sb = new StringBuilder();
        sb.append("# Migration Report — Summary View\n\n");
        sb.append("Classes with direct changes. For parent classes, affected concrete subclasses are listed.\n\n");

        // Classes that have at least one direct (non-inherited) change
        var directlyChanged = classChanges.stream()
                .filter(this::hasDirectChange)
                .sorted(Comparator.comparing(SemanticResourceChange::getLabel))
                .toList();

        for (var classChange : directlyChanged) {
            sb.append("## ").append(formatChangeType(classChange.getSemanticResourceChangeType()))
                    .append(" `").append(classChange.getLabel()).append("`\n\n");
            sb.append("**IRI:** `").append(classChange.getIri()).append("`\n\n");

            // Show only direct changes
            var directFieldChanges = classChange.getChanges(); // class-level are always direct
            appendFieldChangesTable(sb, directFieldChanges);

            appendDirectProperties(sb, "Attributes", classChange.getAttributes());
            appendDirectProperties(sb, "Associations", classChange.getAssociations());
            appendDirectProperties(sb, "Enum Entries", classChange.getEnumEntries());

            // List affected concrete subclasses
            var affectedSubclasses = findAffectedSubclasses(classChange, classChanges);
            if (!affectedSubclasses.isEmpty()) {
                sb.append("\n**Affected concrete classes:**\n\n");
                for (var sub : affectedSubclasses) {
                    sb.append("- `").append(sub.getLabel()).append("`\n");
                }
                sb.append("\n");
            }

            sb.append("---\n\n");
        }

        return sb.toString();
    }

    private boolean hasDirectChange(SemanticClassChange c) {
        if (!c.getChanges().isEmpty()) return true;
        var allProperties = new ArrayList<SemanticResourceChange>(c.getAssociations());
        allProperties.addAll(c.getAttributes());
        allProperties.addAll(c.getEnumEntries());

        if (allProperties.isEmpty()) return false;
        return allProperties.stream().anyMatch(p ->
                p.getSemanticResourceChangeType() != SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                        && p.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETED_FROM_INHERITANCE);
    }

    private List<SemanticClassChange> findAffectedSubclasses(
            SemanticClassChange parentChange,
            List<SemanticClassChange> allChanges) {
        var directPropertyLabels = collectDirectPropertyLabels(parentChange);

        return allChanges.stream()
                .filter(c -> !c.getIri().equals(parentChange.getIri()))
                .filter(c -> hasMatchingInheritedProperty(c, directPropertyLabels))
                .sorted(Comparator.comparing(SemanticResourceChange::getLabel))
                .toList();
    }

    private Set<String> collectDirectPropertyLabels(SemanticClassChange classChange) {
        var labels = new HashSet<String>();
        addDirectLabels(labels, classChange.getAttributes());
        addDirectLabels(labels, classChange.getAssociations());
        addDirectLabels(labels, classChange.getEnumEntries());
        return labels;
    }

    private <T extends SemanticResourceChange> void addDirectLabels(Set<String> labels, List<T> properties) {
        if (properties == null) return;
        properties.stream()
                .filter(p -> p.getSemanticResourceChangeType() != SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                        && p.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETED_FROM_INHERITANCE)
                .forEach(p -> labels.add(p.getLabel()));
    }

    private boolean hasMatchingInheritedProperty(
            SemanticClassChange classChange, Set<String> parentPropertyLabels) {
        return matchesAny(classChange.getAttributes(), parentPropertyLabels)
                || matchesAny(classChange.getAssociations(), parentPropertyLabels)
                || matchesAny(classChange.getEnumEntries(), parentPropertyLabels);
    }

    private <T extends SemanticResourceChange> boolean matchesAny(List<T> properties, Set<String> labels) {
        if (properties == null) return false;
        return properties.stream()
                .filter(p -> p.getSemanticResourceChangeType() == SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                        || p.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETED_FROM_INHERITANCE)
                .anyMatch(p -> labels.contains(p.getLabel()));
    }

    private void appendFieldChangesTable(StringBuilder sb, List<SemanticFieldChange> changes) {
        if (changes == null || changes.isEmpty()) return;
        sb.append("| Change | From | To |\n");
        sb.append("|--------|------|----|\n");
        for (var c : changes) {
            sb.append("| ").append(c.getSemanticFieldChangeType())
                    .append(" | ").append(c.getFrom() != null ? "`" + c.getFrom() + "`" : "—")
                    .append(" | ").append(c.getTo() != null ? "`" + c.getTo() + "`" : "—")
                    .append(" |\n");
        }
        sb.append("\n");
    }

    private <T extends SemanticResourceChange> void appendPropertySection(
            StringBuilder sb, String title, List<T> properties) {
        if (properties == null || properties.isEmpty()) return;
        sb.append("### ").append(title).append("\n\n");
        for (var prop : properties) {
            sb.append("- ").append(formatChangeType(prop.getSemanticResourceChangeType()))
                    .append(" `").append(prop.getLabel()).append("`");
            if (!prop.getChanges().isEmpty()) {
                sb.append("\n");
                appendFieldChangesTable(sb, prop.getChanges());
            } else {
                sb.append("\n");
            }
        }
        sb.append("\n");
    }

    private <T extends SemanticResourceChange> void appendDirectProperties(
            StringBuilder sb, String title, List<T> properties) {
        if (properties == null) return;
        var direct = properties.stream()
                .filter(p -> p.getSemanticResourceChangeType() != SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                        && p.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETED_FROM_INHERITANCE)
                .toList();
        if (direct.isEmpty()) return;
        appendPropertySection(sb, title, direct);
    }

    private String formatChangeType(SemanticResourceChangeType type) {
        return switch (type) {
            case ADD -> "[Added]";
            case DELETE -> "[Deleted]";
            case CHANGE -> "[Changed]";
            case RENAME -> "[Renamed]";
            case ADDED_FROM_INHERITANCE -> "[Added via inheritance]";
            case DELETED_FROM_INHERITANCE -> "[Deleted via inheritance]";
        };
    }
}
