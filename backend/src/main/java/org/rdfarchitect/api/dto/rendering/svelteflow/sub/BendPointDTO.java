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

package org.rdfarchitect.api.dto.rendering.svelteflow.sub;

import lombok.Builder;
import lombok.Data;

/** DTO representing a single bend point or end point of a SvelteFlow edge. */
@Data
@Builder
public class BendPointDTO {

    /** The mRID of the point, sent back unchanged when the layout of the edge is saved. */
    private String id;

    private PositionDTO position;

    /**
     * The side of an end point, {@code source} or {@code target}: the point is glued to the border
     * of the source or the target class of the edge. Null for a bend point.
     */
    private String side;
}
