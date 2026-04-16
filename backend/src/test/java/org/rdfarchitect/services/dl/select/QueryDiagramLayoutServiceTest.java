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

package org.rdfarchitect.services.dl.select;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.AssociationDecorationPositionDTO;
import org.rdfarchitect.services.dl.AssociationLayoutConstants;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.associationlayout.UpdateAssociationLayoutService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class QueryDiagramLayoutServiceTest extends DiagramLayoutServicesTestBase {

    private static final UUID FROM_ASSOCIATION_UUID = UUID.fromString("43236908-a7f7-4749-bb8b-3ac9250de656");

    private static QueryDiagramLayoutService queryDiagramLayoutService;
    private static UpdateAssociationLayoutService updateAssociationLayoutService;

    @BeforeAll
    static void setUpEnvironment() {
        queryDiagramLayoutService = new QueryDiagramLayoutService(databasePort);
        updateAssociationLayoutService = new UpdateAssociationLayoutService(databasePort);
    }

    @Test
    void fetchRenderingLayoutData_withAssociationLayout_returnsPersistedOffsets() {
        addGraphFromFile("association.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);

        var labelPositionDTO = new AssociationDecorationPositionDTO();
        labelPositionDTO.setAssociationUUID(FROM_ASSOCIATION_UUID);
        labelPositionDTO.setDecorationType(AssociationLayoutConstants.LABEL_DECORATION_TYPE);
        labelPositionDTO.setXPosition(14.0F);
        labelPositionDTO.setYPosition(-6.0F);

        var multiplicityPositionDTO = new AssociationDecorationPositionDTO();
        multiplicityPositionDTO.setAssociationUUID(FROM_ASSOCIATION_UUID);
        multiplicityPositionDTO.setDecorationType(AssociationLayoutConstants.MULTIPLICITY_DECORATION_TYPE);
        multiplicityPositionDTO.setXPosition(4.0F);
        multiplicityPositionDTO.setYPosition(9.0F);

        updateAssociationLayoutService.updateAssociationDecorationPositions(
                  graphIdentifier,
                  PACKAGE_A_UUID,
                  List.of(labelPositionDTO, multiplicityPositionDTO));

        var result = queryDiagramLayoutService.fetchRenderingLayoutData(graphIdentifier, PACKAGE_A_UUID);
        var associationLayoutData = result.getAssociationLayoutingData().get(FROM_ASSOCIATION_UUID);

        assertThat(associationLayoutData).isNotNull();
        assertThat(associationLayoutData.getLabelLayoutingData().getPosition().getX()).isEqualTo(14.0F);
        assertThat(associationLayoutData.getLabelLayoutingData().getPosition().getY()).isEqualTo(-6.0F);
        assertThat(associationLayoutData.getMultiplicityLayoutingData().getPosition().getX()).isEqualTo(4.0F);
        assertThat(associationLayoutData.getMultiplicityLayoutingData().getPosition().getY()).isEqualTo(9.0F);
    }
}
