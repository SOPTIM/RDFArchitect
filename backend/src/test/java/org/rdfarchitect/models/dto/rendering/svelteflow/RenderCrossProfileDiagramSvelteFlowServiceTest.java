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

package org.rdfarchitect.models.dto.rendering.svelteflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.SuperClassDTO;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.GraphSourceDTO;
import org.rdfarchitect.api.dto.cross_profile_diagram.MergedClassDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class RenderCrossProfileDiagramSvelteFlowServiceTest {

    private static final String DATASET_NAME = "myDataset";
    private static final String GRAPH_URI = "http://example.com/graph1";
    private static final String GRAPH_COLOR = "#ff0000";

    @Mock private FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;

    @InjectMocks private RenderCrossProfileDiagramSvelteFlowService service;

    private MergedClassDTO.MergedClassDTOBuilder classBuilder(String label) {
        return MergedClassDTO.builder()
                .uuid(UUID.randomUUID())
                .classUri("http://example.com/ontology#" + label)
                .label(label);
    }

    private <T> GraphSourceDTO<T> graphSourced(T value) {
        return new GraphSourceDTO<>(GRAPH_URI, GRAPH_COLOR, value);
    }

    private CrossProfileDiagramDTO diagramOf(MergedClassDTO... classes) {
        return new CrossProfileDiagramDTO(UUID.randomUUID(), List.of(classes));
    }

    @Test
    void renderCrossProfileDiagramUML_emptyClasses_returnsEmptyArrays() {
        var diagram = new CrossProfileDiagramDTO(UUID.randomUUID(), List.of());

        var result = (SvelteFlowDTO) service.renderCrossProfileDiagramUML(diagram, DATASET_NAME);

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_nullClasses_returnsEmptyArrays() {
        var diagram = new CrossProfileDiagramDTO(UUID.randomUUID(), null);

        var result = (SvelteFlowDTO) service.renderCrossProfileDiagramUML(diagram, DATASET_NAME);

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_singleClass_createsNodeWithCorrectData() {
        var attribute =
                AttributeDTO.builder()
                        .label("attribute1")
                        .dataType(new DataTypeDTO("string", "xsd"))
                        .multiplicity("M:1..1")
                        .build();

        var mergedClass =
                classBuilder("ClassA")
                        .stereotypes(List.of(new CIMSStereotype(CIMStereotypes.concreteString)))
                        .attributes(List.of(graphSourced(attribute)))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(mergedClass), DATASET_NAME);

        assertThat(result.getNodes()).hasSize(1);
        var node = result.getNodes().getFirst();
        assertThat(node.getId()).isEqualTo(mergedClass.getUuid());
        assertThat(node.getType()).isEqualTo("class");
        assertThat(node.getPosition().getX()).isZero();
        assertThat(node.getPosition().getY()).isZero();
        assertThat(node.getData().getLabel()).isEqualTo("ClassA");
        // concrete class -> no "abstract" stereotype, concrete itself filtered out
        assertThat(node.getData().getStereotypes()).isEmpty();
        assertThat(node.getData().getAttributes()).hasSize(1);
        var attrDTO = node.getData().getAttributes().getFirst();
        assertThat(attrDTO.getLabel()).isEqualTo("attribute1");
        assertThat(attrDTO.getType()).isEqualTo("string");
        assertThat(attrDTO.getMultiplicity()).isEqualTo("M:1..1");
        assertThat(attrDTO.getGraphUri()).isEqualTo(GRAPH_URI);
        assertThat(attrDTO.getColor()).isEqualTo(GRAPH_COLOR);
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_nonConcreteClass_addsAbstractStereotype() {
        var mergedClass = classBuilder("AbstractClass").build();
        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(mergedClass), DATASET_NAME);

        assertThat(result.getNodes().getFirst().getData().getStereotypes())
                .containsExactly("abstract");
    }

    @Test
    void renderCrossProfileDiagramUML_enumerationClass_mapsToLocalNameAndSortsStereotypes() {
        var mergedClass =
                classBuilder("MyEnum")
                        .stereotypes(List.of(new CIMSStereotype(CIMStereotypes.enumerationString)))
                        .build();
        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(mergedClass), DATASET_NAME);

        // not concrete -> "abstract" added, enumeration URI mapped to local name -> sorted
        assertThat(result.getNodes().getFirst().getData().getStereotypes())
                .containsExactly("abstract", "enumeration");
    }

    @Test
    void renderCrossProfileDiagramUML_classWithEnumEntries_createsEnumEntryDTOs() {
        var entry = EnumEntryDTO.builder().label("entry1").build();
        var mergedClass = classBuilder("MyEnum").enumEntries(List.of(graphSourced(entry))).build();
        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(mergedClass), DATASET_NAME);

        var enumEntries = result.getNodes().getFirst().getData().getEnumEntries();
        assertThat(enumEntries).hasSize(1);
        assertThat(enumEntries.getFirst().getLabel()).isEqualTo("entry1");
        assertThat(enumEntries.getFirst().getGraphUri()).isEqualTo(GRAPH_URI);
        assertThat(enumEntries.getFirst().getColor()).isEqualTo(GRAPH_COLOR);
    }

    @Test
    void renderCrossProfileDiagramUML_superClassInDiagram_createsInheritanceEdge() {
        var subClass = classBuilder("SubClass");
        var superClass = classBuilder("SuperClass").build();

        var subClassBuilt =
                subClass.superClasses(
                                List.of(graphSourced(new SuperClassDTO("prefix", "SuperClass"))))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(subClassBuilt, superClass), DATASET_NAME);

        assertThat(result.getEdges()).hasSize(1);
        var edge = result.getEdges().getFirst();
        assertThat(edge.getType()).isEqualTo("inheritance");
        assertThat(edge.getSource()).isEqualTo(subClassBuilt.getUuid());
        assertThat(edge.getTarget()).isEqualTo(superClass.getUuid());
        assertThat(edge.getData()).isNull();
    }

    @Test
    void renderCrossProfileDiagramUML_superClassNotInDiagram_createsNoInheritanceEdge() {
        var subClass =
                classBuilder("SubClass")
                        .superClasses(List.of(graphSourced(new SuperClassDTO("prefix", "Unknown"))))
                        .build();
        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(subClass), DATASET_NAME);

        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_superClassInDiagram_addsInheritedPropertiesToNode() {
        var inheritedAttribute =
                AttributeDTO.builder()
                        .label("inheritedAttr")
                        .dataType(new DataTypeDTO("string", "xsd"))
                        .multiplicity("M:1..1")
                        .build();

        var superClass =
                classBuilder("SuperClass")
                        .attributes(List.of(graphSourced(inheritedAttribute)))
                        .build();
        var subClass =
                classBuilder("SubClass")
                        .superClasses(
                                List.of(graphSourced(new SuperClassDTO("prefix", "SuperClass"))))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(subClass, superClass), DATASET_NAME);

        var subNode =
                result.getNodes().stream()
                        .filter(n -> n.getId().equals(subClass.getUuid()))
                        .findFirst()
                        .orElseThrow();
        assertThat(subNode.getData().getSuperClasses()).hasSize(1);
        var renderedSuper = subNode.getData().getSuperClasses().getFirst();
        assertThat(renderedSuper.getUuid()).isEqualTo(superClass.getUuid());
        assertThat(renderedSuper.getLabel()).isEqualTo("SuperClass");
        assertThat(renderedSuper.getAttributes()).hasSize(1);
        var inherited = renderedSuper.getAttributes().getFirst();
        assertThat(inherited.getLabel()).isEqualTo("inheritedAttr");
        assertThat(inherited.getType()).isEqualTo("string");
        assertThat(inherited.getColor()).isEqualTo(GRAPH_COLOR);
    }

    @Test
    void renderCrossProfileDiagramUML_transitiveSuperClasses_areCollectedInOrder() {
        var grandParent = classBuilder("GrandParent").build();
        var parent =
                classBuilder("Parent")
                        .superClasses(
                                List.of(graphSourced(new SuperClassDTO("prefix", "GrandParent"))))
                        .build();
        var child =
                classBuilder("Child")
                        .superClasses(List.of(graphSourced(new SuperClassDTO("prefix", "Parent"))))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(child, parent, grandParent), DATASET_NAME);

        var childNode =
                result.getNodes().stream()
                        .filter(n -> n.getId().equals(child.getUuid()))
                        .findFirst()
                        .orElseThrow();
        assertThat(childNode.getData().getSuperClasses())
                .extracting("label")
                .containsExactly("Parent", "GrandParent");
    }

    @Test
    void renderCrossProfileDiagramUML_superClassNotInDiagram_stillRendersLabelWithoutMembers() {
        var subClass =
                classBuilder("SubClass")
                        .superClasses(
                                List.of(graphSourced(new SuperClassDTO("prefix", "ExternalBase"))))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(subClass), DATASET_NAME);

        var superClasses = result.getNodes().getFirst().getData().getSuperClasses();
        assertThat(superClasses).hasSize(1);
        assertThat(superClasses.getFirst().getLabel()).isEqualTo("ExternalBase");
        assertThat(superClasses.getFirst().getUuid()).isNull();
        assertThat(superClasses.getFirst().getAttributes()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_cyclicInheritance_terminatesWithoutDuplicates() {
        var classA =
                classBuilder("CycleA")
                        .superClasses(List.of(graphSourced(new SuperClassDTO("prefix", "CycleB"))))
                        .build();
        var classB =
                classBuilder("CycleB")
                        .superClasses(List.of(graphSourced(new SuperClassDTO("prefix", "CycleA"))))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(classA, classB), DATASET_NAME);

        var nodeA =
                result.getNodes().stream()
                        .filter(n -> n.getId().equals(classA.getUuid()))
                        .findFirst()
                        .orElseThrow();
        assertThat(nodeA.getData().getSuperClasses()).extracting("label").containsExactly("CycleB");
    }

    @Test
    void renderCrossProfileDiagramUML_association_createsAssociationEdgeWithCleanedMultiplicity() {
        var sourceClass = classBuilder("Source");
        var targetClass = classBuilder("Target").build();

        var fromAssoc =
                AssociationDTO.builder()
                        .multiplicity("M:0..n")
                        .range(new DataTypeDTO("Target", "prefix", DataTypeDTO.Type.RANGE))
                        .associationUsed(true)
                        .build();
        var toAssoc =
                AssociationDTO.builder().multiplicity("M:1..1").associationUsed(false).build();
        var pair = new AssociationPairDTO(fromAssoc, toAssoc);

        var sourceClassBuilt = sourceClass.associationPairs(List.of(graphSourced(pair))).build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(sourceClassBuilt, targetClass), DATASET_NAME);

        assertThat(result.getEdges()).hasSize(1);
        var edge = result.getEdges().getFirst();
        assertThat(edge.getType()).isEqualTo("association");
        assertThat(edge.getSource()).isEqualTo(sourceClassBuilt.getUuid());
        assertThat(edge.getTarget()).isEqualTo(targetClass.getUuid());
        assertThat(edge.getData().getToMultiplicity()).isEqualTo("1..1");
        assertThat(edge.getData().getFromMultiplicity()).isEqualTo("0..n");
        // useToAssociation <- from.isAssociationUsed(); useFromAssociation <-
        // to.isAssociationUsed()
        assertThat(edge.getData().isUseToAssociation()).isTrue();
        assertThat(edge.getData().isUseFromAssociation()).isFalse();
        assertThat(edge.getData().getGraphUri()).isEqualTo(GRAPH_URI);
        assertThat(edge.getData().getColor()).isEqualTo(GRAPH_COLOR);
    }

    @Test
    void renderCrossProfileDiagramUML_associationTargetNotInDiagram_createsNoAssociationEdge() {
        var fromAssoc =
                AssociationDTO.builder()
                        .multiplicity("M:0..n")
                        .range(new DataTypeDTO("Unknown", "prefix", DataTypeDTO.Type.RANGE))
                        .build();
        var toAssoc = AssociationDTO.builder().multiplicity("M:1..1").build();
        var pair = new AssociationPairDTO(fromAssoc, toAssoc);

        var sourceClass =
                classBuilder("Source").associationPairs(List.of(graphSourced(pair))).build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(sourceClass), DATASET_NAME);

        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    void renderCrossProfileDiagramUML_reciprocalAssociation_deduplicatesToSingleEdge() {
        var classAUuid = UUID.randomUUID();
        var classBUuid = UUID.randomUUID();

        var aToB =
                new AssociationPairDTO(
                        AssociationDTO.builder()
                                .multiplicity("M:0..n")
                                .range(new DataTypeDTO("ClassB", "prefix", DataTypeDTO.Type.RANGE))
                                .build(),
                        AssociationDTO.builder().multiplicity("M:1..1").build());

        var bToA =
                new AssociationPairDTO(
                        AssociationDTO.builder()
                                .multiplicity("M:1..1")
                                .range(new DataTypeDTO("ClassA", "prefix", DataTypeDTO.Type.RANGE))
                                .build(),
                        AssociationDTO.builder().multiplicity("M:0..n").build());

        var classA =
                MergedClassDTO.builder()
                        .uuid(classAUuid)
                        .classUri("http://example.com/ontology#ClassA")
                        .label("ClassA")
                        .associationPairs(List.of(graphSourced(aToB)))
                        .build();
        var classB =
                MergedClassDTO.builder()
                        .uuid(classBUuid)
                        .classUri("http://example.com/ontology#ClassB")
                        .label("ClassB")
                        .associationPairs(List.of(graphSourced(bToA)))
                        .build();

        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(
                                diagramOf(classA, classB), DATASET_NAME);

        assertThat(result.getEdges()).hasSize(1);
        assertThat(result.getEdges().getFirst().getType()).isEqualTo("association");
    }

    @Test
    void renderCrossProfileDiagramUML_associationWithNullFrom_isSkipped() {
        var pair = new AssociationPairDTO(null, AssociationDTO.builder().build());
        var sourceClass =
                classBuilder("Source").associationPairs(List.of(graphSourced(pair))).build();
        when(fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(any(), any()))
                .thenReturn(null);

        var result =
                (SvelteFlowDTO)
                        service.renderCrossProfileDiagramUML(diagramOf(sourceClass), DATASET_NAME);

        assertThat(result.getEdges()).isEmpty();
    }
}
