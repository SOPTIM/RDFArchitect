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
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.umladapted.data.CIMClassUMLAdapted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClassUMLAdaptedMapperTest {

    @Autowired
    private ClassUMLAdaptedMapper classMapper;

    private CIMClassUMLAdapted cimClass;
    private ClassUMLAdaptedDTO classDTO;

    @BeforeEach
    void setUp() {
        cimClass = new CIMClassUMLAdapted(
                  CIMClass.builder()
                          .uuid(UUID.randomUUID())
                          .uri(new URI("http://example.com#TestClass"))
                          .label(new RDFSLabel("TestClass", "en"))
                          .build()
        );
        classDTO = ClassUMLAdaptedDTO.builder()
                                     .uuid(cimClass.getUuid())
                                     .prefix("http://example.com#")
                                     .label("TestClass")
                                     .build();
    }

    @Nested
    class toDTOTests {

        @Test
        void toDTO_minimalClass() {
            var dto = classMapper.toDTO(cimClass);

            assertAll(
                      () -> assertThat(dto).isNotNull(),
                      () -> assertThat(dto.getUuid()).isEqualTo(cimClass.getUuid()),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.com#"),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestClass"),
                      () -> assertThat(dto.getSuperClass()).isNull(),
                      () -> assertThat(dto.getComment()).isNull(),
                      () -> assertThat(dto.getStereotypes()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getAttributes()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getEnumEntries()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getAssociationPairs()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getBelongsToCategory()).isNull()
                     );
        }

        @Test
        void toDTO_allCoreFields() {
            cimClass.setSuperClass(new RDFSSubClassOf(new URI("http://example.com/superClass#SuperClass"), new RDFSLabel("SuperClass")));
            cimClass.setComment(new RDFSComment("This is a test class", new URI("http://www.w3.org/2001/XMLSchema#String")));
            cimClass.setStereotypes(List.of(new CIMSStereotype("Entsoe"), new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML#concrete"), new CIMSStereotype("Operation")));
            cimClass.setBelongsToCategory(new CIMSBelongsToCategory(new URI("http://example.com/Category#TestCategory"), new RDFSLabel("TestCategory", "en"), UUID.randomUUID()));

            var dto = classMapper.toDTO(cimClass);
            var belongsToCategoryDTO = new BelongsToCategoryDTO(
                      "http://example.com/Category#",
                      "TestCategory",
                      cimClass.getBelongsToCategory().getUuid());

            assertAll(
                      () -> assertThat(dto.getUuid()).isEqualTo(cimClass.getUuid()),
                      () -> assertThat(dto.getPrefix()).isEqualTo("http://example.com#"),
                      () -> assertThat(dto.getLabel()).isEqualTo("TestClass"),
                      () -> assertThat(dto.getSuperClass().getPrefix()).isEqualTo("http://example.com/superClass#"),
                      () -> assertThat(dto.getSuperClass().getLabel()).isEqualTo("SuperClass"),
                      () -> assertThat(dto.getComment()).isEqualTo("This is a test class"),
                      () -> assertThat(dto.getStereotypes()).isEqualTo(List.of("Entsoe", "http://iec.ch/TC57/NonStandard/UML#concrete", "Operation")),
                      () -> assertThat(dto.getAttributes()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getEnumEntries()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getAssociationPairs()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(dto.getBelongsToCategory()).isEqualTo(belongsToCategoryDTO)
                     );
        }
    }

    @Nested
    class toCIMTests {

        @Test
        void toCIM_minimalClass() {
            var mappedCIMClass = classMapper.toCIMObject(classDTO);

            assertAll(
                      () -> assertThat(mappedCIMClass.getUuid()).isEqualTo(classDTO.getUuid()),
                      () -> assertThat(mappedCIMClass.getUri()).isEqualTo(new URI("http://example.com#TestClass")),
                      () -> assertThat(mappedCIMClass.getLabel()).isEqualTo(new RDFSLabel("TestClass", "en")),
                      () -> assertThat(mappedCIMClass.getSuperClass()).isNull(),
                      () -> assertThat(mappedCIMClass.getStereotypes()).isEqualTo(List.of()),
                      () -> assertThat(mappedCIMClass.getAttributes()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getEnumEntries()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getAssociationPairs()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getComment()).isNull(),
                      () -> assertThat(mappedCIMClass.getBelongsToCategory()).isNull()
                     );
        }

        @Test
        void toCIM_allCoreFields() {
            classDTO.setSuperClass(new SuperClassDTO("http://example.com/superClass#", "SuperClass"));
            classDTO.setComment("This is a test class");
            classDTO.setStereotypes(List.of("Entsoe", "http://iec.ch/TC57/NonStandard/UML#concrete", "Operation"));

            var mappedCIMClass = classMapper.toCIMObject(classDTO);

            assertAll(
                      () -> assertThat(mappedCIMClass.getUuid()).isEqualTo(classDTO.getUuid()),
                      () -> assertThat(mappedCIMClass.getUri()).isEqualTo(new URI("http://example.com#TestClass")),
                      () -> assertThat(mappedCIMClass.getLabel()).isEqualTo(new RDFSLabel("TestClass", "en")),
                      () -> assertThat(mappedCIMClass.getSuperClass().getUri()).isEqualTo(new URI("http://example.com/superClass#SuperClass")),
                      () -> assertThat(mappedCIMClass.getComment()).isEqualTo(new RDFSComment("This is a test class", new URI("http://www.w3.org/2001/XMLSchema#String"))),
                      () -> assertThat(mappedCIMClass.getStereotypes()).isEqualTo(List.of(new CIMSStereotype("Entsoe"), new CIMSStereotype("http://iec.ch/TC57/NonStandard/UML" +
                                                                                                                                                     "#concrete"),
                                                                                          new CIMSStereotype("Operation"))),
                      () -> assertThat(mappedCIMClass.getAttributes()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getEnumEntries()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getAssociationPairs()).isEqualTo(Collections.emptyList()),
                      () -> assertThat(mappedCIMClass.getBelongsToCategory()).isNull()
                     );
        }
    }
}