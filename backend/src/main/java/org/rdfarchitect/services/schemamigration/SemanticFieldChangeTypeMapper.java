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

import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.triplechanges.TriplePropertyChange;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

public class SemanticFieldChangeTypeMapper {
    private SemanticFieldChangeTypeMapper() {}

    public static SemanticFieldChangeType mapPredicateToChangeType(String resourceType, TriplePropertyChange propertyChange) {
        var predicate = propertyChange.getPredicate();
        if (predicate.equals(RDFS.label.toString())) {
            return SemanticFieldChangeType.LABEL_CHANGE;
        } else if (predicate.equals(RDFS.comment.toString())) {
            return SemanticFieldChangeType.COMMENT_CHANGE;
        }

        return switch (resourceType) {
            case "Class" -> mapClassPredicateToChangeType(propertyChange);
            case "Attribute" -> mapAttributePredicateToChangeType(propertyChange);
            case "Association" -> mapAssociationPredicateToChangeType(propertyChange);
            case "EnumEntry" -> mapEnumEntryPredicateToChangeType(propertyChange);
            default -> null;
        };
    }

    private static SemanticFieldChangeType mapClassPredicateToChangeType(TriplePropertyChange propertyChange) {
        var predicate = propertyChange.getPredicate();
        if (predicate.equals(RDFS.subClassOf.toString())) {
            return SemanticFieldChangeType.SUPERCLASS_CHANGE;
        } else if (predicate.equals(CIMS.belongsToCategory.toString())) {
            return SemanticFieldChangeType.BELONGS_TO_CATEGORY_CHANGE;
        } else if (predicate.equals(CIMS.stereotype.toString())) {
            if (propertyChange.getFrom() == null) {
                return SemanticFieldChangeType.STEREOTYPE_ADDED;
            } else if (propertyChange.getTo() == null) {
                if (propertyChange.getFrom().equals(CIMStereotypes.concrete.toString())) {
                    return SemanticFieldChangeType.MADE_ABSTRACT;
                } else {
                    return SemanticFieldChangeType.STEREOTYPE_REMOVED;
                }
            }
        }
        return null;
    }

    private static SemanticFieldChangeType mapAttributePredicateToChangeType(TriplePropertyChange propertyChange) {
        var predicate = propertyChange.getPredicate();
        if (predicate.equals(RDFS.domain.toString())) {
            return SemanticFieldChangeType.DOMAIN_CHANGE;
        } else if (predicate.equals(RDFS.range.toString()) || predicate.equals(CIMS.datatype.toString())) {
            return SemanticFieldChangeType.DATATYPE_CHANGE;
        } else if (predicate.equals(CIMS.multiplicity.toString())) {
            if(propertyChange.getTo() != null) {
                var newMultiplicity = propertyChange.getTo().split("#")[1];
                if(newMultiplicity.equals("M:0..1")) {
                    return SemanticFieldChangeType.MADE_OPTIONAL;
                } else if(newMultiplicity.equals("M:1") || newMultiplicity.equals("M:1..1")) {
                    return SemanticFieldChangeType.MADE_REQUIRED;
                }
            }
            return SemanticFieldChangeType.MULTIPLICITY_CHANGE;
        } else if (predicate.equals(CIMS.isDefault.toString())) {
            return SemanticFieldChangeType.DEFAULT_VALUE_CHANGE;
        } else if (predicate.equals(CIMS.isFixed.toString())) {
            return SemanticFieldChangeType.FIXED_VALUE_CHANGE;
        }
        return null;
    }

    private static SemanticFieldChangeType mapAssociationPredicateToChangeType(TriplePropertyChange propertyChange) {
        var predicate = propertyChange.getPredicate();
        if (predicate.equals(RDFS.domain.toString())) {
            return SemanticFieldChangeType.DOMAIN_CHANGE;
        } else if (predicate.equals(RDFS.range.toString())) {
            return SemanticFieldChangeType.TARGET_CHANGE;
        } else if (predicate.equals(CIMS.multiplicity.toString())) {
            return SemanticFieldChangeType.MULTIPLICITY_CHANGE;
        } else if (predicate.equals(CIMS.associationUsed.toString())) {
            return SemanticFieldChangeType.ASSOCIATION_USED_CHANGE;
        }
        return null;
    }

    private static SemanticFieldChangeType mapEnumEntryPredicateToChangeType(TriplePropertyChange propertyChange) {
        var predicate = propertyChange.getPredicate();
        if (predicate.equals(RDF.type.toString())) {
            return SemanticFieldChangeType.DOMAIN_CHANGE;
        } else if (predicate.equals(CIMS.stereotype.toString())) {
            if (propertyChange.getFrom() == null) {
                return SemanticFieldChangeType.STEREOTYPE_ADDED;
            } else if (propertyChange.getTo() == null) {
                return SemanticFieldChangeType.STEREOTYPE_REMOVED;
            }
        }
        return null;
    }
}
