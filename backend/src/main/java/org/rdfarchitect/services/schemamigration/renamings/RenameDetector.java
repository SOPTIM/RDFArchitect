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

import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class RenameDetector {

    private static final double SIMILARITY_THRESHOLD = 0.8;

    public List<RenameCandidate<SemanticClassChange>> detectClassRenames(
            List<SemanticClassChange> semanticChanges) {
        var deletedClasses =
                semanticChanges.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                .equals(SemanticResourceChangeType.DELETE))
                        .toList();
        var addedClasses =
                semanticChanges.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                .equals(SemanticResourceChangeType.ADD))
                        .toList();

        return matchBySimilarity(addedClasses, deletedClasses);
    }

    public <T extends SemanticResourceChange> List<RenameCandidate<T>> detectPropertyRenames(
            List<T> resources) {
        var deletedResources =
                resources.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                .equals(SemanticResourceChangeType.DELETE))
                        .toList();
        var addedResources =
                resources.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                .equals(SemanticResourceChangeType.ADD))
                        .toList();

        return matchBySimilarity(addedResources, deletedResources);
    }

    private <T extends SemanticResourceChange> List<RenameCandidate<T>> matchBySimilarity(
            List<T> added, List<T> deleted) {
        List<RenameCandidate<T>> renames = new ArrayList<>();
        Set<T> unmatchedAdded = new HashSet<>(added);

        for (T deletedItem : deleted) {
            double bestScore = 0.0;
            T bestMatch = null;

            for (T newItem : unmatchedAdded) {
                double score = SimilarityCalculator.calculateSimilarity(newItem, deletedItem);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = newItem;
                }
            }

            if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD) {
                unmatchedAdded.remove(bestMatch);
                renames.add(new RenameCandidate<>(deletedItem, bestMatch, bestScore));
            }
        }
        return renames;
    }
}
