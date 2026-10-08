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

package org.rdfarchitect.services.dl.update;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeLayoutReconciler;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeResolver;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutDataService;
import org.rdfarchitect.services.dl.update.labellayout.UpdateLabelLayoutService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class UpdateDiagramLayoutService implements UpdateDiagramLayoutUseCase {

    private final DatabasePort databasePort;

    @Override
    public List<EdgePointIdDTO> updateDiagramLayout(
            GraphIdentifier graphIdentifier, UUID diagramUUID, DiagramLayoutDTO diagramLayout) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var layout = ctx.getDiagramLayout();
            var resolvedDiagramUUID =
                    diagramUUID != null ? diagramUUID : layout.getDefaultPackageMRID().getUuid();
            var newPointIds =
                    applyDiagramLayout(
                            layout.getDiagramLayoutModel(),
                            resolvedDiagramUUID,
                            diagramLayout,
                            EdgeResolver.lazily(
                                    () ->
                                            EdgeResolver.forGraph(
                                                    graphIdentifier.graphUri(),
                                                    ctx.getRdfGraph())));
            ctx.commit();
            return newPointIds;
        }
    }

    @Override
    public List<EdgePointIdDTO> updateDiagramLayout(
            String datasetName, UUID diagramUUID, DiagramLayoutDTO diagramLayout) {
        return applyDiagramLayout(
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel(),
                diagramUUID,
                diagramLayout,
                EdgeResolver.lazily(() -> EdgeResolver.forDataset(databasePort, datasetName)));
    }

    /**
     * Stores classes, then edges, then labels of a diagram, inside a transaction the caller holds.
     */
    private static List<EdgePointIdDTO> applyDiagramLayout(
            Model diagramLayoutModel,
            UUID diagramUUID,
            DiagramLayoutDTO diagramLayout,
            Supplier<EdgeResolver> edgeResolver) {
        if (UpdateClassLayoutService.applyClassPositions(
                diagramLayoutModel,
                diagramUUID,
                Objects.requireNonNullElse(diagramLayout.getClasses(), List.of()))) {
            EdgeLayoutReconciler.reconcileEdges(
                    diagramLayoutModel, diagramUUID, edgeResolver.get());
        }
        var newPointIds =
                UpdateEdgeLayoutDataService.applyEdgeLayouts(
                        diagramLayoutModel,
                        diagramUUID,
                        Objects.requireNonNullElse(diagramLayout.getEdges(), List.of()),
                        edgeResolver);
        UpdateLabelLayoutService.applyLabelPositions(
                diagramLayoutModel,
                diagramUUID,
                Objects.requireNonNullElse(diagramLayout.getLabels(), List.of()));
        return newPointIds;
    }
}
