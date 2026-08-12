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
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.springframework.util.StringUtils;

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
        byte[] result = exportHTMLService.exportGraphAsHTML(GRAPH_IDENTIFIER, "png");
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
        assertThat(html).doesNotContain("<h1>Concrete Classes</h1>");
        assertThat(html).doesNotContain("<h1>Abstract Classes</h1>");
    }

    @Test
    void exportGraphAsHTML_concreteClassWithoutStereotype_isListedAsConcrete() {
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Concrete Classes</h1>");
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
    void exportGraphAsHTML_enumeration_isListedInOwnSectionBehindTheClasses() {
        var enumeration =
                classBuilder("MyEnum")
                        .stereotypes(List.of(CIMStereotypes.enumerationString))
                        .build();
        var concreteClass =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();
        var abstractClass = classBuilder("AbstractClass").stereotypes(List.of()).build();

        var html = exportHtml(List.of(enumeration, concreteClass, abstractClass));

        assertThat(html)
                .containsSubsequence(
                        "<h1>Concrete Classes</h1>",
                        "id=\"MyClass\"",
                        "<h1>Abstract Classes</h1>",
                        "id=\"AbstractClass\"",
                        "<h1>Enumerations</h1>",
                        "id=\"MyEnum\"");
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
        assertThat(html).doesNotContain("<h1>Concrete Classes</h1>");
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
        var categoryUuid = UUID.randomUUID();
        var category = BelongsToCategoryDTO.builder().label("MyPackage").uuid(categoryUuid).build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .belongsToCategory(category)
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("images/" + categoryUuid + ".png");
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
        var from =
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
                        .associationPairs(List.of(new AssociationPairDTO(from, null)))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("id=\"MyClass.myRole\"");
        assertThat(html).contains("0..*");
        assertThat(html).contains("href=\"#OtherClass\">OtherClass</a>");
        assertThat(html).contains("role comment");
    }

    @Test
    void exportGraphAsHTML_classWithSuperClass_containsInheritedMembersSection() {
        var parentAttribute =
                AttributeDTO.builder()
                        .label("inheritedAttribute")
                        .domain("ParentClass")
                        .multiplicity("1..1")
                        .build();
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
    void exportGraphAsHTML_inheritedAssociation_containsRoleName() {
        var parentAssociation =
                AssociationDTO.builder()
                        .label("inheritedRole")
                        .domain("ParentClass")
                        .multiplicity("0..n")
                        .range(new DataTypeDTO("OtherClass", "example"))
                        .build();
        var parent =
                classBuilder("ParentClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .associationPairs(List.of(new AssociationPairDTO(parentAssociation, null)))
                        .build();
        var superClassRef = ClassUMLAdaptedDTO.builder().label("ParentClass").build();
        var child =
                classBuilder("ChildClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(superClassRef)
                        .build();

        var html = exportHtml(List.of(parent, child));

        assertThat(html).contains("<p class=\"inheritrole\">inheritedRole </p>");
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

    @Test
    void exportGraphAsHTML_labelAndCommentWithMarkup_areEscaped() {
        var clazz =
                classBuilder("<script>alert('x')</script>")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .comment("Comment with <b>markup</b> & special \"chars\"")
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).doesNotContain("<script>alert('x')</script>");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
        assertThat(html).doesNotContain("<b>markup</b>");
        assertThat(html)
                .contains("Comment with &lt;b&gt;markup&lt;/b&gt; &amp; special &quot;chars&quot;");
    }

    @Test
    void exportGraphAsHTML_classMatchingTwoStereotypes_isListedOnlyUnderFirstMatchingStereotype() {
        var clazz =
                classBuilder("MultiStereotypeClass")
                        .stereotypes(
                                List.of(
                                        CIMStereotypes.concreteString,
                                        CIMStereotypes.shortCircuitString,
                                        CIMStereotypes.entsoeString))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Classes (" + CIMStereotypes.shortCircuitString + ")</h1>");
        assertThat(html).doesNotContain("<h1>Classes (" + CIMStereotypes.entsoeString + ")</h1>");
    }

    @Test
    void
            exportGraphAsHTML_abstractClassWithStereotype_isGroupedUnderStereotypeNotAbstractSection() {
        var clazz =
                classBuilder("AbstractWithStereotype")
                        .stereotypes(List.of(CIMStereotypes.entsoeString))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Classes (" + CIMStereotypes.entsoeString + ")</h1>");
        assertThat(html).doesNotContain("<h1>Abstract Classes</h1>");
        assertThat(html).contains("h2 class=\"abstract\"");
    }

    @Test
    void exportGraphAsHTML_classMatchingTwoStereotypes_appearsExactlyOnceInOutput() {
        var clazz =
                classBuilder("OnceOnlyClass")
                        .stereotypes(
                                List.of(
                                        CIMStereotypes.concreteString,
                                        CIMStereotypes.shortCircuitString,
                                        CIMStereotypes.entsoeString))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(StringUtils.countOccurrencesOf(html, "id=\"OnceOnlyClass\"")).isEqualTo(1);
    }

    @Test
    void exportGraphAsHTML_enumerationClass_containsEnumEntriesInsteadOfNativeMembers() {
        var entry =
                EnumEntryDTO.builder()
                        .prefix("MyEnum")
                        .label("VALUE_A")
                        .comment("first value")
                        .build();
        var clazz =
                classBuilder("MyEnum")
                        .stereotypes(
                                List.of(
                                        CIMStereotypes.concreteString,
                                        CIMStereotypes.enumerationString))
                        .enumEntries(List.of(entry))
                        .attributes(List.of(AttributeDTO.builder().label("ignoredAttr").build()))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h3>Enumeration Values</h3>");
        assertThat(html).contains("id=\"MyEnum.VALUE_A\"");
        assertThat(html).contains("first value");
        assertThat(html).doesNotContain("<h3>Native Members</h3>");
        assertThat(html).doesNotContain("ignoredAttr");
    }

    @Test
    void exportGraphAsHTML_attributeWithoutDataType_rendersRowWithoutTypeLink() {
        var attribute =
                AttributeDTO.builder().label("attr").domain("MyClass").multiplicity("1..1").build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("id=\"MyClass.attr\"");
        assertThat(html).contains("1..1");
        assertThat(html).doesNotContain("<a href=\"#null\">");
    }

    @Test
    void exportGraphAsHTML_associationWithoutRange_rendersRowWithoutTypeLink() {
        var from =
                AssociationDTO.builder()
                        .label("myRole")
                        .domain("MyClass")
                        .multiplicity("0..1")
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .associationPairs(List.of(new AssociationPairDTO(from, null)))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("id=\"MyClass.myRole\"");
        assertThat(html).contains("0..1");
    }

    @Test
    void exportGraphAsHTML_classWithoutComment_doesNotContainCommentParagraph() {
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).doesNotContain("<p class=\"comment\">");
    }

    @Test
    void exportGraphAsHTML_classWithoutPackage_doesNotContainPackageParagraph() {
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).doesNotContain("<p class=\"package\">");
    }

    @Test
    void exportGraphAsHTML_multiLevelInheritance_resolvesFullAncestorChain() {
        var grandparent =
                classBuilder("Grandparent")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(
                                List.of(
                                        AttributeDTO.builder()
                                                .label("gpAttr")
                                                .domain("Grandparent")
                                                .build()))
                        .build();
        var parent =
                classBuilder("Parent")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(ClassUMLAdaptedDTO.builder().label("Grandparent").build())
                        .build();
        var child =
                classBuilder("Child")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(ClassUMLAdaptedDTO.builder().label("Parent").build())
                        .build();

        var html = exportHtml(List.of(grandparent, parent, child));

        assertThat(html).contains("Inheritance pass: ->Parent->Grandparent");
        assertThat(html).contains("gpAttr");
    }

    @Test
    void exportGraphAsHTML_unresolvableSuperClass_doesNotContainInheritedMembersSection() {
        var superClassRef = ClassUMLAdaptedDTO.builder().label("NotInList").build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(superClassRef)
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).doesNotContain("<h3>Inherited Members</h3>");
    }

    @Test
    void exportGraphAsHTML_threeClassInheritanceCycle_doesNotLoopInfinitely() {
        var classA = ClassUMLAdaptedDTO.builder().label("A").build();
        var classB = ClassUMLAdaptedDTO.builder().label("B").build();
        var classC = ClassUMLAdaptedDTO.builder().label("C").build();

        var a =
                classBuilder("A")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(classB)
                        .build();
        var b =
                classBuilder("B")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(classC)
                        .build();
        var c =
                classBuilder("C")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(classA)
                        .build();

        var html = exportHtml(List.of(a, b, c));

        assertThat(html).contains("Inheritance pass: ->B->C");
    }

    @Test
    void exportGraphAsHTML_classWithUnknownStereotype_isListedInGeneralClassesSection() {
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(
                                List.of(CIMStereotypes.concreteString, "SomeUnknownStereotype"))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html).contains("<h1>Concrete Classes</h1>");
        assertThat(html).doesNotContain("<h1>Classes (SomeUnknownStereotype)</h1>");
    }

    @Test
    void exportGraphAsHTML_introList_containsAllKnownStereotypesInOrder() {
        var html = exportHtml(List.of());

        var shortCircuitIndex = html.indexOf(CIMStereotypes.shortCircuitString);
        var descriptionIndex = html.indexOf(CIMStereotypes.descriptionString);
        var operationIndex = html.indexOf(CIMStereotypes.operationString);
        var europeanIndex = html.indexOf(CIMStereotypes.europeanString);
        var entsoeIndex = html.indexOf(CIMStereotypes.entsoeString);

        assertThat(shortCircuitIndex).isPositive().isLessThan(descriptionIndex);
        assertThat(descriptionIndex).isLessThan(operationIndex);
        assertThat(operationIndex).isLessThan(europeanIndex);
        assertThat(europeanIndex).isLessThan(entsoeIndex);
    }

    @Test
    void exportGraphAsHTML_multipleClassesInSameSection_areRenderedInGivenOrder() {
        var first =
                classBuilder("AClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();
        var second =
                classBuilder("BClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var html = exportHtml(List.of(first, second));

        assertThat(html.indexOf("id=\"AClass\""))
                .isPositive()
                .isLessThan(html.indexOf("id=\"BClass\""));
    }

    @Test
    void
            exportGraphAsHTML_classWithAttributesAndAssociations_rendersAttributesBeforeAssociations() {
        var attribute = AttributeDTO.builder().label("myAttribute").domain("MyClass").build();
        var from =
                AssociationDTO.builder()
                        .label("myRole")
                        .domain("MyClass")
                        .multiplicity("0..*")
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .associationPairs(List.of(new AssociationPairDTO(from, null)))
                        .build();

        var html = exportHtml(List.of(clazz));

        assertThat(html.indexOf("id=\"MyClass.myAttribute\""))
                .isPositive()
                .isLessThan(html.indexOf("id=\"MyClass.myRole\""));
    }

    @Test
    void exportGraphAsHTML_output_endsWithHtmlClosingTag() {
        var html = exportHtml(List.of());

        assertThat(html).endsWith("</html>");
    }
}
