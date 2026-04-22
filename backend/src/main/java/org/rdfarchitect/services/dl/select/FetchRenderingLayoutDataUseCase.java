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

package org.rdfarchitect.services.dl.select;

import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface FetchRenderingLayoutDataUseCase {

    /**
     * Fetches all layout data necessary for constructing the DTO object for rendering
     *
     * @param graphIdentifier the identifier of the graph
     * @param packageUUID     the UUID of the package for which the layout information shall be fetched
     *
     * @return {@link RenderingLayoutData DTO object containing necessary layout information for rendering}
     */
    RenderingLayoutData fetchRenderingLayoutData(GraphIdentifier graphIdentifier, UUID packageUUID);

    /**
     * Fetches all layout data necessary for constructing the DTO object for rendering
     *
     * @param datasetName the literal name of the dataset
     * @param diagramId     the UUID of the custom diagram for which the layout information shall be fetched
     *
     * @return {@link RenderingLayoutData DTO object containing necessary layout information for rendering}
     */
    RenderingLayoutData fetchGlobalRenderingLayoutData(String datasetName, UUID diagramId);
}
