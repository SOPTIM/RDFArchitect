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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgePointIdDTO;
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
    void updateDatasetDiagramLayout_passesTheLayoutAndReturnsTheNewPointIds() {
        var diagramUUID = UUID.randomUUID();
        var diagramLayout = new DiagramLayoutDTO();
        var newPointIds = List.of(new EdgePointIdDTO("client", UUID.randomUUID().toString()));
        when(updateDiagramLayoutUseCase.updateDiagramLayout("dataset", diagramUUID, diagramLayout))
                .thenReturn(newPointIds);

        var response =
                controller.updateDatasetDiagramLayout(
                        HttpHeaders.ORIGIN, "dataset", diagramUUID.toString(), diagramLayout);

        assertThat(response).isEqualTo(newPointIds);
    }

    @Test
    void updateDatasetEdgeLayouts_passesTheEdgesAndReturnsTheNewPointIds() {
        var diagramUUID = UUID.randomUUID();
        var edgeLayouts = List.of(new EdgeLayoutDTO());
        var newPointIds = List.of(new EdgePointIdDTO("client", UUID.randomUUID().toString()));
        when(updateEdgeLayoutUseCase.updateEdgeLayouts("dataset", diagramUUID, edgeLayouts))
                .thenReturn(newPointIds);

        var response =
                controller.updateDatasetEdgeLayouts(
                        HttpHeaders.ORIGIN, "dataset", diagramUUID.toString(), edgeLayouts);

        assertThat(response).isEqualTo(newPointIds);
    }
}
