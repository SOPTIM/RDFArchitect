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
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeLayoutReconciler;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeResolver;
import org.rdfarchitect.services.rendering.CIMProfileModel;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.DiagramClassSelection;
import org.rdfarchitect.services.rendering.MergedClasses;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyncDiagramLayoutService implements SyncDiagramLayoutUseCase {

    private final DatabasePort databasePort;

    @Override
    public void syncDiagramLayout(GraphIdentifier graphIdentifier) {
        syncGraphDiagramLayout(graphIdentifier);
        syncDatasetDiagramLayout(graphIdentifier.datasetName());
    }

    @Override
    public void syncDatasetDiagramLayout(String datasetName) {
        var diagramLayoutModel =
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel();
        var diagramMRIDs = DLObjectFetcher.fetchDiagramMRIDs(diagramLayoutModel);
        if (diagramMRIDs.isEmpty()) {
            return;
        }
        var profiles = CIMProfileModels.loadAll(databasePort, Map.of(), datasetName, null);
        var edgeResolver = EdgeResolver.forMergedProfiles(profiles);
        var crossProfileDiagramUUID =
                databasePort.getCrossProfileDiagramInfo(datasetName).getCrossProfileDiagramUUID();
        var customDiagrams = databasePort.getDatasetDiagrams(datasetName);
        for (var diagramMRID : diagramMRIDs) {
            syncDiagram(
                    diagramLayoutModel,
                    diagramMRID,
                    shownClassesOfDatasetDiagram(
                            profiles,
                            diagramMRID.getUuid(),
                            crossProfileDiagramUUID,
                            customDiagrams),
                    edgeResolver);
        }
    }

    /**
     * Syncs the package diagrams and the custom diagrams of a graph, see {@link #syncDiagram(Model,
     * MRID, Optional, EdgeResolver)}.
     */
    private void syncGraphDiagramLayout(GraphIdentifier graphIdentifier) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
            var cimModel =
                    new CIMModelFacade(
                            graphIdentifier.graphUri(),
                            ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            var edgeResolver = EdgeResolver.forGraph(cimModel);
            var defaultPackageUUID = diagramLayout.getDefaultPackageMRID().getUuid();
            for (var diagramMRID : DLObjectFetcher.fetchDiagramMRIDs(diagramLayoutModel)) {
                syncDiagram(
                        diagramLayoutModel,
                        diagramMRID,
                        shownClassesOfGraphDiagram(
                                cimModel,
                                diagramMRID.getUuid(),
                                defaultPackageUUID,
                                ctx.getCustomDiagrams()),
                        edgeResolver);
            }
            ctx.commit();
        }
    }

    /**
     * Syncs a single diagram: a diagram without owner is deleted together with all its layout data,
     * otherwise the layout data of classes it no longer shows is deleted and its edges are
     * reconciled with the classes that remain.
     *
     * @param shownClasses the classes the diagram shows without any filter, or empty if the diagram
     *     has no owner anymore
     */
    private static void syncDiagram(
            Model diagramLayoutModel,
            MRID diagramMRID,
            Optional<Set<UUID>> shownClasses,
            EdgeResolver edgeResolver) {
        if (shownClasses.isEmpty()) {
            DLUpdates.deleteDiagramCascade(diagramLayoutModel, diagramMRID);
            return;
        }
        syncClassLayout(diagramLayoutModel, diagramMRID, shownClasses.get());
        EdgeLayoutReconciler.reconcileEdges(
                diagramLayoutModel, diagramMRID.getUuid(), edgeResolver);
    }

    /**
     * Deletes the layout data, including point and glue point, of every class of a diagram that the
     * diagram no longer shows. Layout data of shown classes is left as it is and never created
     * here.
     */
    private static void syncClassLayout(
            Model diagramLayoutModel, MRID diagramMRID, Set<UUID> shownClasses) {
        for (var classDO : DLObjectFetcher.fetchDiagramClassDOs(diagramLayoutModel, diagramMRID)) {
            if (!shownClasses.contains(classDO.getBelongsToIdentifiedObject().getUuid())) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, classDO.getMRID());
            }
        }
    }

    /**
     * The classes a diagram of a graph shows with no filter applied: the classes of a custom
     * diagram, or the classes of a package (the default package for classes without one) together
     * with the classes outside of it they relate to, see {@link DiagramClassSelection}.
     *
     * @return the UUIDs of the shown classes, or empty if the diagram belongs to neither a custom
     *     diagram nor a package of the graph
     */
    private static Optional<Set<UUID>> shownClassesOfGraphDiagram(
            ICIMModelFacade cimModel,
            UUID diagramUUID,
            UUID defaultPackageUUID,
            Map<UUID, CustomDiagram> customDiagrams) {
        var filter = new GraphFilter(true);
        var customDiagram = customDiagrams.get(diagramUUID);
        if (customDiagram != null) {
            filter.setAllowedUUIDs(
                    customDiagram.getClasses().stream()
                            .map(ClassInDiagram::getUuid)
                            .filter(Objects::nonNull)
                            .map(UUID::toString)
                            .toList());
        } else if (!diagramUUID.equals(defaultPackageUUID)) {
            if (cimModel.getCIMClassCategory(diagramUUID) == null) {
                return Optional.empty();
            }
            filter.setPackageUUID(diagramUUID.toString());
        }
        return Optional.of(
                DiagramClassSelection.select(cimModel, filter).classes().values().stream()
                        .map(ICIMClass::getUuid)
                        .collect(Collectors.toSet()));
    }

    /**
     * The classes a merged diagram shows, identified by their merged UUIDs: every class of the
     * dataset for the cross-profile diagram, the classes it holds for a custom diagram of the
     * dataset.
     *
     * @return the merged UUIDs of the shown classes, or empty if the diagram is neither the
     *     cross-profile diagram nor a custom diagram of the dataset
     */
    private static Optional<Set<UUID>> shownClassesOfDatasetDiagram(
            List<CIMProfileModel> profiles,
            UUID diagramUUID,
            UUID crossProfileDiagramUUID,
            Map<UUID, CustomDiagram> customDiagrams) {
        if (diagramUUID.equals(crossProfileDiagramUUID)) {
            return Optional.of(
                    profiles.stream()
                            .flatMap(profile -> profile.model().getCIMClasses().stream())
                            .map(
                                    cimClass ->
                                            CrossProfileUtils.mergedUuid(
                                                    cimClass.getUri().toString()))
                            .collect(Collectors.toSet()));
        }
        var customDiagram = customDiagrams.get(diagramUUID);
        if (customDiagram == null) {
            return Optional.empty();
        }
        return Optional.of(
                MergedClasses.renderedClassUris(profiles, customDiagram.getClasses()).stream()
                        .map(CrossProfileUtils::mergedUuid)
                        .collect(Collectors.toSet()));
    }
}
