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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.ClassLayoutPositionDTO;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class UpdateClassLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static UpdateClassLayoutService service;

    @BeforeAll
    static void setUpEnvironment() {
        service = new UpdateClassLayoutService(databasePort, packageMapper);
    }

    private static final String CLASS_A_URI = "http://example.com#classA";
    private static final String GRAPH_URI = "http://example.com#graph";

    @Test
    void addClassesToCustomDatasetDiagram_layoutIsKeyedByTheMergedNodeUuid() {
        var diagramUUID = createWorkspaceDiagram();

        service.addClassesToCustomDatasetDiagram(
                graphIdentifier.datasetName(), diagramUUID, List.of(classAInDiagram()));

        var model =
                databasePort
                        .getDatasetDiagramLayout(graphIdentifier.datasetName())
                        .getDiagramLayoutModel();
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForClass(
                                model, diagramUUID, CrossProfileUtils.mergedClassUuid(CLASS_A_URI)))
                .isNotNull();
        assertThat(DLObjectFetcher.fetchDiagramDOForClass(model, diagramUUID, CLASS_A_UUID))
                .isNull();
    }

    @Test
    void removeClassesFromCustomDatasetDiagram_mergedNodeUuid_removesTheClassBehindIt() {
        var datasetName = graphIdentifier.datasetName();
        var diagramUUID = createWorkspaceDiagram();
        service.addClassesToCustomDatasetDiagram(
                datasetName, diagramUUID, List.of(classAInDiagram()));

        service.removeClassesFromCustomDatasetDiagram(
                datasetName, diagramUUID, List.of(CrossProfileUtils.mergedClassUuid(CLASS_A_URI)));

        assertThat(databasePort.getDatasetDiagrams(datasetName).get(diagramUUID).getClasses())
                .isEmpty();
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForClass(
                                databasePort
                                        .getDatasetDiagramLayout(datasetName)
                                        .getDiagramLayoutModel(),
                                diagramUUID,
                                CrossProfileUtils.mergedClassUuid(CLASS_A_URI)))
                .isNull();
    }

    @Test
    void addClassesToCustomDiagram_schemaDiagram_layoutIsKeyedByTheClassUuid() {
        addGraphFromFile("package_and_class.ttl");
        var diagramUUID = UUID.randomUUID();
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            ctx.getCustomDiagrams()
                    .put(diagramUUID, new CustomDiagram(diagramUUID, "custom", new ArrayList<>()));
            ctx.commit("created custom diagram");
        }

        service.addClassesToCustomDiagram(graphIdentifier, diagramUUID, List.of(classAInDiagram()));

        var model =
                databasePort
                        .getGraphWithContext(graphIdentifier)
                        .getDiagramLayout()
                        .getDiagramLayoutModelDirect();
        assertThat(DLObjectFetcher.fetchDiagramDOForClass(model, diagramUUID, CLASS_A_UUID))
                .isNotNull();
    }

    private static ClassInDiagram classAInDiagram() {
        return new ClassInDiagram(CLASS_A_UUID, new URI(GRAPH_URI));
    }

    @AfterEach
    void cleanUpDatasetDiagrams() {
        var datasetName = graphIdentifier.datasetName();
        databasePort.getDatasetDiagrams(datasetName).clear();
        databasePort.getDatasetDiagramLayout(datasetName).getDiagramLayoutModel().removeAll();
        databasePort.deleteGraph(new GraphIdentifier(datasetName, GRAPH_URI));
    }

    private static UUID createWorkspaceDiagram() {
        addGraphFromFile("package_and_class.ttl", GRAPH_URI);
        var diagramUUID = UUID.randomUUID();
        databasePort
                .getDatasetDiagrams(graphIdentifier.datasetName())
                .put(diagramUUID, new CustomDiagram(diagramUUID, "custom", new ArrayList<>()));
        return diagramUUID;
    }

    @Test
    void createClassLayoutData_diagramExists_createsClassLayoutData() {
        // Arrange
        addGraphFromFile("package.ttl");

        // Act
        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("http://example.org#")
                        .build();
        service.createClassLayoutData(
                graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID, null);

        // Assert
        assertInitialClassLayoutData(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
    }

    @Test
    void createClassLayoutData_withInitialPosition_createsClassLayoutDataAtPosition() {
        // Arrange
        addGraphFromFile("package.ttl");
        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("http://example.org#")
                        .build();
        var classLayoutPosition = new ClassLayoutPositionDTO();
        classLayoutPosition.setXPosition(123.0F);
        classLayoutPosition.setYPosition(456.0F);

        // Act
        service.createClassLayoutData(
                graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID, classLayoutPosition);

        // Assert
        assertDiagramObject(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
        assertDiagramObjectCoordinates(CLASS_A_UUID, 123.0F, 456.0F);
    }

    @Test
    void createClassLayoutData_packageNeverOpened_createsTheDiagramItBelongsTo() {
        // Layout data is only written once a package is opened, so a class can be the first thing
        // a package ever gets. Without a diagram of its own the object below is invisible to both
        // fetch queries: the position is lost and the next call adds a second object for it.
        addGraphFromFile("package.ttl");
        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("http://example.org#")
                        .build();
        var classLayoutPosition = new ClassLayoutPositionDTO();
        classLayoutPosition.setXPosition(123.0F);
        classLayoutPosition.setYPosition(456.0F);

        // Act
        service.createClassLayoutData(
                graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID, classLayoutPosition);

        // Assert
        assertDiagram(PACKAGE_A_UUID, "");
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForClass(
                                diagramLayout.getDiagramLayoutModelDirect(),
                                PACKAGE_A_UUID,
                                CLASS_A_UUID))
                .isNotNull();
        assertDiagramObjectCoordinates(CLASS_A_UUID, 123.0F, 456.0F);
    }

    @Test
    void createClassLayoutData_layoutDataExists_keepsExistingLayoutData() {
        // Arrange: a class that already has layout data, as when it takes over an uri whose
        // class was deleted while references to it remained
        addGraphFromFile("package_and_class.ttl");
        initialiseDiagramLayout();
        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("http://example.org#")
                        .build();
        var classPositionDTO = new ClassPositionDTO();
        classPositionDTO.setClassUUID(CLASS_A_UUID);
        classPositionDTO.setXPosition(123.0F);
        classPositionDTO.setYPosition(456.0F);
        service.updateClassPositions(graphIdentifier, PACKAGE_A_UUID, List.of(classPositionDTO));

        // Act
        service.createClassLayoutData(
                graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID, null);

        // Assert
        assertThat(
                        diagramLayout
                                .getDiagramLayoutModelDirect()
                                .listSubjectsWithProperty(
                                        DL.belongsToIdentifiedObject,
                                        ResourceFactory.createResource(
                                                new MRID(CLASS_A_UUID).getFullMRID()))
                                .toList())
                .hasSize(1);
        assertDiagramObjectCoordinates(CLASS_A_UUID, 123.0F, 456.0F);
    }

    @Test
    void createClassLayoutData_layoutDataWithoutPoint_placesClassAtRequestedPosition() {
        // Arrange: a diagram object that lost its point, so the class has layout data but no place
        addGraphFromFile("package_and_class.ttl");
        initialiseDiagramLayout();
        var doMRID = assertDiagramObject(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
        var diagramLayoutModel = diagramLayout.getDiagramLayoutModelDirect();
        DLUpdates.deleteDiagramObjectPoint(
                diagramLayoutModel,
                DLObjectFetcher.fetchDOPForDO(diagramLayoutModel, doMRID).getMRID());
        assertDiagramObjectPointDoesNotExist(doMRID);

        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("http://example.org#")
                        .build();
        var classLayoutPosition = new ClassLayoutPositionDTO();
        classLayoutPosition.setXPosition(12.0F);
        classLayoutPosition.setYPosition(34.0F);

        // Act
        service.createClassLayoutData(
                graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID, classLayoutPosition);

        // Assert
        assertDiagramObjectCoordinates(CLASS_A_UUID, 12.0F, 34.0F);
    }

    @Test
    void updateClassPositions_fullGraph_repositionsClasses() {
        // Arrange
        addGraphFromFile("full_graph.ttl");
        initialiseDiagramLayout();

        // Act
        var classAPositionDTO = new ClassPositionDTO();
        classAPositionDTO.setClassUUID(CLASS_A_UUID);
        classAPositionDTO.setXPosition(1.0F);
        classAPositionDTO.setYPosition(1.0F);
        service.updateClassPositions(graphIdentifier, PACKAGE_A_UUID, List.of(classAPositionDTO));

        // Assert
        assertDiagramObjectCoordinates(CLASS_A_UUID, 1.0F, 1.0F);
    }

    @Test
    void updateDiagramObjectName_classExists_updatesDiagramObjectName() {
        // Arrange
        addGraphFromFile("package_and_class.ttl");
        initialiseDiagramLayout();

        // Act
        service.updateDiagramObjectName(graphIdentifier, CLASS_A_UUID, "newClassLabel");

        // Assert
        assertDiagramObject(CLASS_A_UUID, PACKAGE_A_UUID, "newClassLabel");
    }

    @Test
    void deleteClassLayoutData_classExists_deletesClassLayoutData() {
        // Arrange
        addGraphFromFile("association.ttl");
        initialiseDiagramLayout();
        var diagramObjects =
                diagramLayout
                        .getDiagramLayoutModelDirect()
                        .listSubjectsWithProperty(
                                DL.belongsToIdentifiedObject,
                                ResourceFactory.createResource(
                                        new MRID(CLASS_A_UUID).getFullMRID()));
        var do1 = diagramObjects.next();
        var do2 = diagramObjects.next();
        var do1mRID = new MRID(DLUtils.extractUUIDFromMRID(do1.getURI()));
        var do2mRID = new MRID(DLUtils.extractUUIDFromMRID(do2.getURI()));

        // Act
        service.deleteClassLayoutData(graphIdentifier, CLASS_A_UUID);

        // Assert
        assertClassDiagramObjectsDoNotExist(CLASS_A_UUID);
        assertDiagramObjectPointDoesNotExist(do1mRID);
        assertDiagramObjectPointDoesNotExist(do2mRID);
    }
}
