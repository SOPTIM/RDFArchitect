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

package org.rdfarchitect.api.dto.enumentries;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.MappingUtils;
import org.rdfarchitect.models.cim.search.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.search.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MappingUtils.class})
public interface EnumEntryMapper {

    EnumEntryMapper INSTANCE = Mappers.getMapper(EnumEntryMapper.class);

    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "comment", source = "comment.value")
    @Mapping(target = "type", source = "type.uri.suffix")
    EnumEntryDTO toDTO(CIMEnumEntry entity);

    List<EnumEntryDTO> toDTOList(List<CIMEnumEntry> entityList);

    @Mapping(target = "uri", source = ".")
    @Mapping(target = "type", source = ".")
    CIMEnumEntry toCIMObject(EnumEntryDTO dto);

    List<CIMEnumEntry> toCIMObjectList(List<EnumEntryDTO> dtoList);

    default String mapStereotype(CIMSStereotype stereotype) {
        return stereotype != null ? "enum" : null;
    }

    default URI buildURI(EnumEntryDTO dto) {
        return new URI(dto.getPrefix() + new URI(dto.getType()).getSuffix() + "." + dto.getLabel());
    }

    default RDFType buildType(EnumEntryDTO dto) {
        return new RDFType(new URI(dto.getType()), new RDFSLabel(new URI(dto.getType()).getSuffix(), "en"));
    }

    default CIMSStereotype buildStereotype(String stereotype) {
        return stereotype != null ? new CIMSStereotype(CIMStereotypes.enumerationString) : null;
    }
}
