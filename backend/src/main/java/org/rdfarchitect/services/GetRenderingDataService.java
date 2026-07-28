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

package org.rdfarchitect.services;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetRenderingDataService implements GetRenderingDataUseCase {

    private final DatabasePort databasePort;
    private final RenderCIMFacadeCollectionUseCase renderer;

    @Override
    public RenderingDataDTO getRenderingData(
            GraphIdentifier graphIdentifier, GraphFilter filter, UUID packageUUID) {
        Graph rdfGraphCopy;
        RenderingLayoutData layoutData;

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            rdfGraphCopy = GraphUtils.deepCopy(ctx.getRdfGraph());

            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
            var classLayoutingData =
                    packageUUID == null
                            ? DLObjectFetcher.fetchDiagramDOPPerClass(
                                    diagramLayoutModel,
                                    diagramLayout.getDefaultPackageMRID().getUuid())
                            : DLObjectFetcher.fetchDiagramDOPPerClass(
                                    diagramLayoutModel, packageUUID);
            layoutData =
                    RenderingLayoutData.builder().classLayoutingData(classLayoutingData).build();
        }

        var cimModel =
                new CIMModelFacade(
                        graphIdentifier.graphUri(), ModelFactory.createModelForGraph(rdfGraphCopy));

        var otherProfiles =
                filter.isIncludePropertiesFromOtherProfiles()
                        ? loadOtherProfiles(graphIdentifier)
                        : List.<CIMProfileModel>of();

        return renderer.renderUML(cimModel, filter, layoutData, otherProfiles);
    }

    private List<CIMProfileModel> loadOtherProfiles(GraphIdentifier graphIdentifier) {
        var datasetName = graphIdentifier.datasetName();
        var otherProfiles = new ArrayList<CIMProfileModel>();

        for (var graphUri : databasePort.listGraphUris(datasetName)) {
            if (graphUri.equals(graphIdentifier.graphUri())) {
                continue;
            }
            otherProfiles.add(CIMProfileModels.load(databasePort, datasetName, graphUri));
        }

        return otherProfiles;
    }
}
