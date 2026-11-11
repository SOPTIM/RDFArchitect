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

import lombok.RequiredArgsConstructor;

import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappingOverview;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappings;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.PotentialPropertyData;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drives the property step of the migration wizard: suggests which deleted attribute, association
 * or enum entry became which added one, and merges the confirmed mappings into the class changes
 * the later steps work on.
 */
@Service
@RequiredArgsConstructor
public class PropertyMappingService
        implements GetPropertyMappingsUseCase, ConfirmPropertyMappingsUseCase {

    private final MigrationSessionStore migrationSessionStore;

    @Override
    public List<ClassPropertyMappingOverview> getPropertyMappings() {
        var context = migrationSessionStore.getContext();
        var classChanges =
                context.getDiffAfterClassConfirm().stream()
                        .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));
        var potentialPropertiesByIri = new HashMap<String, PotentialPropertyData>();

        var originalModel = ModelFactory.createModelForGraph(context.getOriginalSchema());
        var updatedModel = ModelFactory.createModelForGraph(context.getUpdatedSchema());

        for (var cls : classChanges.values()) {
            PropertyMappingUtilities.markAssociationsUsed(
                    cls.getAssociations(), originalModel, updatedModel);
            var potentialProperties =
                    PropertyMappingUtilities.findPotentialProperties(
                            cls, classChanges, updatedModel);
            potentialPropertiesByIri.put(cls.getIri(), potentialProperties);

            if (cls.getAttributeResourceMappings().isEmpty()) {
                cls.setAttributeResourceMappings(
                        RenameDetector.detectPropertyMappings(
                                cls.getAttributes(), potentialProperties.attributes()));
            }
            if (cls.getAssociationResourceMappings().isEmpty()) {
                cls.setAssociationResourceMappings(
                        RenameDetector.detectPropertyMappings(
                                cls.getAssociations(), potentialProperties.associations()));
            }
            if (cls.getEnumEntryResourceMappings().isEmpty()) {
                cls.setEnumEntryResourceMappings(
                        RenameDetector.detectPropertyMappings(
                                cls.getEnumEntries(), potentialProperties.enumEntries()));
            }
        }

        PropertyMappingUtilities.collapseMovesIntoMerges(
                classChanges, SemanticClassChange::getAttributeResourceMappings);
        PropertyMappingUtilities.collapseMovesIntoMerges(
                classChanges, SemanticClassChange::getAssociationResourceMappings);
        PropertyMappingUtilities.collapseMovesIntoMerges(
                classChanges, SemanticClassChange::getEnumEntryResourceMappings);

        var mappings = new ArrayList<ClassPropertyMappingOverview>();
        for (var cls : classChanges.values()) {
            if (cls.getAttributes().size()
                            + cls.getAssociations().size()
                            + cls.getEnumEntries().size()
                    > 0) {
                mappings.add(
                        new ClassPropertyMappingOverview(
                                cls, potentialPropertiesByIri.get(cls.getIri())));
            }
        }
        return mappings;
    }

    @Override
    public void confirmPropertyMappings(List<ClassPropertyMappings> classPropertyMappings) {
        var context = migrationSessionStore.getContext();
        var oldClassChanges = context.getDiffAfterClassConfirm();
        var newClassChanges = new ArrayList<SemanticClassChange>();
        for (var cls : oldClassChanges) {
            newClassChanges.add(new SemanticClassChange(cls));
        }
        var newClassChangesByIri =
                newClassChanges.stream()
                        .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));

        var allAttributeMappings = new ArrayList<ResourceMapping<SemanticAttributeChange>>();
        var allAssociationMappings = new ArrayList<ResourceMapping<SemanticAssociationChange>>();
        var allEnumEntryMappings = new ArrayList<ResourceMapping<SemanticEnumEntryChange>>();

        for (var propertyRename : classPropertyMappings) {
            // update rename candidates in the version after the class confirm, so that the
            // suggested renames reflect the input, when going back to that step
            var oldClassChange =
                    oldClassChanges.stream()
                            .filter(c -> c.getLabel().equals(propertyRename.getClassLabel()))
                            .findFirst()
                            .orElseThrow();
            oldClassChange.setAttributeResourceMappings(propertyRename.getAttributeMappings());
            oldClassChange.setAssociationResourceMappings(propertyRename.getAssociationMappings());
            oldClassChange.setEnumEntryResourceMappings(propertyRename.getEnumEntryMappings());

            allAttributeMappings.addAll(propertyRename.getAttributeMappings());
            allAssociationMappings.addAll(propertyRename.getAssociationMappings());
            allEnumEntryMappings.addAll(propertyRename.getEnumEntryMappings());
        }

        PropertyMappingUtilities.applyPropertyMappings(
                newClassChangesByIri, SemanticClassChange::getAttributes, allAttributeMappings);
        PropertyMappingUtilities.applyPropertyMappings(
                newClassChangesByIri, SemanticClassChange::getAssociations, allAssociationMappings);
        PropertyMappingUtilities.applyPropertyMappings(
                newClassChangesByIri, SemanticClassChange::getEnumEntries, allEnumEntryMappings);

        RenameReclassifier.reclassifyChangesDueToRenames(
                newClassChanges, context.getResourceMappings());

        context.setDiffAfterPropertyConfirm(newClassChanges);
    }
}
