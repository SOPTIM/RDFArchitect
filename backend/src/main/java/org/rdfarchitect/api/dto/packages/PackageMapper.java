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
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.BelongsToCategoryDTO;
import org.rdfarchitect.api.dto.MappingUtils;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {MappingUtils.class})
public interface PackageMapper {

    PackageMapper INSTANCE = Mappers.getMapper(PackageMapper.class);

    default CIMPackage toCIMObject(PackageDTO dto) {
        if (isDefaultPackage(dto)) {
            return null;
        }

        var cIMPackage = CIMPackage.builder();

        cIMPackage.uri(buildURI(dto));
        cIMPackage.uuid(dto.getUuid());
        cIMPackage.label(MappingUtils.buildLabel(dto.getLabel()));
        cIMPackage.comment(MappingUtils.buildComment(dto.getComment()));
        cIMPackage.belongsToCategory(buildBelongsToCategory(dto.getBelongsToCategory()));

        return cIMPackage.build();
    }

    private boolean isDefaultPackage(PackageDTO dto) {
        return dto == null
                || (dto.getPrefix() == null
                        && (dto.getLabel() == null || "default".equals(dto.getLabel())));
    }

    List<CIMPackage> toCIMObjectList(List<PackageDTO> dtoList);

    default PackageDTO toDTO(CIMPackage packageCIM) {
        if (packageCIM == null) {
            return null;
        }
        var dto = new PackageDTO();
        dto.setUuid(packageCIM.getUuid());
        if (packageCIM.getUri() == null) {
            dto.setLabel(packageCIM.getLabel() != null ? packageCIM.getLabel().getValue() : null);
            dto.setPrefix(null);
        } else {
            dto.setLabel(packageCIM.getUri().getSuffix());
            dto.setPrefix(packageCIM.getUri().getPrefix());
        }
        dto.setComment(packageCIM.getComment() != null ? packageCIM.getComment().getValue() : null);
        dto.setBelongsToCategory(mapBelongsToCategory(packageCIM.getBelongsToCategory()));
        return dto;
    }

    default List<PackageDTO> toDTOList(List<CIMPackage> packageCIMList) {
        if (packageCIMList == null) {
            return null;
        }
        return packageCIMList.stream().map(this::toDTO).toList();
    }

    default BelongsToCategoryDTO mapBelongsToCategory(CIMSBelongsToCategory belongsToCategory) {
        if (belongsToCategory == null) {
            return null;
        }
        return new BelongsToCategoryDTO(
                belongsToCategory.getUri().getPrefix(),
                belongsToCategory.getUri().getSuffix(),
                belongsToCategory.getUuid());
    }

    default URI buildURI(PackageDTO dto) {
        if (dto.getPrefix() == null
                && (dto.getLabel() == null || "default".equals(dto.getLabel()))) {
            return null;
        }
        return new URI(dto.getPrefix() + dto.getLabel());
    }

    default CIMSBelongsToCategory buildBelongsToCategory(
            BelongsToCategoryDTO belongsToCategoryDTO) {
        if (belongsToCategoryDTO == null) {
            return null;
        }
        return new CIMSBelongsToCategory(
                new URI(belongsToCategoryDTO.getPrefix() + belongsToCategoryDTO.getLabel()),
                new RDFSLabel(belongsToCategoryDTO.getLabel(), "en"),
                belongsToCategoryDTO.getUuid());
    }
}
