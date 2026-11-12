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

import lombok.RequiredArgsConstructor;

import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.migration.DefaultValueView;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drives the default-value step of the migration wizard: asks for a value wherever instance data
 * cannot be migrated without one, and stores the answers as the final change list.
 */
@Service
@RequiredArgsConstructor
public class DefaultValuesService
        implements GetDefaultValueViewsUseCase, SubmitDefaultValuesUseCase {

    private final MigrationSessionStore migrationSessionStore;

    @Override
    public List<DefaultValueView> getDefaultValueViews() {
        var context = migrationSessionStore.getContext();
        var newChangeList = PropagatedChangeCalculator.computePropagatedClassChanges(context);
        var result = new ArrayList<DefaultValueView>();

        var updatedModel = ModelFactory.createModelForGraph(context.getUpdatedSchema());
        DefaultValueAssigner.assignDefaultValues(newChangeList, updatedModel, result);

        return result;
    }

    @Override
    public void submitDefaultValues(List<DefaultValueView> defaultValueViews) {
        var context = migrationSessionStore.getContext();
        var classes = PropagatedChangeCalculator.computePropagatedClassChanges(context);
        var classChangeMap =
                classes.stream().collect(Collectors.toMap(SemanticClassChange::getLabel, c -> c));

        for (var defaultValueView : defaultValueViews) {
            var classChange = classChangeMap.get(defaultValueView.getClassLabel());
            if (classChange != null) {
                classChange.setAttributes(defaultValueView.getAttributes());
                classChange.setAssociations(defaultValueView.getAssociations());
                classChange.setEnumEntries(defaultValueView.getEnumEntries());
            }
        }

        var newChangeList = new ArrayList<>(classChangeMap.values());
        context.setDiffAfterDefaultValueConfirm(newChangeList);
    }
}
