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
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ClassMappingOverview {

    private List<SemanticClassChange> added;

    private List<ResourceMapping<SemanticClassChange>> deletedAndRemapped;

    public ClassMappingOverview(
            List<SemanticClassChange> changes,
            List<ResourceMapping<SemanticClassChange>> resourceMappings) {
        this.added =
                changes.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADD)
                        .toList();
        var deleted =
                changes.stream()
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
                                                .map(SemanticClassChange::getLabel))
                        .collect(Collectors.toSet());

        // Add all unmapped deletions as potential renamingCandidates with confidenceScore 0
        var deletedAndRenamedCandidates = new ArrayList<>(resourceMappings);
        for (var change : deleted) {
            var label = change.getLabel();
            if (!mappedDeleted.contains(label)) {
                deletedAndRenamedCandidates.add(new ResourceMapping<>(change));
            }
        }
        this.deletedAndRemapped = deletedAndRenamedCandidates;
    }
}
