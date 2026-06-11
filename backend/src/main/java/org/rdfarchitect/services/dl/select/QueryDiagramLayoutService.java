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

package org.rdfarchitect.services.dl.select;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayoutDelta;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryDiagramLayoutService implements FetchRenderingLayoutDataUseCase {

    public final DatabasePort databasePort;

    @Override
    public RenderingLayoutData fetchRenderingLayoutData(
            GraphIdentifier graphIdentifier, UUID packageUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            DiagramLayoutDelta diagramLayoutDelta = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayoutDelta.getDiagramLayoutModel();
            return fetchRenderingLayoutData(
                    diagramLayoutDelta.getDefaultPackageMRID().getUuid(),
                    diagramLayoutModel,
                    packageUUID);
        }
    }

    @Override
    public RenderingLayoutData fetchGlobalRenderingLayoutData(String datasetName, UUID diagramId) {
        var diagramLayout = databasePort.getDatasetDiagramLayout(datasetName);
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
        return fetchRenderingLayoutData(
                diagramLayout.getDefaultPackageMRID().getUuid(), diagramLayoutModel, diagramId);
    }

    private RenderingLayoutData fetchRenderingLayoutData(
            UUID defaultPackageUUID, Model diagramLayoutModel, UUID diagramId) {

        Map<UUID, DiagramObjectPoint> classLayoutingData;
        if (diagramId == null) {
            classLayoutingData =
                    DLObjectFetcher.fetchDiagramDOPPerClass(diagramLayoutModel, defaultPackageUUID);
        } else {
            classLayoutingData =
                    DLObjectFetcher.fetchDiagramDOPPerClass(diagramLayoutModel, diagramId);
        }

        return RenderingLayoutData.builder().classLayoutingData(classLayoutingData).build();
    }
}
