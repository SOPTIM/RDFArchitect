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

package org.rdfarchitect.services.schemamigration;

import lombok.experimental.UtilityClass;

import org.apache.jena.vocabulary.RDF;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.changes.triplechanges.TripleClassChange;
import org.rdfarchitect.models.changes.triplechanges.TriplePropertyChange;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class SemanticChangeAnalyser {

    public List<SemanticClassChange> getSemanticChanges(List<TripleClassChange> classChanges) {
        List<SemanticClassChange> semanticChanges = new ArrayList<>();
        for (var classChange : classChanges) {
            semanticChanges.add(getSemanticChangesForClass(classChange));
        }

        return semanticChanges;
    }

    private SemanticClassChange getSemanticChangesForClass(TripleClassChange tripleClassChange) {
        var semanticChangeObject = new SemanticClassChange(tripleClassChange);
        var classChanges = tripleClassChange.getChanges();

        if (classChanges != null) {
            semanticChangeObject.setSemanticResourceChangeType(getResourceChangeType(classChanges));

            // parse properties for individual changes
            for (var propertyChange : classChanges) {
                var change = new SemanticFieldChange(propertyChange);
                var mappedType =
                        SemanticFieldChangeTypeMapper.mapPredicateToChangeType(
                                "Class", propertyChange);
                if (mappedType != null) {
                    change.setSemanticFieldChangeType(mappedType);
                    semanticChangeObject.getChanges().add(change);
                }
            }
        } else {
            semanticChangeObject.setSemanticResourceChangeType(SemanticResourceChangeType.CHANGE);
        }

        if (tripleClassChange.getAttributes() != null) {
            for (var attribute : tripleClassChange.getAttributes()) {
                semanticChangeObject.getAttributes().add(getSemanticChangesForAttribute(attribute));
            }
        }
        if (tripleClassChange.getAssociations() != null) {
            for (var association : tripleClassChange.getAssociations()) {
                semanticChangeObject
                        .getAssociations()
                        .add(getSemanticChangesForAssociation(association));
            }
        }
        if (tripleClassChange.getEnumEntries() != null) {
            for (var enumEntry : tripleClassChange.getEnumEntries()) {
                semanticChangeObject
                        .getEnumEntries()
                        .add(getSemanticChangesForEnumEntry(enumEntry));
            }
        }

        return semanticChangeObject;
    }

    private SemanticAttributeChange getSemanticChangesForAttribute(TripleResourceChange attribute) {
        var semanticChangeObject = new SemanticAttributeChange(attribute);
        var attributeChanges = attribute.getChanges();

        semanticChangeObject.setSemanticResourceChangeType(getResourceChangeType(attributeChanges));

        var changes = semanticChangeObject.getChanges();
        // parse properties for individual changes
        for (var propertyChange : attributeChanges) {
            var action = new SemanticFieldChange(propertyChange);
            var mappedType =
                    SemanticFieldChangeTypeMapper.mapPredicateToChangeType(
                            "Attribute", propertyChange);
            if (mappedType != null) {
                action.setSemanticFieldChangeType(mappedType);
                changes.add(action);
            }
        }

        return semanticChangeObject;
    }

    private SemanticAssociationChange getSemanticChangesForAssociation(
            TripleResourceChange association) {
        var semanticChangeObject = new SemanticAssociationChange(association);
        var associationChanges = association.getChanges();

        semanticChangeObject.setSemanticResourceChangeType(
                getResourceChangeType(associationChanges));

        // parse properties for individual changes
        for (var propertyChange : associationChanges) {
            var action = new SemanticFieldChange(propertyChange);
            var mappedType =
                    SemanticFieldChangeTypeMapper.mapPredicateToChangeType(
                            "Association", propertyChange);
            if (mappedType != null) {
                action.setSemanticFieldChangeType(mappedType);
                semanticChangeObject.getChanges().add(action);
            }
        }

        return semanticChangeObject;
    }

    private SemanticEnumEntryChange getSemanticChangesForEnumEntry(TripleResourceChange enumEntry) {
        var semanticChangeObject = new SemanticEnumEntryChange(enumEntry);
        var enumEntryChanges = enumEntry.getChanges();

        semanticChangeObject.setSemanticResourceChangeType(getResourceChangeType(enumEntryChanges));

        // parse properties for individual changes
        for (var propertyChange : enumEntryChanges) {
            var action = new SemanticFieldChange(propertyChange);
            var mappedType =
                    SemanticFieldChangeTypeMapper.mapPredicateToChangeType(
                            "EnumEntry", propertyChange);
            if (mappedType != null) {
                action.setSemanticFieldChangeType(mappedType);
                semanticChangeObject.getChanges().add(action);
            }
        }

        return semanticChangeObject;
    }

    private SemanticResourceChangeType getResourceChangeType(List<TriplePropertyChange> changes) {
        var typeChange =
                changes.stream()
                        .filter(c -> c.getPredicate().equals(RDF.type.toString()))
                        .findFirst()
                        .orElse(null);
        if (typeChange != null) {
            if (typeChange.getFrom() == null) {
                return SemanticResourceChangeType.ADD;
            } else if (typeChange.getTo() == null) {
                return SemanticResourceChangeType.DELETE;
            }
        } else {
            return SemanticResourceChangeType.CHANGE;
        }
        return null;
    }
}
