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
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.models.cim.search.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.search.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class PackageMapperTest {

    private final PackageMapper packageMapper = Mappers.getMapper(PackageMapper.class);
    CIMPackage cimPackage;
    PackageDTO packageDTO;

    @BeforeEach
    void setUp() {
        cimPackage = CIMPackage.builder()
                               .uuid(UUID.randomUUID())
                               .uri(new URI("http://example.org#TestPackage"))
                               .label(new RDFSLabel("TestPackage", "en"))
                               .build();

        packageDTO = PackageDTO.builder()
                               .uuid(UUID.randomUUID())
                               .prefix("http://example.org#")
                               .label("TestPackage")
                               .build();
    }

    @Nested
    class ToDTOTests {

        @Test
        void toDTO_minimalPackage() {
            var dto = packageMapper.toDTO(cimPackage);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimPackage.getUuid()),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestPackage"),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getComment()).isNull(),
                      () -> assertThat(dto.getBelongsToCategory()).isNull()
                     );
        }

        @Test
        void toDTO_fullPackage() {
            var belongsToCategory =
                      new CIMSBelongsToCategory(new URI("http://example.org/Packages#TestParentPackage"), new RDFSLabel("TestParentPackage", "en"), UUID.randomUUID());
            cimPackage.setComment(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String")));
            cimPackage.setBelongsToCategory(belongsToCategory);

            var dto = packageMapper.toDTO(cimPackage);

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimPackage.getUuid()),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestPackage"),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.org#"),
                      () -> assertThat(dto.getComment()).isEqualTo("Test comment"),
                      () -> assertThat(dto.getBelongsToCategory().getPrefix()).isEqualTo("http://example.org/Packages#"),
                      () -> assertThat(dto.getBelongsToCategory().getLabel()).isEqualTo("TestParentPackage"),
                      () -> assertThat(dto.getBelongsToCategory().getUuid()).isEqualTo(belongsToCategory.getUuid())
                     );
        }

        @Test
        void toDTO_nullPackage() {
            var dto = packageMapper.toDTO(null);

            assertThat(dto).isNull();
        }
    }

    @Nested
    class toCIMPackageTests {

        @Test
        void toCIMObject_minimalPackage() {
            var mappedCIMPackage = packageMapper.toCIMObject(packageDTO);

            assertAll(
                      () -> assertThat(mappedCIMPackage.getUuid()).isEqualTo(packageDTO.getUuid()),
                      () -> assertThat(mappedCIMPackage.getUri()).isEqualTo(new URI("http://example.org#Package_TestPackage")),
                      () -> assertThat(mappedCIMPackage.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en")),
                      () -> assertThat(mappedCIMPackage.getComment()).isNull(),
                      () -> assertThat(mappedCIMPackage.getBelongsToCategory()).isNull()
                     );
        }

        @Test
        void toCIMObject_fullPackage() {
            packageDTO.setComment("Test comment");
            packageDTO.setBelongsToCategory(new BelongsToCategoryDTO("http://example.org/Packages#", "TestParentPackage", UUID.randomUUID()));

            var mappedCIMPackage = packageMapper.toCIMObject(packageDTO);

            var expectedBelongsToCategory = new CIMSBelongsToCategory();
            expectedBelongsToCategory.setUri(new URI("http://example.org/Packages#Package_TestParentPackage"));
            expectedBelongsToCategory.setLabel(new RDFSLabel("TestParentPackage", "en"));
            expectedBelongsToCategory.setUuid(packageDTO.getBelongsToCategory().getUuid());
            assertAll(
                      () -> assertThat(mappedCIMPackage.getUuid()).isEqualTo(packageDTO.getUuid()),
                      () -> assertThat(mappedCIMPackage.getUri()).isEqualTo(new URI("http://example.org#Package_TestPackage")),
                      () -> assertThat(mappedCIMPackage.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en")),
                      () -> assertThat(mappedCIMPackage.getComment()).isEqualTo(new RDFSComment("Test comment", new URI("http://www.w3.org/2001/XMLSchema#String"))),
                      () -> assertThat(mappedCIMPackage.getBelongsToCategory()).isEqualTo(expectedBelongsToCategory)
                     );
        }

        @Test
        void toCIMObject_nullPackage() {
            var mappedCIMPackage = packageMapper.toCIMObject(null);

            assertThat(mappedCIMPackage).isNull();
        }
    }
}
