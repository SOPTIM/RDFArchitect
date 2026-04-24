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

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.Collections;
import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {MappingUtils.class})
public interface ClassMapper {

    ClassMapper INSTANCE = Mappers.getMapper(ClassMapper.class);

    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "comment", source = "comment.value")
    @Mapping(target = "belongsToCategory", source = "belongsToCategory.uuid")
    ClassDTO toDTO(CIMClass cimClass);

    List<ClassDTO> toDTOList(List<CIMClass> cimClassList);

    @Mapping(target = "uri", source = ".")
    @Mapping(target = "belongsToCategory", ignore = true)
    // Wird separat behandelt
    CIMClass toCIMObject(ClassDTO dto);

    List<CIMClass> toCIMObjectList(List<ClassDTO> dtoList);

    default List<String> mapStereotypes(List<CIMSStereotype> stereotypes) {
        if (stereotypes == null || stereotypes.isEmpty()) {
            return Collections.emptyList();
        }
        return stereotypes.stream().map(CIMSStereotype::getStereotype).toList();
    }

    default SuperClassDTO mapSuperClass(RDFSSubClassOf superClass) {
        if (superClass == null) {
            return null;
        }
        return new SuperClassDTO(superClass.getUri().getPrefix(), superClass.getUri().getSuffix());
    }

    default URI buildURI(ClassDTO dto) {
        return new URI(dto.getPrefix() + dto.getLabel());
    }

    default RDFSSubClassOf buildSuperClass(SuperClassDTO dto) {
        if (dto == null) {
            return null;
        }
        return new RDFSSubClassOf(
                new URI(dto.getPrefix() + dto.getLabel()), new RDFSLabel(dto.getLabel(), "en"));
    }

    default List<CIMSStereotype> buildStereotypes(List<String> stereotypes) {
        if (stereotypes == null || stereotypes.isEmpty()) {
            return Collections.emptyList();
        }
        return stereotypes.stream().map(CIMSStereotype::new).toList();
    }
}
