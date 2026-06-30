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
        assertThat(script).contains(PREFIX + "DiagramObject>");
        assertThat(script).contains(PREFIX + "TextDiagramObject>");
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

        assertThat(script).isNotBlank();
        assertThat(script).contains(PREFIX + "TextDiagramObject>");
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

        assertThat(script).contains(PREFIX + "TextDiagramObject>");
        assertThat(script).doesNotContain(PREFIX + "DiagramObject>");
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
