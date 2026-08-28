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
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.RenderCIMFacadeCollectionUseCase;
import org.rdfarchitect.services.select.ListGraphsUseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetRenderingDataService implements GetRenderingDataUseCase {

    private final DatabasePort databasePort;
    private final RenderCIMFacadeCollectionUseCase renderer;
    private final ListGraphsUseCase listGraphsUseCase;

    @Override
    public RenderingDataDTO getRenderingData(
            GraphIdentifier graphIdentifier, GraphFilter filter, UUID packageUUID) {
        Graph rdfGraphCopy;
        RenderingLayoutData layoutData;

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            rdfGraphCopy = GraphUtils.deepCopy(ctx.getRdfGraph());
            layoutData = fetchLayoutData(ctx, packageUUID);
        }

        return render(graphIdentifier, filter, rdfGraphCopy, layoutData);
    }

    @Override
    public RenderingDataDTO getCustomDiagramRenderingData(
            GraphIdentifier graphIdentifier, GraphFilter filter, UUID diagramUUID) {
        Graph rdfGraphCopy;
        RenderingLayoutData layoutData;

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram == null) {
                throw new IllegalArgumentException(
                        "Diagram with ID "
                                + diagramUUID
                                + " not found in graph "
                                + graphIdentifier);
            }
            filter.setAllowedUUIDs(
                    diagram.getClasses().stream()
                            .map(ClassInDiagram::getUuid)
                            .filter(Objects::nonNull)
                            .map(UUID::toString)
                            .toList());
            filter.setIncludeRelationsToExternalPackages(false);
            rdfGraphCopy = GraphUtils.deepCopy(ctx.getRdfGraph());
            layoutData = fetchLayoutData(ctx, diagramUUID);
        }

        return render(graphIdentifier, filter, rdfGraphCopy, layoutData);
    }

    private RenderingLayoutData fetchLayoutData(GraphContext ctx, UUID diagramUUID) {
        var diagramLayout = ctx.getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
        var classLayoutingData =
                diagramUUID == null
                        ? DLObjectFetcher.fetchDiagramDOPPerClass(
                                diagramLayoutModel, diagramLayout.getDefaultPackageMRID().getUuid())
                        : DLObjectFetcher.fetchDiagramDOPPerClass(diagramLayoutModel, diagramUUID);
        return RenderingLayoutData.builder().classLayoutingData(classLayoutingData).build();
    }

    private RenderingDataDTO render(
            GraphIdentifier graphIdentifier,
            GraphFilter filter,
            Graph rdfGraphCopy,
            RenderingLayoutData layoutData) {
        var cimModel =
                new CIMModelFacade(
                        graphIdentifier.graphUri(), ModelFactory.createModelForGraph(rdfGraphCopy));

        if (!filter.isIncludePropertiesFromOtherProfiles()) {
            return renderer.renderUML(cimModel, filter, layoutData, List.of(), null, null);
        }

        var datasetName = graphIdentifier.datasetName();
        var keywords = CIMProfileModels.keywordsByGraphUri(listGraphsUseCase, datasetName);
        var otherProfiles =
                CIMProfileModels.loadAll(
                        databasePort, keywords, datasetName, graphIdentifier.graphUri());
        var primaryColor =
                databasePort
                        .getCrossProfileDiagramInfo(datasetName)
                        .getColor(graphIdentifier.graphUri());

        return renderer.renderUML(
                cimModel,
                filter,
                layoutData,
                otherProfiles,
                primaryColor,
                keywords.get(graphIdentifier.graphUri()));
    }
}
