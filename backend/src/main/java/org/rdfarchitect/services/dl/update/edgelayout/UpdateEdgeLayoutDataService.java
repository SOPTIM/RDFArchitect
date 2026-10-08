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

package org.rdfarchitect.services.dl.update.edgelayout;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateEdgeLayoutDataService implements UpdateEdgeLayoutUseCase {

    private final DatabasePort databasePort;

    @Override
    public List<EdgePointIdDTO> updateEdgeLayouts(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<EdgeLayoutDTO> edges) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var resolvedDiagramUUID =
                    diagramUUID != null
                            ? diagramUUID
                            : diagramLayout.getDefaultPackageMRID().getUuid();
            var newPointIds =
                    applyEdgeLayouts(
                            diagramLayout.getDiagramLayoutModel(),
                            resolvedDiagramUUID,
                            edges,
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
    public List<EdgePointIdDTO> updateEdgeLayouts(
            String datasetName, UUID diagramUUID, List<EdgeLayoutDTO> edges) {
        return applyEdgeLayouts(
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel(),
                diagramUUID,
                edges,
                EdgeResolver.lazily(() -> EdgeResolver.forDataset(databasePort, datasetName)));
    }

    /**
     * Stores the points of the given edges of a diagram, inside a transaction the caller holds. The
     * points sent for an edge are its full, ordered list from source to target and replace the
     * stored ones:
     *
     * <ul>
     *   <li>a point whose id is the mRID of a stored point of the edge updates that point, and is
     *       left untouched if neither position, sequence number nor glue point changed
     *   <li>any other point is new and stored under an mRID of its own, which is returned for the
     *       id it was sent with
     *   <li>stored points that are not sent anymore are deleted
     *   <li>the first and the last point are glued to the glue point of the source and the target
     *       class if they are end points
     * </ul>
     *
     * The sequence numbers count from the side of the identified object of the edge diagram object,
     * so the points are reversed if the edge was drawn the other way round. A class of an edge that
     * has no layout data in the diagram yet gets it at 0;0, where it is shown without layout data,
     * so the edge gets its diagram object. Edges that are not part of the schema or that do not
     * name both identified objects and both classes are ignored.
     *
     * @param diagramLayoutModel the diagram layout holding the diagram
     * @param diagramUUID the UUID of the diagram
     * @param edges the edges to store
     * @param edgeResolver resolves the edges between the classes of the diagram, only needed if
     *     class layout data has to be created
     * @return the mRIDs the new points are stored under
     */
    public static List<EdgePointIdDTO> applyEdgeLayouts(
            Model diagramLayoutModel,
            UUID diagramUUID,
            List<EdgeLayoutDTO> edges,
            Supplier<EdgeResolver> edgeResolver) {
        var completeEdges =
                edges.stream().filter(UpdateEdgeLayoutDataService::identifiesItsEdge).toList();
        if (completeEdges.isEmpty()) {
            return List.of();
        }
        if (ensureClassLayoutData(diagramLayoutModel, diagramUUID, completeEdges)) {
            EdgeLayoutReconciler.reconcileEdges(
                    diagramLayoutModel, diagramUUID, edgeResolver.get());
        }

        var edgeDOs = edgeDOsByKey(diagramLayoutModel, diagramUUID);
        var gluePoints = new HashMap<UUID, MRID>();
        var newPointIds = new ArrayList<EdgePointIdDTO>();
        for (var edge : completeEdges) {
            var edgeDO =
                    edgeDOs.get(
                            EdgeKey.of(
                                    DiagramObjectStyle.byName(edge.getKind()),
                                    edge.getSourceObject(),
                                    edge.getTargetObject()));
            if (edgeDO == null) {
                continue;
            }
            newPointIds.addAll(
                    applyEdgeLayout(diagramLayoutModel, diagramUUID, edgeDO, edge, gluePoints));
        }
        return newPointIds;
    }

    /** Whether an edge layout names both identified objects and both classes of its edge. */
    private static boolean identifiesItsEdge(EdgeLayoutDTO edge) {
        return edge.getSourceObject() != null
                && edge.getTargetObject() != null
                && edge.getSourceClass() != null
                && edge.getTargetClass() != null;
    }

    /**
     * Creates the layout data of the classes of the given edges that have none in the diagram, at
     * 0;0.
     *
     * @return whether layout data was created for a class
     */
    private static boolean ensureClassLayoutData(
            Model diagramLayoutModel, UUID diagramUUID, List<EdgeLayoutDTO> edges) {
        if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
        }
        var classUUIDs =
                DLObjectFetcher.fetchDiagramClassDOs(diagramLayoutModel, new MRID(diagramUUID))
                        .stream()
                        .map(classDO -> classDO.getBelongsToIdentifiedObject().getUuid())
                        .collect(Collectors.toSet());
        var createdClassLayoutData = false;
        for (var edge : edges) {
            for (var classUUID : List.of(edge.getSourceClass(), edge.getTargetClass())) {
                if (classUUIDs.add(classUUID)) {
                    DiagramLayoutServiceUtils.insertClassLayoutData(
                            diagramLayoutModel, diagramUUID, "", classUUID, 0, 0);
                    createdClassLayoutData = true;
                }
            }
        }
        return createdClassLayoutData;
    }

    private static Map<EdgeKey, DiagramObject> edgeDOsByKey(
            Model diagramLayoutModel, UUID diagramUUID) {
        var edgeDOs = new HashMap<EdgeKey, DiagramObject>();
        for (var edgeDO :
                DLObjectFetcher.fetchDiagramEdgeDOs(diagramLayoutModel, new MRID(diagramUUID))) {
            var key = EdgeKey.of(edgeDO);
            if (key != null) {
                edgeDOs.putIfAbsent(key, edgeDO);
            }
        }
        return edgeDOs;
    }

    /**
     * Replaces the stored points of one edge, see {@link #applyEdgeLayouts}.
     *
     * @param gluePoints the glue points of the classes of the diagram resolved so far
     * @return the mRIDs the new points of the edge are stored under
     */
    private static List<EdgePointIdDTO> applyEdgeLayout(
            Model diagramLayoutModel,
            UUID diagramUUID,
            DiagramObject edgeDO,
            EdgeLayoutDTO edge,
            Map<UUID, MRID> gluePoints) {
        var points = new ArrayList<>(Objects.requireNonNullElse(edge.getPoints(), List.of()));
        var firstClass = edge.getSourceClass();
        var lastClass = edge.getTargetClass();
        if (!edge.getSourceObject().equals(edgeDO.getBelongsToIdentifiedObject().getUuid())) {
            Collections.reverse(points);
            firstClass = edge.getTargetClass();
            lastClass = edge.getSourceClass();
        }

        var storedPoints = new LinkedHashMap<String, DiagramObjectPoint>();
        for (var storedPoint :
                DLObjectFetcher.fetchDOPsForDO(diagramLayoutModel, edgeDO.getMRID())) {
            storedPoints.put(storedPoint.getMRID().getUuid().toString(), storedPoint);
        }

        var newPointIds = new ArrayList<EdgePointIdDTO>();
        var lastIndex = points.size() - 1;
        for (var index = 0; index < points.size(); index++) {
            var point = points.get(index);
            MRID gluePoint = null;
            if (point.isEndPoint() && index == 0) {
                gluePoint = gluePointOf(diagramLayoutModel, diagramUUID, firstClass, gluePoints);
            } else if (point.isEndPoint() && index == lastIndex) {
                gluePoint = gluePointOf(diagramLayoutModel, diagramUUID, lastClass, gluePoints);
            }

            var storedPoint = point.getId() != null ? storedPoints.remove(point.getId()) : null;
            if (storedPoint == null) {
                var pointMRID = new MRID(UUID.randomUUID());
                insertPoint(diagramLayoutModel, pointMRID, edgeDO, point, index, gluePoint);
                newPointIds.add(new EdgePointIdDTO(point.getId(), pointMRID.getUuid().toString()));
            } else if (!isUnchanged(storedPoint, point, index, gluePoint)) {
                DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, storedPoint.getMRID());
                insertPoint(
                        diagramLayoutModel, storedPoint.getMRID(), edgeDO, point, index, gluePoint);
            }
        }
        for (var removedPoint : storedPoints.values()) {
            DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, removedPoint.getMRID());
        }
        return newPointIds;
    }

    private static void insertPoint(
            Model diagramLayoutModel,
            MRID pointMRID,
            DiagramObject edgeDO,
            EdgePointDTO point,
            int sequenceNumber,
            MRID gluePoint) {
        DiagramLayoutServiceUtils.insertEdgePoint(
                diagramLayoutModel,
                pointMRID,
                edgeDO.getMRID(),
                point.getXPosition(),
                point.getYPosition(),
                sequenceNumber,
                gluePoint);
    }

    private static boolean isUnchanged(
            DiagramObjectPoint storedPoint,
            EdgePointDTO point,
            int sequenceNumber,
            MRID gluePoint) {
        return storedPoint.getPosition().getX() == point.getXPosition()
                && storedPoint.getPosition().getY() == point.getYPosition()
                && Objects.equals(storedPoint.getSequenceNumber(), sequenceNumber)
                && Objects.equals(storedPoint.getBelongsToGluePoint(), gluePoint);
    }

    /**
     * The glue point of a class in the diagram, the point an end point at the border of the class
     * is glued to.
     *
     * @return the glue point, or null if the class has no layout data in the diagram
     */
    private static MRID gluePointOf(
            Model diagramLayoutModel,
            UUID diagramUUID,
            UUID classUUID,
            Map<UUID, MRID> gluePoints) {
        return gluePoints.computeIfAbsent(
                classUUID,
                uuid -> {
                    var classDO =
                            DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                    diagramLayoutModel,
                                    diagramUUID,
                                    uuid,
                                    DiagramObjectStyle.CLASS);
                    return classDO != null
                            ? DiagramLayoutServiceUtils.resolveClassGluePoint(
                                    diagramLayoutModel, classDO.getMRID())
                            : null;
                });
    }
}
