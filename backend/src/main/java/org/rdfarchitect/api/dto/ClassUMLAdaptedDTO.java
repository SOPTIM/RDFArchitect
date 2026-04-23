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

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassUMLAdaptedDTO {

    private UUID uuid;

    private String prefix;

    private String label;

    private SuperClassDTO superClass;

    private String comment;

    private List<String> stereotypes;

    @Builder.Default private List<AttributeDTO> attributes = new ArrayList<>();

    @Builder.Default private List<EnumEntryDTO> enumEntries = new ArrayList<>();

    @Builder.Default private List<AssociationPairDTO> associationPairs = new ArrayList<>();

    @JsonProperty("package")
    private BelongsToCategoryDTO belongsToCategory;
}
