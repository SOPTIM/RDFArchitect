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
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UtilityClass
public class RenameObjectBuilder {

    public SemanticResourceChange createRenameObject(RenameCandidate renameCandidate) {
        var addedResource = renameCandidate.getNewResource();
        var deletedResource = renameCandidate.getOldResource();
        var result = addedResource.copy();
        result.setOldIRI(deletedResource.getIri());
        result.setSemanticResourceChangeType(SemanticResourceChangeType.RENAME);
        result.setChanges(mergeChanges(addedResource.getChanges(), deletedResource.getChanges()));

        if (result instanceof SemanticClassChange) {
            return createRenameClassChange(
                    (SemanticClassChange) addedResource,
                    (SemanticClassChange) deletedResource,
                    result);
        }

        return result;
    }

    private SemanticClassChange createRenameClassChange(
            SemanticClassChange added,
            SemanticClassChange deleted,
            SemanticResourceChange baseResult) {
        var classChange = (SemanticClassChange) baseResult;
        classChange.setAttributes(
                mergePropertyList(added.getAttributes(), deleted.getAttributes(), classChange));
        classChange.setAssociations(
                mergePropertyList(added.getAssociations(), deleted.getAssociations(), classChange));
        classChange.setEnumEntries(
                mergePropertyList(added.getEnumEntries(), deleted.getEnumEntries(), classChange));
        return classChange;
    }

    public <T extends SemanticResourceChange> List<T> mergePropertyList(
            List<T> added, List<T> deleted, SemanticClassChange domain) {
        var result = new ArrayList<T>();
        var remainingAdded = new ArrayList<>(added);

        for (T deletedItem : deleted) {
            if (remainingAdded.contains(deletedItem)) {
                remainingAdded.remove(deletedItem);
                continue;
            }

            var matchingAdded = findMatchingProperty(remainingAdded, deletedItem);

            if (matchingAdded != null) {
                @SuppressWarnings("unchecked")
                var mergedMember = (T) createMergedProperty(matchingAdded, deletedItem, domain);
                remainingAdded.remove(matchingAdded);
                result.add(mergedMember);
            } else {
                result.add(deletedItem);
            }
        }

        result.addAll(remainingAdded);
        return result;
    }

    public List<SemanticFieldChange> mergeChanges(
            List<SemanticFieldChange> added, List<SemanticFieldChange> deleted) {
        var result = new ArrayList<SemanticFieldChange>();
        var remainingAdded = new ArrayList<>(added);

        for (var deletedChange : deleted) {
            var addedChange = findMatchingChange(remainingAdded, deletedChange);

            if (addedChange != null) {
                processMergedChange(result, addedChange, deletedChange, remainingAdded);
            } else {
                result.add(deletedChange);
            }
        }

        result.addAll(remainingAdded);
        return result;
    }

    private <T extends SemanticResourceChange> T findMatchingProperty(
            List<T> properties, T target) {
        return properties.stream()
                .filter(member -> member.getLabel().equals(target.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private <T extends SemanticResourceChange> SemanticResourceChange createMergedProperty(
            T added, T deleted, SemanticClassChange domain) {
        var mergedMember = added.copy();
        mergedMember.setSemanticResourceChangeType(SemanticResourceChangeType.CHANGE);
        mergedMember.setChanges(mergeChanges(added.getChanges(), deleted.getChanges()));
        mergedMember.setOldIRI(deleted.getIri());

        updateDomainChangeType(mergedMember, domain);

        return mergedMember;
    }

    private void updateDomainChangeType(
            SemanticResourceChange mergedMember, SemanticClassChange domain) {
        var domainChange =
                mergedMember.getChanges().stream()
                        .filter(
                                change ->
                                        change.getSemanticFieldChangeType()
                                                .equals(SemanticFieldChangeType.DOMAIN_CHANGE))
                        .findFirst()
                        .orElse(null);

        if (domainChange != null && domainChange.getFrom().equals(domain.getOldIRI())) {
            domainChange.setSemanticFieldChangeType(SemanticFieldChangeType.DOMAIN_RENAME);
        }
    }

    private SemanticFieldChange findMatchingChange(
            List<SemanticFieldChange> changes, SemanticFieldChange target) {
        if (target.getSemanticFieldChangeType() == SemanticFieldChangeType.MULTIPLICITY_CHANGE) {
            return changes.stream()
                    .filter(
                            change ->
                                    change.getSemanticFieldChangeType()
                                                    == SemanticFieldChangeType.MADE_OPTIONAL
                                            || change.getSemanticFieldChangeType()
                                                    == SemanticFieldChangeType.MADE_REQUIRED
                                            || change.getSemanticFieldChangeType()
                                                    == SemanticFieldChangeType.MULTIPLICITY_CHANGE)
                    .findFirst()
                    .orElse(null);
        }

        return changes.stream()
                .filter(
                        change ->
                                target.getSemanticFieldChangeType()
                                        .equals(change.getSemanticFieldChangeType()))
                .findFirst()
                .orElse(null);
    }

    private void processMergedChange(
            List<SemanticFieldChange> result,
            SemanticFieldChange addedChange,
            SemanticFieldChange deletedChange,
            List<SemanticFieldChange> remainingAdded) {
        var mergedChange = new SemanticFieldChange(addedChange);
        remainingAdded.remove(mergedChange);

        // Filter out changes with same value after merge
        if (Objects.equals(mergedChange.getTo(), deletedChange.getFrom())) {
            return;
        }

        if (mergedChange.getSemanticFieldChangeType() == SemanticFieldChangeType.MADE_REQUIRED
                || mergedChange.getSemanticFieldChangeType()
                        == SemanticFieldChangeType.MADE_OPTIONAL
                || mergedChange.getSemanticFieldChangeType()
                        == SemanticFieldChangeType.MULTIPLICITY_CHANGE) {
            var oldMultiplicity =
                    CIMPropertyUtils.resolveMultiplicity(deletedChange.getFrom().split("#")[1]);
            var newMultiplicity =
                    CIMPropertyUtils.resolveMultiplicity(addedChange.getTo().split("#")[1]);
            if (oldMultiplicity.equals(newMultiplicity)) {
                return;
            }
        }

        mergedChange.setFrom(deletedChange.getFrom());
        result.add(mergedChange);
    }
}
