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

package org.rdfarchitect.api.dto.packages;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.BelongsToCategoryDTO;
import org.rdfarchitect.api.dto.MappingUtils;
import org.rdfarchitect.models.cim.search.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.search.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.search.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.search.data.dto.relations.uri.URI;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MappingUtils.class})
public interface PackageMapper {

    PackageMapper INSTANCE = Mappers.getMapper(PackageMapper.class);

    @Mapping(target = "label", source = "label.value")
    @Mapping(target = "prefix", source = "uri.prefix")
    @Mapping(target = "comment", source = "comment.value")
    PackageDTO toDTO(CIMPackage packageCIM);

    List<PackageDTO> toDTOList(List<CIMPackage> packageCIMList);

    @Mapping(target = "uri", source = ".")
    CIMPackage toCIMObject(PackageDTO dto);

    List<CIMPackage> toCIMObjectList(List<PackageDTO> dtoList);

    default BelongsToCategoryDTO mapBelongsToCategory(CIMSBelongsToCategory belongsToCategory) {
        if (belongsToCategory == null) {
            return null;
        }
        return new BelongsToCategoryDTO(
                  belongsToCategory.getUri().getPrefix(),
                  belongsToCategory.getLabel().getValue(),
                  belongsToCategory.getUuid()
        );
    }

    default URI buildURI(PackageDTO dto) {
        return new URI(dto.getPrefix() + "Package_" + dto.getLabel());
    }

    default CIMSBelongsToCategory buildBelongsToCategory(BelongsToCategoryDTO belongsToCategoryDTO) {
        if (belongsToCategoryDTO == null) {
            return null;
        }
        return new CIMSBelongsToCategory(
                  new URI(belongsToCategoryDTO.getPrefix() + "Package_" + belongsToCategoryDTO.getLabel()),
                  new RDFSLabel(belongsToCategoryDTO.getLabel(), "en"),
                  belongsToCategoryDTO.getUuid()
        );
    }
}
