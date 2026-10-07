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

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps the edge diagram objects of a diagram in line with the schema. Edge layout data is created
 * lazily, like the layout data of classes: an edge gets its diagram object as soon as both classes
 * it connects have one in the diagram. Reconciling a diagram is idempotent, so it is simply run
 * wherever class layout data or the edges between classes may have changed.
 */
@UtilityClass
public class EdgeLayoutReconciler {

    /**
     * Brings the edge diagram objects of a diagram in line with the edges between its classes:
     *
     * <ul>
     *   <li>edges between two classes of the diagram that have no diagram object get one
     *   <li>edge diagram objects whose edge no longer exists, for example because the super class
     *       or the association changed or one of the classes left the diagram, are deleted together
     *       with their points and, for associations, the labels anchored to their ends
     *   <li>edge diagram objects whose class labels changed are renamed
     *   <li>a second diagram object of the same edge is deleted, so every edge keeps one
     * </ul>
     *
     * @param diagramLayoutModel the diagram layout holding the diagram
     * @param diagramUUID the UUID of the diagram
     * @param edgeResolver resolves the edges between the classes of the diagram from the schema
     */
    public void reconcileEdges(
            Model diagramLayoutModel, UUID diagramUUID, EdgeResolver edgeResolver) {
        var classUUIDs =
                DLObjectFetcher.fetchDiagramClassDOs(diagramLayoutModel, new MRID(diagramUUID))
                        .stream()
                        .map(classDO -> classDO.getBelongsToIdentifiedObject().getUuid())
                        .collect(Collectors.toSet());
        var missingEdges = new LinkedHashMap<>(edgeResolver.edgesBetween(classUUIDs));

        for (var edgeDO :
                DLObjectFetcher.fetchDiagramEdgeDOs(diagramLayoutModel, new MRID(diagramUUID))) {
            var edge = EdgeKey.of(edgeDO);
            var name = edge != null ? missingEdges.remove(edge) : null;
            if (name == null) {
                deleteEdge(diagramLayoutModel, diagramUUID, edgeDO);
            } else if (!name.equals(edgeDO.getName())) {
                DLUpdates.updateDiagramObjectName(diagramLayoutModel, edgeDO, name);
            }
        }

        missingEdges.forEach(
                (edge, name) ->
                        DiagramLayoutServiceUtils.insertEdgeDiagramObject(
                                diagramLayoutModel, diagramUUID, edge, name));
    }

    private void deleteEdge(Model diagramLayoutModel, UUID diagramUUID, DiagramObject edgeDO) {
        DiagramLayoutServiceUtils.deleteEdgeLayoutData(diagramLayoutModel, edgeDO.getMRID());
        if (edgeDO.getBelongsToDiagramObjectStyle() != DiagramObjectStyle.ASSOCIATION) {
            return;
        }
        var associationEnds = new HashSet<UUID>();
        associationEnds.add(edgeDO.getBelongsToIdentifiedObject().getUuid());
        if (edgeDO.getOtherClass() != null) {
            associationEnds.add(edgeDO.getOtherClass().getUuid());
        }
        for (var label : DLObjectFetcher.fetchDiagramLabelDOs(diagramLayoutModel, diagramUUID)) {
            if (associationEnds.contains(label.getBelongsToIdentifiedObject().getUuid())) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, label.getMRID());
            }
        }
    }
}
