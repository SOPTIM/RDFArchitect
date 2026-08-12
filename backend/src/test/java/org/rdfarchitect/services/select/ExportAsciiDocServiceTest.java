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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ExportAsciiDocServiceTest {

    private static final GraphIdentifier GRAPH_IDENTIFIER =
            new GraphIdentifier("dataset", "http://example.com/EQ");

    @Mock private GetClassListUseCase getClassListUseCase;

    @InjectMocks private ExportAsciiDocService exportAsciiDocService;

    private String exportAsciiDoc(List<ClassUMLAdaptedDTO> classList) {
        return exportAsciiDoc(GRAPH_IDENTIFIER, classList, false);
    }

    private String exportAsciiDocWithEmbeddedDiagrams(List<ClassUMLAdaptedDTO> classList) {
        return exportAsciiDoc(GRAPH_IDENTIFIER, classList, true);
    }

    private String exportAsciiDoc(
            GraphIdentifier graphIdentifier, List<ClassUMLAdaptedDTO> classList) {
        return exportAsciiDoc(graphIdentifier, classList, false);
    }

    private String exportAsciiDoc(
            GraphIdentifier graphIdentifier,
            List<ClassUMLAdaptedDTO> classList,
            boolean embedDiagrams) {
        when(getClassListUseCase.getFullClassList(graphIdentifier)).thenReturn(classList);
        byte[] result =
                exportAsciiDocService.exportGraphAsAsciiDoc(graphIdentifier, "png", embedDiagrams);
        return new String(result, StandardCharsets.UTF_8);
    }

    private ClassUMLAdaptedDTO.ClassUMLAdaptedDTOBuilder classBuilder(String label) {
        return ClassUMLAdaptedDTO.builder().uuid(UUID.randomUUID()).label(label);
    }

    @Test
    void exportGraphAsAsciiDoc_emptyClassList_containsStereotypeListOnly() {
        var adoc = exportAsciiDoc(List.of());

        assertThat(adoc).startsWith("List of stereotypes to categorize subProfiles:");
        assertThat(adoc).contains("* " + CIMStereotypes.entsoeString);
        assertThat(adoc).doesNotContain("== Concrete Classes");
    }

    @Test
    void exportGraphAsAsciiDoc_concreteClassWithoutStereotype_isListedAsConcrete() {
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("== Concrete Classes\n");
        assertThat(adoc).contains("[[EQ_MyClass]]\n=== MyClass\n");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithoutConcreteStereotype_isListedAsAbstract() {
        var clazz = classBuilder("AbstractClass").stereotypes(List.of()).build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("== Abstract Classes");
    }

    @Test
    void exportGraphAsAsciiDoc_enumeration_isListedInOwnSectionBehindTheClasses() {
        var enumeration =
                classBuilder("MyEnum")
                        .stereotypes(List.of(CIMStereotypes.enumerationString))
                        .build();
        var concreteClass =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();
        var abstractClass = classBuilder("AbstractClass").stereotypes(List.of()).build();

        var adoc = exportAsciiDoc(List.of(enumeration, concreteClass, abstractClass));

        assertThat(adoc)
                .containsSubsequence(
                        "== Concrete Classes",
                        "[[EQ_MyClass]]",
                        "== Abstract Classes",
                        "[[EQ_AbstractClass]]",
                        "== Enumerations",
                        "[[EQ_MyEnum]]");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithKnownStereotype_isGroupedUnderStereotypeSection() {
        var clazz =
                classBuilder("EntsoeClass")
                        .stereotypes(
                                List.of(CIMStereotypes.concreteString, CIMStereotypes.entsoeString))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("== Classes (" + CIMStereotypes.entsoeString + ")");
        assertThat(adoc).contains("=== EntsoeClass (" + CIMStereotypes.entsoeString + ")");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithComment_containsCommentAsParagraph() {
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .comment("First line\nsecond line")
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("\nFirst line second line\n");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithPackage_containsDiagramLink() {
        var packageUuid = UUID.randomUUID();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .belongsToCategory(
                                BelongsToCategoryDTO.builder()
                                        .uuid(packageUuid)
                                        .label("Package_Core")
                                        .build())
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("link:images/" + packageUuid + ".png[Package++_++Core]");
    }

    @Test
    void exportGraphAsAsciiDoc_embeddedDiagrams_showsTheDiagramInsteadOfLinkingIt() {
        var packageUuid = UUID.randomUUID();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .belongsToCategory(
                                BelongsToCategoryDTO.builder()
                                        .uuid(packageUuid)
                                        .label("Core")
                                        .build())
                        .build();

        var adoc = exportAsciiDocWithEmbeddedDiagrams(List.of(clazz));

        assertThat(adoc).contains(".Core\nimage::images/" + packageUuid + ".png[Core]");
        assertThat(adoc).doesNotContain("link:images/");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithAttribute_containsTableRow() {
        var attribute =
                AttributeDTO.builder()
                        .label("myAttribute")
                        .domain("MyClass")
                        .multiplicity("M:1..1")
                        .dataType(new DataTypeDTO("String", "example"))
                        .comment("attribute comment")
                        .build();
        var stringClass =
                classBuilder("String").stereotypes(List.of(CIMStereotypes.concreteString)).build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz, stringClass));

        assertThat(adoc).contains("==== Native Members");
        assertThat(adoc).contains("[cols=\"4*\"]\n|===\n");
        assertThat(adoc)
                .contains("|myAttribute |M:1..1 |xref:EQ_String[String] |attribute comment\n");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithAssociation_containsTableRow() {
        var from =
                AssociationDTO.builder()
                        .label("myRole")
                        .domain("MyClass")
                        .multiplicity("M:0..n")
                        .range(new DataTypeDTO("OtherClass", "example"))
                        .comment("role comment")
                        .build();
        var other =
                classBuilder("OtherClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .associationPairs(List.of(new AssociationPairDTO(from, null)))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz, other));

        assertThat(adoc)
                .contains("|myRole |M:0..n |xref:EQ_OtherClass[OtherClass] |role comment\n");
    }

    @Test
    void exportGraphAsAsciiDoc_typeOutsideOfTheExport_isNotLinked() {
        var attribute =
                AttributeDTO.builder()
                        .label("myAttribute")
                        .multiplicity("M:1..1")
                        .dataType(new DataTypeDTO("NotExported", "example"))
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("|myAttribute |M:1..1 |NotExported |\n");
        assertThat(adoc).doesNotContain("xref:EQ_NotExported");
    }

    @Test
    void exportGraphAsAsciiDoc_enumeration_containsEnumerationValues() {
        var entry =
                EnumEntryDTO.builder()
                        .prefix("example")
                        .label("myEntry")
                        .comment("entry comment")
                        .build();
        var clazz =
                classBuilder("MyEnum")
                        .stereotypes(List.of(CIMStereotypes.enumerationString))
                        .enumEntries(List.of(entry))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("==== Enumeration Values");
        assertThat(adoc).contains("[cols=\"2*\"]\n|===\n|myEntry |entry comment\n|===");
    }

    @Test
    void exportGraphAsAsciiDoc_classWithSuperClass_containsInheritedMembers() {
        var parentAttribute =
                AttributeDTO.builder()
                        .label("inheritedAttribute")
                        .multiplicity("M:0..1")
                        .dataType(new DataTypeDTO("String", "example"))
                        .build();
        var parentAssociation =
                AssociationDTO.builder()
                        .label("inheritedRole")
                        .multiplicity("M:0..n")
                        .range(new DataTypeDTO("String", "example"))
                        .build();
        var parent =
                classBuilder("ParentClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(parentAttribute))
                        .associationPairs(List.of(new AssociationPairDTO(parentAssociation, null)))
                        .build();
        var stringClass =
                classBuilder("String").stereotypes(List.of(CIMStereotypes.concreteString)).build();
        var child =
                classBuilder("ChildClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(ClassUMLAdaptedDTO.builder().label("ParentClass").build())
                        .build();

        var adoc = exportAsciiDoc(List.of(parent, child, stringClass));

        assertThat(adoc).contains("==== Inherited Members");
        assertThat(adoc).contains("_Inheritance pass: ->ParentClass_");
        assertThat(adoc).contains("[cols=\"5*\"]\n|===\n");
        assertThat(adoc)
                .contains(
                        "|inheritedAttribute |M:0..1 |xref:EQ_String[String] | |see"
                                + " xref:EQ_ParentClass[ParentClass]\n");
        assertThat(adoc)
                .contains(
                        "|inheritedRole |M:0..n |xref:EQ_String[String] | |see"
                                + " xref:EQ_ParentClass[ParentClass]\n");
    }

    @Test
    void exportGraphAsAsciiDoc_unresolvableSuperClass_doesNotContainInheritedMembers() {
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .superClass(ClassUMLAdaptedDTO.builder().label("Unknown").build())
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).doesNotContain("==== Inherited Members");
    }

    @Test
    void exportGraphAsAsciiDoc_commentWithAsciiDocSyntax_isEscaped() {
        var attribute =
                AttributeDTO.builder()
                        .label("myAttribute")
                        .multiplicity("M:1..1")
                        .comment("a | b and *stars* and _underscores_")
                        .build();
        var clazz =
                classBuilder("MyClass")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .comment(".leading dot")
                        .attributes(List.of(attribute))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz));

        assertThat(adoc).contains("{empty}.leading dot");
        assertThat(adoc)
                .contains(
                        "|a \\| b and ++*++stars++*++ and ++_++underscores++_++\n"
                                .replace("\n", ""));
    }

    @Test
    void exportGraphAsAsciiDoc_labelWithSpaces_usesSameIdentifierForAnchorAndReference() {
        var attribute =
                AttributeDTO.builder()
                        .label("myAttribute")
                        .multiplicity("M:1..1")
                        .dataType(new DataTypeDTO("My Class", "example"))
                        .build();
        var target =
                classBuilder("My Class")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .build();
        var clazz =
                classBuilder("Other")
                        .stereotypes(List.of(CIMStereotypes.concreteString))
                        .attributes(List.of(attribute))
                        .build();

        var adoc = exportAsciiDoc(List.of(clazz, target));

        assertThat(adoc).contains("[[EQ_My_Class]]");
        assertThat(adoc).contains("xref:EQ_My_Class[My Class]");
    }

    @Test
    void exportGraphAsAsciiDoc_defaultGraph_usesDefaultAnchorPrefix() {
        var graphIdentifier = new GraphIdentifier("dataset", "default");
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var adoc = exportAsciiDoc(graphIdentifier, List.of(clazz));

        assertThat(adoc).contains("[[default_MyClass]]");
    }

    @Test
    void exportGraphAsAsciiDoc_graphNameStartingWithDigit_producesValidIdentifier() {
        var graphIdentifier = new GraphIdentifier("dataset", "http://example.com/ns/3.0");
        var clazz =
                classBuilder("MyClass").stereotypes(List.of(CIMStereotypes.concreteString)).build();

        var adoc = exportAsciiDoc(graphIdentifier, List.of(clazz));

        assertThat(adoc).contains("[[_3_0_MyClass]]");
    }
}
