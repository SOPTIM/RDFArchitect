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
import org.rdfarchitect.api.dto.rendering.mermaid.MermaidDTO;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RenderCIMCollectionMermaidServiceTest extends RenderCIMCollectionTestBase {

    private float countOccurrences(String str, String subStr) {
        return (str.length() - str.replace(subStr, "").length()) / (float) subStr.length();
    }

    @Test
    void renderUML_emptyCollection_emptyMermaidDiagram() {
        //Arrange

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

        //Assert
        assertThat(result).endsWith("classDiagram\n");
    }

    @Test
    void renderUML_nullCollection_throwsException() {
        //Assert/Act
        assertThatException().isThrownBy(() -> mermaidRenderer.renderUML(null, null, null));
    }

    @Test
    void renderUML_collectionWithOnlyPackages_emptyMermaidDiagram() {
        //Arrange
        addPackage("package_package1");
        addPackage("package_package2");

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

        //Assert
        assertThat(result).endsWith("classDiagram\n");
    }

    @Test
    void renderUML_collectionWithOneAbstractClass_MermaidStringContainingOneClass() {
        //Arrange
        addClass(null, "class1");
        var class1 = cimCollection.getClasses().getFirst();

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

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
        var class1 = cimCollection.getClasses().getFirst();
        class1.setStereotypes(new ArrayList<>(List.of(new CIMSStereotype(CIMStereotypes.concrete.getURI()))));

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

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
        var class1 = cimCollection.getClasses().getFirst();
        addAttribute("class1", "attribute1", XSDDatatype.XSDstring);
        addAttribute("class1", "attribute2", XSDDatatype.XSDint);

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

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
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();
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
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();
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
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();
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
        var enum1 = cimCollection.getEnums().getFirst();

        //Act
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();
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
        var result = ((MermaidDTO) mermaidRenderer.renderUML(cimCollection, null, null)).mermaidString();

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
