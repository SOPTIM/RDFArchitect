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

package org.rdfarchitect.models.dto.rendering.svelteflow;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.dto.rendering.RenderMergedDiagramUseCase;
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.MergedClasses;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.rdfarchitect.services.select.ListGraphsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Renders the merged diagrams by reading every graph of the dataset through the CIM facade and
 * merging the classes with {@link RenderCIMFacadeCollectionUseCase#renderMergedUML}. The
 * cross-profile diagram renders all merged classes, a workspace-level custom diagram only the ones
 * it holds.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "rendering.renderer",
        havingValue = "svelteflow",
        matchIfMissing = true)
public class RenderMergedDiagramSvelteFlowService implements RenderMergedDiagramUseCase {

    private final DatabasePort databasePort;
    private final GetCustomDiagramsUseCase getCustomDiagramsUseCase;
    private final FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;
    private final RenderCIMFacadeCollectionUseCase renderer;
    private final ListGraphsUseCase listGraphsUseCase;

    @Override
    public RenderingDataDTO renderCrossProfileDiagram(String datasetName) {
        var profiles = buildProfiles(datasetName);
        var diagram = getCustomDiagramsUseCase.getCrossProfileDiagram(datasetName, true, profiles);
        var layoutData =
                fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(
                        datasetName, diagram.getDiagramId());
        return renderer.renderMergedUML(profiles, layoutData);
    }

    @Override
    public RenderingDataDTO renderCustomDatasetDiagram(String datasetName, UUID diagramId) {
        var diagram = databasePort.getDatasetDiagrams(datasetName).get(diagramId);
        if (diagram == null) {
            throw new IllegalArgumentException(
                    "Diagram with ID " + diagramId + " not found in dataset " + datasetName);
        }
        var profiles = buildProfiles(datasetName);
        var layoutData =
                fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(
                        datasetName, diagramId);
        return renderMerged(profiles, diagram, layoutData);
    }

    private RenderingDataDTO renderMerged(
            List<CIMProfileModel> profiles, CustomDiagram diagram, RenderingLayoutData layoutData) {
        var renderedClassUris = MergedClasses.renderedClassUris(profiles, diagram.getClasses());
        return renderer.renderMergedUML(profiles, layoutData, renderedClassUris);
    }

    private List<CIMProfileModel> buildProfiles(String datasetName) {
        return CIMProfileModels.loadAll(
                databasePort,
                CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName),
                datasetName,
                null);
    }
}
