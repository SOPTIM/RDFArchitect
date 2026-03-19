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
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;

@Mapper(componentModel = "spring", uses = {AssociationMapper.class})
public interface AssociationPairMapper {

    @Mapping(target = "from", source = "associationPair.from")
    @Mapping(target = "to", source = "associationPair.to")
    AssociationPairDTO toDTO(CIMAssociationPair associationPair);

    List<AssociationPairDTO> toDTOList(List<CIMAssociationPair> associationPairs);

    default CIMAssociationPair toCIMObject(AssociationPairDTO associationPairDTO) {
        CIMAssociation from = AssociationMapper.INSTANCE.toCIMObject(
                  associationPairDTO.getFrom(),
                  associationPairDTO.getTo().getPrefix() + new URI(associationPairDTO.getTo().getDomain()).getSuffix() + "." + associationPairDTO.getTo().getLabel()
                                                                    );

        CIMAssociation to = AssociationMapper.INSTANCE.toCIMObject(
                  associationPairDTO.getTo(),
                  associationPairDTO.getFrom().getPrefix() + new URI(associationPairDTO.getFrom().getDomain()).getSuffix() + "." + associationPairDTO.getFrom().getLabel()
                                                                  );

        return new CIMAssociationPair(from, to);
    }

    List<CIMAssociationPair> toCIMObjectList(List<AssociationPairDTO> associationPairDTOs);
}
