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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
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
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.relations.model.CIMResourceUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.rendering.MergedClasses;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
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
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagramLayout = ctx.getDiagramLayout();
            var diagramLayoutModel = diagramLayout.getDiagramLayoutModel();
            UUID packageUUID =
                    packageDTO != null
                            ? packageMapper.toCIMObject(packageDTO).getUuid()
                            : diagramLayout.getDefaultPackageMRID().getUuid();
            // Layout data is only created once a package is opened, so the diagram this object
            // belongs to may still be missing. Without it both fetch queries skip the object.
            if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, packageUUID) == null) {
                DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, packageUUID, "");
            }

            var existingDiagramObject =
                    DLObjectFetcher.fetchDiagramDOForClass(
                            diagramLayoutModel, packageUUID, classUUID);
            if (existingDiagramObject != null) {
                // The class takes over an uri that already had layout data, for example because a
                // class of that name was deleted while references to it remained. Keeping that
                // layout data avoids a second diagram object for the same class.
                if (classLayoutPosition != null) {
                    moveDiagramObject(
                            diagramLayoutModel,
                            existingDiagramObject,
                            packageUUID,
                            classLayoutPosition.getXPosition(),
                            classLayoutPosition.getYPosition(),
                            null);
                    transaction.commit("laid out class %s".formatted(classUUID));
                }
                return;
            }

            var doMRID =
                    DiagramLayoutServiceUtils.insertDiagramObject(
                            diagramLayoutModel, packageUUID, className, classUUID);
            float xPosition = classLayoutPosition != null ? classLayoutPosition.getXPosition() : 0;
            float yPosition = classLayoutPosition != null ? classLayoutPosition.getYPosition() : 0;
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                    diagramLayoutModel, doMRID, packageUUID, xPosition, yPosition);
            transaction.commit("laid out class %s".formatted(classUUID));
        }
    }

    /**
     * Moves the point of an existing diagram object.
     *
     * @param zPosition the stacking order to apply, or {@code null} to keep the current one.
     */
    private void moveDiagramObject(
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
                    diagramLayoutModel, diagramObject.getMRID(), diagramUUID, xPosition, yPosition);
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

    @Override
    public void updateClassPositions(
            GraphIdentifier graphIdentifier,
            UUID packageUUID,
            List<ClassPositionDTO> classPositionDTOList) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
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
                moveDiagramObject(
                        diagramLayoutModel,
                        diagramObject,
                        resolvedPackageUUID,
                        classPositionDTO.getXPosition(),
                        classPositionDTO.getYPosition(),
                        classPositionDTO.getZPosition());
            }

            transaction.commit("moved classes in package %s".formatted(packageUUID));
        }
    }

    @Override
    public void updateClassPositions(
            String datasetName, UUID diagramUUID, List<ClassPositionDTO> classPositionDTOList) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            applyClassPositions(
                    transaction.layout().getDiagramLayoutModel(),
                    diagramUUID,
                    classPositionDTOList);
            transaction.commit("moved classes in diagram %s".formatted(diagramUUID));
        }
    }

    private void applyClassPositions(
            Model diagramLayoutModel,
            UUID diagramUUID,
            List<ClassPositionDTO> classPositionDTOList) {

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
            moveDiagramObject(
                    diagramLayoutModel,
                    diagramObject,
                    diagramUUID,
                    classPositionDTO.getXPosition(),
                    classPositionDTO.getYPosition(),
                    classPositionDTO.getZPosition());
        }
    }

    @Override
    public void updateDiagramObjectName(
            GraphIdentifier graphIdentifier, UUID classUUID, String name) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID)) {
                DLUpdates.updateDiagramObjectName(diagramLayoutModel, diagramObject, name);
            }
            transaction.commit("renamed class %s in the diagrams".formatted(classUUID));
        }
    }

    @Override
    public void deleteClassLayoutData(GraphIdentifier graphIdentifier, UUID classUUID) {
        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            for (var diagramObject : DLObjectFetcher.fetchAllDOs(diagramLayoutModel, classUUID)) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
            deleteOrphanedLabels(diagramLayoutModel, ctx.getRdfGraph(), classUUID);
            transaction.commit("removed the layout of class %s".formatted(classUUID));
        }
    }

    /**
     * Drops the layout of labels anchored to an association end of the deleted class. Labels are
     * anchored to the resource whose text they display, e.g. an association end, so deleting a
     * class leaves the labels of its associations behind: {@code CIMUpdates.deleteClass} never
     * cascades to a class's associations, so their own {@code rdfa:uuid} outlives the class.
     */
    private void deleteOrphanedLabels(Model diagramLayoutModel, Graph rdfGraph, UUID classUUID) {
        var danglingAssociationEndUuids = danglingAssociationEndUuids(rdfGraph, classUUID);
        if (danglingAssociationEndUuids.isEmpty()) {
            return;
        }
        for (var label : DLObjectFetcher.fetchAllLabelDOs(diagramLayoutModel)) {
            if (danglingAssociationEndUuids.contains(
                    label.getBelongsToIdentifiedObject().getUuid())) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, label.getMRID());
            }
        }
    }

    /**
     * Finds the UUIDs of both ends of every association still pointing at the deleted class as its
     * domain or range, so their multiplicity labels can be dropped. Includes each association's
     * inverse end, since a label may be anchored to either side of the pair.
     */
    private Set<UUID> danglingAssociationEndUuids(Graph rdfGraph, UUID classUUID) {
        var model = ModelFactory.createModelForGraph(rdfGraph);
        var classResource =
                model.listSubjectsWithProperty(RDFA.uuid, classUUID.toString())
                        .nextOptional()
                        .orElse(null);
        if (classResource == null) {
            return Set.of();
        }

        Set<Resource> associationEnds = new LinkedHashSet<>();
        associationEnds.addAll(
                model.listSubjectsWithProperty(RDFS.domain, classResource)
                        .filterKeep(CIMPropertyUtils::isAssociation)
                        .toList());
        associationEnds.addAll(
                model.listSubjectsWithProperty(RDFS.range, classResource)
                        .filterKeep(CIMPropertyUtils::isAssociation)
                        .toList());

        Set<UUID> uuids = new HashSet<>();
        for (var end : associationEnds) {
            uuids.add(CIMResourceUtils.findUuidForResource(end));
            var inverse = end.getProperty(CIMS.inverseRoleName);
            if (inverse != null && inverse.getObject().isResource()) {
                uuids.add(CIMResourceUtils.findUuidForResource(inverse.getObject().asResource()));
            }
        }
        return uuids;
    }

    @Override
    public void addClassesToCustomDiagram(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<ClassInDiagram> classes) {
        if (classes.isEmpty()) {
            return;
        }

        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.addAll(classes);
                diagram.setClasses(updated);
            }
            var diagramLayoutModel = ctx.getDiagramLayout().getDiagramLayoutModel();
            insertLayoutForClasses(
                    diagramLayoutModel,
                    diagramUUID,
                    classes.stream()
                            .map(ClassInDiagram::getUuid)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
            transaction.commit("added classes to diagram %s".formatted(diagramUUID));
        }
    }

    private static Set<UUID> mergedUuidsOf(
            List<ClassInDiagram> classes, Map<UUID, String> classUriByUuid) {
        var mergedUuids = new LinkedHashSet<UUID>();
        for (var cls : classes) {
            var classUri = classUriByUuid.get(cls.getUuid());
            if (classUri != null) {
                mergedUuids.add(CrossProfileUtils.mergedUuid(classUri));
            }
        }
        return mergedUuids;
    }

    private static void insertLayoutForClasses(
            Model diagramLayoutModel, UUID diagramUUID, Set<UUID> mergedUuids) {
        if (DLObjectFetcher.fetchDiagram(diagramLayoutModel, diagramUUID) == null) {
            DiagramLayoutServiceUtils.insertDiagram(diagramLayoutModel, diagramUUID, "");
        }
        for (var mergedUuid : mergedUuids) {
            if (DLObjectFetcher.fetchDiagramDOForClass(diagramLayoutModel, diagramUUID, mergedUuid)
                    != null) {
                continue;
            }
            var doMRID =
                    DiagramLayoutServiceUtils.insertDiagramObject(
                            diagramLayoutModel, diagramUUID, "", mergedUuid);
            DiagramLayoutServiceUtils.insertDiagramObjectPoint(
                    diagramLayoutModel, diagramUUID, doMRID);
        }
    }

    @Override
    public void removeClassesFromCustomDiagram(
            GraphIdentifier graphIdentifier, UUID diagramUUID, List<UUID> classUUIDs) {
        if (classUUIDs.isEmpty()) {
            return;
        }

        try (var transaction =
                databasePort.beginTransaction(graphIdentifier.datasetName(), ReadWrite.WRITE)) {
            var ctx = transaction.graph(graphIdentifier.graphUri());
            var diagram = ctx.getCustomDiagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.removeIf(cls -> classUUIDs.contains(cls.getUuid()));
                diagram.setClasses(updated);
            }
            deleteLayoutForClasses(
                    ctx.getDiagramLayout().getDiagramLayoutModel(), diagramUUID, classUUIDs);
            transaction.commit("removed classes from diagram %s".formatted(diagramUUID));
        }
    }

    private static Predicate<ClassInDiagram> removedByAnyOf(
            List<UUID> classUUIDs, Map<UUID, String> classUriByUuid) {
        return cls -> {
            if (classUUIDs.contains(cls.getUuid())) {
                return true;
            }
            var classUri = classUriByUuid.get(cls.getUuid());
            return classUri != null && classUUIDs.contains(CrossProfileUtils.mergedUuid(classUri));
        };
    }

    private static void deleteLayoutForClasses(
            Model diagramLayoutModel, UUID diagramUUID, List<UUID> classUUIDs) {
        for (var classUUID : classUUIDs) {
            var diagramObject =
                    DLObjectFetcher.fetchDiagramDOForClass(
                            diagramLayoutModel, diagramUUID, classUUID);
            if (diagramObject != null) {
                DLUpdates.deleteDiagramObjectCascade(diagramLayoutModel, diagramObject.getMRID());
            }
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

        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var diagram = transaction.diagrams().get(diagramUUID);
            if (diagram != null) {
                var updated = diagram.getClasses();
                updated.addAll(classes);
                diagram.setClasses(updated);
            }
            insertLayoutForClasses(
                    transaction.layout().getDiagramLayoutModel(),
                    diagramUUID,
                    mergedUuidsOf(classes, classUriByUuid(datasetName, classes)));
            transaction.commit("added classes to diagram %s".formatted(diagramUUID));
        }
    }

    private Map<UUID, String> classUriByUuid(String datasetName, List<ClassInDiagram> classes) {
        var existingGraphUris = new HashSet<>(databasePort.listGraphUris(datasetName));
        var classesByGraphUri =
                classes.stream()
                        .filter(cls -> cls.getGraphUri() != null && cls.getUuid() != null)
                        .filter(cls -> existingGraphUris.contains(cls.getGraphUri().toString()))
                        .collect(Collectors.groupingBy(cls -> cls.getGraphUri().toString()));

        var classUriByUuid = new HashMap<UUID, String>();
        for (var entry : classesByGraphUri.entrySet()) {
            var identifier = new GraphIdentifier(datasetName, entry.getKey());
            try (var transaction =
                    databasePort.beginTransaction(identifier.datasetName(), ReadWrite.READ)) {
                var ctx = transaction.graph(identifier.graphUri());
                var model =
                        new CIMModelFacade(
                                entry.getKey(),
                                ModelFactory.createModelForGraph(ctx.getRdfGraph()));
                classUriByUuid.putAll(
                        MergedClasses.classUriByUuid(
                                entry.getValue(), Map.of(entry.getKey(), model)));
            }
        }
        return classUriByUuid;
    }

    @Override
    public void removeClassesFromCustomDatasetDiagram(
            String datasetName, UUID diagramUUID, List<UUID> classUUIDs) {
        if (classUUIDs.isEmpty()) {
            return;
        }

        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var model = transaction.layout().getDiagramLayoutModel();
            var diagram = transaction.diagrams().get(diagramUUID);
            if (diagram == null) {
                deleteLayoutForClasses(model, diagramUUID, classUUIDs);
            } else {
                var updated = diagram.getClasses();
                var classUriByUuid = classUriByUuid(datasetName, updated);
                updated.removeIf(removedByAnyOf(classUUIDs, classUriByUuid));
                diagram.setClasses(updated);

                var stillRendered = mergedUuidsOf(updated, classUriByUuid);
                deleteLayoutForClasses(
                        model,
                        diagramUUID,
                        classUUIDs.stream().filter(uuid -> !stillRendered.contains(uuid)).toList());
            }
            transaction.commit("removed classes from diagram %s".formatted(diagramUUID));
        }
    }

    @Override
    public void migrateLayoutToNewClassUri(
            String datasetName, UUID oldMergedUuid, UUID newMergedUuid, String newClassUri) {
        try (var transaction = databasePort.beginTransaction(datasetName, ReadWrite.WRITE)) {
            var model = transaction.layout().getDiagramLayoutModel();

            var diagramUUIDs = new LinkedHashSet<UUID>();
            diagramUUIDs.add(transaction.crossProfileInfo().getCrossProfileDiagramUUID());
            diagramUUIDs.addAll(transaction.diagrams().keySet());

            for (var diagramUUID : diagramUUIDs) {
                migrateLayoutToNewClassUri(
                        model, diagramUUID, oldMergedUuid, newMergedUuid, newClassUri);
            }
            transaction.commit(
                    "moved the layout of class %s to %s".formatted(oldMergedUuid, newClassUri));
        }
    }

    private static void migrateLayoutToNewClassUri(
            Model model,
            UUID diagramUUID,
            UUID oldMergedUuid,
            UUID newMergedUuid,
            String newClassUri) {
        if (DLObjectFetcher.fetchDiagram(model, diagramUUID) == null) {
            return;
        }

        var existingNew = DLObjectFetcher.fetchDiagramDOForClass(model, diagramUUID, newMergedUuid);
        if (existingNew != null) {
            return;
        }

        var oldDO = DLObjectFetcher.fetchDiagramDOForClass(model, diagramUUID, oldMergedUuid);
        if (oldDO == null) {
            return;
        }

        var oldDOP = DLObjectFetcher.fetchDOPForDO(model, oldDO.getMRID());
        var position = oldDOP.getPosition();

        var newDoMRID =
                DiagramLayoutServiceUtils.insertDiagramObject(
                        model, diagramUUID, newClassUri, newMergedUuid);

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
