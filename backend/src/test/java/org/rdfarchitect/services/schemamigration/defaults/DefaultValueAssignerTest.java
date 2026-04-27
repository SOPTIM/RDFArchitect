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

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.List;

class DefaultValueAssignerTest {

    private static final String PREFIX = "http://example.org#";
    private static final String CIMS_PREFIX =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    private Model model;

    @BeforeEach
    void setUp() {
        model = ModelFactory.createDefaultModel();
    }

    @Nested
    class PopulateDefaultsForAttributesTest {

        @Test
        void populateDefaultsForAttributes_attributeWithDefaultValue_setsDefaultValue() {
            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.isDefault, "DefaultValue");
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.getDefaultValue()).isEqualTo("DefaultValue");
        }

        @Test
        void populateDefaultsForAttributes_attributeWithFixedValue_setsDefaultValue() {
            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.isFixed, "FixedValue");
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.getDefaultValue()).isEqualTo("FixedValue");
        }

        @Test
        void populateDefaultsForAttributes_optionalAttribute_setsOptionalTrue() {
            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.isOptional()).isTrue();
        }

        @Test
        void populateDefaultsForAttributes_requiredAttribute_setsOptionalFalse() {
            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.isOptional()).isFalse();
        }

        @Test
        void populateDefaultsForAttributes_primitiveDatatype_setsPrimitiveAndDatatype() {
            var attr = model.createResource(PREFIX + "Attribute1");
            var xsdString = model.createResource("http://www.w3.org/2001/XMLSchema#string");
            xsdString.addProperty(CIMS.stereotype, model.createResource(CIMS_PREFIX + "Primitive"));
            attr.addProperty(CIMS.datatype, xsdString);
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.getPrimitiveDataType())
                    .isEqualTo("http://www.w3.org/2001/XMLSchema#string");
            assertThat(attributeChange.getDataType())
                    .isEqualTo("http://www.w3.org/2001/XMLSchema#string");
        }

        @Test
        void populateDefaultsForAttributes_enumAttribute_setsAllowedValues() {
            var enumClass = model.createResource(PREFIX + "StatusEnum");
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

            var enumEntry1 = model.createResource(PREFIX + "StatusEnum.ACTIVE");
            enumEntry1.addProperty(RDF.type, enumClass);

            var enumEntry2 = model.createResource(PREFIX + "StatusEnum.INACTIVE");
            enumEntry2.addProperty(RDF.type, enumClass);

            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(RDF.type, RDF.Property);
            attr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
            attr.addProperty(RDFS.range, enumClass);
            attr.addProperty(CIMS.datatype, enumClass);
            attr.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.getDataType()).isEqualTo(PREFIX + "StatusEnum");
            assertThat(attributeChange.getAllowedValues()).hasSize(2);
            assertThat(attributeChange.getAllowedValues())
                    .contains(PREFIX + "StatusEnum.ACTIVE", PREFIX + "StatusEnum.INACTIVE");
        }

        @Test
        void populateDefaultsForAttributes_deletedAttribute_skipsProcessing() {
            var attr = model.createResource(PREFIX + "Attribute1");
            attr.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.isDefault, "DefaultValue");

            var attributeChange =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(attributeChange), model);

            assertThat(attributeChange.getDefaultValue()).isNull();
        }

        @Test
        void populateDefaultsForAttributes_multipleAttributes_processesAll() {
            var attr1 = model.createResource(PREFIX + "Attribute1");
            attr1.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr1.addProperty(CIMS.isDefault, "Default1");
            attr1.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..1"));

            var attr2 = model.createResource(PREFIX + "Attribute2");
            attr2.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#int"));
            attr2.addProperty(CIMS.isDefault, "0");
            attr2.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));

            var change1 =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute1")
                            .label("Attribute1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            var change2 =
                    SemanticAttributeChange.builder()
                            .iri(PREFIX + "Attribute2")
                            .label("Attribute2")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultValueToAttributes(List.of(change1, change2), model);

            assertThat(change1.getDefaultValue()).isEqualTo("Default1");
            assertThat(change2.getDefaultValue()).isEqualTo("0");
        }
    }

    @Nested
    class PopulateDefaultsForAssociationsTest {

        @Test
        void populateDefaultsForAssociations_associationWithRange_setsRange() {
            var targetClass = model.createResource(PREFIX + "TargetClass");
            var assoc = model.createResource(PREFIX + "Association1");
            assoc.addProperty(RDFS.range, targetClass);
            assoc.addProperty(CIMS.associationUsed, "No");

            var associationChange =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association1")
                            .label("Association1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultsToAssociations(List.of(associationChange), model);

            assertThat(associationChange.getRange()).isEqualTo(PREFIX + "TargetClass");
        }

        @Test
        void populateDefaultsForAssociations_associationUsedYes_setsAssociationUsedTrue() {
            var targetClass = model.createResource(PREFIX + "TargetClass");
            var assoc = model.createResource(PREFIX + "Association1");
            assoc.addProperty(RDFS.range, targetClass);
            assoc.addProperty(CIMS.associationUsed, "Yes");

            var associationChange =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association1")
                            .label("Association1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultsToAssociations(List.of(associationChange), model);

            assertThat(associationChange.isAssociationUsed()).isTrue();
        }

        @Test
        void populateDefaultsForAssociations_associationUsedNo_setsAssociationUsedFalse() {
            var targetClass = model.createResource(PREFIX + "TargetClass");
            var assoc = model.createResource(PREFIX + "Association1");
            assoc.addProperty(RDFS.range, targetClass);
            assoc.addProperty(CIMS.associationUsed, "No");

            var associationChange =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association1")
                            .label("Association1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultsToAssociations(List.of(associationChange), model);

            assertThat(associationChange.isAssociationUsed()).isFalse();
        }

        @Test
        void populateDefaultsForAssociations_deletedAssociation_skipsProcessing() {
            var targetClass = model.createResource(PREFIX + "TargetClass");
            var assoc = model.createResource(PREFIX + "Association1");
            assoc.addProperty(RDFS.range, targetClass);
            assoc.addProperty(CIMS.associationUsed, "Yes");

            var associationChange =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association1")
                            .label("Association1")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            DefaultValueAssigner.assignDefaultsToAssociations(List.of(associationChange), model);

            assertThat(associationChange.getRange()).isNull();
            assertThat(associationChange.isAssociationUsed()).isFalse();
        }

        @Test
        void populateDefaultsForAssociations_multipleAssociations_processesAll() {
            var targetClass1 = model.createResource(PREFIX + "TargetClass1");
            var assoc1 = model.createResource(PREFIX + "Association1");
            assoc1.addProperty(RDFS.range, targetClass1);
            assoc1.addProperty(CIMS.associationUsed, "Yes");

            var targetClass2 = model.createResource(PREFIX + "TargetClass2");
            var assoc2 = model.createResource(PREFIX + "Association2");
            assoc2.addProperty(RDFS.range, targetClass2);
            assoc2.addProperty(CIMS.associationUsed, "No");

            var change1 =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association1")
                            .label("Association1")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            var change2 =
                    SemanticAssociationChange.builder()
                            .iri(PREFIX + "Association2")
                            .label("Association2")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultsToAssociations(List.of(change1, change2), model);

            assertThat(change1.getRange()).isEqualTo(PREFIX + "TargetClass1");
            assertThat(change1.isAssociationUsed()).isTrue();
            assertThat(change2.getRange()).isEqualTo(PREFIX + "TargetClass2");
            assertThat(change2.isAssociationUsed()).isFalse();
        }
    }

    @Nested
    class PopulateDefaultsForEnumEntriesTest {

        @Test
        void populateDefaultsForEnumEntries_deletedEnumEntry_setsAllowedValues() {
            var enumClass = model.createResource(PREFIX + "StatusEnum");
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

            var enumEntry1 = model.createResource(PREFIX + "StatusEnum.ACTIVE");
            enumEntry1.addProperty(RDF.type, enumClass);

            var enumEntry2 = model.createResource(PREFIX + "StatusEnum.INACTIVE");
            enumEntry2.addProperty(RDF.type, enumClass);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "StatusEnum")
                            .label("StatusEnum")
                            .build();

            var enumEntryChange =
                    SemanticEnumEntryChange.builder()
                            .iri(PREFIX + "StatusEnum.ACTIVE")
                            .label("ACTIVE")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            DefaultValueAssigner.assignDefaultsToEnumEntries(
                    classChange, List.of(enumEntryChange), model);

            assertThat(enumEntryChange.getAllowedValues()).hasSize(2);
            assertThat(enumEntryChange.getAllowedValues())
                    .contains(PREFIX + "StatusEnum.ACTIVE", PREFIX + "StatusEnum.INACTIVE");
        }

        @Test
        void populateDefaultsForEnumEntries_addedEnumEntry_doesNotSetAllowedValues() {
            var enumClass = model.createResource(PREFIX + "StatusEnum");
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

            var enumEntry1 = model.createResource(PREFIX + "StatusEnum.ACTIVE");
            enumEntry1.addProperty(RDF.type, enumClass);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "StatusEnum")
                            .label("StatusEnum")
                            .build();

            var enumEntryChange =
                    SemanticEnumEntryChange.builder()
                            .iri(PREFIX + "StatusEnum.INACTIVE")
                            .label("INACTIVE")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            DefaultValueAssigner.assignDefaultsToEnumEntries(
                    classChange, List.of(enumEntryChange), model);

            assertThat(enumEntryChange.getAllowedValues()).isEmpty();
        }

        @Test
        void populateDefaultsForEnumEntries_multipleDeletedEntries_setsAllowedValuesForEach() {
            var enumClass = model.createResource(PREFIX + "StatusEnum");
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

            var enumEntry1 = model.createResource(PREFIX + "StatusEnum.ACTIVE");
            enumEntry1.addProperty(RDF.type, enumClass);

            var enumEntry2 = model.createResource(PREFIX + "StatusEnum.INACTIVE");
            enumEntry2.addProperty(RDF.type, enumClass);

            var enumEntry3 = model.createResource(PREFIX + "StatusEnum.PENDING");
            enumEntry3.addProperty(RDF.type, enumClass);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "StatusEnum")
                            .label("StatusEnum")
                            .build();

            var change1 =
                    SemanticEnumEntryChange.builder()
                            .iri(PREFIX + "StatusEnum.ACTIVE")
                            .label("ACTIVE")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            var change2 =
                    SemanticEnumEntryChange.builder()
                            .iri(PREFIX + "StatusEnum.INACTIVE")
                            .label("INACTIVE")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            DefaultValueAssigner.assignDefaultsToEnumEntries(
                    classChange, List.of(change1, change2), model);

            assertThat(change1.getAllowedValues()).hasSize(3);
            assertThat(change2.getAllowedValues()).hasSize(3);
        }

        @Test
        void populateDefaultsForEnumEntries_emptyEnum_setsEmptyAllowedValues() {
            var enumClass = model.createResource(PREFIX + "StatusEnum");
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "StatusEnum")
                            .label("StatusEnum")
                            .build();

            var enumEntryChange =
                    SemanticEnumEntryChange.builder()
                            .iri(PREFIX + "StatusEnum.ACTIVE")
                            .label("ACTIVE")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            DefaultValueAssigner.assignDefaultsToEnumEntries(
                    classChange, List.of(enumEntryChange), model);

            assertThat(enumEntryChange.getAllowedValues()).isEmpty();
        }
    }
}
