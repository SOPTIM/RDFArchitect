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

package org.rdfarchitect.services.dl.update.associationlayout;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.dl.AssociationDecorationPositionDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.relations.XYPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.AssociationLayoutConstants;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateAssociationLayoutService implements UpdateAssociationDecorationPositionsUseCase {

    private final DatabasePort databasePort;

    @Override
    public void updateAssociationDecorationPositions(GraphIdentifier graphIdentifier, UUID packageUUID, List<AssociationDecorationPositionDTO> associationDecorationPositionDTOList) {
        var diagramLayout = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
        var resolvedPackageUUID = packageUUID == null ? diagramLayout.getDefaultPackageMRID().getUuid() : packageUUID;

        for (var associationDecorationPositionDTO : associationDecorationPositionDTOList) {
            var diagramObjectName = AssociationLayoutConstants.getDiagramObjectName(associationDecorationPositionDTO.getDecorationType());
            var existingDiagramObject = DLObjectFetcher.fetchAllDOs(diagramLayoutModel, associationDecorationPositionDTO.getAssociationUUID())
                                                       .stream()
                                                       .filter(diagramObject -> resolvedPackageUUID.equals(diagramObject.getBelongsToDiagram().getUuid()))
                                                       .filter(diagramObject -> diagramObjectName.equals(diagramObject.getName()))
                                                       .findFirst()
                                                       .orElse(null);

            if (existingDiagramObject == null) {
                var diagramObjectMRID = DiagramLayoutServiceUtils.insertDiagramObject(
                          diagramLayoutModel,
                          resolvedPackageUUID,
                          diagramObjectName,
                          associationDecorationPositionDTO.getAssociationUUID());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                          diagramLayoutModel,
                          diagramObjectMRID,
                          new XYPosition(associationDecorationPositionDTO.getXPosition(), associationDecorationPositionDTO.getYPosition()));
                continue;
            }

            var diagramObjectPoint = DLObjectFetcher.fetchDOPForDO(diagramLayoutModel, existingDiagramObject.getMRID());
            if (diagramObjectPoint != null) {
                DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint.getMRID());
            }

            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                      diagramLayoutModel,
                      existingDiagramObject.getMRID(),
                      new XYPosition(associationDecorationPositionDTO.getXPosition(), associationDecorationPositionDTO.getYPosition()));
        }
    }
}
