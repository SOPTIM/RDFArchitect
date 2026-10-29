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
 * An edge point: a docking point at the border of a class or a bend point in between. The backend
 * renders the stored points of an edge in this form, and the frontend sends them back in the same
 * form to save the layout of the edge. The position of a point within the list of points of its
 * edge is its sequence number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdgePointDTO {

    /**
     * The mRID of the point. A point the frontend creates gets a new UUID there, which becomes its
     * mRID once the point is saved.
     */
    private String id;

    private double x;

    private double y;

    /** The side of a docking point. Null for a bend point. */
    private Side side;

    /** The class a docking point is pinned to the border of. */
    public enum Side {
        /** The source class of the edge. */
        @JsonProperty("source")
        SOURCE,

        /** The target class of the edge. */
        @JsonProperty("target")
        TARGET
    }
}
