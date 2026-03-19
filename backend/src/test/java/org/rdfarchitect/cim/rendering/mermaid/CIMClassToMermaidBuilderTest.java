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

package org.rdfarchitect.cim.rendering.mermaid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMClassToMermaidBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CIMClassToMermaidBuilderTest {

    private static final String URI_PREFIX = "http://example.com#";

    private static final String CLASS_LABEL = "exampleClass";

    private static final UUID CLASS_UUID = UUID.randomUUID();

    private static final String EXPECTED_CLASS_HEADER = "class `" +
              CLASS_UUID +
              "`[\"" +
              CLASS_LABEL +
              "\"]{\n";

    private CIMClass cimClass;

    @BeforeEach
    void setUp() {
        cimClass = CIMClass.builder()
                           .uri(new URI(URI_PREFIX + CLASS_LABEL))
                           .label(new RDFSLabel(CLASS_LABEL))
                           .build();
    }

    @Test
    void build_emptyClass_returnsAbstractMermaidClass() {
        //Arrange
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "    <<abstract>>\n" +
                            "}\n"
                                    );
    }

    @Test
    void build_concreteClass_returnsEmptyMermaidClass() {
        //Arrange
        var stereotypeList = new ArrayList<CIMSStereotype>();
        stereotypeList.add(new CIMSStereotype(CIMStereotypes.concrete.toString()));
        cimClass.setStereotypes(stereotypeList);
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "}\n"
                                    );
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "any", URI_PREFIX + "stereotype", "enum"})
    void build_concreteClassWithOtherStereotype_returnsMermaidClassWithStereotype(String stereotype) {
        //Arrange
        var stereotypeList = new ArrayList<CIMSStereotype>();
        stereotypeList.add(new CIMSStereotype(CIMStereotypes.concrete.toString()));
        stereotypeList.add(new CIMSStereotype(stereotype));
        cimClass.setStereotypes(stereotypeList);
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "    <<" +
                            stereotype +
                            ">>\n" +
                            "}\n"
                                    );
    }

    @Test
    void build_concreteClassWithCIMSEnumerationStereotype_returnsMermaidClassWithShortenedEnumerationStereotype() {
        //Arrange
        var stereotypeList = new ArrayList<CIMSStereotype>();
        stereotypeList.add(new CIMSStereotype(CIMStereotypes.concrete.toString()));
        stereotypeList.add(new CIMSStereotype(CIMStereotypes.enumeration.getURI()));
        cimClass.setStereotypes(stereotypeList);
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "    <<enumeration>>\n" +
                            "}\n"
                                    );
    }

    @Test
    void build_concreteClassWithMultipleStereotypes_returnsMermaidClassWithMultipleStereotypesInAlphabeticalOrder() {
        //Arrange
        var stereotypeList = new ArrayList<CIMSStereotype>();
        stereotypeList.add(new CIMSStereotype("foo"));
        stereotypeList.add(new CIMSStereotype("any"));
        stereotypeList.add(new CIMSStereotype("stereotype"));
        stereotypeList.add(new CIMSStereotype(CIMStereotypes.enumeration.getURI()));

        cimClass.setStereotypes(stereotypeList);
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "    <<abstract, any, enumeration, foo, stereotype>>\n" +
                            "}\n"
                                    );
    }

    @ParameterizedTest
    @ValueSource(strings = {"anything", "foo", "x: Integer\n", "<<abstract>>", "foo%$&(/^°)`*'"})
    void appendClassContents_appendAnyString_returnsStringContainedInMermaidClass(String contents) {
        //Arrange
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        //Act
        var result = builder.appendClassContents(List.of(new StringBuilder(contents))).build().toString();

        //Assert
        assertThat(result).isEqualTo(
                  EXPECTED_CLASS_HEADER +
                            "    <<abstract>>\n" +
                            "    " +
                            contents +
                            "}\n"
                                    );
    }

    @Test
    void appendClassContents_appendMultipleContents_returnsSortedTabPrefixedContentsWrappedInMermaidClass() {
        //Arrange
        var builder = new CIMClassToMermaidBuilder(cimClass, CLASS_UUID);

        var contents = new ArrayList<StringBuilder>();
        contents.add(new StringBuilder("foo\n"));
        contents.add(new StringBuilder("anything\n"));
        contents.add(new StringBuilder("x: Integer\n"));
        contents.add(new StringBuilder("loremIpsum\n"));
        contents.add(new StringBuilder());

        //Act
        var result = builder.appendClassContents(contents).build().toString();

        //Assert
        var expectedResult = EXPECTED_CLASS_HEADER + """
                      <<abstract>>
                          anything
                      foo
                      loremIpsum
                      x: Integer
                  }
                  """;
        assertThat(result).isEqualTo(expectedResult);
    }
}
