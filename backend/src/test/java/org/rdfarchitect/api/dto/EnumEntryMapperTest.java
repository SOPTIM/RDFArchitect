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
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.models.cim.search.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.search.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class EnumEntryMapperTest {

    private final EnumEntryMapper enumEntryMapper = Mappers.getMapper(EnumEntryMapper.class);

    CIMEnumEntry cimEnumEntry;
    EnumEntryDTO enumEntryDTO;

    @BeforeEach
    void setUp() {
        cimEnumEntry = CIMEnumEntry.builder()
                                   .uuid(UUID.randomUUID())
                                   .uri(new URI("http://example.org#TestEnumEntry"))
                                   .label(new RDFSLabel("TestEnumEntry", "en"))
                                   .type(new RDFType(new URI("http://example.org#TestEnum"), new RDFSLabel("TestEnum", "en")))
                                   .build();

        enumEntryDTO = EnumEntryDTO.builder()
                                   .uuid(UUID.randomUUID())
                                   .prefix("http://example.org#")
                                   .label("EnumEntry")
                                   .type("http://example.org#TestEnum")
                                   .build();
    }

    @Nested
    class toDTOTests {

        @Test
        void toDto_minimalEnumEntry() {
            var dto = enumEntryMapper.toDTO(cimEnumEntry);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimEnumEntry.getUuid()),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestEnumEntry"),
                      () -> assertThat(dto.getType()).isEqualTo("TestEnum"),
                      () -> assertThat(dto.getComment()).isNull(),
                      () -> assertThat(dto.getStereotype()).isNull()
                     );
        }

        @Test
        void toDTO_fullEnumEntry() {
            cimEnumEntry.setComment(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String")));
            cimEnumEntry.setStereotype(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#enumeration"));

            var dto = enumEntryMapper.toDTO(cimEnumEntry);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimEnumEntry.getUuid()),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestEnumEntry"),
                      () -> assertThat(dto.getType()).isEqualTo("TestEnum"),
                      () -> assertThat(dto.getComment()).isEqualTo("Test comment"),
                      () -> assertThat(dto.getStereotype()).isEqualTo("enum")
                     );
        }
    }

    @Nested
    class toCIMObjectTests {

        @Test
        void toCIMObject_minimalEnumEntry() {
            CIMEnumEntry mappedCIMEnumEntry = enumEntryMapper.toCIMObject(enumEntryDTO);

            assertAll(
                      () -> assertThat(mappedCIMEnumEntry.getUuid()).isEqualTo(enumEntryDTO.getUuid()),
                      () -> assertThat(mappedCIMEnumEntry.getUri()).isEqualTo(new URI("http://example.org#TestEnum.EnumEntry")),
                      () -> assertThat(mappedCIMEnumEntry.getLabel()).isEqualTo(new RDFSLabel("EnumEntry", "en")),
                      () -> assertThat(mappedCIMEnumEntry.getType()).isEqualTo(new RDFType(new URI("http://example.org#TestEnum"), new RDFSLabel("TestEnum", "en"))),
                      () -> assertThat(mappedCIMEnumEntry.getComment()).isNull(),
                      () -> assertThat(mappedCIMEnumEntry.getStereotype()).isNull()
                     );
        }

        @Test
        void toCIMObject_fullEnumEntry() {
            enumEntryDTO.setComment("Test comment");
            enumEntryDTO.setStereotype("enum");

            CIMEnumEntry mappedCIMEnumEntry = enumEntryMapper.toCIMObject(enumEntryDTO);

            assertAll(
                      () -> assertThat(mappedCIMEnumEntry.getUuid()).isEqualTo(enumEntryDTO.getUuid()),
                      () -> assertThat(mappedCIMEnumEntry.getUri()).isEqualTo(new URI("http://example.org#TestEnum.EnumEntry")),
                      () -> assertThat(mappedCIMEnumEntry.getLabel()).isEqualTo(new RDFSLabel("EnumEntry", "en")),
                      () -> assertThat(mappedCIMEnumEntry.getType()).isEqualTo(new RDFType(new URI("http://example.org#TestEnum"), new RDFSLabel("TestEnum", "en"))),
                      () -> assertThat(mappedCIMEnumEntry.getComment()).isEqualTo(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String"))),
                      () -> assertThat(mappedCIMEnumEntry.getStereotype()).isEqualTo(new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#enumeration"))
                     );
        }
    }
}
