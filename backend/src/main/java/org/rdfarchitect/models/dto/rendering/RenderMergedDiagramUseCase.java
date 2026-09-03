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

package org.rdfarchitect.models.dto.rendering;

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;

import java.util.UUID;

/**
 * Renders the diagrams that show classes merged across the profiles of a dataset: the cross-profile
 * diagram, which holds every class, and the workspace-level custom diagrams, which hold a manually
 * chosen subset. Graph-level custom diagrams are not merged - they render like a package diagram.
 */
public interface RenderMergedDiagramUseCase {

    /**
     * Renders the cross-profile (merged) diagram for a dataset from the CIM facades of all its
     * graphs.
     *
     * @param datasetName The name of the dataset.
     * @return The rendering data for the merged diagram.
     */
    RenderingDataDTO renderCrossProfileDiagram(String datasetName);

    /**
     * Renders a workspace-level custom diagram: the merged diagram narrowed down to the classes the
     * diagram holds.
     *
     * @param datasetName The name of the dataset.
     * @param diagramId The id of the custom diagram.
     * @return The rendering data for the merged diagram.
     */
    RenderingDataDTO renderCustomDatasetDiagram(String datasetName, UUID diagramId);
}
