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

/** The mRID a new point of an edge was stored under, for the id the frontend sent it with. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdgePointIdDTO {

    /** The id the frontend sent the point with. */
    @JsonProperty("clientId")
    private String clientId;

    /** The mRID the point is stored under, the id to send for it from now on. */
    @JsonProperty("id")
    private String id;
}
