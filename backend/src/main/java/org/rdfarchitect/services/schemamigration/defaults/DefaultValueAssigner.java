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

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.migration.DefaultValueView;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.relations.model.properties.CIMAttributeUtils;

import java.util.List;

@UtilityClass
public class DefaultValueAssigner {

    public void assignDefaultValues(List<SemanticClassChange> classes, Model model, List<DefaultValueView> result) {
        for (var cls : classes) {
            if (!hasPropertyChanges(cls) || cls.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETE) {
                continue;
            }

            DefaultValueAssigner.assignDefaultValueToAttributes(cls.getAttributes(), model);
            DefaultValueAssigner.assignDefaultsToAssociations(cls.getAssociations(), model);
            DefaultValueAssigner.assignDefaultsToEnumEntries(cls, cls.getEnumEntries(), model);
            result.add(new DefaultValueView(cls.getLabel(), cls.getAttributes(), cls.getAssociations(), cls.getEnumEntries()));
        }
    }

    private boolean hasPropertyChanges(SemanticClassChange cls) {
        return !cls.getAttributes().isEmpty() || !cls.getAssociations().isEmpty() || !cls.getEnumEntries().isEmpty();
    }

    public void assignDefaultValueToAttributes(List<SemanticAttributeChange> attributes, Model model) {
        for (var attributeChange : attributes) {
            if (attributeChange.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETE ||
                      attributeChange.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETED_FROM_INHERITANCE) {
                continue;
            }

            var attributeResource = model.getResource(attributeChange.getIri());
            assignDefaultValueToAttribute(attributeChange, attributeResource);
        }
    }

    public void assignDefaultsToAssociations(List<SemanticAssociationChange> associations, Model model) {
        for (var associationChange : associations) {
            if (associationChange.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETE ||
                      associationChange.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETED_FROM_INHERITANCE) {
                continue;
            }

            var associationResource = model.getResource(associationChange.getIri());
            assignDefaultValueToAssociation(associationChange, associationResource);
        }
    }

    public void assignDefaultsToEnumEntries(SemanticClassChange enumClass, List<SemanticEnumEntryChange> enumEntries, Model model) {
        var enumResource = model.getResource(enumClass.getIri());
        var allowedValues = model.listSubjectsWithProperty(RDF.type, enumResource).toList();
        for (var enumEntryChange : enumEntries) {
            if (enumEntryChange.getSemanticResourceChangeType() == SemanticResourceChangeType.DELETE) {
                enumEntryChange.setAllowedValues(allowedValues.stream().map(Resource::getURI).toList());
            }
        }
    }

    private void assignDefaultValueToAttribute(SemanticAttributeChange attributeChange, Resource attributeResource) {
        if (attributeResource.getProperty(CIMS.isDefault) != null) {
            attributeChange.setDefaultValue(attributeResource.getProperty(CIMS.isDefault).getString());
        }
        if (attributeResource.getProperty(CIMS.isFixed) != null) {
            attributeChange.setDefaultValue(attributeResource.getProperty(CIMS.isFixed).getString());
        }

        attributeChange.setOptional(CIMAttributeUtils.isOptional(attributeResource));

        if (CIMAttributeUtils.hasPrimitiveDatatype(attributeResource) || CIMAttributeUtils.hasCIMDatatype(attributeResource) || CIMAttributeUtils.hasXSDDatatype(attributeResource)) {
            attributeChange.setPrimitiveDataType(CIMAttributeUtils.getPrimitiveDatatype(attributeResource).getURI());
            attributeChange.setDataType(attributeResource.getProperty(CIMS.datatype).getResource().getURI());
        } else if (CIMAttributeUtils.hasEnumAttribute(attributeResource)) {
            assignDefaultValueToEnumAttribute(attributeChange, attributeResource);
        } else {
            var datatype = attributeResource.getProperty(CIMS.datatype).getResource().getURI();
            attributeChange.setDataType(datatype);
            attributeChange.setPrimitiveDataType(datatype);
        }
    }

    private void assignDefaultValueToEnumAttribute(SemanticAttributeChange attributeChange, Resource attributeResource) {
        attributeChange.setDataType(attributeResource.getProperty(RDFS.range).getResource().getURI());
        var enumEntries = CIMAttributeUtils.listEnumDatatypeEntries(attributeResource).stream()
                                           .map(Resource::getURI)
                                           .toList();
        attributeChange.setAllowedValues(enumEntries);
    }

    private void assignDefaultValueToAssociation(SemanticAssociationChange associationChange, Resource associationResource) {
        associationChange.setRange(associationResource.getProperty(RDFS.range).getResource().getURI());
        var associationUsed = associationResource.getProperty(CIMS.associationUsed).getString().equals("Yes");
        associationChange.setAssociationUsed(associationUsed);
    }
}

