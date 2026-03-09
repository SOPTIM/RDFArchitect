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

package org.rdfarchitect.services.dl;

import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabase;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.rdf.graph.wrapper.DiagramLayout;
import org.rdfarchitect.services.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.GraphToCIMCollectionConverterUseCase;
import org.rdfarchitect.services.dl.update.UpdateDiagramLayoutService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class DiagramLayoutServicesTestBase {

    public static final String TTL_FILES_PATH = "src/test/java/org/rdfarchitect/services/dl/update/";
    public static final GraphIdentifier graphIdentifier = new GraphIdentifier("default", "default");
    //CONSTANTS FOR ASSERTS
    public static final String DEFAULT_PACKAGE_LABEL = "default/default";
    public static final String PACKAGE_A_LABEL = "packageA";
    public static final UUID PACKAGE_A_UUID = UUID.fromString("43836908-c7f7-4749-bb8b-3ac9250de655");
    public static final String PACKAGE_B_LABEL = "packageB";
    public static final UUID PACKAGE_B_UUID = UUID.fromString("05ef0165-712c-4eaa-833b-cedf2d340a3b");
    public static final String CLASS_A_LABEL = "classA";
    public static final UUID CLASS_A_UUID = UUID.fromString("62118c18-10a9-49ba-b605-e74292a85186");
    public static final String CLASS_B_LABEL = "classB";
    public static final UUID CLASS_B_UUID = UUID.fromString("a516a307-21ee-4a79-a817-1a4a57a1b8de");
    public static final String CLASS_C_LABEL = "classC";
    public static final UUID CLASS_C_UUID = UUID.fromString("06610df1-4fde-4b75-b845-ef04b947054c");
    public static GraphToCIMCollectionConverterUseCase converter;
    public static DatabasePort databasePort;
    public static PackageMapper packageMapper;
    public static InMemoryDatabase database;
    public static DiagramLayout diagramLayout;
    public static UpdateDiagramLayoutService updateDiagramLayoutService;

    @BeforeAll
    static void setUpEnvironment() {
        diagramLayout = new DiagramLayout();
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl());
        converter = new GraphToCIMCollectionConverterService(databasePort);
        packageMapper = Mappers.getMapper(PackageMapper.class);
        updateDiagramLayoutService = new UpdateDiagramLayoutService(databasePort, converter);
    }

    public static void addGraphFromFile(String fileName) {
        byte[] content;
        try {
            content = Files.readAllBytes(Path.of(TTL_FILES_PATH + fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var file = new MockMultipartFile(fileName, fileName, "text/turtle", content);
        var graph = new GraphFileSourceBuilderImpl()
                  .setFile(file)
                  .setGraphName(graphIdentifier.getGraphUri())
                  .build()
                  .graph();
        databasePort.createGraph(graphIdentifier, graph);
        databasePort.getGraphWithContext(graphIdentifier).setDiagramLayout(diagramLayout);
    }

    public static void assertDiagram(UUID packageUUID, String packageName) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();
        var diagram = model.getResource(new MRID(packageUUID).getFullMRID());
        assertThat(diagram).isNotNull();
        assertThat(diagram.hasProperty(RDF.type, DL.diagramType)).isTrue();
        assertThat(diagram.hasProperty(DL.orientation, DL.negativeOrientation)).isTrue();
        assertThat(diagram.hasProperty(CIM.ioName, ResourceFactory.createPlainLiteral(packageName))).isTrue();
    }

    public static void assertDiagramDoesNotExist(UUID packageUUID) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramResource = ResourceFactory.createResource(new MRID(packageUUID).getFullMRID());

        assertThat(model.contains(diagramResource, RDF.type, DL.diagramType)).isFalse();
    }

    public static void assertInitialClassLayoutData(UUID classUUID, UUID packageUUID, String className) {
        var doMRID = assertDiagramObject(classUUID, packageUUID, className);
        assertDiagramObjectPoint(doMRID, 0.0F, 0.0F);
    }

    public static MRID assertDiagramObject(UUID classUUID, UUID packageUUID, String className) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjects = model.listSubjectsWithProperty(
                  DL.belongsToIdentifiedObject,
                  ResourceFactory.createResource(new MRID(classUUID).getFullMRID())
                                                           );
        assertThat(diagramObjects.hasNext()).isTrue();

        var diagramObject = diagramObjects.next();

        assertThat(diagramObject).isNotNull();

        var doMRID = diagramObject.getURI();

        assertThat(diagramObject.hasProperty(RDF.type, DL.diagramObjectType)).isTrue();
        assertThat(diagramObject.hasProperty(DL.belongsToDiagram, ResourceFactory.createResource(new MRID(packageUUID).getFullMRID()))).isTrue();
        assertThat(diagramObject.hasProperty(CIM.ioName, ResourceFactory.createPlainLiteral(className))).isTrue();

        return new MRID(DLUtils.extractUUIDFromMRID(doMRID));
    }

    public static void assertClassDiagramObjectsDoNotExist(UUID classUUID) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjects = model.listSubjectsWithProperty(
                  DL.belongsToIdentifiedObject,
                  ResourceFactory.createResource(new MRID(classUUID).getFullMRID())
                                                           );

        assertThat(diagramObjects.hasNext()).isFalse();
    }

    public static void assertSpecificDiagramObjectDoesNotExist(UUID classUUID, UUID packageUUID) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var classResource = ResourceFactory.createResource(new MRID(classUUID).getFullMRID());
        var packageResource = ResourceFactory.createResource(new MRID(packageUUID).getFullMRID());

        var matchingDiagramObjects = model.listSubjectsWithProperty(DL.belongsToIdentifiedObject, classResource)
                                          .toList()
                                          .stream()
                                          .filter(diagramObject -> diagramObject.hasProperty(DL.belongsToDiagram, packageResource))
                                          .toList();

        assertThat(matchingDiagramObjects).isEmpty();
    }

    public static void assertDiagramObjectPoint(MRID doMRID, float xPosition, float yPosition) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjectPoints = model.listSubjectsWithProperty(
                  DL.belongsToDiagramObject,
                  ResourceFactory.createResource(doMRID.getFullMRID())
                                                                );
        assertThat(diagramObjectPoints.hasNext()).isTrue();

        var diagramObjectPoint = diagramObjectPoints.next();

        assertThat(diagramObjectPoint).isNotNull();
        assertThat(diagramObjectPoint.hasProperty(RDF.type, DL.diagramObjectPointType)).isTrue();
        assertThat(diagramObjectPoint.hasProperty(DL.xPosition, ResourceFactory.createPlainLiteral(String.valueOf(xPosition)))).isTrue();
        assertThat(diagramObjectPoint.hasProperty(DL.yPosition, ResourceFactory.createPlainLiteral(String.valueOf(yPosition)))).isTrue();
    }

    public static void assertDiagramObjectPointDoesNotExist(MRID doMRID) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjectPoints = model.listSubjectsWithProperty(
                  DL.belongsToDiagramObject,
                  ResourceFactory.createResource(doMRID.getFullMRID()));

        assertThat(diagramObjectPoints.hasNext()).isFalse();
    }

    public static void assertDiagramObjectCoordinates(UUID classUUID, float xPosition, float yPosition) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();

        var diagramObjects = model.listSubjectsWithProperty(
                  DL.belongsToIdentifiedObject,
                  ResourceFactory.createResource(new MRID(classUUID).getFullMRID())
                                                           );
        assertThat(diagramObjects.hasNext()).isTrue();

        var diagramObject = diagramObjects.next();

        assertThat(diagramObject).isNotNull();

        var doMRID = diagramObject.getURI();

        var diagramObjectPoints = model.listSubjectsWithProperty(
                  DL.belongsToDiagramObject,
                  ResourceFactory.createResource(doMRID)
                                                                );
        assertThat(diagramObjectPoints.hasNext()).isTrue();

        var diagramObjectPoint = diagramObjectPoints.next();

        assertThat(diagramObjectPoint).isNotNull();
        assertThat(diagramObjectPoint.hasProperty(RDF.type, DL.diagramObjectPointType)).isTrue();
        assertThat(diagramObjectPoint.hasProperty(DL.xPosition, ResourceFactory.createPlainLiteral(String.valueOf(xPosition)))).isTrue();
        assertThat(diagramObjectPoint.hasProperty(DL.yPosition, ResourceFactory.createPlainLiteral(String.valueOf(yPosition)))).isTrue();
    }

    @AfterEach
    void tearDown() {
        databasePort.deleteGraph(graphIdentifier);
        diagramLayout = new DiagramLayout();
    }
}
