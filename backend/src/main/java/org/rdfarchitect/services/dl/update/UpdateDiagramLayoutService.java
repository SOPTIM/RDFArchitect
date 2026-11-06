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
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.services.ChangeDescriptions;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeLayoutSync;
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
    public void updateDiagramLayout(
            GraphIdentifier graphIdentifier, UUID diagramUUID, DiagramLayoutDTO diagramLayout) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var layout = ctx.getDiagramLayout();
            var diagramLayoutModel = layout.getDiagramLayoutModel();
            var resolvedDiagramUUID =
                    diagramUUID != null ? diagramUUID : layout.getDefaultPackageMRID().getUuid();
            var firstLayout =
                    DLObjectFetcher.fetchDiagram(diagramLayoutModel, resolvedDiagramUUID) == null;
            applyDiagramLayout(
                    diagramLayoutModel,
                    resolvedDiagramUUID,
                    diagramLayout,
                    EdgeResolver.lazily(
                            () ->
                                    EdgeResolver.forGraph(
                                            graphIdentifier.graphUri(), ctx.getRdfGraph())));
            DiagramLayoutServiceUtils.commitLayout(
                    transaction,
                    firstLayout || diagramLayout.isAutomatic(),
                    describe(
                            diagramLayout,
                            "package",
                            ChangeDescriptions.packageName(
                                    ctx.getRdfGraph(), diagramLayoutModel, resolvedDiagramUUID)));
        }
    }

    @Override
    public void updateDiagramLayout(
            String datasetName, UUID diagramUUID, DiagramLayoutDTO diagramLayout) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var diagramLayoutModel = transaction.layout().getDiagramLayoutModel();
            var firstLayout = DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null;
            applyDiagramLayout(
                    diagramLayoutModel,
                    diagramUUID,
                    diagramLayout,
                    EdgeResolver.lazily(() -> EdgeResolver.forDataset(databasePort, datasetName)));
            DiagramLayoutServiceUtils.commitLayout(
                    transaction,
                    firstLayout || diagramLayout.isAutomatic(),
                    describe(
                            diagramLayout,
                            "diagram",
                            ChangeDescriptions.diagramName(diagramLayoutModel, diagramUUID)));
        }
    }

    /**
     * What a layout change is recorded as in the changelog: laying the diagram out anew, or moving
     * the classes, the edge points or the labels, whichever the user moved; the edges and labels
     * sent along with moved classes moved with them.
     */
    private static String describe(DiagramLayoutDTO diagramLayout, String kind, String name) {
        if (diagramLayout.isAllEdges()) {
            return ChangeDescriptions.actionOn("Laid out", kind, name);
        }
        String action;
        if (!Objects.requireNonNullElse(diagramLayout.getClasses(), List.of()).isEmpty()) {
            action = "Moved classes";
        } else if (!Objects.requireNonNullElse(diagramLayout.getEdges(), List.of()).isEmpty()) {
            action = UpdateEdgeLayoutDataService.EDGE_POINTS_MOVED;
        } else {
            action = "Moved labels";
        }
        return ChangeDescriptions.actionIn(action, kind, name);
    }

    /**
     * Stores classes, then edges, then labels of a diagram, inside a transaction the caller holds.
     */
    private static void applyDiagramLayout(
            Model diagramLayoutModel,
            UUID diagramUUID,
            DiagramLayoutDTO diagramLayout,
            Supplier<EdgeResolver> edgeResolver) {
        if (UpdateClassLayoutService.applyClassPositions(
                diagramLayoutModel,
                diagramUUID,
                Objects.requireNonNullElse(diagramLayout.getClasses(), List.of()))) {
            EdgeLayoutSync.syncEdges(diagramLayoutModel, diagramUUID, edgeResolver.get());
        }
        var edges = Objects.requireNonNullElse(diagramLayout.getEdges(), List.<EdgeLayoutDTO>of());
        if (diagramLayout.isAllEdges()) {
            UpdateEdgeLayoutDataService.deletePointsOfOtherEdges(
                    diagramLayoutModel, diagramUUID, edges);
        }
        UpdateEdgeLayoutDataService.applyEdgeLayouts(
                diagramLayoutModel, diagramUUID, edges, edgeResolver);
        UpdateLabelLayoutService.applyLabelPositions(
                diagramLayoutModel,
                diagramUUID,
                Objects.requireNonNullElse(diagramLayout.getLabels(), List.of()));
    }
}
