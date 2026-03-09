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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.rendering.GraphFilter;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;

class UpdateDiagramLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static UpdateDiagramLayoutService service;
    private static GraphFilter graphFilter;

    @BeforeAll
    static void setUpEnvironment() {
        service = new UpdateDiagramLayoutService(databasePort, converter);
        graphFilter = new GraphFilter(true);
        graphFilter.setPackageUUID(String.valueOf(PACKAGE_A_UUID));
    }

    @Test
    void createDiagramLayout_graphWithTwoPackages_createsLayout() {
        //Arrange
        addGraphFromFile("full_graph.ttl");

        //Act
        service.createDiagramLayout(graphIdentifier);

        //Assert
        assertDiagram((diagramLayout.getDefaultPackageMRID().getUuid()), DEFAULT_PACKAGE_LABEL);
        assertDiagram(PACKAGE_A_UUID, PACKAGE_A_LABEL);
        assertDiagram(PACKAGE_B_UUID, PACKAGE_B_LABEL);
        assertInitialClassLayoutData(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
        assertInitialClassLayoutData(CLASS_B_UUID, PACKAGE_B_UUID, CLASS_B_LABEL);
        assertInitialClassLayoutData(CLASS_C_UUID, diagramLayout.getDefaultPackageMRID().getUuid(), CLASS_C_LABEL);
    }

    @Test
    void ensureDiagramLayoutExists_emptyDiagramLayout_createsDiagram() {
        //Arrange
        addGraphFromFile("package.ttl");

        //Act
        var cimCollection = converter.convert(graphIdentifier, graphFilter);
        service.ensureDiagramLayoutExists(graphIdentifier, PACKAGE_A_UUID, cimCollection);

        //Assert
        assertDiagram(PACKAGE_A_UUID, PACKAGE_A_LABEL);
    }

    @Test
    void ensureDiagramLayoutExists_emptyDiagramLayout_createsClassLayout() {
        //Arrange
        addGraphFromFile("package_and_class.ttl");

        //Act
        var cimCollection = converter.convert(graphIdentifier, graphFilter);
        service.ensureDiagramLayoutExists(graphIdentifier, PACKAGE_A_UUID, cimCollection);

        //Assert
        assertInitialClassLayoutData(CLASS_A_UUID, PACKAGE_A_UUID, CLASS_A_LABEL);
    }
}
