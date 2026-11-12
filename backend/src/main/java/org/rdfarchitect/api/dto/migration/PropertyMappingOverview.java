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

package org.rdfarchitect.api.dto.migration;

import lombok.Data;

import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class PropertyMappingOverview<T extends SemanticResourceChange> {

    private List<T> addedInClass;

    private List<T> allAdded;

    private List<ResourceMapping<T>> deletedAndRemapped;

    public PropertyMappingOverview(
            List<T> changesInClass, List<T> allChanges, List<ResourceMapping<T>> resourceMappings) {
        this.allAdded =
                allChanges.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADD)
                        .toList();
        this.addedInClass =
                changesInClass.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADD)
                        .toList();
        var deleted =
                changesInClass.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.DELETE)
                        .toList();
        var mappedDeleted =
                resourceMappings.stream()
                        .flatMap(
                                r ->
                                        r.getOldResources().stream()
                                                .map(SemanticResourceChange::getIri))
                        .collect(Collectors.toSet());

        var deletedIris =
                deleted.stream().map(SemanticResourceChange::getIri).collect(Collectors.toSet());

        // To avoid duplication in the frontend, only mappings that include a deleted resource are
        // considered "owned" by the deleted resource.
        // Deletions with no detected mapping are also included, to allow custom mapping in the
        // frontend.
        var ownedMappings =
                resourceMappings.stream()
                        .filter(
                                mapping ->
                                        mapping.getOldResources().stream()
                                                .anyMatch(o -> deletedIris.contains(o.getIri())))
                        .toList();
        var deletedAndRenamedCandidates = new ArrayList<>(ownedMappings);
        for (var change : deleted) {
            if (!mappedDeleted.contains(change.getIri())) {
                deletedAndRenamedCandidates.add(new ResourceMapping<>(change));
            }
        }
        this.deletedAndRemapped = deletedAndRenamedCandidates;
    }
}
