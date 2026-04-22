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

import lombok.AllArgsConstructor;
import lombok.Data;

import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class ResourceRenameOverview<T extends SemanticResourceChange> {

    private List<T> added;

    private List<T> modified;

    private List<RenameCandidate<T>> deletedAndRenamed;

    public ResourceRenameOverview(List<T> changes, List<RenameCandidate<T>> renameCandidates) {
        this.added =
                changes.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADD)
                        .toList();
        this.modified =
                changes.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.CHANGE)
                        .toList();
        var deleted =
                changes.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.DELETE)
                        .toList();
        var mappedDeleted =
                renameCandidates.stream()
                        .map(r -> r.getOldResource().getLabel())
                        .collect(Collectors.toSet());

        // Add all unmapped deletions as potential renamingCandidates with confidenceScore 0
        var deletedAndRenamedCandidates = new ArrayList<>(renameCandidates);
        for (var change : deleted) {
            var label = change.getLabel();
            if (!mappedDeleted.contains(label)) {
                deletedAndRenamedCandidates.add(new RenameCandidate<>(change));
            }
        }
        this.deletedAndRenamed = deletedAndRenamedCandidates;
    }
}
