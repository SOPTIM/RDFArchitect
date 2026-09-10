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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayList;
import java.util.List;

class InheritanceChangeHandlerTest {

    private static final String PREFIX = "http://example.org#";
    private static final String CIMS_PREFIX =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    private Model oldModel;
    private Model newModel;
    private List<SemanticClassChange> allClassChanges;
    private List<RenameCandidate<SemanticClassChange>> classRenames;

    @BeforeEach
    void setUp() {
        oldModel = ModelFactory.createDefaultModel();
        newModel = ModelFactory.createDefaultModel();
        allClassChanges = new ArrayList<>();
        classRenames = new ArrayList<>();
    }

    @Nested
    class SuperclassChangeTest {

        @Test
        void addPropertyChangesFromInheritance_superclassAdded_addsInheritedProperties() {
            // Setup old model - Equipment without superclass
            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // Setup new model - Equipment inherits from IdentifiedObject
            var newIdentifiedObject = newModel.createResource(PREFIX + "IdentifiedObject");
            newIdentifiedObject.addProperty(RDF.type, RDFS.Class);
            newIdentifiedObject.addProperty(RDFS.label, newModel.createLiteral("IdentifiedObject"));
            newIdentifiedObject.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var nameAttr = newModel.createResource(PREFIX + "IdentifiedObject.name");
            nameAttr.addProperty(RDF.type, RDF.Property);
            nameAttr.addProperty(RDFS.label, newModel.createLiteral("name"));
            nameAttr.addProperty(RDFS.domain, newIdentifiedObject);
            nameAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            nameAttr.addProperty(
                    CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            nameAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newIdentifiedObject);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            assertThat(classChange.getAttributes()).isNotEmpty();
            assertThat(classChange.getAttributes())
                    .anyMatch(
                            attr ->
                                    attr.getLabel().equals("IdentifiedObject.name")
                                            && attr.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void addPropertyChangesFromInheritance_superclassRemoved_removesInheritedProperties() {
            // Setup old model - Equipment inherits from IdentifiedObject
            var oldIdentifiedObject = oldModel.createResource(PREFIX + "IdentifiedObject");
            oldIdentifiedObject.addProperty(RDF.type, RDFS.Class);
            oldIdentifiedObject.addProperty(RDFS.label, oldModel.createLiteral("IdentifiedObject"));
            oldIdentifiedObject.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var oldNameAttr = oldModel.createResource(PREFIX + "IdentifiedObject.name");
            oldNameAttr.addProperty(RDF.type, RDF.Property);
            oldNameAttr.addProperty(RDFS.label, oldModel.createLiteral("name"));
            oldNameAttr.addProperty(RDFS.domain, oldIdentifiedObject);
            oldNameAttr.addProperty(
                    CIMS.datatype,
                    oldModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            oldNameAttr
                    .addProperty(CIMS.multiplicity, oldModel.createResource(CIMS_PREFIX + "M:1..1"))
                    .addProperty(RDFS.label, oldModel.createLiteral(CIMS_PREFIX + "M:1..1"));
            oldNameAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(RDFS.subClassOf, oldIdentifiedObject);
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // Setup new model - Equipment without superclass
            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            assertThat(classChange.getAttributes()).isNotEmpty();
            assertThat(classChange.getAttributes())
                    .anyMatch(
                            attr ->
                                    attr.getLabel().equals("IdentifiedObject.name")
                                            && attr.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
        }

        @Test
        void addPropertyChangesFromInheritance_superclassChanged_updatesProperties() {
            // Setup old model - Equipment inherits from OldBase
            var oldBase = oldModel.createResource(PREFIX + "OldBase");
            oldBase.addProperty(RDF.type, RDFS.Class);
            oldBase.addProperty(RDFS.label, oldModel.createLiteral("OldBase"));
            oldBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var oldAttr = oldModel.createResource(PREFIX + "OldBase.oldProperty");
            oldAttr.addProperty(RDF.type, RDF.Property);
            oldAttr.addProperty(RDFS.label, oldModel.createLiteral("oldProperty"));
            oldAttr.addProperty(RDFS.domain, oldBase);
            oldAttr.addProperty(
                    CIMS.datatype,
                    oldModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            oldAttr.addProperty(CIMS.multiplicity, oldModel.createResource(CIMS_PREFIX + "M:1..1"));
            oldAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(RDFS.subClassOf, oldBase);
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // Setup new model - Equipment inherits from NewBase
            var newBase = newModel.createResource(PREFIX + "NewBase");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("NewBase"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newAttr = newModel.createResource(PREFIX + "NewBase.newProperty");
            newAttr.addProperty(RDF.type, RDF.Property);
            newAttr.addProperty(RDFS.label, newModel.createLiteral("newProperty"));
            newAttr.addProperty(RDFS.domain, newBase);
            newAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            newAttr.addProperty(CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            newAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newBase);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            assertThat(classChange.getAttributes()).hasSize(2);
            assertThat(classChange.getAttributes())
                    .anyMatch(
                            attr ->
                                    attr.getLabel().equals("OldBase.oldProperty")
                                            && attr.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
            assertThat(classChange.getAttributes())
                    .anyMatch(
                            attr ->
                                    attr.getLabel().equals("NewBase.newProperty")
                                            && attr.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void addPropertyChangesFromInheritance_superclassRenamed_noPropertyChanges() {
            // Setup old model
            var oldBase = oldModel.createResource(PREFIX + "OldBase");
            oldBase.addProperty(RDF.type, RDFS.Class);
            oldBase.addProperty(RDFS.label, oldModel.createLiteral("OldBase"));
            oldBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var oldAttr = oldModel.createResource(PREFIX + "OldBase.property");
            oldAttr.addProperty(RDF.type, RDF.Property);
            oldAttr.addProperty(RDFS.label, oldModel.createLiteral("property"));
            oldAttr.addProperty(RDFS.domain, oldBase);
            oldAttr.addProperty(
                    CIMS.datatype,
                    oldModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            oldAttr.addProperty(CIMS.multiplicity, oldModel.createResource(CIMS_PREFIX + "M:1..1"));
            oldAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(RDFS.subClassOf, oldBase);
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // Setup new model - OldBase renamed to NewBase
            var newBase = newModel.createResource(PREFIX + "NewBase");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("NewBase"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newAttr = newModel.createResource(PREFIX + "NewBase.property");
            newAttr.addProperty(RDF.type, RDF.Property);
            newAttr.addProperty(RDFS.label, newModel.createLiteral("property"));
            newAttr.addProperty(RDFS.domain, newBase);
            newAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            newAttr.addProperty(CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            newAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newBase);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var deletedBase =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "OldBase")
                            .label("OldBase")
                            .semanticResourceChangeType(SemanticResourceChangeType.DELETE)
                            .build();

            var addedBase =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "NewBase")
                            .label("NewBase")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .build();

            classRenames.add(new RenameCandidate<>(deletedBase, addedBase, 0.9));

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            // Should not add or remove properties since it's just a rename
            assertThat(classChange.getAttributes()).isEmpty();
        }

        @Test
        void addPropertyChangesFromInheritance_commonSuperclass_noChanges() {
            // Both old and new have the same superclass
            var base = oldModel.createResource(PREFIX + "Base");
            base.addProperty(RDF.type, RDFS.Class);
            base.addProperty(RDFS.label, oldModel.createLiteral("Base"));
            base.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(RDFS.subClassOf, base);
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newBase);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            assertThat(classChange.getAttributes()).isEmpty();
        }
    }

    @Nested
    class DerivingClassesTest {

        @Test
        void addPropertyChangesFromInheritance_superclassAdded_propagatesToDerivedClasses() {
            // Setup old model
            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var oldTransformer = oldModel.createResource(PREFIX + "Transformer");
            oldTransformer.addProperty(RDFS.label, oldModel.createLiteral("Transformer"));
            oldTransformer.addProperty(RDF.type, RDFS.Class);
            oldTransformer.addProperty(RDFS.subClassOf, oldEquipment);
            oldTransformer.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // Setup new model - Equipment now has a superclass with properties
            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var baseAttr = newModel.createResource(PREFIX + "Base.baseProperty");
            baseAttr.addProperty(RDF.type, RDF.Property);
            baseAttr.addProperty(RDFS.label, newModel.createLiteral("baseProperty"));
            baseAttr.addProperty(RDFS.domain, newBase);
            baseAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            baseAttr.addProperty(
                    CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            baseAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newBase);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newTransformer = newModel.createResource(PREFIX + "Transformer");
            newTransformer.addProperty(RDF.type, RDFS.Class);
            newTransformer.addProperty(RDFS.label, newModel.createLiteral("Transformer"));
            newTransformer.addProperty(RDFS.subClassOf, newEquipment);
            newTransformer.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var equipmentChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();
            var superClassChange =
                    new SemanticFieldChange(
                            SemanticFieldChangeType.SUPERCLASS_CHANGE, null, PREFIX + "Base");
            equipmentChange.getChanges().add(superClassChange);

            allClassChanges.add(equipmentChange);

            InheritanceChangeHandler.processInheritanceChanges(
                    allClassChanges, newModel, oldModel, classRenames);

            // Check that Transformer was added to allClassChanges and has the property
            var transformerChange =
                    allClassChanges.stream()
                            .filter(c -> c.getIri().equals(PREFIX + "Transformer"))
                            .findFirst();

            assertThat(transformerChange).isPresent();
            assertThat(transformerChange.get().getAttributes())
                    .anyMatch(
                            attr ->
                                    attr.getLabel().equals("Base.baseProperty")
                                            && attr.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void addPropertyChangesFromInheritance_abstractClass_doesNotGetProperties() {
            // Setup models with abstract base class
            var oldBase = oldModel.createResource(PREFIX + "Base");
            oldBase.addProperty(RDF.type, RDFS.Class);
            oldBase.addProperty(RDFS.label, oldModel.createLiteral("Base"));

            var newIdentified = newModel.createResource(PREFIX + "IdentifiedObject");
            newIdentified.addProperty(RDF.type, RDFS.Class);
            newIdentified.addProperty(RDFS.label, newModel.createLiteral("IdentifiedObject"));

            var nameAttr = newModel.createResource(PREFIX + "IdentifiedObject.name");
            nameAttr.addProperty(RDF.type, RDF.Property);
            nameAttr.addProperty(RDFS.label, newModel.createLiteral("name"));
            nameAttr.addProperty(RDFS.domain, newIdentified);
            nameAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            nameAttr.addProperty(
                    CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            nameAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(RDFS.subClassOf, newIdentified);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Base")
                            .label("Base")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            // Abstract classes themselves don't get instance-level property changes
            assertThat(classChange.getAttributes()).isEmpty();
        }
    }

    @Nested
    class AssociationHandlingTest {

        @Test
        void addPropertyChangesFromInheritance_superclassWithAssociation_addsAssociation() {
            // Setup new model with association in superclass
            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var targetClass = newModel.createResource(PREFIX + "Target");
            targetClass.addProperty(RDFS.label, newModel.createLiteral("Target"));

            var assoc = newModel.createResource(PREFIX + "Base.Target");
            assoc.addProperty(RDF.type, RDF.Property);
            assoc.addProperty(RDFS.label, newModel.createLiteral("association"));
            assoc.addProperty(RDFS.domain, newBase);
            assoc.addProperty(RDFS.range, targetClass);
            assoc.addProperty(CIMS.associationUsed, "No");
            assoc.addProperty(CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:0..*"));
            assoc.addProperty(
                    CIMS.inverseRoleName, newModel.createResource(CIMS_PREFIX + "Target.Base"));

            var oldEquipment = oldModel.createResource(PREFIX + "Equipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("Equipment"));
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newEquipment = newModel.createResource(PREFIX + "Equipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("Equipment"));
            newEquipment.addProperty(RDFS.subClassOf, newBase);
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Equipment")
                            .label("Equipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    classChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            assertThat(classChange.getAssociations()).isNotEmpty();
            assertThat(classChange.getAssociations())
                    .anyMatch(
                            assoc2 ->
                                    assoc2.getLabel().equals("Base.Target")
                                            && assoc2.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }
    }

    @Nested
    class EdgeCasesTest {

        @Test
        void addPropertyChangesFromInheritance_classWithOldIRI_usesOldIRI() {
            var oldEquipment = oldModel.createResource(PREFIX + "OldEquipment");
            oldEquipment.addProperty(RDF.type, RDFS.Class);
            oldEquipment.addProperty(RDFS.label, oldModel.createLiteral("OldEquipment"));
            oldEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newEquipment = newModel.createResource(PREFIX + "NewEquipment");
            newEquipment.addProperty(RDF.type, RDFS.Class);
            newEquipment.addProperty(RDFS.label, newModel.createLiteral("NewEquipment"));
            newEquipment.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "NewEquipment")
                            .oldIRI(PREFIX + "OldEquipment")
                            .label("NewEquipment")
                            .semanticResourceChangeType(SemanticResourceChangeType.RENAME)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(classChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            // Should not throw exception
            assertThatCode(
                            () ->
                                    InheritanceChangeHandler.addPropertyChangesFromInheritance(
                                            classChange,
                                            allClassChanges,
                                            classChangeMap,
                                            newModel,
                                            oldModel,
                                            classRenames))
                    .doesNotThrowAnyException();
        }

        @Test
        void addPropertyChangesFromInheritance_newPropertyInSuperclass_notAddedToDerived() {
            // Setup: Superclass has a property that was just added
            var oldBase = oldModel.createResource(PREFIX + "Base");
            oldBase.addProperty(RDF.type, RDFS.Class);
            oldBase.addProperty(RDFS.label, oldModel.createLiteral("Base"));
            oldBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newAttr = newModel.createResource(PREFIX + "Base.newProperty");
            newAttr.addProperty(RDF.type, RDF.Property);
            newAttr.addProperty(RDFS.label, newModel.createLiteral("newProperty"));
            newAttr.addProperty(RDFS.domain, newBase);
            newAttr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            newAttr.addProperty(CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            newAttr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var oldDerived = oldModel.createResource(PREFIX + "Derived");
            oldDerived.addProperty(RDF.type, RDFS.Class);
            oldDerived.addProperty(RDFS.label, oldModel.createLiteral("Derived"));
            oldDerived.addProperty(RDFS.subClassOf, oldBase);
            oldDerived.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var newDerived = newModel.createResource(PREFIX + "Derived");
            newDerived.addProperty(RDF.type, RDFS.Class);
            newDerived.addProperty(RDFS.label, newModel.createLiteral("Derived"));
            newDerived.addProperty(RDFS.subClassOf, newBase);
            newDerived.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var baseChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Base")
                            .label("Base")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(
                                    new ArrayList<>(
                                            List.of(
                                                    SemanticAttributeChange.builder()
                                                            .iri(PREFIX + "Base.newProperty")
                                                            .label("newProperty")
                                                            .semanticResourceChangeType(
                                                                    SemanticResourceChangeType.ADD)
                                                            .build())))
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            var derivedChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Derived")
                            .label("Derived")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(baseChange);
            allClassChanges.add(derivedChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    derivedChange,
                    allClassChanges,
                    classChangeMap,
                    newModel,
                    oldModel,
                    classRenames);

            // The new property should not be added to derived class since it's new in the base
            assertThat(derivedChange.getAttributes()).isEmpty();
        }

        @Test
        void addPropertyChangesFromInheritance_addedDerivedClass_notProcessed() {
            var newBase = newModel.createResource(PREFIX + "Base");
            newBase.addProperty(RDF.type, RDFS.Class);
            newBase.addProperty(RDFS.label, newModel.createLiteral("Base"));
            newBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var attr = newModel.createResource(PREFIX + "Base.property");
            attr.addProperty(RDF.type, RDF.Property);
            attr.addProperty(RDFS.label, newModel.createLiteral("property"));
            attr.addProperty(RDFS.domain, newBase);
            attr.addProperty(
                    CIMS.datatype,
                    newModel.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attr.addProperty(CIMS.multiplicity, newModel.createResource(CIMS_PREFIX + "M:1..1"));
            attr.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

            var oldBase = oldModel.createResource(PREFIX + "Base");
            oldBase.addProperty(RDF.type, RDFS.Class);
            oldBase.addProperty(RDFS.label, oldModel.createLiteral("Base"));
            oldBase.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            // New derived class (didn't exist in old model)
            var newDerived = newModel.createResource(PREFIX + "NewDerivedClass");
            newDerived.addProperty(RDF.type, RDFS.Class);
            newDerived.addProperty(RDFS.label, newModel.createLiteral("NewDerivedClass"));
            newDerived.addProperty(RDFS.subClassOf, newBase);
            newDerived.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

            var baseChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "Base")
                            .label("Base")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            var newDerivedChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + "NewDerivedClass")
                            .label("NewDerivedClass")
                            .semanticResourceChangeType(SemanticResourceChangeType.ADD)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();

            allClassChanges.add(baseChange);
            allClassChanges.add(newDerivedChange);

            var classChangeMap =
                    allClassChanges.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            SemanticClassChange::getIri, c -> c));

            InheritanceChangeHandler.addPropertyChangesFromInheritance(
                    baseChange, allClassChanges, classChangeMap, newModel, oldModel, classRenames);

            // New derived classes should not get properties added
            assertThat(newDerivedChange.getAttributes()).isEmpty();
        }
    }

    @Nested
    class InheritanceChainTest {

        /**
         * A class is reached both through its own changed super class and as a deriving class of
         * every changed class above it, so a chain of super class changes used to report each
         * inherited property once per level.
         */
        @Test
        void processInheritanceChanges_chainOfSuperclassChanges_addsInheritedPropertyOnce() {
            declareUnrelatedClasses(oldModel);
            var oldBase = oldModel.getResource(PREFIX + "Base");
            declareAttribute(oldModel, oldBase, "Base.baseProperty");

            declareChain(newModel);
            var newBase = newModel.getResource(PREFIX + "Base");
            declareAttribute(newModel, newBase, "Base.baseProperty");

            allClassChanges.add(superclassChange("Derived", "Middle"));
            allClassChanges.add(superclassChange("Middle", "Base"));

            InheritanceChangeHandler.processInheritanceChanges(
                    allClassChanges, newModel, oldModel, classRenames);

            assertThat(inheritedAttributes(PREFIX + "Derived", PREFIX + "Base.baseProperty"))
                    .singleElement()
                    .satisfies(
                            attr ->
                                    assertThat(attr.getSemanticResourceChangeType())
                                            .isEqualTo(
                                                    SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE));
            assertThat(inheritedAttributes(PREFIX + "Middle", PREFIX + "Base.baseProperty"))
                    .hasSize(1);
        }

        @Test
        void processInheritanceChanges_chainOfSuperclassChanges_removesInheritedPropertyOnce() {
            declareChain(oldModel);
            var oldBase = oldModel.getResource(PREFIX + "Base");
            declareAttribute(oldModel, oldBase, "Base.baseProperty");

            declareUnrelatedClasses(newModel);
            var newBase = newModel.getResource(PREFIX + "Base");
            declareAttribute(newModel, newBase, "Base.baseProperty");

            allClassChanges.add(superclassChange("Derived", "Middle"));
            allClassChanges.add(superclassChange("Middle", "Base"));

            InheritanceChangeHandler.processInheritanceChanges(
                    allClassChanges, newModel, oldModel, classRenames);

            assertThat(inheritedAttributes(PREFIX + "Derived", PREFIX + "Base.baseProperty"))
                    .singleElement()
                    .satisfies(
                            attr ->
                                    assertThat(attr.getSemanticResourceChangeType())
                                            .isEqualTo(
                                                    SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE));
            assertThat(inheritedAttributes(PREFIX + "Middle", PREFIX + "Base.baseProperty"))
                    .hasSize(1);
        }

        @Test
        void processInheritanceChanges_chainOfSuperclassChanges_addsInheritedAssociationOnce() {
            declareUnrelatedClasses(oldModel);
            var oldBase = oldModel.getResource(PREFIX + "Base");
            declareAssociation(oldModel, oldBase, "Base.baseAssociation");

            declareChain(newModel);
            var newBase = newModel.getResource(PREFIX + "Base");
            declareAssociation(newModel, newBase, "Base.baseAssociation");

            allClassChanges.add(superclassChange("Derived", "Middle"));
            allClassChanges.add(superclassChange("Middle", "Base"));

            InheritanceChangeHandler.processInheritanceChanges(
                    allClassChanges, newModel, oldModel, classRenames);

            assertThat(
                            classChange(PREFIX + "Derived").getAssociations().stream()
                                    .filter(a -> a.getIri().equals(PREFIX + "Base.baseAssociation"))
                                    .toList())
                    .hasSize(1);
        }

        /** Derived -&gt; Middle -&gt; Base, all concrete. */
        private void declareChain(Model model) {
            declareUnrelatedClasses(model);
            model.getResource(PREFIX + "Derived")
                    .addProperty(RDFS.subClassOf, model.getResource(PREFIX + "Middle"));
            model.getResource(PREFIX + "Middle")
                    .addProperty(RDFS.subClassOf, model.getResource(PREFIX + "Base"));
        }

        private void declareUnrelatedClasses(Model model) {
            for (var name : List.of("Base", "Middle", "Derived")) {
                var cls = model.createResource(PREFIX + name);
                cls.addProperty(RDF.type, RDFS.Class);
                cls.addProperty(RDFS.label, model.createLiteral(name));
                cls.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
            }
        }

        private void declareAttribute(Model model, Resource domain, String name) {
            var attribute = model.createResource(PREFIX + name);
            attribute.addProperty(RDF.type, RDF.Property);
            attribute.addProperty(RDFS.label, model.createLiteral(name));
            attribute.addProperty(RDFS.domain, domain);
            attribute.addProperty(
                    CIMS.datatype, model.createResource("http://www.w3.org/2001/XMLSchema#string"));
            attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
            attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        }

        private void declareAssociation(Model model, Resource domain, String name) {
            var association = model.createResource(PREFIX + name);
            association.addProperty(RDF.type, RDF.Property);
            association.addProperty(RDFS.label, model.createLiteral(name));
            association.addProperty(RDFS.domain, domain);
            association.addProperty(RDFS.range, domain);
            association.addProperty(CIMS.associationUsed, "No");
            association.addProperty(
                    CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..1"));
            association.addProperty(
                    CIMS.inverseRoleName, model.createResource(PREFIX + name + ".inverse"));
        }

        private SemanticClassChange superclassChange(String classLabel, String newSuperClass) {
            var classChange =
                    SemanticClassChange.builder()
                            .iri(PREFIX + classLabel)
                            .label(classLabel)
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(new ArrayList<>())
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build();
            classChange
                    .getChanges()
                    .add(
                            new SemanticFieldChange(
                                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                                    null,
                                    PREFIX + newSuperClass));
            return classChange;
        }

        private SemanticClassChange classChange(String iri) {
            return allClassChanges.stream()
                    .filter(c -> c.getIri().equals(iri))
                    .findFirst()
                    .orElseThrow();
        }

        private List<SemanticAttributeChange> inheritedAttributes(
                String classIri, String attributeIri) {
            return classChange(classIri).getAttributes().stream()
                    .filter(a -> a.getIri().equals(attributeIri))
                    .toList();
        }
    }
}
