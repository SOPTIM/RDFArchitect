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

package org.rdfarchitect.api.dto.association;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.MappingUtils;
import org.rdfarchitect.cim.data.dto.CIMAssociation;
import org.rdfarchitect.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

@Mapper(componentModel = "spring", uses = {MappingUtils.class})
public interface AssociationMapper {

    AssociationMapper INSTANCE = Mappers.getMapper(AssociationMapper.class);

    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "comment", source = "comment.value")
    @Mapping(target = "multiplicity", source = "multiplicity.uri.suffix")
    @Mapping(target = "domain", source = "domain.uri.suffix")
    AssociationDTO toDTO(CIMAssociation association);

    @Mapping(target = "uri", source = "dto")
    @Mapping(target = "domain", source = "dto")
    @Mapping(target = "inverseRoleName", expression = "java(buildInverseRoleName(dto, inverseLabel))")
    CIMAssociation toCIMObject(AssociationDTO dto, String inverseLabel);

    default DataTypeDTO mapRange(RDFSRange range) {
        return new DataTypeDTO(range.getLabel().getValue(), range.getUri().getPrefix(), DataTypeDTO.Type.RANGE);
    }

    default boolean mapAssociationUsed(CIMSAssociationUsed associationUsed) {
        return associationUsed != null && "Yes".equals(associationUsed.getAssociationUsed());
    }

    default URI buildURI(AssociationDTO dto) {
        return new URI(dto.getPrefix() + dto.getDomain() + "." + dto.getLabel());
    }

    default CIMSMultiplicity buildMultiplicity(String multiplicity) {
        return new CIMSMultiplicity(new URI("http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#" + multiplicity));
    }

    default RDFSDomain buildDomain(AssociationDTO dto) {
        return new RDFSDomain(new URI(dto.getPrefix() + dto.getDomain()), new RDFSLabel(dto.getDomain(), "en"));
    }

    default RDFSRange buildRange(DataTypeDTO range) {
        return new RDFSRange(new URI(range.getPrefix() + range.getLabel()), new RDFSLabel(range.getLabel(), "en"));
    }

    default CIMSAssociationUsed buildAssociationUsed(boolean associationUsed) {
        return new CIMSAssociationUsed(associationUsed ? "Yes" : "No");
    }

    default CIMSInverseRoleName buildInverseRoleName(AssociationDTO dto, String inverseLabel) {
        return new CIMSInverseRoleName(dto.getRange().getPrefix() + dto.getRange().getLabel() + "." + inverseLabel);
    }
}
