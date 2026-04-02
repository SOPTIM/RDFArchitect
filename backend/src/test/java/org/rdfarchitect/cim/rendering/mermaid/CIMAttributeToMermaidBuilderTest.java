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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMAttributeToMermaidBuilder;

import static org.assertj.core.api.Assertions.*;

class CIMAttributeToMermaidBuilderTest {

    private static final String URI_PREFIX = "http://example.com#";

    @Test
    void build_validAttribute_returnsMermaidAttribute() {
        //Arrange
        var attribute = CIMAttribute.builder()
                                    .label(new RDFSLabel("attribute"))
                                    .dataType(new CIMSDataType(new URI(URI_PREFIX + "datatype"), new RDFSLabel("datatype"), CIMSDataType.Type.UNKNOWN))
                                    .build();
        var builder = new CIMAttributeToMermaidBuilder(attribute);
        //Act

        var result = builder.build().toString();
        //Assert

        assertThat(result).isEqualTo("attribute: datatype\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"attribute", "var", "x", "1", "\"sdf!§$", ""})
    void build_validAttributeWithDifferentLabels_returnsMermaidAttribute(String label) {
        //Arrange
        var attribute = CIMAttribute.builder()
                                    .label(new RDFSLabel(label))
                                    .dataType(new CIMSDataType(new URI(URI_PREFIX + "datatype"), new RDFSLabel("datatype"), CIMSDataType.Type.UNKNOWN))
                                    .build();
        var builder = new CIMAttributeToMermaidBuilder(attribute);
        //Act

        var result = builder.build().toString();
        //Assert

        assertThat(result).isEqualTo(label + ": datatype\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"datatype", "var", "x", "1", "\"sdf!§$", ""})
    void build_validAttributeWithDifferentDataTypes_returnsMermaidAttribute(String dataType) {
        //Arrange
        var attribute = CIMAttribute.builder()
                                    .label(new RDFSLabel("attribute"))
                                    .dataType(new CIMSDataType(new URI(URI_PREFIX + dataType), new RDFSLabel(dataType), CIMSDataType.Type.UNKNOWN))
                                    .build();
        var builder = new CIMAttributeToMermaidBuilder(attribute);
        //Act

        var result = builder.build().toString();
        //Assert

        assertThat(result).isEqualTo("attribute: " + dataType + "\n");
    }

    @Test
    void build_attributeWithNullType_throwsException() {
        //Arrange
        var attribute = CIMAttribute.builder()
                                    .label(new RDFSLabel("attribute"))
                                    .dataType(null)
                                    .build();
        var builder = new CIMAttributeToMermaidBuilder(attribute);

        //Act/Assert
        assertThatException().isThrownBy(builder::build);
    }

    @Test
    void build_attributeWithNullLabel_throwsException() {
        //Arrange
        var attribute = CIMAttribute.builder()
                                    .label(null)
                                    .dataType(new CIMSDataType(new URI(URI_PREFIX + "datatype"), new RDFSLabel("datatype"), CIMSDataType.Type.UNKNOWN))
                                    .build();
        var builder = new CIMAttributeToMermaidBuilder(attribute);

        //Act/Assert
        assertThatException().isThrownBy(builder::build);
    }
}
