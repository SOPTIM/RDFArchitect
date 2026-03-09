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

package org.rdfarchitect.services.rendering;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class RenderCIMCollectionSvelteFlowServiceTest extends RenderCIMCollectionTestBase {

    @Test
    void renderUML_emptyCollection_emptyArrays() {
        //Arrange

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderUML_nullCollection_throwsException() {
        assertThatException().isThrownBy(() -> svelteFlowRenderer.renderUML(null, null, null));
    }

    @Test
    void renderUML_collectionWithOnlyPackages_emptyMermaidDiagram() {
        //Arrange
        addPackage("package_package1");
        addPackage("package_package2");

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderUML_singleClass_createsNodeWithCorrectData() {
        //Arrange
        addPackage("package_package1");
        addClass("package_package1", "class1");
        var classesIterator = cimCollection.getClasses().iterator();
        var class1 = classesIterator.next();
        class1.setStereotypes(List.of(new CIMSStereotype("Entsoe"), new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#concrete")));
        class1.setBelongsToCategory(new CIMSBelongsToCategory(new URI("http://example.com/Category#package_package1"), new RDFSLabel("package1", "en"), UUID.randomUUID()));
        addAttribute("class1", "attribute1", XSDDatatype.XSDstring);
        addAttribute("class1", "attribute2", XSDDatatype.XSDint);
        var attributeIterator = cimCollection.getAttributes().iterator();
        var attribute1 = attributeIterator.next();
        attribute1.setMultiplicity(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#1...1"));
        var attribute2 = attributeIterator.next();
        attribute2.setMultiplicity(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#0...n"));

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        var nodeDTO = result.getNodes().get(0);
        var attr1DTO = nodeDTO.getData().getAttributes().get(0);
        var attr2DTO = nodeDTO.getData().getAttributes().get(1);
        assertThat(result.getNodes()).hasSize(1);
        assertThat(nodeDTO.getId()).isNotNull();
        assertThat(nodeDTO.getType()).isEqualTo("class");
        assertThat(nodeDTO.getData().getLabel()).isEqualTo("class1");
        assertThat(nodeDTO.getData().getBelongsToCategory()).isEqualTo("package1");
        assertThat(nodeDTO.getData().getStereotypes()).hasSize(1).contains("Entsoe");
        assertThat(nodeDTO.getData().getAttributes()).hasSize(2);
        assertThat(attr1DTO.getLabel()).isEqualTo("attribute1");
        assertThat(attr1DTO.getType()).isEqualTo("string");
        assertThat(attr1DTO.getMultiplicity()).isEqualTo("1...1");
        assertThat(attr2DTO.getLabel()).isEqualTo("attribute2");
        assertThat(attr2DTO.getType()).isEqualTo("int");
        assertThat(attr2DTO.getMultiplicity()).isEqualTo("0...n");
        assertThat(nodeDTO.getData().getEnumEntries()).isEmpty();
    }

    @Test
    void renderUML_singleEnum_createsNodeWithCorrectData() {
        //Arrange
        addPackage("package_package1");
        addEnum("package_package1", "enum1");
        addEnumEntry("enum1", "enumEntry1");
        addEnumEntry("enum1", "enumEntry2");

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        var nodeDTO = result.getNodes().get(0);
        assertThat(result.getNodes()).hasSize(1);
        assertThat(nodeDTO.getData().getStereotypes()).hasSize(2).contains("enumeration").contains("abstract");
        assertThat(nodeDTO.getData().getEnumEntries()).hasSize(2).contains("enumEntry1").contains("enumEntry2");
    }

    @Test
    void renderUML_superClassAndSubClass_createsInheritanceEdge() {
        //Arrange
        addPackage("package_package1");
        addClass("package_package1", "subClass");
        addClass("package_package1", "superClass");
        var classesIterator = cimCollection.getClasses().iterator();
        var subClass = classesIterator.next();
        var superClass = classesIterator.next();
        subClass.setSuperClass(new RDFSSubClassOf(superClass.getUri(), superClass.getLabel()));

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        var inheritanceEdgeDTO = result.getEdges().get(0);
        var subClassUUID = result.getNodes().get(0).getId();
        var superClassUUID = result.getNodes().get(1).getId();
        assertThat(result.getEdges()).hasSize(1);
        assertThat(inheritanceEdgeDTO.getType()).isEqualTo("inheritance");
        assertThat(inheritanceEdgeDTO.getSource()).isEqualTo(subClassUUID);
        assertThat(inheritanceEdgeDTO.getTarget()).isEqualTo(superClassUUID);
        assertThat(inheritanceEdgeDTO.getData()).isNull();
    }

    @Test
    void renderUML_twoClassesAndAssociation_createsAssociationEdge() {
        //Arrange
        addPackage("package_package1");
        addClass("package_package1", "class1");
        addClass("package_package2", "class2");
        addAssociation("class1", "class2", AssociationUsed.YES, AssociationUsed.NO);
        var associationIterator = cimCollection.getAssociations().iterator();
        var fromAssoc = associationIterator.next();
        var toAssoc = associationIterator.next();
        fromAssoc.setMultiplicity(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#0...n"));
        toAssoc.setMultiplicity(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#1...1"));

        //Act
        var result = (SvelteFlowDTO) svelteFlowRenderer.renderUML(cimCollection, null, null);

        //Assert
        var associationEdgeDTO = result.getEdges().get(0);
        assertThat(result.getEdges()).hasSize(1);
        assertThat(associationEdgeDTO.getData().getFromMultiplicity()).isEqualTo("0...n");
        assertThat(associationEdgeDTO.getData().getToMultiplicity()).isEqualTo("1...1");
        assertThat(associationEdgeDTO.getData().isUseFromAssociation()).isFalse();
        assertThat(associationEdgeDTO.getData().isUseToAssociation()).isTrue();
    }
}
