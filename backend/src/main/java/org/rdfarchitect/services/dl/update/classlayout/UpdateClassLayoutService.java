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
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.ClassLayoutPositionDTO;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
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

            var existingDiagramObject =
                    DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                            diagramLayoutModel, packageUUID, classUUID);
            if (existingDiagramObject != null) {
                // The class takes over an uri that already had layout data, for example because a
                // class of that name was deleted while references to it remained. Keeping that
                // layout data avoids a second diagram object for the same class.
                if (classLayoutPosition != null) {
                    moveClassDOPPosition(
                            diagramLayoutModel,
                            existingDiagramObject,
                            packageUUID,
                            classLayoutPosition.getXPosition(),
                            classLayoutPosition.getYPosition(),
                            null);
                    ctx.commit();
                }
                return;
            }
            DiagramLayoutServiceUtils.insertClassLayoutData(
                    diagramLayoutModel,
                    packageUUID,
                    className,
                    classUUID,
                    classLayoutPosition != null ? classLayoutPosition.getXPosition() : 0,
                    classLayoutPosition != null ? classLayoutPosition.getYPosition() : 0);
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

            applyClassPositions(diagramLayoutModel, resolvedPackageUUID, classPositionDTOList);

            ctx.commit();
        }
    }

    @Override
    public void updateClassPositions(
            String datasetName, UUID diagramUUID, List<ClassPositionDTO> classPositionDTOList) {
        var diagramLayoutModel =
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel();

        applyClassPositions(diagramLayoutModel, diagramUUID, classPositionDTOList);
    }

    private void applyClassPositions(
            Model diagramLayoutModel,
            UUID diagramUUID,
            List<ClassPositionDTO> classPositionDTOList) {
        for (var classPositionDTO : classPositionDTOList) {
            var diagramObject =
                    DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                            diagramLayoutModel, diagramUUID, classPositionDTO.getClassUUID());
            if (diagramObject == null) {
                if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
                    DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
                }
                DiagramLayoutServiceUtils.insertClassLayoutData(
                        diagramLayoutModel,
                        diagramUUID,
                        "",
                        classPositionDTO.getClassUUID(),
                        classPositionDTO.getXPosition(),
                        classPositionDTO.getYPosition());
                continue;
            }
            moveClassDOPPosition(
                    diagramLayoutModel,
                    diagramObject,
                    diagramUUID,
                    classPositionDTO.getXPosition(),
                    classPositionDTO.getYPosition(),
                    classPositionDTO.getZPosition());
        }
    }

    /**
     * Moves the diagram object point of an existing class diagram object. If the diagram object has
     * no point yet, a new one is created. The glue point invariant is upheld: the (re)created point
     * always references the class' glue point, reusing an existing one or creating it if missing.
     *
     * @param zPosition the stacking order to apply, or {@code null} to keep the current one.
     */
    private void moveClassDOPPosition(
            Model diagramLayoutModel,
            DiagramObject diagramObject,
            UUID diagramUUID,
            float xPosition,
            float yPosition,
            Integer zPosition) {
        var diagramObjectPoint =
                DLObjectFetcher.fetchDOPForDO(diagramLayoutModel, diagramObject.getMRID());

        if (diagramObjectPoint == null) {
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                    diagramLayoutModel,
                    diagramObject.getMRID(),
                    diagramUUID,
                    xPosition,
                    yPosition,
                    null,
                    resolveGluePointMRID(diagramLayoutModel, diagramObject.getMRID()));
            return;
        }

        DLUpdates.deleteDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint.getMRID());
        diagramObjectPoint.setPosition(
                new XYZPosition(
                        xPosition,
                        yPosition,
                        zPosition != null ? zPosition : diagramObjectPoint.getPosition().getZ()));
        DLUpdates.insertDiagramObjectPoint(diagramLayoutModel, diagramObjectPoint);
    }

    /**
     * Resolves the glue point mRID for a class diagram object, reusing the existing glue point or
     * creating a new one if the class does not have one yet.
     */
    private MRID resolveGluePointMRID(Model diagramLayoutModel, MRID diagramObjectMRID) {
        var gluePoint = DLObjectFetcher.fetchGluePointForDO(diagramLayoutModel, diagramObjectMRID);
        return gluePoint != null
                ? gluePoint.getMRID()
                : DiagramLayoutServiceUtils.insertDiagramObjectGluePoint(diagramLayoutModel);
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
                DiagramLayoutServiceUtils.insertClassLayoutData(
                        diagramLayoutModel, diagramUUID, "", cls.getUuid(), 0, 0);
            }
            ctx.commit();
        }
    }

    @Override
    public void removeClassesFromCustomDiagram(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<UUID> classUUIDs) {
        if (classUUIDs.isEmpty()) {
            return;
        }

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.removeIf(c -> classUUIDs.contains(c.getUuid()));
                diagram.setClasses(updated);
            }
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var classUUID : classUUIDs) {
                var diagramObject =
                        DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                diagramLayoutModel, diagramUUID, classUUID);
                if (diagramObject != null) {
                    DLUpdates.deleteDiagramObjectCascade(
                            diagramLayoutModel, diagramObject.getMRID());
                }
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
            DiagramLayoutServiceUtils.insertClassLayoutData(
                    diagramLayoutModel, diagramUUID, "", cls.getUuid(), 0, 0);
        }
    }

    @Override
    public void removeClassesFromCustomDatasetDiagram(
            String datasetName, UUID diagramUUID, List<UUID> classUUIDs) {
        if (classUUIDs.isEmpty()) {
            return;
        }

        var diagram = databasePort.getDatasetDiagrams(datasetName).get(diagramUUID);
        if (diagram != null) {
            var updated = diagram.getClasses();
            updated.removeIf(c -> classUUIDs.contains(c.getUuid()));
            diagram.setClasses(updated);
        }
        var diagramLayoutModel =
                databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel();
        for (var classUUID : classUUIDs) {
            var diagramObject =
                    DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                            diagramLayoutModel, diagramUUID, classUUID);
            if (diagramObject != null) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
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
                DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                        model, crossProfileDiagramUUID, newMergedUuid);
        if (existingNew != null) {
            return;
        }

        var oldDO =
                DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                        model, crossProfileDiagramUUID, oldMergedUuid);
        if (oldDO == null) {
            return;
        }

        var oldDOP = DLObjectFetcher.fetchDOPForDO(model, oldDO.getMRID());
        var position = oldDOP.getPosition();

        var gluePointMRID = DiagramLayoutServiceUtils.insertDiagramObjectGluePoint(model);
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
                        .belongsToGluePoint(gluePointMRID)
                        .build();
        DLUpdates.insertDiagramObjectPoint(model, newDiagramObjectPoint);
    }
}
