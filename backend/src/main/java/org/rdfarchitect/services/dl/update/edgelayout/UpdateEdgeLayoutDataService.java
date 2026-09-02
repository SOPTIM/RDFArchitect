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
import org.rdfarchitect.api.dto.dl.BendPointDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateEdgeLayoutDataService
        implements CreateEdgeLayoutDataUseCase,
        DeleteEdgeLayoutDataUseCase,
        UpdateBendPointsUseCase {

    private final DatabasePort databasePort;

    //TODO SEHR WICHTIG: END POINTS SIND NOCH AUßEN VOR: also im updateBendPoints unten hab ich die noch nicht eingebaut, weil hier das API design noch sehr offen war. also ob ich end points über diese methode mache oder iwie anders

    @Override
    public void createEdgeLayoutData(
            GraphIdentifier graphIdentifier,
            UUID diagramUUID,
            UUID identifiedObjectUUID,
            String edgeName) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            DiagramLayoutServiceUtils.insertDiagramObject(
                    diagramLayoutModel, diagramUUID, edgeName, identifiedObjectUUID);
            ctx.commit();
        }
    }

    @Override
    public void deleteEdgeLayoutData(
            GraphIdentifier graphIdentifier, UUID diagramUUID, UUID identifiedObjectUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            var edgeDO =
                    DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                            diagramLayoutModel, diagramUUID, identifiedObjectUUID);
            if (edgeDO != null) {
                DiagramLayoutServiceUtils.deleteEdgeLayoutData(
                        diagramLayoutModel, edgeDO.getMRID());
            }
            ctx.commit();
        }
    }

    @Override
    public void updateBendPoints(
            GraphIdentifier graphIdentifier,
            UUID diagramUUID,
            UUID identifiedObjectUUID,
            List<BendPointDTO> bendPoints) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            var edgeDO =
                    DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                            diagramLayoutModel, diagramUUID, identifiedObjectUUID);
            if (edgeDO == null) {
                return;
            }

            var existingPoints =
                    DLObjectFetcher.fetchDOPsForDO(diagramLayoutModel, edgeDO.getMRID());
            existingPoints.forEach(
                    dop ->
                            DLUpdates.deleteDiagramObjectPoint(
                                    diagramLayoutModel, dop.getMRID()));

            for (var bendPoint : bendPoints) {
                DiagramLayoutServiceUtils.insertBendPoint(
                        diagramLayoutModel,
                        edgeDO.getMRID(),
                        bendPoint.getXPosition(),
                        bendPoint.getYPosition(),
                        bendPoint.getSequenceNumber());
            }
            ctx.commit();
        }
    }
}
