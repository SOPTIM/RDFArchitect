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

package org.rdfarchitect.api.dto.attributes;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.MappingUtils;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MappingUtils.class}, imports = CIMSStereotype.class)
public interface AttributeMapper {

    AttributeMapper INSTANCE = Mappers.getMapper(AttributeMapper.class);

    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "domain", source = "domain.uri")
    @Mapping(target = "multiplicity", source = "multiplicity.uri.suffix")
    @Mapping(target = "comment", source = "comment.value")
    @Mapping(target = "fixedValue", source = "fixedValue.value")
    @Mapping(target = "defaultValue", source = "defaultValue.value")
    AttributeDTO toDTO(CIMAttribute attribute);

    List<AttributeDTO> toDTOList(List<CIMAttribute> attributesList);

    default String map(URI value) {
        return value.toString();
    }

    @Mapping(target = "uri", source = ".")
    @Mapping(target = "domain", source = ".")
    @Mapping(target = "stereotype", expression = "java(new CIMSStereotype(\"http://iec.ch/TC57/NonStandard/UML#attribute\"))")
    @Mapping(target = "fixedValue", source = ".")
    @Mapping(target = "defaultValue", source = ".")
    CIMAttribute toCIMObject(AttributeDTO dto);

    List<CIMAttribute> toCIMObjectList(List<AttributeDTO> dtoList);

    default DataTypeDTO mapDataType(CIMSDataType dataType) {
        var type = DataTypeDTO.Type.valueOf(dataType.getType().toString());
        return new DataTypeDTO(dataType.getUri().getSuffix(), dataType.getUri().getPrefix(), type);
    }

    default URI buildURI(AttributeDTO dto) {
        return new URI(dto.getPrefix() + new URI(dto.getDomain()).getSuffix() + '.' + dto.getLabel());
    }

    default RDFSDomain buildDomain(AttributeDTO dto) {
        return new RDFSDomain(new URI(dto.getDomain()), new RDFSLabel(new URI(dto.getDomain()).getSuffix(), "en"));
    }

    default CIMSMultiplicity buildMultiplicity(String multiplicity) {
        return new CIMSMultiplicity(new URI("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#" + multiplicity));
    }

    default CIMSIsFixed buildFixedValue(AttributeDTO dto) {
        if (dto.getFixedValue() == null) {
            return null;
        }
        return new CIMSIsFixed(dto.getFixedValue(), new URI(dto.getDataType().getPrefix() + dto.getDataType().getLabel()));
    }

    default CIMSIsDefault buildDefaultValue(AttributeDTO dto) {
        if (dto.getDefaultValue() == null) {
            return null;
        }
        return new CIMSIsDefault(dto.getDefaultValue(), new URI(dto.getDataType().getPrefix() + dto.getDataType().getLabel()));
    }

    default CIMSDataType buildDataType(DataTypeDTO dto) {
        return new CIMSDataType(
                  new URI(dto.getPrefix() + dto.getLabel()),
                  new RDFSLabel(dto.getLabel(), "en"),
                  CIMSDataType.Type.valueOf(dto.getType().toString())
        );
    }
}
