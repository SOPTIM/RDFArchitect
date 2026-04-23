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

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationMapper;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.UUID;

class AssociationMapperTest {

    private final AssociationMapper associationMapper = Mappers.getMapper(AssociationMapper.class);

    CIMAssociation cimAssociation;
    AssociationDTO associationDTO;

    @BeforeEach
    void setUp() {
        cimAssociation =
                CIMAssociation.builder()
                        .uuid(UUID.randomUUID())
                        .uri(new URI("http://example.com#Class1.Class2"))
                        .label(new RDFSLabel("Class2", "en"))
                        .multiplicity(
                                new CIMSMultiplicity(
                                        "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#1...1"))
                        .domain(
                                new RDFSDomain(
                                        new URI("http://example.com#Class1"),
                                        new RDFSLabel("Class1", "en")))
                        .range(
                                new RDFSRange(
                                        new URI("http://example.com/class2#Class2"),
                                        new RDFSLabel("Class2", "en")))
                        .associationUsed(new CIMSAssociationUsed("Yes"))
                        .inverseRoleName(
                                new CIMSInverseRoleName("http://example.com#Class2.Class1"))
                        .build();

        associationDTO =
                AssociationDTO.builder()
                        .uuid(UUID.randomUUID())
                        .prefix("http://example.com#")
                        .label("Class2")
                        .multiplicity("1...1")
                        .domain("http://example.com#Class1")
                        .range(new DataTypeDTO("Class2", "http://example.com/class2#"))
                        .associationUsed(true)
                        .build();
    }

    @Nested
    class toDTOTests {

        @Test
        void toDTO_minimalAssociation() {
            var dto = associationMapper.toDTO(cimAssociation);

            assertAll(
                    () -> assertThat(dto.getUuid()).isEqualTo(cimAssociation.getUuid()),
                    () -> assertThat(dto.getPrefix()).isEqualTo("http://example.com#"),
                    () -> assertThat(dto.getLabel()).isEqualTo("Class2"),
                    () -> assertThat(dto.getComment()).isNull(),
                    () -> assertThat(dto.getMultiplicity()).isEqualTo("1...1"),
                    () -> assertThat(dto.getDomain()).isEqualTo("http://example.com#Class1"),
                    () -> assertThat(dto.getRange().getLabel()).isEqualTo("Class2"),
                    () ->
                            assertThat(dto.getRange().getPrefix())
                                    .isEqualTo("http://example.com/class2#"),
                    () -> assertThat(dto.isAssociationUsed()).isTrue());
        }

        @Test
        void toDTO_fullAssociation() {
            cimAssociation.setComment(
                    new RDFSComment(
                            "Test Comment", new URI("http://www.w3.org/2001/XMLSchema#String")));

            var dto = associationMapper.toDTO(cimAssociation);

            assertAll(
                    () -> assertThat(dto.getUuid()).isEqualTo(cimAssociation.getUuid()),
                    () -> assertThat(dto.getPrefix()).isEqualTo("http://example.com#"),
                    () -> assertThat(dto.getLabel()).isEqualTo("Class2"),
                    () -> assertThat(dto.getComment()).isEqualTo("Test Comment"),
                    () -> assertThat(dto.getMultiplicity()).isEqualTo("1...1"),
                    () -> assertThat(dto.getDomain()).isEqualTo("http://example.com#Class1"),
                    () -> assertThat(dto.getRange().getLabel()).isEqualTo("Class2"),
                    () ->
                            assertThat(dto.getRange().getPrefix())
                                    .isEqualTo("http://example.com/class2#"),
                    () -> assertThat(dto.isAssociationUsed()).isTrue());
        }
    }

    @Nested
    class toCIMObjectTests {

        @Test
        void toCIMObject_minimalAssociation() {
            var mappedCIMAssociation =
                    associationMapper.toCIMObject(
                            associationDTO, "http://example.com/class2#Class2.inverseLabel");

            assertAll(
                    () ->
                            assertThat(mappedCIMAssociation.getUuid())
                                    .isEqualTo(associationDTO.getUuid()),
                    () ->
                            assertThat(mappedCIMAssociation.getUri())
                                    .isEqualTo(new URI("http://example.com#Class1.Class2")),
                    () ->
                            assertThat(mappedCIMAssociation.getLabel())
                                    .isEqualTo(new RDFSLabel("Class2", "en")),
                    () -> assertThat(mappedCIMAssociation.getComment()).isNull(),
                    () ->
                            assertThat(mappedCIMAssociation.getMultiplicity())
                                    .isEqualTo(
                                            new CIMSMultiplicity(
                                                    "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#1...1")),
                    () ->
                            assertThat(mappedCIMAssociation.getDomain())
                                    .isEqualTo(
                                            new RDFSDomain(
                                                    new URI("http://example.com#Class1"),
                                                    new RDFSLabel("Class1", "en"))),
                    () ->
                            assertThat(mappedCIMAssociation.getRange())
                                    .isEqualTo(
                                            new RDFSRange(
                                                    new URI("http://example.com/class2#Class2"),
                                                    new RDFSLabel("Class2", "en"))),
                    () ->
                            assertThat(mappedCIMAssociation.getAssociationUsed())
                                    .isEqualTo(new CIMSAssociationUsed("Yes")),
                    () ->
                            assertThat(mappedCIMAssociation.getInverseRoleName())
                                    .isEqualTo(
                                            new CIMSInverseRoleName(
                                                    "http://example.com/class2#Class2.inverseLabel")));
        }

        @Test
        void toCIMObject_fullAssociation() {
            associationDTO.setComment("Test Comment");

            var mappedCIMAssociation =
                    associationMapper.toCIMObject(
                            associationDTO, "http://example.com/class2#Class2.inverseLabel");

            assertAll(
                    () ->
                            assertThat(mappedCIMAssociation.getUuid())
                                    .isEqualTo(associationDTO.getUuid()),
                    () ->
                            assertThat(mappedCIMAssociation.getUri())
                                    .isEqualTo(new URI("http://example.com#Class1.Class2")),
                    () ->
                            assertThat(mappedCIMAssociation.getLabel())
                                    .isEqualTo(new RDFSLabel("Class2", "en")),
                    () ->
                            assertThat(mappedCIMAssociation.getComment())
                                    .isEqualTo(
                                            new RDFSComment(
                                                    "Test Comment",
                                                    new URI(
                                                            "http://www.w3.org/2001/XMLSchema#String"))),
                    () ->
                            assertThat(mappedCIMAssociation.getMultiplicity())
                                    .isEqualTo(
                                            new CIMSMultiplicity(
                                                    "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#1...1")),
                    () ->
                            assertThat(mappedCIMAssociation.getDomain())
                                    .isEqualTo(
                                            new RDFSDomain(
                                                    new URI("http://example.com#Class1"),
                                                    new RDFSLabel("Class1", "en"))),
                    () ->
                            assertThat(mappedCIMAssociation.getRange())
                                    .isEqualTo(
                                            new RDFSRange(
                                                    new URI("http://example.com/class2#Class2"),
                                                    new RDFSLabel("Class2", "en"))),
                    () ->
                            assertThat(mappedCIMAssociation.getAssociationUsed())
                                    .isEqualTo(new CIMSAssociationUsed("Yes")),
                    () ->
                            assertThat(mappedCIMAssociation.getInverseRoleName())
                                    .isEqualTo(
                                            new CIMSInverseRoleName(
                                                    "http://example.com/class2#Class2.inverseLabel")));
        }
    }
}
