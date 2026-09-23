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

package org.rdfarchitect.services.dl.update.edgelayout;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;

import java.util.UUID;

public interface SyncEdgeCreatedUseCase {

    /**
     * Creates the edge diagram object for a newly created inheritance or association, in the
     * package diagram the "from" class belongs to, and additionally in the cross-profile diagram if
     * the dataset spans more than one schema.
     *
     * @param graphIdentifier the identifier of the graph the edge belongs to
     * @param fromClassUUID the UUID of the class the edge originates from, used to resolve the
     *     package diagram the edge diagram object is created in
     * @param identifiedObjectUUID the UUID of the identified object the edge references (the
     *     association for associations, the sub class for inheritances)
     * @param edgeName the name of the edge diagram object
     * @param style the style of the edge, {@link DiagramObjectStyle#INHERITANCE} or {@link
     *     DiagramObjectStyle#ASSOCIATION}
     */
    void syncEdgeCreated(
            GraphIdentifier graphIdentifier,
            UUID fromClassUUID,
            UUID identifiedObjectUUID,
            String edgeName,
            DiagramObjectStyle style);
}
