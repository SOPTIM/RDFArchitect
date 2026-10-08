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

import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;
import java.util.UUID;

public interface UpdateEdgeLayoutUseCase {

    /**
     * Stores the points of the given edges of a package or custom diagram of a graph, see {@link
     * UpdateEdgeLayoutDataService#applyEdgeLayouts}.
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID the UUID of the diagram, or null for the diagram of the default package
     * @param edges the full, ordered list of points of every edge to store
     * @return the mRIDs the new points are stored under
     */
    List<EdgePointIdDTO> updateEdgeLayouts(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<EdgeLayoutDTO> edges);

    /**
     * Stores the points of the given edges of a merged diagram of a dataset, see {@link
     * UpdateEdgeLayoutDataService#applyEdgeLayouts}.
     *
     * @param datasetName the name of the dataset
     * @param diagramUUID the UUID of the cross-profile or custom diagram of the dataset
     * @param edges the full, ordered list of points of every edge to store
     * @return the mRIDs the new points are stored under
     */
    List<EdgePointIdDTO> updateEdgeLayouts(
            String datasetName, UUID diagramUUID, List<EdgeLayoutDTO> edges);
}
