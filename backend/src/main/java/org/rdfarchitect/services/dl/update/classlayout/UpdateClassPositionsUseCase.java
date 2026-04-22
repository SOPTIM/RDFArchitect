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

package org.rdfarchitect.services.dl.update.classlayout;

import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.Diagram;

import java.util.List;
import java.util.UUID;

public interface UpdateClassPositionsUseCase {

    /**
     * Updates the positions of classes within the {@link Diagram diagram} associated with the given
     * package.
     *
     * @param graphIdentifier the identifier of the graph
     * @param packageUUID the UUID of the package identifying the diagram
     * @param classPositionDTOList the list of class repositioning instructions containing class
     *     UUIDs and their new positions
     */
    void updateClassPositions(
            GraphIdentifier graphIdentifier,
            UUID packageUUID,
            List<ClassPositionDTO> classPositionDTOList);
}
