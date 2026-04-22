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

public interface ReplaceCustomDiagramUseCase {

    /**
     * Replaces or creates a custom diagram in the specified graph.
     *
     * @param graphIdentifier the graph of the diagram to be replaced
     * @param diagramId       the id of the diagram to be replaced
     * @param diagram         the new diagram to replace the old one with
     */
    void replaceCustomDiagram(GraphIdentifier graphIdentifier, String diagramId, CustomDiagram diagram);

    /**
     * Replaces or creates a custom diagram in the specified dataset.
     *
     * @param datasetName the dataset of the diagram to be replaced
     * @param diagramId   the id of the diagram to be replaced
     * @param diagram     the new diagram to replace the old one with
     */
    void replaceCustomDiagram(String datasetName, String diagramId, CustomDiagram diagram);
}
