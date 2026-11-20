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

import org.rdfarchitect.models.changes.MappingType;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@UtilityClass
public class RenameDetector {

    private static final double SIMILARITY_THRESHOLD = 0.8;

    public List<ResourceMapping<SemanticClassChange>> detectClassRenames(
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

        var mapping = matchBySimilarity(addedClasses, deletedClasses);
        mapping.forEach(m -> m.setType(MappingType.RENAME));
        return mapping;
    }

    public <T extends SemanticPropertyChange> List<ResourceMapping<T>> detectPropertyMappings(
            List<T> changesInClass, List<T> changesInChain) {
        var deletedResources =
                new ArrayList<>(
                        changesInClass.stream()
                                .filter(
                                        change ->
                                                change.getSemanticResourceChangeType()
                                                        .equals(SemanticResourceChangeType.DELETE))
                                .toList());
        var addedResources =
                new ArrayList<>(
                        changesInChain.stream()
                                .filter(
                                        change ->
                                                change.getSemanticResourceChangeType()
                                                        .equals(SemanticResourceChangeType.ADD))
                                .toList());
        var deletedInChain =
                changesInChain.stream()
                        .filter(
                                change ->
                                        change.getSemanticResourceChangeType()
                                                .equals(SemanticResourceChangeType.DELETE))
                        .toList();

        var mappings = detectSplits(deletedResources, addedResources, deletedInChain);
        var fuzzyMappings = matchBySimilarity(addedResources, deletedResources);
        for (var mapping : fuzzyMappings) {
            if (!Objects.equals(
                    mapping.getOldResources().getFirst().getDomain(),
                    mapping.getNewResources().getFirst().getDomain())) {
                mapping.setType(MappingType.MOVE);
            } else {
                mapping.setType(MappingType.RENAME);
            }
        }
        mappings.addAll(fuzzyMappings);
        return mappings;
    }

    private <T extends SemanticPropertyChange> List<ResourceMapping<T>> detectSplits(
            List<T> deletedResources, List<T> addedResources, List<T> deletedInChain) {
        var splits = new ArrayList<ResourceMapping<T>>();
        for (var deletedItem : List.copyOf(deletedResources)) {
            var exactMatches =
                    addedResources.stream()
                            .filter(added -> added.getLabel().equals(deletedItem.getLabel()))
                            .toList();
            if (exactMatches.size() >= 2
                    && !isIndependentPerClass(deletedItem, exactMatches, deletedInChain)) {
                splits.add(new ResourceMapping<>(deletedItem, exactMatches, 1.0));
                addedResources.removeAll(exactMatches);
                deletedResources.remove(deletedItem);
            }
        }
        return splits;
    }

    private <T extends SemanticPropertyChange> boolean isIndependentPerClass(
            T deletedItem, List<T> exactMatches, List<T> deletedInChain) {
        var sameNameDeletions =
                deletedInChain.stream()
                        .filter(deleted -> deleted.getLabel().equals(deletedItem.getLabel()))
                        .count();
        // the chain always holds the class's own properties, but callers may pass only the
        // candidates - deletedItem itself is a deletion of that name either way
        return Math.max(sameNameDeletions, 1) >= exactMatches.size();
    }

    private <T extends SemanticResourceChange> List<ResourceMapping<T>> matchBySimilarity(
            List<T> added, List<T> deleted) {
        List<ResourceMapping<T>> renames = new ArrayList<>();
        Set<T> unmatchedAdded = new HashSet<>(added);

        for (T deletedItem : deleted) {
            var bestScore = 0.;
            T bestMatch = null;

            for (T newItem : unmatchedAdded) {
                double score;
                if (newItem.getLabel().equals(deletedItem.getLabel())) {
                    score = 1.;
                } else {
                    score = SimilarityCalculator.calculateSimilarity(newItem, deletedItem);
                }

                if (score > bestScore
                        || (score == bestScore
                                && score > 0
                                && staysInSameClass(newItem, bestMatch, deletedItem))) {
                    bestScore = score;
                    bestMatch = newItem;
                }
            }

            if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD) {
                unmatchedAdded.remove(bestMatch);
                renames.add(new ResourceMapping<>(deletedItem, bestMatch, bestScore));
            }
        }
        return renames;
    }

    /**
     * Breaks a tie between equally named candidates: one that stayed on the class the property was
     * deleted from beats one of the same name elsewhere in the chain.
     */
    private <T extends SemanticResourceChange> boolean staysInSameClass(
            T candidate, T currentBest, T deletedItem) {
        return isOnSameClass(candidate, deletedItem) && !isOnSameClass(currentBest, deletedItem);
    }

    private boolean isOnSameClass(
            SemanticResourceChange candidate, SemanticResourceChange deleted) {
        return candidate instanceof SemanticPropertyChange property
                && deleted instanceof SemanticPropertyChange deletedProperty
                && property.getDomain() != null
                && property.getDomain().equals(deletedProperty.getDomain());
    }
}
