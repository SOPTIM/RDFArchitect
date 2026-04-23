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

import org.apache.commons.text.similarity.CosineSimilarity;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class SimilarityCalculator {

    private static final double GENERIC_WEIGHT = 0.7;
    private static final double STRUCTURAL_WEIGHT = 0.3;

    public <T extends SemanticResourceChange> double calculateSimilarity(T added, T deleted) {
        var baseScore = calculateLabelSimilarity(added, deleted);
        if (added instanceof SemanticEnumEntryChange
                && deleted instanceof SemanticEnumEntryChange) {
            return baseScore;
        } else {
            var structuralScore = calculateStructuralSimilarity(added, deleted);
            return GENERIC_WEIGHT * baseScore + STRUCTURAL_WEIGHT * structuralScore;
        }
    }

    private <T extends SemanticResourceChange> double calculateLabelSimilarity(T added, T deleted) {
        var cosineSimilarity = new CosineSimilarity();
        return cosineSimilarity.cosineSimilarity(
                toBigramVector(added.getLabel()), toBigramVector(deleted.getLabel()));
    }

    public static Map<CharSequence, Integer> toBigramVector(String s) {
        var vector = new HashMap<CharSequence, Integer>();
        for (int i = 0; i < s.length() - 1; i++) {
            String bigram = s.substring(i, i + 2);
            vector.merge(bigram, 1, Integer::sum);
        }
        return vector;
    }

    private <T extends SemanticResourceChange> double calculateStructuralSimilarity(
            T added, T deleted) {
        if (deleted instanceof SemanticClassChange && added instanceof SemanticClassChange) {
            return calculateClassSimilarity(
                    (SemanticClassChange) added, (SemanticClassChange) deleted);
        } else if (deleted instanceof SemanticAttributeChange
                && added instanceof SemanticAttributeChange) {
            return calculateAttributeSimilarity(
                    (SemanticAttributeChange) added, (SemanticAttributeChange) deleted);
        } else if (deleted instanceof SemanticAssociationChange
                && added instanceof SemanticAssociationChange) {
            return calculateAssociationSimilarity(
                    (SemanticAssociationChange) added, (SemanticAssociationChange) deleted);
        }
        return 0;
    }

    private double calculateClassSimilarity(
            SemanticClassChange added, SemanticClassChange deleted) {
        var superClassScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.SUPERCLASS_CHANGE);
        var stereotypeScore = calculateStereotypeSimilarity(added, deleted);
        var propertyScore = calculatePropertySimilarity(added, deleted);

        return (superClassScore + stereotypeScore + propertyScore) / 3.0;
    }

    private double calculateStereotypeSimilarity(
            SemanticClassChange added, SemanticClassChange deleted) {
        var deletedStereotypes =
                deleted.getChanges().stream()
                        .filter(
                                c ->
                                        c.getSemanticFieldChangeType()
                                                == SemanticFieldChangeType.STEREOTYPE_REMOVED)
                        .map(SemanticFieldChange::getFrom)
                        .toList();

        var addedStereotypes =
                added.getChanges().stream()
                        .filter(
                                c ->
                                        c.getSemanticFieldChangeType()
                                                == SemanticFieldChangeType.STEREOTYPE_ADDED)
                        .map(SemanticFieldChange::getTo)
                        .toList();

        if (deletedStereotypes.isEmpty()) {
            return 1.0;
        }

        var matchingStereotypes =
                deletedStereotypes.stream().filter(addedStereotypes::contains).count();

        return (double) matchingStereotypes / deletedStereotypes.size();
    }

    private double calculatePropertySimilarity(
            SemanticClassChange added, SemanticClassChange deleted) {
        var deletedProperties = collectPropertyLabels(deleted);
        var addedProperties = collectPropertyLabels(added);

        if (deletedProperties.isEmpty()) {
            return 1.0;
        }

        var matchingProperties =
                deletedProperties.stream().filter(addedProperties::contains).count();

        return (double) matchingProperties / deletedProperties.size();
    }

    private List<String> collectPropertyLabels(SemanticClassChange classChange) {
        var properties = new ArrayList<String>();
        properties.addAll(
                classChange.getAttributes().stream()
                        .map(SemanticAttributeChange::getLabel)
                        .toList());
        properties.addAll(
                classChange.getAssociations().stream()
                        .map(SemanticAssociationChange::getLabel)
                        .toList());
        properties.addAll(
                classChange.getEnumEntries().stream()
                        .map(SemanticResourceChange::getLabel)
                        .toList());
        return properties;
    }

    private double calculateAttributeSimilarity(
            SemanticAttributeChange added, SemanticAttributeChange deleted) {
        var datatypeScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.DATATYPE_CHANGE);
        var multiplicityScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.MULTIPLICITY_CHANGE);
        var fixedValueScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.FIXED_VALUE_CHANGE);
        var defaultValueScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.DEFAULT_VALUE_CHANGE);

        return (datatypeScore + multiplicityScore + fixedValueScore + defaultValueScore) / 4.0;
    }

    private double calculateAssociationSimilarity(
            SemanticAssociationChange added, SemanticAssociationChange deleted) {
        var targetClassScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.TARGET_CHANGE);
        var multiplicityScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.MULTIPLICITY_CHANGE);
        var associationUsedScore =
                compareFieldValues(added, deleted, SemanticFieldChangeType.ASSOCIATION_USED_CHANGE);

        return (targetClassScore + multiplicityScore + associationUsedScore) / 3.0;
    }

    private <T extends SemanticResourceChange> double compareFieldValues(
            T added, T deleted, SemanticFieldChangeType changeType) {
        var deletedValue =
                getValueForChangeType(deleted, changeType)
                        .map(SemanticFieldChange::getFrom)
                        .orElse(null);
        var addedValue =
                getValueForChangeType(added, changeType)
                        .map(SemanticFieldChange::getTo)
                        .orElse(null);
        return Objects.equals(deletedValue, addedValue) ? 1.0 : 0.0;
    }

    private <T extends SemanticResourceChange> Optional<SemanticFieldChange> getValueForChangeType(
            T change, SemanticFieldChangeType changeType) {
        return change.getChanges().stream()
                .filter(c -> c.getSemanticFieldChangeType() == changeType)
                .findFirst();
    }
}
