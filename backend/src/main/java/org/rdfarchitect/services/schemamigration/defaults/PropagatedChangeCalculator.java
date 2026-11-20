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

import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.context.SchemaMigrationContext;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Derives the class changes the default-value step works on: the confirmed property mappings plus
 * the changes a class inherits because a superclass changed or a property moved to another domain.
 */
@UtilityClass
public class PropagatedChangeCalculator {

    /**
     * Copies the changes confirmed in the property step and enriches the copies with the
     * inheritance and cross-domain consequences of those confirmations.
     *
     * @param context the migration session context to read the confirmed state from
     * @return a fresh list of class changes, leaving the context untouched
     */
    public List<SemanticClassChange> computePropagatedClassChanges(SchemaMigrationContext context) {
        var changes = context.getDiffAfterPropertyConfirm();

        var newChangeList = new ArrayList<SemanticClassChange>();
        for (var cls : changes) {
            newChangeList.add(new SemanticClassChange(cls));
        }

        var oldModel = ModelFactory.createModelForGraph(context.getOriginalSchema());
        var updatedModel = ModelFactory.createModelForGraph(context.getUpdatedSchema());
        var classRenames = context.getResourceMappings();

        var movedPropertyIris = collectMovedPropertyIris(newChangeList);

        InheritanceChangeHandler.processInheritanceChanges(
                newChangeList, updatedModel, oldModel, classRenames, movedPropertyIris);

        var crossDomainAttributeMappings =
                collectCrossDomainMappings(
                        context.getDiffAfterClassConfirm(),
                        SemanticClassChange::getAttributeResourceMappings);
        var crossDomainAssociationMappings =
                collectCrossDomainMappings(
                        context.getDiffAfterClassConfirm(),
                        SemanticClassChange::getAssociationResourceMappings);
        CrossDomainPropertyPropagationHandler.processPropertyMoveInheritanceChanges(
                newChangeList,
                oldModel,
                updatedModel,
                crossDomainAttributeMappings,
                crossDomainAssociationMappings);

        return newChangeList;
    }

    private Set<String> collectMovedPropertyIris(List<SemanticClassChange> classChanges) {
        var movedPropertyIris = new HashSet<String>();
        for (var classChange : classChanges) {
            addMovedPropertyIris(movedPropertyIris, classChange.getAttributes());
            addMovedPropertyIris(movedPropertyIris, classChange.getAssociations());
        }
        return movedPropertyIris;
    }

    private <T extends SemanticResourceChange> void addMovedPropertyIris(
            Set<String> movedPropertyIris, List<T> properties) {
        for (var property : properties) {
            var changeType = property.getSemanticResourceChangeType();
            if (changeType != SemanticResourceChangeType.MERGE
                    && changeType != SemanticResourceChangeType.MOVE
                    && changeType != SemanticResourceChangeType.SPLIT) {
                continue;
            }
            movedPropertyIris.add(property.getIri());
            if (property.getOldIRI() != null) {
                movedPropertyIris.add(property.getOldIRI());
            }
            movedPropertyIris.addAll(property.getAdditionalOldIRIs());
        }
    }

    private <T extends SemanticResourceChange> List<ResourceMapping<T>> collectCrossDomainMappings(
            List<SemanticClassChange> classChanges,
            Function<SemanticClassChange, List<ResourceMapping<T>>> mappingAccessor) {
        var seen = new HashSet<UUID>();
        var result = new ArrayList<ResourceMapping<T>>();
        for (var classChange : classChanges) {
            for (var mapping : mappingAccessor.apply(classChange)) {
                if (seen.add(mapping.getId())) {
                    result.add(mapping);
                }
            }
        }
        return result;
    }
}
