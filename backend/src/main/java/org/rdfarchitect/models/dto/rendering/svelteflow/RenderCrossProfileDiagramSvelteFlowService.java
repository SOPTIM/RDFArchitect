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

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.dto.rendering.RenderCrossProfileDiagramUseCase;
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the cross-profile (merged) diagram by reading every graph of the dataset through the CIM
 * facade and merging the classes with {@link RenderCIMFacadeCollectionUseCase#renderMergedUML}.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "rendering.renderer",
        havingValue = "svelteflow",
        matchIfMissing = true)
public class RenderCrossProfileDiagramSvelteFlowService
        implements RenderCrossProfileDiagramUseCase {

    private final DatabasePort databasePort;
    private final GetCustomDiagramsUseCase getCustomDiagramsUseCase;
    private final FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;
    private final RenderCIMFacadeCollectionUseCase renderer;

    @Override
    public RenderingDataDTO renderCrossProfileDiagram(String datasetName) {
        var diagram = getCustomDiagramsUseCase.getCrossProfileDiagram(datasetName, true);
        var layoutData =
                fetchRenderingLayoutDataUseCase.fetchGlobalRenderingLayoutData(
                        datasetName, diagram.getDiagramId());
        return renderer.renderMergedUML(buildProfiles(datasetName), layoutData);
    }

    private List<CIMProfileModel> buildProfiles(String datasetName) {
        var profiles = new ArrayList<CIMProfileModel>();
        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            profiles.add(CIMProfileModels.load(databasePort, datasetName, graphUri));
        }
        return profiles;
    }
}
