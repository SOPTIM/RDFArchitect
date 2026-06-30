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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.api.dto.dl.ClassLayoutPositionDTO;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateClassLayoutService
        implements UpdateClassPositionsUseCase,
                CreateClassLayoutDataUseCase,
                DeleteClassLayoutDataUseCase,
                UpdateDiagramObjectNameUseCase,
                CustomDiagramLayoutUseCase,
                CrossProfileDiagramLayoutUseCase {

    private final DatabasePort databasePort;
    private final PackageMapper packageMapper;

    @Override
    public void createClassLayoutData(
            GraphIdentifier graphIdentifier,
            PackageDTO packageDTO,
            String className,
            UUID classUUID,
            ClassLayoutPositionDTO classLayoutPosition) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
            UUID packageUUID =
                    packageDTO != null
                            ? packageMapper.toCIMObject(packageDTO).getUuid()
                            : diagramLayout.getDefaultPackageMRID().getUuid();

            var doMRID =
                    DiagramLayoutServiceUtils.insertDiagramObject(
                            diagramLayoutModel, packageUUID, className, classUUID);
            float xPosition = classLayoutPosition != null ? classLayoutPosition.getXPosition() : 0;
            float yPosition = classLayoutPosition != null ? classLayoutPosition.getYPosition() : 0;
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                    diagramLayoutModel, doMRID, packageUUID, xPosition, yPosition);
            ctx.commit();
        }
    }

    @Override
    public void updateClassPositions(
            GraphIdentifier graphIdentifier,
            UUID packageUUID,
            List<ClassPositionDTO> classPositionDTOList) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
            var resolvedPackageUUID =
                    packageUUID != null
                            ? packageUUID
                            : diagramLayout.getDefaultPackageMRID().getUuid();

            for (var classPositionDTO : classPositionDTOList) {
                var diagramObject =
                        DLObjectFetcher.fetchDiagramDOForClass(
                                diagramLayoutModel,
                                resolvedPackageUUID,
                                classPositionDTO.getClassUUID());
                if (diagramObject == null) {
                    if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, resolvedPackageUUID)
                            == null) {
                        DiagramLayoutServiceUtils.insertDiagram(
                                diagramLayoutModel, resolvedPackageUUID, "");
                    }
                    var doMRID =
                            DiagramLayoutServiceUtils.insertDiagramObject(
                                    diagramLayoutModel,
                                    resolvedPackageUUID,
                                    "",
                                    classPositionDTO.getClassUUID());
                    DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                            diagramLayoutModel,
                            doMRID,
                            resolvedPackageUUID,
                            classPositionDTO.getXPosition(),
                            classPositionDTO.getYPosition());
                    continue;
                }
                var diagramObjectPoint =
                        DLObjectFetcher.fetchDOPForDO(diagramLayoutModel, diagramObject.getMRID());
                DLUpdates.deleteDiagramObjectPoint(
                        diagramLayoutModel, diagramObjectPoint.getMRID());
                diagramObjectPoint.setPosition(
                        new XYZPosition(
                                classPositionDTO.getXPosition(),
                                classPositionDTO.getYPosition(),
                                classPositionDTO.getZPosition()));
                DLUpdates.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint);
            }

            ctx.commit();
        }
    }

    @Override
    public void updateClassPositions(
            String datasetName, UUID diagramUUID, List<ClassPositionDTO> classPositionDTOList) {
        var diagramLayout = databasePort.getDatasetDiagramLayout(datasetName);
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();

        for (var classPositionDTO : classPositionDTOList) {
            var diagramObject =
                    DLObjectFetcher.fetchDiagramDOForClass(
                            diagramLayoutModel, diagramUUID, classPositionDTO.getClassUUID());
            if (diagramObject == null) {
                if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
                    DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
                }
                var doMRID =
                        DiagramLayoutServiceUtils.insertDiagramObject(
                                diagramLayoutModel,
                                diagramUUID,
                                "",
                                classPositionDTO.getClassUUID());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                        diagramLayoutModel,
                        doMRID,
                        diagramUUID,
                        classPositionDTO.getXPosition(),
                        classPositionDTO.getYPosition());
                continue;
            }
            var diagramObjectPoint =
                    DLObjectFetcher.fetchDOPForDO(diagramLayoutModel, diagramObject.getMRID());
            DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint.getMRID());
            diagramObjectPoint.setPosition(
                    new XYZPosition(
                            classPositionDTO.getXPosition(),
                            classPositionDTO.getYPosition(),
                            classPositionDTO.getZPosition()));
            DLUpdates.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint);
        }
    }

    @Override
    public void updateDiagramObjectName(
            GraphIdentifier graphIdentifier, UUID classUUID, String name) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID)) {
                DLUpdates.updateDiagramObjectName(diagramLayoutModel, diagramObject, name);
            }
            ctx.commit();
        }
    }

    @Override
    public void deleteClassLayoutData(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID)) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
            ctx.commit();
        }
    }

    @Override
    public void addClassesToCustomDiagram(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<ClassInDiagram> classes) {
        if (classes.isEmpty()) {
            return;
        }

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.addAll(classes);
                diagram.setClasses(updated);
            }
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
                DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
            }
            for (var cls : classes) {
                var doMRID =
                        DiagramLayoutServiceUtils.insertDiagramObject(
                                diagramLayoutModel, diagramUUID, "", cls.getUuid());
                DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                        diagramLayoutModel, diagramUUID, doMRID);
            }
            ctx.commit();
        }
    }

    @Override
    public void removeClassFromCustomDiagram(
            GraphIdentifier graphIdentifier, UUID diagramUUID, UUID classUUID) {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.removeIf(c -> c.getUuid().equals(classUUID));
                diagram.setClasses(updated);
            }
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            var diagramObject =
                    DLObjectFetcher.fetchDiagramDOForClass(
                            diagramLayoutModel, diagramUUID, classUUID);
            if (diagramObject != null) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
            ctx.commit();
        }
    }

    @Override
    public void addClassesToCustomDatasetDiagram(
            String datasetName, UUID diagramUUID, List<ClassInDiagram> classes) {
        if (classes.isEmpty()) {
            return;
        }
        var graphUris =
                classes.stream().map(c -> c.getGraphUri().toString()).collect(Collectors.toSet());
        var existingGraphUris = new HashSet<>(databasePort.listGraphUris(datasetName));
        if (!existingGraphUris.containsAll(graphUris)) {
            throw new IllegalArgumentException(
                    "Some referenced graphs do not exist in dataset: " + datasetName);
        }

        var diagram = databasePort.getDatasetDiagrams(datasetName).get(diagramUUID);
        if (diagram != null) {
            var updated = diagram.getClasses();
            updated.addAll(classes);
            diagram.setClasses(updated);
        }
        var diagramLayoutModel =
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel();
        if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
        }
        for (var cls : classes) {
            var doMRID =
                    DiagramLayoutServiceUtils.insertDiagramObject(
                            diagramLayoutModel, diagramUUID, "", cls.getUuid());
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                    diagramLayoutModel, diagramUUID, doMRID);
        }
    }

    @Override
    public void removeClassFromCustomDatasetDiagram(
            String datasetName, UUID diagramUUID, UUID classUUID) {
        var diagram = databasePort.getDatasetDiagrams(datasetName).get(diagramUUID);
        if (diagram != null) {
            var updated = diagram.getClasses();
            updated.removeIf(c -> c.getUuid().equals(classUUID));
            diagram.setClasses(updated);
        }
        var diagramLayoutModel =
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel();
        var diagramObject =
                DLObjectFetcher.fetchDiagramDOForClass(diagramLayoutModel, diagramUUID, classUUID);
        if (diagramObject != null) {
            DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
        }
    }

    @Override
    public void migrateLayoutToNewClassUri(
            String datasetName, UUID oldMergedUuid, UUID newMergedUuid, String newClassUri) {
        var diagramLayout = databasePort.getDatasetDiagramLayout(datasetName);
        var model = diagramLayout.getDiagramLayoutModel();
        var crossProfileDiagramUUID =
                databasePort.getCrossProfileDiagramInfo(datasetName).getCrossProfileDiagramUUID();

        if (DLObjectFetcher.fetchDiagram(model, crossProfileDiagramUUID) == null) {
            return;
        }

        var existingNew =
                DLObjectFetcher.fetchDiagramDOForClass(
                        model, crossProfileDiagramUUID, newMergedUuid);
        if (existingNew != null) {
            return;
        }

        var oldDO =
                DLObjectFetcher.fetchDiagramDOForClass(
                        model, crossProfileDiagramUUID, oldMergedUuid);
        if (oldDO == null) {
            return;
        }

        var oldDOP = DLObjectFetcher.fetchDOPForDO(model, oldDO.getMRID());
        var position = oldDOP.getPosition();

        var newDoMRID =
                DiagramLayoutServiceUtils.insertDiagramObject(
                        model, crossProfileDiagramUUID, newClassUri, newMergedUuid);

        var newDiagramObjectPoint =
                DiagramObjectPoint.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .position(
                                new XYZPosition(
                                        position.getX(), position.getY(), position.getZ() + 1))
                        .belongsToDiagramObject(newDoMRID)
                        .build();
        DLUpdates.insertDiagramObjectPoint(model, newDiagramObjectPoint);
    }
}
