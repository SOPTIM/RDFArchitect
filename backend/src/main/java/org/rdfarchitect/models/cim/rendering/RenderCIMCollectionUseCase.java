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

package org.rdfarchitect.models.cim.rendering;

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;

import java.util.UUID;

/**
 * Converts a {@link CIMCollection} to a DTO that contains data required to render a UML diagram.
 */
public interface RenderCIMCollectionUseCase {

    /**
     * Generates the rendering data for a CIMCollection, that belongs to a specific graph.
     *
     * @param cimCollection the CIMCollection to be converted
     * @param graphIdentifier the dataset name and graph uri of the graph that the collection
     *     belongs to
     * @param diagramId the id of the package or custom diagram that should be rendered
     * @return a dto that contains all data required to render a UML diagram for the given
     *     collection
     */
    RenderingDataDTO renderUML(
            CIMCollection cimCollection, GraphIdentifier graphIdentifier, UUID diagramId);

    /**
     * Generates the rendering data for a CIMCollection, that belongs not to one specific graph, but
     * the dataset as a whole.
     *
     * @param cimCollection the CIMCollection to be converted
     * @param datasetName the dataset name that the collection belongs to
     * @param diagramId the id of the custom diagram that should be rendered
     * @return a dto that contains all data required to render a UML diagram for the given
     *     collection
     */
    RenderingDataDTO renderGlobalUML(
            CIMCollection cimCollection, String datasetName, UUID diagramId);
}
