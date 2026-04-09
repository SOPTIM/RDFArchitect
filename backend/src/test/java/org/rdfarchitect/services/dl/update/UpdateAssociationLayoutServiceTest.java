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
import org.rdfarchitect.api.dto.dl.AssociationDecorationPositionDTO;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.services.dl.AssociationLayoutConstants;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.associationlayout.UpdateAssociationLayoutService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UpdateAssociationLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static final UUID FROM_ASSOCIATION_UUID = UUID.fromString("43236908-a7f7-4749-bb8b-3ac9250de656");
    private static final UUID TO_ASSOCIATION_UUID = UUID.fromString("e88a28a4-bced-4945-9157-8cd4a0af7268");

    private static UpdateAssociationLayoutService service;

    @BeforeAll
    static void setUpEnvironment() {
        service = new UpdateAssociationLayoutService(databasePort);
    }

    @Test
    void updateAssociationDecorationPositions_missingEntries_createsLayoutData() {
        addGraphFromFile("association.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        service.updateAssociationDecorationPositions(graphIdentifier, PACKAGE_A_UUID, List.of(
                  createPositionDTO(FROM_ASSOCIATION_UUID, AssociationLayoutConstants.LABEL_DECORATION_TYPE, 12.5F, -9.0F),
                  createPositionDTO(TO_ASSOCIATION_UUID, AssociationLayoutConstants.MULTIPLICITY_DECORATION_TYPE, -7.0F, 18.0F)));

        assertAssociationDecorationCoordinates(
                  FROM_ASSOCIATION_UUID,
                  PACKAGE_A_UUID,
                  AssociationLayoutConstants.LABEL_DECORATION_TYPE,
                  12.5F,
                  -9.0F);
        assertAssociationDecorationCoordinates(
                  TO_ASSOCIATION_UUID,
                  PACKAGE_A_UUID,
                  AssociationLayoutConstants.MULTIPLICITY_DECORATION_TYPE,
                  -7.0F,
                  18.0F);
    }

    @Test
    void updateAssociationDecorationPositions_existingEntries_updatesCoordinates() {
        addGraphFromFile("association.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        service.updateAssociationDecorationPositions(graphIdentifier, PACKAGE_A_UUID, List.of(
                  createPositionDTO(FROM_ASSOCIATION_UUID, AssociationLayoutConstants.LABEL_DECORATION_TYPE, 2.0F, 3.0F)));
        service.updateAssociationDecorationPositions(graphIdentifier, PACKAGE_A_UUID, List.of(
                  createPositionDTO(FROM_ASSOCIATION_UUID, AssociationLayoutConstants.LABEL_DECORATION_TYPE, 8.0F, -4.0F)));

        assertAssociationDecorationCoordinates(
                  FROM_ASSOCIATION_UUID,
                  PACKAGE_A_UUID,
                  AssociationLayoutConstants.LABEL_DECORATION_TYPE,
                  8.0F,
                  -4.0F);
    }

    private static AssociationDecorationPositionDTO createPositionDTO(UUID associationUUID, String decorationType, float xPosition, float yPosition) {
        var positionDTO = new AssociationDecorationPositionDTO();
        positionDTO.setAssociationUUID(associationUUID);
        positionDTO.setDecorationType(decorationType);
        positionDTO.setXPosition(xPosition);
        positionDTO.setYPosition(yPosition);
        return positionDTO;
    }

    private static void assertAssociationDecorationCoordinates(
              UUID associationUUID,
              UUID packageUUID,
              String decorationType,
              float xPosition,
              float yPosition) {
        var model = databasePort.getGraphWithContext(graphIdentifier).getDiagramLayout().getDiagramLayoutModel();
        var associationResource = ResourceFactory.createResource(new MRID(associationUUID).getFullMRID());
        var packageResource = ResourceFactory.createResource(new MRID(packageUUID).getFullMRID());
        var diagramObjectName = AssociationLayoutConstants.getDiagramObjectName(decorationType);

        var diagramObject = model.listSubjectsWithProperty(DL.belongsToIdentifiedObject, associationResource)
                                 .toList()
                                 .stream()
                                 .filter(candidate -> candidate.hasProperty(DL.belongsToDiagram, packageResource))
                                 .filter(candidate -> candidate.hasProperty(CIM.ioName, ResourceFactory.createPlainLiteral(diagramObjectName)))
                                 .findFirst()
                                 .orElseThrow();

        var diagramObjectMRID = new MRID(DLUtils.extractUUIDFromMRID(diagramObject.getURI()));
        assertDiagramObjectPoint(diagramObjectMRID, xPosition, yPosition);
    }
}
