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

package org.rdfarchitect.cim.data;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.data.dto.CIMAssociation;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.CIMCollection;
import org.rdfarchitect.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.cim.data.dto.CIMPackage;
import org.rdfarchitect.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.cim.data.dto.relations.RDFType;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.cim.rendering.mermaid.RenderCIMCollectionMermaidService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CIMCollectionMermaidRendererImplTest {

    private static final String URI_PREFIX = "http://example.com#";

    private enum AssociationUsed {
        YES,
        NO
    }

    private CIMCollection cimCollection;

    private RenderCIMCollectionUseCase renderer;

    private void addPackage(String packageLabel) {
        var uri = new URI(URI_PREFIX + packageLabel);
        var label = new RDFSLabel(packageLabel);

        var cimPackage = CIMPackage.builder()
                                   .uuid(UUID.randomUUID())
                                   .uri(uri)
                                   .label(label)
                                   .build();
        cimCollection.getPackages().add(cimPackage);
    }

    private void addClass(String packageLabel, String classLabel) {
        var uri = new URI(URI_PREFIX + classLabel);
        var label = new RDFSLabel(classLabel);

        var cimClass = CIMClass.builder()
                               .uuid(UUID.randomUUID())
                               .uri(uri)
                               .label(label)
                               .superClass(null)
                               .belongsToCategory(null);
        if (packageLabel != null) {
            cimClass.belongsToCategory(new CIMSBelongsToCategory(new URI(URI_PREFIX + packageLabel), new RDFSLabel(packageLabel), UUID.randomUUID()));
        }
        cimCollection.getClasses().add(cimClass.build());
    }

    private void addAttribute(String classLabel, String attributeLabel, XSDDatatype datatype) {
        var uri = new URI(URI_PREFIX + classLabel + "." + attributeLabel);
        var label = new RDFSLabel(attributeLabel);

        var dataTypeUri = new URI(datatype.getURI());
        var dataType = new CIMSPrimitiveDataType(dataTypeUri, new RDFSLabel(dataTypeUri.getSuffix()));

        var attribute = CIMAttribute.builder()
                                    .uuid(UUID.randomUUID())
                                    .uri(uri)
                                    .label(label)
                                    .dataType(dataType)
                                    .domain(new RDFSDomain(new URI(URI_PREFIX + classLabel), new RDFSLabel(classLabel)))
                                    .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                                    .stereotype(new CIMSStereotype(CIMStereotypes.attribute.getURI()))
                                    .build();

        cimCollection.getAttributes().add(attribute);
    }

    private void addAssociation(String domainLabel, String rangeLabel, AssociationUsed fromAssociationUsed, AssociationUsed toAssociationUsed) {
        var fromUri = new URI(URI_PREFIX + domainLabel + "." + rangeLabel);
        var toUri = new URI(URI_PREFIX + rangeLabel + "." + domainLabel);
        var fromLabel = new RDFSLabel(fromUri.getSuffix());
        var toLabel = new RDFSLabel(toUri.getSuffix());

        var domainUri = new URI(URI_PREFIX + domainLabel);
        var domainRDFSLabel = new RDFSLabel(domainLabel);

        var rangeUri = new URI(URI_PREFIX + rangeLabel);
        var rangeRDFSLabel = new RDFSLabel(rangeLabel);

        var from = CIMAssociation.builder()
                                 .uuid(UUID.randomUUID())
                                 .uri(fromUri)
                                 .label(fromLabel)
                                 .domain(new RDFSDomain(domainUri, domainRDFSLabel))
                                 .range(new RDFSRange(rangeUri, rangeRDFSLabel))
                                 .inverseRoleName(new CIMSInverseRoleName(toUri))
                                 .associationUsed(new CIMSAssociationUsed(fromAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                                 .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                                 .build();

        var to = CIMAssociation.builder()
                               .uuid(UUID.randomUUID())
                               .uri(toUri)
                               .label(toLabel)
                               .domain(new RDFSDomain(rangeUri, rangeRDFSLabel))
                               .range(new RDFSRange(domainUri, domainRDFSLabel))
                               .inverseRoleName(new CIMSInverseRoleName(fromUri))
                               .associationUsed(new CIMSAssociationUsed(toAssociationUsed == AssociationUsed.YES ? "Yes" : "No"))
                               .multiplicity(new CIMSMultiplicity(URI_PREFIX + "M:1"))
                               .build();

        cimCollection.getAssociations().add(from);
        cimCollection.getAssociations().add(to);
    }

    private void addEnum(String packageLabel, String enumLabel) {
        var uri = new URI(URI_PREFIX + enumLabel);
        var label = new RDFSLabel(enumLabel);

        var cimEnum = CIMClass.builder()
                              .uuid(UUID.randomUUID())
                              .uri(uri)
                              .label(label)
                              .belongsToCategory(null);

        if (packageLabel != null) {
            cimEnum.belongsToCategory(new CIMSBelongsToCategory(new URI(URI_PREFIX + packageLabel), new RDFSLabel(packageLabel), UUID.randomUUID()));
        }
        cimEnum.stereotypes(new ArrayList<>(List.of(new CIMSStereotype(CIMStereotypes.enumeration.getURI()))));
        cimCollection.getEnums().add(cimEnum.build());
    }

    private void addEnumEntry(String enumLabel, String enumEntryLabel) {
        var enumEntry = CIMEnumEntry.builder()
                                    .uuid(UUID.randomUUID())
                                    .uri(new URI(URI_PREFIX + enumEntryLabel))
                                    .label(new RDFSLabel(enumEntryLabel))
                                    .type(new RDFType(new URI(URI_PREFIX + enumLabel), new RDFSLabel(enumLabel)))
                                    .build();

        cimCollection.getEnumEntries().add(enumEntry);
    }

    private float countOccurrences(String str, String subStr) {
        return (str.length() - str.replace(subStr, "").length()) / (float) subStr.length();
    }

    @BeforeEach
    void setUp() {
        cimCollection = new CIMCollection();
        renderer = new RenderCIMCollectionMermaidService();
    }

    @Test
    void renderUML_emptyCollection_emptyMermaidDiagram() {
        //Arrange

        //Act
        var result = renderer.renderUML(cimCollection);

        //Assert
        assertThat(result).endsWith("classDiagram\n");
    }

    @Test
    void renderUML_nullCollection_throwsException() {
        //Assert/Act
        assertThatException().isThrownBy(() -> renderer.renderUML(null));
    }

    @Test
    void renderUML_collectionWithOnlyPackages_emptyMermaidDiagram() {
        //Arrange
        addPackage("package_package1");
        addPackage("package_package2");

        //Act
        var result = renderer.renderUML(cimCollection);

        //Assert
        assertThat(result).endsWith("classDiagram\n");
    }

    @Test
    void renderUML_collectionWithOneAbstractClass_MermaidStringContainingOneClass() {
        //Arrange
        addClass(null, "class1");
        var class1 = cimCollection.getClasses().iterator().next();

        //Act
        var result = renderer.renderUML(cimCollection);

        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(result).containsSubsequence("click `" + match + "` call getClassInformation(\"" + class1.getUuid() + "\")");
            assertThat(result).containsSubsequence("class `" + match + "`[\"class1\"]{\n        <<abstract>>\n    }");
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_collectionWithOneConcreteClass_MermaidStringContainingOneClass() {
        //Arrange
        addClass(null, "class1");
        var class1 = cimCollection.getClasses().iterator().next();
        class1.setStereotypes(new ArrayList<>(List.of(new CIMSStereotype(CIMStereotypes.concrete.getURI()))));

        //Act
        var result = renderer.renderUML(cimCollection);

        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(result).containsSubsequence("click `" + match + "` call getClassInformation(\"" + class1.getUuid() + "\")");
            assertThat(result).containsSubsequence("class `" + match + "`[\"class1\"]{\n    }");
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_collection_WithOneClassContainingAttributes_MermaidStringContainingOneClassWithAttributes() {
        //Arrange
        addClass(null, "class1");
        var class1 = cimCollection.getClasses().iterator().next();
        addAttribute("class1", "attribute1", XSDDatatype.XSDstring);
        addAttribute("class1", "attribute2", XSDDatatype.XSDint);

        //Act
        var result = renderer.renderUML(cimCollection);

        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(result).containsSubsequence("click `" + match + "` call getClassInformation(\"" + class1.getUuid() + "\")");
            assertThat(result).containsSubsequence("class `" + match + "`[\"class1\"]{\n        <<abstract>>\n        attribute1: string [1]\n        attribute2: int [1]\n    }");
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_collectionWithMultipleClasses_MermaidStringContainingMultipleClasses() {
        //Arrange
        addClass(null, "class1");
        addClass(null, "class2");
        addClass(null, "class3");

        //Act
        var result = renderer.renderUML(cimCollection);
        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);


        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_collectionWithOneAssociation_MermaidStringContainingOneAssociation() {
        //Arrange
        addClass(null, "class1");
        addClass(null, "class2");
        addAssociation("class1", "class2", AssociationUsed.YES, AssociationUsed.YES);

        //Act
        var result = renderer.renderUML(cimCollection);
        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(result).containsSubsequence("click `");
        assertThat(result).containsSubsequence("` \"M:1\" <--> \"M:1\" `");
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(countOccurrences(result, match)).isEqualTo(4);
        }
    }

    @Test
    void renderUML_collectionWithClassesInPackages_MermaidStringContainingClassesInPackages() {
        //Arrange
        addPackage("package_package1");
        addPackage("package_package2");
        addClass("package_package1", "class1");
        addClass("package_package2", "class2");

        //Act
        var result = renderer.renderUML(cimCollection);
        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_collectionWithEnum_MermaidStringContainingEnum() {
        //Arrange
        addEnum(null, "enum1");
        var enum1 = cimCollection.getEnums().iterator().next();

        //Act
        var result = renderer.renderUML(cimCollection);
        Pattern pattern = Pattern.compile("[a-f0-9-]{36}");
        Matcher matcher = pattern.matcher(result);

        //Assert
        assertThat(matcher.find()).isTrue();
        matcher.reset();
        while (matcher.find()) {
            var match = matcher.group();
            assertThat(result).containsSubsequence("click `" + match + "` call getClassInformation(\"" + enum1.getUuid() + "\")");
            assertThat(result).containsSubsequence("class `" + match + "`[\"enum1\"]{\n        <<abstract, enumeration>>\n    }");
            assertThat(countOccurrences(result, match)).isEqualTo(3);
        }
    }

    @Test
    void renderUML_complexCollection_ContainsAllElements() {
        //Arrange
        addPackage("package_package1");
        addPackage("package_package2");
        addClass("package_package1", "class1");
        addClass("package_package2", "class2");
        var classesIterator = cimCollection.getClasses().iterator();
        var class1 = classesIterator.next();
        var class2 = classesIterator.next();
        class1.setSuperClass(new RDFSSubClassOf(class2.getUri(), class2.getLabel()));
        addAttribute("class1", "attribute1", XSDDatatype.XSDstring);
        addAttribute("class1", "attribute2", XSDDatatype.XSDint);
        addAssociation("class1", "class2", AssociationUsed.YES, AssociationUsed.YES);
        addAssociation("class1", "enum1", AssociationUsed.YES, AssociationUsed.NO);
        addEnum("package_package1", "enum1");
        addEnumEntry("enum1", "enumEntry1");
        addEnumEntry("enum1", "enumEntry2");

        //Act
        var result = renderer.renderUML(cimCollection);

        //Assert
        assertThat(result.replaceAll("[a-f0-9-]{36}", "UUID")).endsWith("""
                                                                                  classDiagram
                                                                                      namespace package_package1{
                                                                                          class `UUID`["class1"]{
                                                                                              <<abstract>>
                                                                                              attribute1: string [1]
                                                                                              attribute2: int [1]
                                                                                          }
                                                                                          class `UUID`["enum1"]{
                                                                                              <<abstract, enumeration>>
                                                                                              enumEntry1
                                                                                              enumEntry2
                                                                                          }
                                                                                      }
                                                                                      namespace package_package2{
                                                                                          class `UUID`["class2"]{
                                                                                              <<abstract>>
                                                                                          }
                                                                                      }
                                                                                      `UUID` --|> `UUID`
                                                                                      `UUID` "M:1" <--> "M:1" `UUID`
                                                                                      `UUID` "M:1" --> "M:1" `UUID`
                                                                                      click `UUID` call getClassInformation("UUID")
                                                                                      click `UUID` call getClassInformation("UUID")
                                                                                      click `UUID` call getClassInformation("UUID")
                                                                                  """);
    }
}