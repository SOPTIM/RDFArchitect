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

package org.rdfarchitect.services.dl.update.labellayout;

import org.rdfarchitect.api.dto.dl.LabelPositionDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;
import java.util.UUID;

public interface UpdateLabelPositionsUseCase {

    /**
     * Updates the offsets of movable labels within the diagram associated with the given package.
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID the UUID of the package identifying the diagram
     * @param labelPositionDTOList the labels to reposition; entries without an offset are reset to
     *     their default placement
     */
    void updateLabelPositions(
            GraphIdentifier graphIdentifier,
            UUID diagramUUID,
            List<LabelPositionDTO> labelPositionDTOList);

    /**
     * Updates the offsets of movable labels within the diagram associated with the given custom
     * diagram.
     *
     * @param datasetName the literal name of the dataset
     * @param diagramUUID the UUID of the custom diagram identifying the diagram
     * @param labelPositionDTOList the labels to reposition; entries without an offset are reset to
     *     their default placement
     */
    void updateLabelPositions(
            String datasetName, UUID diagramUUID, List<LabelPositionDTO> labelPositionDTOList);
}
