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

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Layout data of several kinds of elements of a diagram that a single user action changed together,
 * e.g. the classes and edges after moving classes or after the automatic layout. Every part is
 * optional and only the elements sent are updated.
 */
@Data
@NoArgsConstructor
public class DiagramLayoutDTO {

    private List<ClassPositionDTO> classes;

    private List<EdgeLayoutDTO> edges;

    /**
     * Whether {@link #edges} holds every edge the frontend shows, e.g. after the automatic layout.
     * The stored points of any other edge of the diagram, i.e. one hidden by a filter, are deleted
     * then, so it is drawn straight once it is shown again instead of along the old layout.
     */
    private boolean allEdges;

    private List<LabelPositionDTO> labels;
}
