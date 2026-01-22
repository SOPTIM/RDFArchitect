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

package org.rdfarchitect.api.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class AttributeMapperTest {

    private final AttributeMapper attributeMapper = Mappers.getMapper(AttributeMapper.class);

    CIMAttribute cimAttribute;
    AttributeDTO attributeDTO;

    @BeforeEach
    void setUp() {
        cimAttribute = CIMAttribute.builder()
                                   .uuid(UUID.randomUUID())
                                   .uri(new URI("http://example.org#TestClass.TestAttribute"))
                                   .label(new RDFSLabel("TestAttribute", "en"))
                                   .domain(new RDFSDomain(new URI("http://example.org#TestClass"), new RDFSLabel("TestClass", "en")))
                                   .dataType(new CIMSDataType(new URI("http://www.w3.org/2001/XMLSchema#String"), new RDFSLabel("String", "en"), CIMSDataType.Type.PRIMITIVE))
                                   .stereotype(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#attribute"))
                                   .multiplicity(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#0...1"))
                                   .build();

        attributeDTO = AttributeDTO.builder()
                                   .uuid(UUID.randomUUID())
                                   .label("TestAttribute")
                                   .prefix("http://example.org#")
                                   .domain("http://example.org#TestClass")
                                   .multiplicity("0...1")
                                   .dataType(new DataTypeDTO("String", "http://www.w3.org/2001/XMLSchema#", DataTypeDTO.Type.PRIMITIVE))
                                   .build();
    }

    @Nested
    class ToDTOTests {

        @Test
        void toDTO_minimalAttribute() {
            var dto = attributeMapper.toDTO(cimAttribute);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimAttribute.getUuid()),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestAttribute"),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getDomain()).isEqualTo("http://example.org#TestClass"),
                      () -> assertThat(dto.getMultiplicity()).isEqualTo("0...1"),
                      () -> assertThat(dto.getComment()).isNull(),
                      () -> assertThat(dto.getFixedValue()).isNull(),
                      () -> assertThat(dto.getDefaultValue()).isNull()
                     );
        }

        @Test
        void toDTO_fullAttribute() {
            cimAttribute.setComment(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String")));
            cimAttribute.setFixedValue(new CIMSIsFixed("FixedValue", new URI("http://www.w3.org/2001/XMLSchema#String")));
            cimAttribute.setDefaultValue(new CIMSIsDefault("DefaultValue", new URI("http://www.w3.org/2001/XMLSchema#String")));

            var dto = attributeMapper.toDTO(cimAttribute);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimAttribute.getUuid()),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestAttribute"),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getDomain()).isEqualTo("http://example.org#TestClass"),
                      () -> assertThat(dto.getMultiplicity()).isEqualTo("0...1"),
                      () -> assertThat(dto.getComment()).isEqualTo("Test comment"),
                      () -> assertThat(dto.getFixedValue()).isEqualTo("FixedValue"),
                      () -> assertThat(dto.getDefaultValue()).isEqualTo("DefaultValue")
                     );
        }

        @Test
        void toDTO_nullAttribute() {
            var dto = attributeMapper.toDTO(null);

            assertThat(dto).isNull();
        }
    }

    @Nested
    class toCIMObjectTests {

        @Test
        void toCIMObject_minimalAttribute() {
            var mappedCIMAttribute = attributeMapper.toCIMObject(attributeDTO);

            assertAll(
                      () -> assertThat(mappedCIMAttribute.getUuid()).isEqualTo(attributeDTO.getUuid()),
                      () -> assertThat(mappedCIMAttribute.getUri()).isEqualTo(new URI("http://example.org#TestClass.TestAttribute")),
                      () -> assertThat(mappedCIMAttribute.getLabel()).isEqualTo(new RDFSLabel("TestAttribute", "en")),
                      () -> assertThat(mappedCIMAttribute.getDomain()).isEqualTo(new RDFSDomain(new URI("http://example.org#TestClass"), new RDFSLabel("TestClass", "en"))),
                      () -> assertThat(mappedCIMAttribute.getMultiplicity()).isEqualTo(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#0...1")),
                      () -> assertThat(mappedCIMAttribute.getDataType()).isEqualTo(new CIMSDataType(new URI("http://www.w3.org/2001/XMLSchema#String"), new RDFSLabel("String",
                                                                                                                                                                      "en"),
                                                                                                    CIMSDataType.Type.PRIMITIVE)),
                      () -> assertThat(mappedCIMAttribute.getComment()).isNull(),
                      () -> assertThat(mappedCIMAttribute.getStereotype()).isEqualTo(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#attribute")),
                      () -> assertThat(mappedCIMAttribute.getFixedValue()).isNull(),
                      () -> assertThat(mappedCIMAttribute.getDefaultValue()).isNull()
                     );
        }

        @Test
        void toCIMObject_fullAttribute() {
            attributeDTO.setComment("Test comment");
            attributeDTO.setFixedValue("FixedValue");
            attributeDTO.setDefaultValue("DefaultValue");

            var mappedCIMAttribute = attributeMapper.toCIMObject(attributeDTO);

            assertAll(
                      () -> assertThat(mappedCIMAttribute.getUuid()).isEqualTo(attributeDTO.getUuid()),
                      () -> assertThat(mappedCIMAttribute.getUri()).isEqualTo(new URI("http://example.org#TestClass.TestAttribute")),
                      () -> assertThat(mappedCIMAttribute.getLabel()).isEqualTo(new RDFSLabel("TestAttribute", "en")),
                      () -> assertThat(mappedCIMAttribute.getDomain()).isEqualTo(new RDFSDomain(new URI("http://example.org#TestClass"), new RDFSLabel("TestClass", "en"))),
                      () -> assertThat(mappedCIMAttribute.getMultiplicity()).isEqualTo(new CIMSMultiplicity("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#0...1")),
                      () -> assertThat(mappedCIMAttribute.getDataType()).isEqualTo(new CIMSDataType(new URI("http://www.w3.org/2001/XMLSchema#String"), new RDFSLabel("String",
                                                                                                                                                                      "en"),
                                                                                                    CIMSDataType.Type.PRIMITIVE)),
                      () -> assertThat(mappedCIMAttribute.getComment()).isEqualTo(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String"))),
                      () -> assertThat(mappedCIMAttribute.getStereotype()).isEqualTo(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#attribute")),
                      () -> assertThat(mappedCIMAttribute.getFixedValue()).isEqualTo(new CIMSIsFixed("FixedValue", new URI("http://www.w3.org/2001/XMLSchema#String"))),
                      () -> assertThat(mappedCIMAttribute.getDefaultValue()).isEqualTo(new CIMSIsDefault("DefaultValue", new URI("http://www.w3.org/2001/XMLSchema#String")))
                     );
        }

        @Test
        void toCIMObject_nonPrimitiveDoesNotForceXsdDatatypeForFixedDefault() {
            attributeDTO.setComment("Test comment");
            attributeDTO.setFixedValue("FixedValue");
            attributeDTO.setDefaultValue("DefaultValue");
            attributeDTO.setDataType(new DataTypeDTO("CustomType", "http://example.org#", DataTypeDTO.Type.RANGE));

            var mappedCIMAttribute = attributeMapper.toCIMObject(attributeDTO);

            assertAll(
                      () -> assertThat(mappedCIMAttribute.getFixedValue()).isNotNull(),
                      () -> assertThat(mappedCIMAttribute.getFixedValue().getDataType()).isNull(),
                      () -> assertThat(mappedCIMAttribute.getDefaultValue()).isNotNull(),
                      () -> assertThat(mappedCIMAttribute.getDefaultValue().getDataType()).isNull()
                     );
        }

        @Test
        void toCIMObject_nullAttribute() {
            var mappedCIMAttribute = attributeMapper.toCIMObject(null);

            assertThat(mappedCIMAttribute).isNull();
        }
    }
}
