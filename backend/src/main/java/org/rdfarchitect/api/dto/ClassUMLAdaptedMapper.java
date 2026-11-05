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
import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.api.dto.enumentries.EnumEntryMapper;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.umladapted.data.CIMClassUMLAdapted;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {
            AttributeMapper.class,
            EnumEntryMapper.class,
            AssociationPairMapper.class,
            PackageMapper.class,
            MappingUtils.class
        })
public interface ClassUMLAdaptedMapper {

    ClassUMLAdaptedMapper INSTANCE = Mappers.getMapper(ClassUMLAdaptedMapper.class);

    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "comment", source = "comment.value")
    @Mapping(target = "superClass", source = "superClasses")
    ClassUMLAdaptedDTO toDTO(CIMClassUMLAdapted cimClassUMLAdapted);

    List<ClassUMLAdaptedDTO> toDTOList(List<CIMClassUMLAdapted> cimClassList);

    @Mapping(target = "uri", source = ".")
    @Mapping(target = "graphUri", ignore = true)
    @Mapping(target = "superClasses", source = "superClass")
    CIMClassUMLAdapted toCIMObject(ClassUMLAdaptedDTO dto);

    List<CIMClassUMLAdapted> toCIMObjectList(List<ClassUMLAdaptedDTO> dtoList);

    /**
     * The first superclass only, because this DTO nests one superclass to carry a single lineage.
     * The graph may hold several; see {@link #buildSuperClasses}.
     */
    default ClassUMLAdaptedDTO mapSuperClass(List<RDFSSubClassOf> superClasses) {
        if (superClasses == null || superClasses.isEmpty()) {
            return null;
        }
        var superClass = superClasses.getFirst();
        return ClassUMLAdaptedDTO.builder()
                .prefix(superClass.getUri().getPrefix())
                .label(superClass.getUri().getSuffix())
                .build();
    }

    default URI buildURI(ClassUMLAdaptedDTO dto) {
        return new URI(dto.getPrefix() + dto.getLabel());
    }

    /**
     * The superclasses a saved class keeps, which is the one this DTO carries and no more. A class
     * imported with several loses the others the first time it is saved, the same way a package
     * loses its parent and an association its stereotypes.
     */
    default List<RDFSSubClassOf> buildSuperClasses(ClassUMLAdaptedDTO dto) {
        if (dto == null) {
            return List.of();
        }
        return List.of(
                new RDFSSubClassOf(
                        new URI(dto.getPrefix() + dto.getLabel()),
                        new RDFSLabel(dto.getLabel(), "en")));
    }
}
