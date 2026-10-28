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

package org.rdfarchitect.api.controller.datasets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.services.dl.update.UpdateDiagramLayoutUseCase;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutUseCase;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

class GlobalDiagramLayoutDataRESTControllerTest {

    private UpdateDiagramLayoutUseCase updateDiagramLayoutUseCase;
    private UpdateEdgeLayoutUseCase updateEdgeLayoutUseCase;
    private GlobalDiagramLayoutDataRESTController controller;

    @BeforeEach
    void setUp() {
        updateDiagramLayoutUseCase = mock(UpdateDiagramLayoutUseCase.class);
        updateEdgeLayoutUseCase = mock(UpdateEdgeLayoutUseCase.class);
        controller =
                new GlobalDiagramLayoutDataRESTController(
                        updateDiagramLayoutUseCase, updateEdgeLayoutUseCase);
    }

    @Test
    void updateDatasetDiagramLayout_passesTheLayout() {
        var diagramUUID = UUID.randomUUID();
        var diagramLayout = new DiagramLayoutDTO();

        var response =
                controller.updateDatasetDiagramLayout(
                        HttpHeaders.ORIGIN, "dataset", diagramUUID.toString(), diagramLayout);

        verify(updateDiagramLayoutUseCase)
                .updateDiagramLayout("dataset", diagramUUID, diagramLayout);
        assertThat(response).isEqualTo(Response.SUCCESS);
    }

    @Test
    void updateDatasetEdgeLayouts_passesTheEdges() {
        var diagramUUID = UUID.randomUUID();
        var edgeLayouts = List.of(new EdgeLayoutDTO());

        var response =
                controller.updateDatasetEdgeLayouts(
                        HttpHeaders.ORIGIN, "dataset", diagramUUID.toString(), edgeLayouts);

        verify(updateEdgeLayoutUseCase).updateEdgeLayouts("dataset", diagramUUID, edgeLayouts);
        assertThat(response).isEqualTo(Response.SUCCESS);
    }
}
