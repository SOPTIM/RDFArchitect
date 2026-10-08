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

package org.rdfarchitect.services.dl.update;

import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;
import java.util.UUID;

public interface UpdateDiagramLayoutUseCase {

    /**
     * Stores the layout of the classes, edges and labels a single user action changed in a package
     * or custom diagram of a graph, all in one transaction. Only the elements sent are updated:
     * classes as for the class positions, edges as for the edge layouts and labels as for the label
     * positions. Classes come first, so edges between classes that only get their layout data in
     * the same request can be stored as well.
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID the UUID of the diagram, or null for the diagram of the default package
     * @param diagramLayout the layout of the changed elements
     * @return the mRIDs the new points of the edges are stored under
     */
    List<EdgePointIdDTO> updateDiagramLayout(
            GraphIdentifier graphIdentifier, UUID diagramUUID, DiagramLayoutDTO diagramLayout);

    /**
     * Stores the layout of the classes, edges and labels a single user action changed in a merged
     * diagram of a dataset, see {@link #updateDiagramLayout(GraphIdentifier, UUID,
     * DiagramLayoutDTO)}.
     *
     * @param datasetName the name of the dataset
     * @param diagramUUID the UUID of the cross-profile or custom diagram of the dataset
     * @param diagramLayout the layout of the changed elements
     * @return the mRIDs the new points of the edges are stored under
     */
    List<EdgePointIdDTO> updateDiagramLayout(
            String datasetName, UUID diagramUUID, DiagramLayoutDTO diagramLayout);
}
