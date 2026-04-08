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
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;

import java.util.List;

public interface GetCustomDiagramsUseCase {

    /**
     * Lists the custom diagrams belonging to a single graph.
     *
     * @param graphIdentifier The graph identifier.
     *
     * @return The custom diagrams for the graph.
     */
    List<CustomDiagram> getCustomDiagramsForGraph(GraphIdentifier graphIdentifier);

     /**
     * Lists the custom diagrams belonging to a single dataset.
     *
     * @param datasetName The name of the dataset.
     *
     * @return The custom diagrams for the dataset.
     */
    List<CustomDiagram> getCustomDiagramsForDataset(String datasetName);

}
