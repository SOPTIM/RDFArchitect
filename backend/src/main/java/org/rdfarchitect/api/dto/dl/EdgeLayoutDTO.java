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

import java.util.List;
import java.util.UUID;

/**
 * The layout of an edge as the frontend draws it: the full, ordered list of its points from the
 * source to the target. Saving it replaces the stored points of the edge, an empty list makes the
 * edge straight again.
 */
@Data
@NoArgsConstructor
public class EdgeLayoutDTO {

    /** The type of the edge, {@code inheritance} or {@code association}. */
    @JsonProperty("kind")
    private String kind;

    /**
     * The sub class of an inheritance or the association end whose domain is the source of the
     * edge, as the edge was rendered with.
     */
    @JsonProperty("sourceObject")
    private UUID sourceObject;

    /** The super class of an inheritance or the inverse association end. */
    @JsonProperty("targetObject")
    private UUID targetObject;

    /** The class the edge starts at, the first point is glued to it if it is an end point. */
    @JsonProperty("sourceClass")
    private UUID sourceClass;

    /** The class the edge ends at, the last point is glued to it if it is an end point. */
    @JsonProperty("targetClass")
    private UUID targetClass;

    @JsonProperty("points")
    private List<EdgePointDTO> points;
}
