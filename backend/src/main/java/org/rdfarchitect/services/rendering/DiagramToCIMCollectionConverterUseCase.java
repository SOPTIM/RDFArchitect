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

package org.rdfarchitect.services.rendering;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;

/**
 * Converts a Diagram to a {@link CIMCollection}.
 */
public interface DiagramToCIMCollectionConverterUseCase {

    /**
     * Converts a Diagram to a {@link CIMCollection}.
     *
     * @param graphIdentifier The graph containing the diagram
     * @param diagramId       The ID of the diagram to be converted.
     *
     * @return The {@link CIMCollection}.
     */
    CIMCollection convert(GraphIdentifier graphIdentifier, String diagramId);

    /**
     * Converts a Diagram to a {@link CIMCollection}.
     *
     * @param datasetName The dataset containing the diagram
     * @param diagramId   The ID of the diagram to be converted.
     *
     * @return The {@link CIMCollection}.
     */
    CIMCollection convert(String datasetName, String diagramId);
}
