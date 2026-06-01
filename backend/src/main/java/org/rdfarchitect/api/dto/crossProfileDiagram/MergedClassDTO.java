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

package org.rdfarchitect.api.dto.crossProfileDiagram;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.rdfarchitect.api.dto.SuperClassDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MergedClassDTO {

    private UUID uuid;
    private String classUri;
    private List<ClassSourceDTO> sources;
    private List<GraphSourcedDTO<SuperClassDTO>> superClasses;
    private List<GraphSourcedDTO<AttributeDTO>> attributes;
    private List<GraphSourcedDTO<EnumEntryDTO>> enumEntries;
    private List<GraphSourcedDTO<AssociationPairDTO>> associationPairs;
    private List<CIMSStereotype> stereotypes;
}
