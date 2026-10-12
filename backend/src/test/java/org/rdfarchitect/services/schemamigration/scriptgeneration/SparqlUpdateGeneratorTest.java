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

package org.rdfarchitect.services.schemamigration.scriptgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.context.SchemaMigrationContext;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlUpdateGenerator;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class SparqlUpdateGeneratorTest {

    private static final String PREFIX = "http://iec.ch/TC57/CIM100#";
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    @Mock private MigrationSessionStore migrationSessionStore;

    @InjectMocks private SparqlUpdateGenerator generator;

    private Model schema;

    @BeforeEach
    void setUp() {
        schema = ModelFactory.createDefaultModel();
        var context = new SchemaMigrationContext();
        context.setUpdatedSchema(schema.getGraph());
        lenient().when(migrationSessionStore.getContext()).thenReturn(context);
    }

    @Test
    void generateAddAttributeUpdate_concreteClassWithSubclass_updatesClassAndAllSubclasses() {
        // DiagramObject (concrete) <- TextDiagramObject (concrete)
        var diagramObject = concreteClass("DiagramObject");
        var textDiagramObject = concreteClass("TextDiagramObject");
        textDiagramObject.addProperty(RDFS.subClassOf, diagramObject);

        var attributeChange = mandatoryAttribute("DiagramObject.test");

        var script =
                generator.generateAddAttributeUpdate(attributeChange, PREFIX + "DiagramObject");

        // The declaring class itself must be migrated, not only its deriving subclass.
        assertThat(script)
                .contains(PREFIX + "DiagramObject>")
                .contains(PREFIX + "TextDiagramObject>");
    }

    @Test
    void generateAddAttributeUpdate_leafConcreteClass_updatesThatClass() {
        // Adding the attribute directly on a leaf class must not produce an empty script.
        var diagramObject = concreteClass("DiagramObject");
        var textDiagramObject = concreteClass("TextDiagramObject");
        textDiagramObject.addProperty(RDFS.subClassOf, diagramObject);

        var attributeChange = mandatoryAttribute("TextDiagramObject.test");

        var script =
                generator.generateAddAttributeUpdate(attributeChange, PREFIX + "TextDiagramObject");

        assertThat(script).isNotBlank().contains(PREFIX + "TextDiagramObject>");
    }

    @Test
    void generateAddAttributeUpdate_abstractClass_onlyUpdatesConcreteSubclasses() {
        // Abstract classes have no direct instances, so only the concrete subclass is migrated.
        var diagramObject = schema.createResource(PREFIX + "DiagramObject");
        diagramObject.addProperty(RDF.type, RDFS.Class);
        var textDiagramObject = concreteClass("TextDiagramObject");
        textDiagramObject.addProperty(RDFS.subClassOf, diagramObject);

        var attributeChange = mandatoryAttribute("DiagramObject.test");

        var script =
                generator.generateAddAttributeUpdate(attributeChange, PREFIX + "DiagramObject");

        assertThat(script)
                .contains(PREFIX + "TextDiagramObject>")
                .doesNotContain(PREFIX + "DiagramObject>");
    }

    @Test
    void generateAddAttributeUpdate_optionalAttributeWithoutForcedDefault_returnsEmpty() {
        concreteClass("DiagramObject");

        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "DiagramObject.test")
                        .primitiveDataType(XSD_STRING)
                        .defaultValue("default")
                        .optional(true)
                        .forceDefaultValue(false)
                        .build();

        var script =
                generator.generateAddAttributeUpdate(attributeChange, PREFIX + "DiagramObject");

        assertThat(script).isEmpty();
    }

    @Test
    void generateAddAttributeUpdate_waivedDefaultValue_returnsEmpty() {
        // RDFA-714: a mandatory attribute may be left without a default value on purpose.
        concreteClass("DiagramObject");

        var attributeChange = mandatoryAttribute("DiagramObject.test");
        attributeChange.setDefaultValue(null);

        var script =
                generator.generateAddAttributeUpdate(attributeChange, PREFIX + "DiagramObject");

        assertThat(script).isEmpty();
    }

    @Test
    void generateAddAttributeToSingleClassUpdate_waivedDefaultValue_returnsEmpty() {
        concreteClass("DiagramObject");

        var attributeChange = mandatoryAttribute("DiagramObject.test");
        attributeChange.setDefaultValue("   ");

        var script =
                generator.generateAddAttributeToSingleClassUpdate(
                        attributeChange, PREFIX + "DiagramObject");

        assertThat(script).isEmpty();
    }

    @Test
    void generateDatatypeChangedUpdate_waivedDefaultValue_convertsNothing() {
        // Without a fallback value the existing values are kept rather than dropped.
        var attributeChange = mandatoryAttribute("DiagramObject.test");
        attributeChange.setDefaultValue(null);

        assertThat(generator.generateDatatypeChangedUpdate(attributeChange)).isEmpty();
        assertThat(generator.generateEnumDatatypeChangedUpdate(attributeChange)).isEmpty();
    }

    @Test
    void generateSplitAttributeUpdate_threeTargets_onlyPopulatesTheInstanceActualClass() {
        // Each instance must only receive the one target property matching its own rdf:type -
        // not every sibling target's property, which was the original bug (RDFA-729).
        concreteClass("ClassA");
        concreteClass("ClassB");
        concreteClass("ClassC");

        var oldAttribute = schema.createProperty(PREFIX + "OldClass.voltage");
        var newAttribute0 = schema.createProperty(PREFIX + "ClassA.voltage");
        var newAttribute1 = schema.createProperty(PREFIX + "ClassB.voltage");
        var newAttribute2 = schema.createProperty(PREFIX + "ClassC.voltage");
        var instance = schema.createResource(PREFIX + "instance1");
        instance.addProperty(RDF.type, schema.createResource(PREFIX + "ClassB"));
        instance.addProperty(oldAttribute, "400");

        var siblings =
                List.of(
                        splitSibling(PREFIX + "OldClass.voltage", PREFIX + "ClassA.voltage", "ClassA"),
                        splitSibling(PREFIX + "OldClass.voltage", PREFIX + "ClassB.voltage", "ClassB"),
                        splitSibling(PREFIX + "OldClass.voltage", PREFIX + "ClassC.voltage", "ClassC"));

        var update = generator.generateSplitAttributeUpdate(siblings);
        UpdateAction.parseExecute(update, schema);

        assertThat(instance.hasProperty(oldAttribute)).isFalse();
        assertThat(instance.hasProperty(newAttribute0)).isFalse();
        assertThat(instance.getProperty(newAttribute1).getString()).isEqualTo("400");
        assertThat(instance.hasProperty(newAttribute2)).isFalse();
    }

    @Test
    void generateSplitAttributeUpdate_targetWithSubclass_scopesToWholeHierarchy() {
        // A class that derived from the split's old domain both before and after keeps its value
        // under the rewritten predicate - the split update must reach its subclasses too, not
        // only the exact target class named on the sibling.
        var classB = concreteClass("ClassB");
        var classBSub = concreteClass("ClassBSub");
        classBSub.addProperty(RDFS.subClassOf, classB);

        var oldAttribute = schema.createProperty(PREFIX + "OldClass.voltage");
        var newAttribute1 = schema.createProperty(PREFIX + "ClassB.voltage");
        var instance = schema.createResource(PREFIX + "instance1");
        instance.addProperty(RDF.type, classBSub);
        instance.addProperty(oldAttribute, "400");

        var siblings =
                List.of(
                        splitSibling(PREFIX + "OldClass.voltage", PREFIX + "ClassA.voltage", "ClassA"),
                        splitSibling(PREFIX + "OldClass.voltage", PREFIX + "ClassB.voltage", "ClassB"));

        var update = generator.generateSplitAttributeUpdate(siblings);
        UpdateAction.parseExecute(update, schema);

        assertThat(instance.hasProperty(oldAttribute)).isFalse();
        assertThat(instance.getProperty(newAttribute1).getString()).isEqualTo("400");
    }

    private SemanticAttributeChange splitSibling(String oldIri, String newIri, String domainLocalName) {
        return SemanticAttributeChange.builder()
                .iri(newIri)
                .oldIRI(oldIri)
                .domain(PREFIX + domainLocalName)
                .build();
    }

    private Resource concreteClass(String localName) {
        var resource = schema.createResource(PREFIX + localName);
        resource.addProperty(RDF.type, RDFS.Class);
        resource.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        return resource;
    }

    private SemanticAttributeChange mandatoryAttribute(String localName) {
        return SemanticAttributeChange.builder()
                .iri(PREFIX + localName)
                .primitiveDataType(XSD_STRING)
                .defaultValue("default")
                .optional(false)
                .build();
    }
}
