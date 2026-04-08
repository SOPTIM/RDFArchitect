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

package org.rdfarchitect.services.diagrams;

import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;

public interface GetFullClassesForDiagramUseCase {

    /**
     * Retrieves the full models of the classes belonging to a custom diagram
     *
     * @param graphIdentifier The identifier of the graph containing the diagram.
     * @param diagramId       The unique identifier of the diagram.
     *
     * @return A list of ClassInDiagram objects representing the classes in the diagram.
     */
    List<ClassDTO> getFullClasses(GraphIdentifier graphIdentifier, String diagramId);

    /**
     * Retrieves the full models of the classes belonging to a custom diagram
     *
     * @param datasetName The name of the dataset containing the diagram.
     * @param diagramId   The unique identifier of the diagram.
     *
     * @return A list of ClassInDiagram objects representing the classes in the diagram.
     */
    List<ClassDTO> getFullClasses(String datasetName, String diagramId);
}
