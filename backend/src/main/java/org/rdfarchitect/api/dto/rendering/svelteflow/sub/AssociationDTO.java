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

import java.util.UUID;

/**
 * DTO representing an association end rendered as text inside a SvelteFlow class node. The
 * association is still drawn as an edge independently of this.
 */
@Data
@Builder
public class AssociationDTO {

    /** The UUID of the association end. */
    private UUID uuid;

    /** The role name of this association end, or null when the model holds none. */
    private String label;

    /** The label of the class this association end points to. */
    private String type;

    private String multiplicity;
    private String graphUri;
    private String graphKeyword;
    private String color;
}
