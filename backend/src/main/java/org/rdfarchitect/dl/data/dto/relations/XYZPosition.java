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

package org.rdfarchitect.dl.data.dto.relations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A position in a diagram, as the diagram layout stores it and as the diagram is rendered with. The
 * z position orders overlapping classes; it is null where the diagram layout stores none, e.g. for
 * the points of edges.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class XYZPosition {

    private double x;

    private double y;

    private Integer z;
}
