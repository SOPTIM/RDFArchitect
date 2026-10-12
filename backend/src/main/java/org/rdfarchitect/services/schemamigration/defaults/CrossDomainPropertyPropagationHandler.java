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

package org.rdfarchitect.services.schemamigration.defaults;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@UtilityClass
public class CrossDomainPropertyPropagationHandler {

    private record PropertyAccess<T extends SemanticPropertyChange>(
            Function<SemanticClassChange, List<T>> existing,
            BiFunction<Resource, SemanticResourceChangeType, T> create) {}

    private static final PropertyAccess<SemanticAttributeChange> ATTRIBUTES =
            new PropertyAccess<>(SemanticClassChange::getAttributes, SemanticAttributeChange::new);
    private static final PropertyAccess<SemanticAssociationChange> ASSOCIATIONS =
            new PropertyAccess<>(
                    SemanticClassChange::getAssociations, SemanticAssociationChange::new);

    public void processCrossDomainPropertyMappings(
            List<SemanticClassChange> classes,
            Model oldModel,
            Model newModel,
            List<ResourceMapping<SemanticAttributeChange>> attributeMappings,
            List<ResourceMapping<SemanticAssociationChange>> associationMappings) {
        var classChangeMap =
                classes.stream().collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));

        for (var mapping : attributeMappings) {
            processMapping(classChangeMap, oldModel, newModel, mapping, ATTRIBUTES);
        }
        for (var mapping : associationMappings) {
            processMapping(classChangeMap, oldModel, newModel, mapping, ASSOCIATIONS);
        }

        classes.clear();
        classes.addAll(classChangeMap.values());
    }

    private <T extends SemanticPropertyChange> void processMapping(
            Map<String, SemanticClassChange> classChangeMap,
            Model oldModel,
            Model newModel,
            ResourceMapping<T> mapping,
            PropertyAccess<T> propertyAccess) {
        switch (mapping.getType()) {
            case MOVE -> processMove(classChangeMap, oldModel, newModel, mapping, propertyAccess);
            case MERGE -> processMerge(classChangeMap, oldModel, newModel, mapping, propertyAccess);
            case SPLIT -> processSplit(classChangeMap, oldModel, newModel, mapping, propertyAccess);
            default -> {
                // RENAME/UNMAPPED mappings stay within one domain - nothing to propagate
            }
        }
    }

    private <T extends SemanticPropertyChange> void processMove(
            Map<String, SemanticClassChange> classChangeMap,
            Model oldModel,
            Model newModel,
            ResourceMapping<T> mapping,
            PropertyAccess<T> propertyAccess) {
        var oldResource = mapping.getOldResources().getFirst();
        var newResource = mapping.getNewResources().getFirst();

        var excluded = Set.of(oldResource.getDomain(), newResource.getDomain());
        var oldDeriving = derivingUris(oldModel, oldResource.getDomain());
        var newDeriving = derivingUris(newModel, newResource.getDomain());

        emitDifference(
                classChangeMap,
                oldModel,
                oldDeriving,
                newDeriving,
                excluded,
                oldResource.getIri(),
                SemanticResourceChangeType.DELETED_FROM_INHERITANCE,
                propertyAccess);
        emitDifference(
                classChangeMap,
                newModel,
                newDeriving,
                oldDeriving,
                excluded,
                newResource.getIri(),
                SemanticResourceChangeType.ADDED_FROM_INHERITANCE,
                propertyAccess);
    }

    private <T extends SemanticPropertyChange> void processMerge(
            Map<String, SemanticClassChange> classChangeMap,
            Model oldModel,
            Model newModel,
            ResourceMapping<T> mapping,
            PropertyAccess<T> propertyAccess) {
        var newResource = mapping.getNewResources().getFirst();
        var newDeriving = derivingUris(newModel, newResource.getDomain());

        var excluded = new HashSet<String>();
        excluded.add(newResource.getDomain());
        for (var oldResource : mapping.getOldResources()) {
            excluded.add(oldResource.getDomain());
        }

        var unionOldDeriving = new HashSet<String>();
        for (var oldResource : mapping.getOldResources()) {
            var oldDeriving = derivingUris(oldModel, oldResource.getDomain());
            unionOldDeriving.addAll(oldDeriving);
            emitDifference(
                    classChangeMap,
                    oldModel,
                    oldDeriving,
                    newDeriving,
                    excluded,
                    oldResource.getIri(),
                    SemanticResourceChangeType.DELETED_FROM_INHERITANCE,
                    propertyAccess);
        }

        emitDifference(
                classChangeMap,
                newModel,
                newDeriving,
                unionOldDeriving,
                excluded,
                newResource.getIri(),
                SemanticResourceChangeType.ADDED_FROM_INHERITANCE,
                propertyAccess);
    }

    private <T extends SemanticPropertyChange> void processSplit(
            Map<String, SemanticClassChange> classChangeMap,
            Model oldModel,
            Model newModel,
            ResourceMapping<T> mapping,
            PropertyAccess<T> propertyAccess) {
        var oldResource = mapping.getOldResources().getFirst();
        var oldDeriving = derivingUris(oldModel, oldResource.getDomain());

        var excluded = new HashSet<String>();
        excluded.add(oldResource.getDomain());
        for (var newResource : mapping.getNewResources()) {
            excluded.add(newResource.getDomain());
        }

        var unionNewDeriving = new HashSet<String>();
        for (var newResource : mapping.getNewResources()) {
            // each split target may have its own datatype/semantics, so a class gaining access
            // through this particular target needs its own default-value prompt for it
            var newDeriving = derivingUris(newModel, newResource.getDomain());
            unionNewDeriving.addAll(newDeriving);
            emitDifference(
                    classChangeMap,
                    newModel,
                    newDeriving,
                    oldDeriving,
                    excluded,
                    newResource.getIri(),
                    SemanticResourceChangeType.ADDED_FROM_INHERITANCE,
                    propertyAccess);
        }

        // a class only loses the property if it kept access through NONE of the split targets
        emitDifference(
                classChangeMap,
                oldModel,
                oldDeriving,
                unionNewDeriving,
                excluded,
                oldResource.getIri(),
                SemanticResourceChangeType.DELETED_FROM_INHERITANCE,
                propertyAccess);
    }

    private <T extends SemanticPropertyChange> void emitDifference(
            Map<String, SemanticClassChange> classChangeMap,
            Model model,
            Set<String> fromDeriving,
            Set<String> toSubtract,
            Set<String> excluded,
            String propertyIri,
            SemanticResourceChangeType changeType,
            PropertyAccess<T> propertyAccess) {
        var difference = new HashSet<>(fromDeriving);
        difference.removeAll(toSubtract);
        difference.removeAll(excluded);

        for (var classUri : difference) {
            var classResource = model.getResource(classUri);
            if (!CIMClassUtils.isInstantiableClass(classResource)) {
                continue;
            }

            var classChange =
                    classChangeMap.computeIfAbsent(
                            classUri,
                            _ ->
                                    new SemanticClassChange(
                                            classResource, SemanticResourceChangeType.CHANGE));
            addPropagatedPropertyChange(
                    classChange, model, propertyIri, changeType, propertyAccess);
        }
    }

    private <T extends SemanticPropertyChange> void addPropagatedPropertyChange(
            SemanticClassChange classChange,
            Model model,
            String propertyIri,
            SemanticResourceChangeType changeType,
            PropertyAccess<T> propertyAccess) {
        var existingChanges = propertyAccess.existing().apply(classChange);
        if (existingChanges.stream().anyMatch(change -> propertyIri.equals(change.getIri()))) {
            return;
        }

        var propertyResource = model.getResource(propertyIri);
        var label = new URI(propertyIri).getSuffix();

        var propertyChange = propertyAccess.create().apply(propertyResource, changeType);
        propertyChange.setLabel(label);
        propertyChange.setDomain(classChange.getIri());
        existingChanges.add(propertyChange);
    }

    private Set<String> derivingUris(Model model, String classIri) {
        if (classIri == null) {
            return Set.of();
        }
        return CIMClassUtils.findDerivingClasses(model.getResource(classIri)).stream()
                .map(Resource::getURI)
                .collect(Collectors.toSet());
    }
}
