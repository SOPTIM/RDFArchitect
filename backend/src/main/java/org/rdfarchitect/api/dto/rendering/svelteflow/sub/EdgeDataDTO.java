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

import java.util.List;
import java.util.UUID;

/** DTO representing the specific data object in a SvelteFlow edge. */
@Data
@Builder
public class EdgeDataDTO {

    /**
     * Identifies the edge for its layout data together with {@link #targetObject}: the sub class of
     * an inheritance or the association end whose domain is the source of the edge. In merged
     * diagrams this is the merged UUID. Sent back unchanged when the layout of the edge is saved.
     */
    private UUID sourceObject;

    /**
     * The super class of an inheritance or the inverse association end, see {@link #sourceObject}.
     */
    private UUID targetObject;

    private EdgeLabelDTO sourceMultiplicityLabel;
    private EdgeLabelDTO targetMultiplicityLabel;
    private EdgeLabelDTO sourceAssociationLabel;
    private EdgeLabelDTO targetAssociationLabel;

    private boolean useToAssociation;
    private boolean useFromAssociation;
    private String graphUri;
    private String graphKeyword;
    private String color;
    private List<BendPointDTO> bendPoints;
}
