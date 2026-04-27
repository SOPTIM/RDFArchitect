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
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.packagelayout.UpdatePackageLayoutService;

class UpdatePackageLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static UpdatePackageLayoutService service;

    @BeforeAll
    static void setUpEnvironment() {
        service = new UpdatePackageLayoutService(databasePort, packageMapper, converter);
    }

    @Test
    void createPackageLayoutData_emptyGraph_createsDiagram() {
        // Arrange
        addGraphFromFile("empty_graph.ttl");

        // Act
        var packageDTO =
                PackageDTO.builder()
                        .uuid(PACKAGE_A_UUID)
                        .label(PACKAGE_A_LABEL)
                        .prefix("ex")
                        .build();
        service.createPackageLayoutData(graphIdentifier, packageDTO, PACKAGE_A_UUID);

        // Assert
        assertDiagram(PACKAGE_A_UUID, PACKAGE_A_LABEL);
    }

    @Test
    void deletePackageLayoutData_fullGraph_deletesSpecificPackageLayoutData() {
        // Arrange
        addGraphFromFile("association.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);
        var diagramAobjectsList =
                diagramLayout
                        .getDiagramLayoutModel()
                        .listSubjectsWithProperty(
                                DL.belongsToDiagram,
                                ResourceFactory.createResource(
                                        new MRID(PACKAGE_A_UUID).getFullMRID()))
                        .toList();
        var doA =
                diagramAobjectsList.stream()
                        .filter(
                                dobj ->
                                        dobj.hasProperty(
                                                DL.belongsToIdentifiedObject,
                                                ResourceFactory.createResource(
                                                        new MRID(CLASS_A_UUID).getFullMRID())))
                        .findFirst()
                        .orElse(null);
        var doB =
                diagramAobjectsList.stream()
                        .filter(
                                dobj ->
                                        dobj.hasProperty(
                                                DL.belongsToIdentifiedObject,
                                                ResourceFactory.createResource(
                                                        new MRID(CLASS_B_UUID).getFullMRID())))
                        .findFirst()
                        .orElse(null);
        var doAmRID = new MRID(DLUtils.extractUUIDFromMRID(doA.getURI()));
        var doBmRID = new MRID(DLUtils.extractUUIDFromMRID(doB.getURI()));

        // Act
        service.deletePackageLayoutData(graphIdentifier, PACKAGE_A_UUID);

        // Assert
        assertDiagramDoesNotExist(PACKAGE_A_UUID);
        assertSpecificDiagramObjectDoesNotExist(CLASS_A_UUID, PACKAGE_A_UUID);
        assertSpecificDiagramObjectDoesNotExist(CLASS_B_UUID, PACKAGE_A_UUID);
        assertDiagramObjectPointDoesNotExist(doAmRID);
        assertDiagramObjectPointDoesNotExist(doBmRID);
        assertDiagram(PACKAGE_B_UUID, PACKAGE_B_LABEL);
        assertInitialClassLayoutData(CLASS_A_UUID, PACKAGE_B_UUID, CLASS_A_LABEL);
        assertInitialClassLayoutData(CLASS_B_UUID, PACKAGE_B_UUID, CLASS_B_LABEL);
    }

    @Test
    void replaceDiagram_diagramExists_replacesDiagram() {
        // Arrange
        addGraphFromFile("package.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        // Act
        service.replaceDiagram(graphIdentifier, PACKAGE_A_UUID, "newDiagramLabel");

        // Assert
        assertDiagram(PACKAGE_A_UUID, "newDiagramLabel");
    }
}
