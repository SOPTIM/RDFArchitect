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

package org.rdfarchitect.services.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.api.dto.BelongsToCategoryDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ExportHTMLServiceTest {

    private static final GraphIdentifier GRAPH_IDENTIFIER =
            new GraphIdentifier("dataset", "http://example.com/graph1");

    @Mock private GetClassListUseCase getClassListUseCase;

    @InjectMocks private ExportHTMLService exportHTMLService;

    private String exportHtml(List<ClassUMLAdaptedDTO> classList) {
        when(getClassListUseCase.getFullClassList(GRAPH_IDENTIFIER)).thenReturn(classList);
        byte[] result = exportHTMLService.exportGraphAsHTML(GRAPH_IDENTIFIER);
        return new String(result, StandardCharsets.UTF_8);
    }

    private ClassUMLAdaptedDTO.ClassUMLAdaptedDTOBuilder classBuilder(String label) {
        return ClassUMLAdaptedDTO.builder().uuid(UUID.randomUUID()).label(label);
    }

    @Test
    void exportGraphAsHTML_emptyClassList_returnsHtmlWithHeaderAndIntroOnly() {
        var html = exportHtml(List.of());

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<title>Profile Documentation</title>");
        assertThat(html).contains("List of stereotypes to categorize subProfiles");
        assertThat(html).doesNotContain("<h1>Classes</h1>");
        assertThat(html).doesNotContain("<h1>Abstract Classes</h1>");
    }

    @Test
    void exportGraphAsHTML_concreteClassWithoutStereotype_isListedInGeneralClassesSection() {
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Classes</h1>");
        assertThat(html).contains("id=\"MyClass\"");
        assertThat(html).contains("h2 class=\"concrete\"");
        assertThat(html).contains(">MyClass<");
    }

    @Test
    void exportGraphAsHTML_classWithoutConcreteStereotype_isListedAsAbstract() {
        var clazz = classBuilder("AbstractClass").stereotypes(List.of()).build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Abstract Classes</h1>");
        assertThat(html).contains("h2 class=\"abstract\"");
    }

    @Test
    void exportGraphAsHTML_classWithKnownStereotype_isGroupedUnderStereotypeSection() {
        var clazz =
                classBuilder("EntsoeClass")
                        .stereotypes(
                                List.of(CIMStereotypes.concreteString, CIMStereotypes.entsoeString))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Classes (" + CIMStereotypes.entsoeString + ")</h1>");
        assertThat(html).doesNotContain("<h1>Classes</h1>");
    }

    @Test
    void exportGraphAsHTML_classWithComment_containsCommentParagraph() {
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .comment("This is a comment")
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<p class=\"comment\">This is a comment</p>");
    }

    @Test
    void exportGraphAsHTML_classWithPackage_containsPackageLink() {
        var category =
                BelongsToCategoryDTO.builder().label("MyPackage").uuid(UUID.randomUUID()).build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .belongsToCategory(category)
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("images\\MyPackage.png");
        assertThat(html).contains(">MyPackage </a>");
    }

    @Test
    void exportGraphAsHTML_classWithAttribute_containsAttributeRow() {
        var attribute =
                AttributeDTO.builder()
                        .label("myAttribute")
                        .domain("MyClass")
                        .multiplicity("1..1")
                        .dataType(new DataTypeDTO("String", "xsd"))
                        .comment("attribute comment")
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h3>Native Members</h3>");
        assertThat(html).contains("id=\"MyClass.myAttribute\"");
        assertThat(html).contains(">myAttribute </p>");
        assertThat(html).contains("attribute comment");
    }

    @Test
    void exportGraphAsHTML_classWithAssociation_containsAssociationRow() {
        var to =
                AssociationDTO.builder()
                        .label("myRole")
                        .domain("MyClass")
                        .multiplicity("0..*")
                        .range(new DataTypeDTO("OtherClass", "example"))
                        .comment("role comment")
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .associationPairs(List.of(new AssociationPairDTO(null, to)))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("id=\"MyClass.myRole\"");
        assertThat(html).contains("[0..*]");
        assertThat(html).contains("href=\"#OtherClass\">OtherClass</a>");
        assertThat(html).contains("role comment");
    }

    @Test
    void exportGraphAsHTML_classWithSuperClass_containsInheritedMembersSection() {
        var parentAttribute =
                AttributeDTO.builder().label("inheritedAttribute").multiplicity("1..1").build();
        var parent =
                classBuilder("ParentClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(parentAttribute))
                        .build();
        var superClassRef = ClassUMLAdaptedDTO.builder().label("ParentClass").build();
        var child =
                classBuilder("ChildClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(superClassRef)
                        .build();

        var html = exportHtml(List.of(parent, child));

        assertThat(html).contains("<h3>Inherited Members</h3>");
        assertThat(html).contains("Inheritance pass: ->ParentClass");
        assertThat(html).contains("inheritedAttribute");
        assertThat(html)
                .contains("href=\"#ParentClass\">\nParentClass".replace("\n", ""))
                .contains("class=\"superclass\"");
    }

    @Test
    void exportGraphAsHTML_selfReferencingSuperClass_doesNotLoopInfinitely() {
        var selfRef = ClassUMLAdaptedDTO.builder().label("MyClass").build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(selfRef)
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).doesNotContain("<h3>Inherited Members</h3>");
    }
}
