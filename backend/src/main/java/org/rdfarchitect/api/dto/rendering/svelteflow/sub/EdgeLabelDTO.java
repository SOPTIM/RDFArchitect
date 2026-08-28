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
 * DTO representing a label rendered next to one end of a SvelteFlow edge. Labels are rendered as
 * their own nodes so they can be dragged independently of the edge.
 */
@Data
@Builder
public class EdgeLabelDTO {

    /** The end of the edge a label is anchored to. */
    public enum Anchor {
        SOURCE,
        TARGET
    }

    private Anchor anchor;

    /** The UUID of the CIM resource this label belongs to, e.g. an association end. */
    private UUID identifiedObjectUUID;

    /** The label kind, e.g. {@code multiplicity}. Together with the UUID it identifies a label. */
    private String kind;

    private String text;

    /**
     * The manually placed position as an offset relative to the class the label is anchored to, or
     * {@code null} when the label has never been moved and falls back to its default placement.
     */
    private PositionDTO offset;
}
