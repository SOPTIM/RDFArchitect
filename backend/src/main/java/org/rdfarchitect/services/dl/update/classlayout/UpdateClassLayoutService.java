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

package org.rdfarchitect.services.dl.update.classlayout;

import lombok.RequiredArgsConstructor;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateClassLayoutService implements UpdateClassPositionsUseCase, CreateClassLayoutDataUseCase, DeleteClassLayoutDataUseCase, UpdateDiagramObjectNameUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;

    @Override
    public void createClassLayoutData(GraphIdentifier graphIdentifier, PackageDTO packageDTO, String className, UUID classUUID) {
        var diagramLayout = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
        UUID packageUUID;

        if (packageDTO != null) {
            var cimPackage = packageMapper.toCIMObject(packageDTO);
            packageUUID = cimPackage.getUuid();
        } else {
            packageUUID = diagramLayout.getDefaultPackageMRID().getUuid();
        }

        MRID doMRID = DiagramLayoutServiceUtils.insertDiagramObject(diagramLayoutModel, packageUUID, className, classUUID);
        DiagramLayoutServiceUtils.insertDiagramObjectPoint(diagramLayoutModel, doMRID);
    }

    @Override
    public void updateClassPositions(GraphIdentifier graphIdentifier, UUID packageUUID, List<ClassPositionDTO> classPositionDTOList) {
        var diagramLayout = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout();
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
        var resolvedPackageUUID = packageUUID != null ?
                                  packageUUID :
                                  diagramLayout.getDefaultPackageMRID().getUuid();

        for (var classPositionDTO : classPositionDTOList) {
            var diagramObject = DLObjectFetcher.fetchDiagramDOForClass(
                      diagramLayoutModel,
                      resolvedPackageUUID,
                      classPositionDTO.getClassUUID());

            var doMRID = diagramObject.getMRID();

            var diagramObjectPoint = DLObjectFetcher.fetchDOPForDO(
                      diagramLayoutModel,
                      doMRID);

            var dopMRID = diagramObjectPoint.getMRID();

            DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, dopMRID);

            diagramObjectPoint.setPosition(new XYZPosition(classPositionDTO.getXPosition(), classPositionDTO.getYPosition(), classPositionDTO.getZPosition()));

            DLUpdates.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint);
        }
    }

    @Override
    public void updateDiagramObjectName(GraphIdentifier graphIdentifier, UUID classUUID, String name) {
        var diagramLayoutModel = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjects = DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID);

        for (var diagramObject : diagramObjects) {
            DLUpdates.updateDiagramObjectName(diagramLayoutModel, diagramObject, name);
        }
    }

    @Override
    public void deleteClassLayoutData(GraphIdentifier graphIdentifier, UUID classUUID) {
        var diagramLayoutModel = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID)) {
            DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
        }
    }
}
