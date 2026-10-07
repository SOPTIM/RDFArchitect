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

import org.rdfarchitect.api.dto.dl.BendPointDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;
import java.util.UUID;

public interface UpdateBendPointsUseCase {

    /**
     * Replaces all bend points of an edge with the provided list of bend points. Passing the full
     * list covers creation, movement and deletion of bend points in a single call, consistent with
     * the replace strategy used elsewhere. Does nothing if the edge has no diagram object in the
     * diagram, i.e. if one of its classes has no layout data there.
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID the UUID of the diagram the edge belongs to
     * @param edge the edge whose bend points are replaced
     * @param bendPoints the new, ordered list of bend points for the edge
     */
    void updateBendPoints(
            GraphIdentifier graphIdentifier,
            UUID diagramUUID,
            EdgeKey edge,
            List<BendPointDTO> bendPoints);
}
