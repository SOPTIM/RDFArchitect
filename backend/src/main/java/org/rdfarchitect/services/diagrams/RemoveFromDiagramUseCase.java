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

import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface RemoveFromDiagramUseCase {

    /**
     * Removes a class from a specified custom diagram.
     *
     * @param graphIdentifier the graph of the custom diagram
     * @param diagramId       the id of the custom diagram
     * @param classId         the uuid of the class to remove from the custom diagram
     */
    void removeFromDiagram(GraphIdentifier graphIdentifier, String diagramId, UUID classId);

    /**
     * Removes a class from all diagrams
     *
     * @param graphIdentifier the graph of the custom diagram
     * @param classId         the uuid of the class to be removed
     */
    void removeFromAllDiagrams(GraphIdentifier graphIdentifier, UUID classId);
}
