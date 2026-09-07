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

package org.rdfarchitect.dl.data.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYOffset;

@Data
@SuperBuilder(toBuilder = true)
public class DiagramObject {

    private MRID mRID;

    /** The display name, only carried by objects that stand for a named resource. */
    private String name;

    private DiagramObjectStyle style;

    private MRID belongsToDiagram;

    private MRID belongsToIdentifiedObject;

    /**
     * The placement relative to whatever the object is anchored to, for objects that are placed by
     * an offset instead of by a {@link DiagramObjectPoint}.
     */
    private XYOffset offset;
}
