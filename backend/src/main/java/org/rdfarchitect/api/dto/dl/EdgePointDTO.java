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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A point of an edge: an end point at the border of a class or a bend point in between. The
 * position of the point within {@link EdgeLayoutDTO#getPoints()} is its sequence number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdgePointDTO {

    /**
     * The id the frontend knows the point by. If it is the mRID of a stored point of the edge, that
     * point is updated. Otherwise the point is new, gets an mRID of its own and the id is reported
     * back in an {@link EdgePointIdDTO}.
     */
    @JsonProperty("id")
    private String id;

    @JsonProperty("xPosition")
    private float xPosition;

    @JsonProperty("yPosition")
    private float yPosition;

    /**
     * The side of an end point, {@code source} or {@code target}: the point is glued to the border
     * of the source or the target class of the edge. Null for a bend point.
     */
    @JsonProperty("side")
    private String side;
}
