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

package org.rdfarchitect.services.schemamigration.renamings;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.models.changes.MappingType;
import org.rdfarchitect.models.changes.PotentialPropertyData;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@UtilityClass
public class PropertyMappingUtilities {

    /**
     * Records whether each association can be instantiated, so the rename step can leave out the
     * ones that carry no instance data and have nothing to map. Only the default-values step fills
     * this in otherwise, which is a step too late.
     *
     * <p>The flag has to come from the schema the association still exists in — a deleted one is
     * only in the original — with the other schema as a fallback.
     */
    public void markAssociationsUsed(
            List<SemanticAssociationChange> associations, Model originalModel, Model updatedModel) {
        for (var association : associations) {
            var deleted =
                    association.getSemanticResourceChangeType()
                            == SemanticResourceChangeType.DELETE;
            association.setAssociationUsed(
                    isAssociationUsed(
                            association.getIri(),
                            deleted ? originalModel : updatedModel,
                            deleted ? updatedModel : originalModel));
        }
    }

    /**
     * Collects the properties a class could map its changes to: its own, plus those of the changed
     * classes above and below it in the hierarchy, since a property can move along it.
     */
    public PotentialPropertyData findPotentialProperties(
            SemanticClassChange classChange,
            Map<String, SemanticClassChange> classChanges,
            Model model) {

        var superClasses = CIMClassUtils.listSuperClasses(model.getResource(classChange.getIri()));
        var derivedClasses =
                CIMClassUtils.findDerivingClasses(model.getResource(classChange.getIri()));

        var attributes = new ArrayList<>(classChange.getAttributes());
        var associations = new ArrayList<>(classChange.getAssociations());
        var enumEntries = new ArrayList<>(classChange.getEnumEntries());

        for (var superClass : superClasses) {
            var superClassChange = classChanges.get(superClass.getURI());
            if (superClassChange != null) {
                attributes.addAll(superClassChange.getAttributes());
                associations.addAll(superClassChange.getAssociations());
                enumEntries.addAll(superClassChange.getEnumEntries());
            }
        }

        for (var derivedClass : derivedClasses) {
            var derivedClassChange = classChanges.get(derivedClass.getURI());
            if (derivedClassChange != null) {
                attributes.addAll(derivedClassChange.getAttributes());
                associations.addAll(derivedClassChange.getAssociations());
                enumEntries.addAll((derivedClassChange.getEnumEntries()));
            }
        }

        return new PotentialPropertyData(attributes, associations, enumEntries);
    }

    /**
     * Replaces the moves that all point at the same new property with a single merge mapping, so
     * siblings losing the same property to their base class are confirmed as one decision.
     */
    public <T extends SemanticPropertyChange> void collapseMovesIntoMerges(
            Map<String, SemanticClassChange> classChanges,
            Function<SemanticClassChange, List<ResourceMapping<T>>> mappingAccessor) {
        var moves =
                classChanges.values().stream()
                        .flatMap(cls -> mappingAccessor.apply(cls).stream())
                        .filter(m -> m.getType() == MappingType.MOVE)
                        .filter(
                                m ->
                                        Objects.equals(
                                                m.getOldResources().getFirst().getLabel(),
                                                m.getNewResources().getFirst().getLabel()))
                        .toList();

        var moveTo =
                moves.stream()
                        .collect(
                                Collectors.groupingBy(
                                        m -> m.getNewResources().getFirst().getIri()));

        for (var sources : moveTo.values()) {
            if (sources.size() < 2) {
                continue;
            }

            var newResource = sources.getFirst().getNewResources().getFirst();
            var oldResources = sources.stream().map(m -> m.getOldResources().getFirst()).toList();
            var merge = new ResourceMapping<>(oldResources, newResource, 1.0);

            for (var source : sources) {
                var oldDomain = classChanges.get(source.getOldResources().getFirst().getDomain());
                if (oldDomain == null) {
                    continue;
                }
                var bucket = mappingAccessor.apply(oldDomain);
                bucket.remove(source);
                bucket.add(merge);
            }
        }
    }

    private boolean isAssociationUsed(String iri, Model preferred, Model fallback) {
        var association = preferred.getResource(iri);
        if (!association.hasProperty(CIMS.associationUsed)) {
            association = fallback.getResource(iri);
        }
        return CIMPropertyUtils.isAssociationUsed(association);
    }

    public <T extends SemanticResourceChange> void applyPropertyMappings(
            Map<String, SemanticClassChange> classesByIri,
            Function<SemanticClassChange, List<T>> propertyListAccessor,
            List<ResourceMapping<T>> mappings) {
        var mappingsIDs = new HashSet<UUID>();

        for (var mapping : mappings) {
            if (!mappingsIDs.add(mapping.getId())) {
                continue;
            }

            var oldIris =
                    mapping.getOldResources().stream()
                            .map(PropertyMappingUtilities::iriOf)
                            .toList();
            var newIris =
                    mapping.getNewResources().stream()
                            .map(PropertyMappingUtilities::iriOf)
                            .toList();

            for (var classChange : classesByIri.values()) {
                propertyListAccessor
                        .apply(classChange)
                        .removeIf(
                                property ->
                                        oldIris.contains(property.getIri())
                                                || newIris.contains(property.getIri()));
            }

            if (mapping.getType() == MappingType.SPLIT) {
                for (var merged : RenameObjectBuilder.createSplitObjects(mapping)) {
                    attachMergedProperty(classesByIri, propertyListAccessor, merged);
                }
            } else {
                attachMergedProperty(
                        classesByIri,
                        propertyListAccessor,
                        RenameObjectBuilder.createRenameObject(mapping));
            }

            attachDeletionMarkersForLosingDomains(classesByIri, propertyListAccessor, mapping);
        }
    }

    private <T extends SemanticResourceChange> void attachDeletionMarkersForLosingDomains(
            Map<String, SemanticClassChange> classesByIri,
            Function<SemanticClassChange, List<T>> propertyListAccessor,
            ResourceMapping<T> mapping) {
        var newDomains =
                mapping.getNewResources().stream()
                        .map(PropertyMappingUtilities::domainOf)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        var targetIris =
                mapping.getNewResources().stream()
                        .map(PropertyMappingUtilities::iriOf)
                        .filter(Objects::nonNull)
                        .toList();

        for (var oldResource : mapping.getOldResources()) {
            var domain = domainOf(oldResource);
            if (domain == null || newDomains.contains(domain)) {
                continue;
            }

            var domainClass = classesByIri.get(domain);
            if (domainClass == null) {
                continue;
            }

            @SuppressWarnings("unchecked")
            var deleted = (T) oldResource.copy();
            deleted.setSemanticResourceChangeType(SemanticResourceChangeType.REMOVED_VIA_MAPPING);
            deleted.setMappedToIRIs(targetIris);
            propertyListAccessor.apply(domainClass).add(deleted);
        }
    }

    private <T extends SemanticResourceChange> void attachMergedProperty(
            Map<String, SemanticClassChange> classesByIri,
            Function<SemanticClassChange, List<T>> propertyListAccessor,
            T merged) {
        if (merged.getChanges().isEmpty()) {
            return;
        }
        var domain = domainOf(merged);
        var targetClass = domain != null ? classesByIri.get(domain) : null;
        if (targetClass != null) {
            propertyListAccessor.apply(targetClass).add(merged);
        }
    }

    private String domainOf(SemanticResourceChange change) {
        return change instanceof SemanticPropertyChange propertyChange
                ? propertyChange.getDomain()
                : null;
    }

    private String iriOf(SemanticResourceChange change) {
        return change != null ? change.getIri() : null;
    }
}
