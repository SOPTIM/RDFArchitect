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

package org.rdfarchitect.api.dto.dl;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** DTO for repositioning a movable diagram label. */
@Data
@NoArgsConstructor
public class LabelPositionDTO {

    /** The UUID of the CIM resource the label belongs to, e.g. an association end. */
    @JsonProperty("identifiedObjectUUID")
    private UUID identifiedObjectUUID;

    /** The label kind, e.g. {@code multiplicity}. Together with the UUID it identifies a label. */
    @JsonProperty("kind")
    private String kind;

    /** Offset relative to the class the label is anchored to. Null resets it to its default. */
    @JsonProperty("xOffset")
    private Float xOffset;

    @JsonProperty("yOffset")
    private Float yOffset;
}
