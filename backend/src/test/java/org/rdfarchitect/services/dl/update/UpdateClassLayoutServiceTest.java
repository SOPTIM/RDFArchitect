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

import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;

import java.util.List;

class UpdateClassLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static UpdateClassLayoutService service;

    @BeforeAll
    static void setUpEnvironment() {
        service = new UpdateClassLayoutService(databasePort, packageMapper);
    }

    @Test
    void createClassLayoutData_diagramExists_createsClassLayoutData() {
        //Arrange
        addGraphFromFile("package.ttl");

        //Act
        var packageDTO = PackageDTO.builder()
                                   .uuid(PACKAGE_A_UUID)
                                   .label(PACKAGE_A_LABEL)
                                   .prefix("ex")
                                   .build();
        service.createClassLayoutData(graphIdentifier, packageDTO, CLASS_A_LABEL, CLASS_A_UUID);

        //Assert
        assertInitialClassLayoutData(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
    }

    @Test
    void updateClassPositions_fullGraph_repositionsClasses() {
        //Arrange
        addGraphFromFile("full_graph.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        //Act
        var classAPositionDTO = new ClassPositionDTO();
        classAPositionDTO.setClassUUID(CLASS_A_UUID);
        classAPositionDTO.setXPosition(1.0F);
        classAPositionDTO.setYPosition(1.0F);
        service.updateClassPositions(graphIdentifier, PACKAGE_A_UUID, List.of(classAPositionDTO));

        //Assert
        assertDiagramObjectCoordinates(CLASS_A_UUID, 1.0F, 1.0F);
    }

    @Test
    void updateDiagramObjectName_classExists_updatesDiagramObjectName() {
        //Arrange
        addGraphFromFile("package_and_class.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        //Act
        service.updateDiagramObjectName(graphIdentifier, CLASS_A_UUID, "newClassLabel");

        //Assert
        assertDiagramObject(CLASS_A_UUID, PACKAGE_A_UUID, "newClassLabel");
    }

    @Test
    void deleteClassLayoutData_classExists_deletesClassLayoutData() {
        //Arrange
        addGraphFromFile("association.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);
        var diagramObjects = diagramLayout.getDiagramLayoutModel()
                                          .listSubjectsWithProperty(
                                                    DL.belongsToIdentifiedObject,
                                                    ResourceFactory.createResource(new MRID(CLASS_A_UUID).getFullMRID())
                                                                   );
        var do1 = diagramObjects.next();
        var do2 = diagramObjects.next();
        var do1mRID = new MRID(DLUtils.extractUUIDFromMRID(do1.getURI()));
        var do2mRID = new MRID(DLUtils.extractUUIDFromMRID(do2.getURI()));

        //Act
        service.deleteClassLayoutData(graphIdentifier, CLASS_A_UUID);

        //Assert
        assertClassDiagramObjectsDoNotExist(CLASS_A_UUID);
        assertDiagramObjectPointDoesNotExist(do1mRID);
        assertDiagramObjectPointDoesNotExist(do2mRID);
    }
}
