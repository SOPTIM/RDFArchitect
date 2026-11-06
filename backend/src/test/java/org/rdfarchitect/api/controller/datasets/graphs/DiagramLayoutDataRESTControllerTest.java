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

package org.rdfarchitect.api.controller.datasets.graphs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.controller.Response;
import org.rdfarchitect.api.controller.datasets.graphs.rendering.DiagramLayoutDataRESTController;
import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.ExpandURIUseCase;
import org.rdfarchitect.services.dl.update.UpdateDiagramLayoutUseCase;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutUseCase;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

class DiagramLayoutDataRESTControllerTest {

    private static final GraphIdentifier GRAPH = new GraphIdentifier("dataset", "expanded-graph");

    private UpdateDiagramLayoutUseCase updateDiagramLayoutUseCase;
    private UpdateEdgeLayoutUseCase updateEdgeLayoutUseCase;
    private DiagramLayoutDataRESTController controller;

    @BeforeEach
    void setUp() {
        var expandURIUseCase = mock(ExpandURIUseCase.class);
        when(expandURIUseCase.expandUri("dataset", "graph")).thenReturn("expanded-graph");
        updateDiagramLayoutUseCase = mock(UpdateDiagramLayoutUseCase.class);
        updateEdgeLayoutUseCase = mock(UpdateEdgeLayoutUseCase.class);
        controller =
                new DiagramLayoutDataRESTController(
                        expandURIUseCase, updateDiagramLayoutUseCase, updateEdgeLayoutUseCase);
    }

    @Test
    void updateDiagramLayout_passesTheLayout() {
        var diagramUUID = UUID.randomUUID();
        var diagramLayout = new DiagramLayoutDTO();

        var response =
                controller.updateDiagramLayout(
                        HttpHeaders.ORIGIN,
                        "dataset",
                        "graph",
                        diagramUUID.toString(),
                        diagramLayout);

        verify(updateDiagramLayoutUseCase).updateDiagramLayout(GRAPH, diagramUUID, diagramLayout);
        assertThat(response).isEqualTo(Response.SUCCESS);
    }

    @Test
    void updateEdgeLayouts_resolvesTheDefaultDiagram() {
        var edgeLayouts = List.of(new EdgeLayoutDTO());

        var response =
                controller.updateEdgeLayouts(
                        HttpHeaders.ORIGIN, "dataset", "graph", "default", edgeLayouts);

        verify(updateEdgeLayoutUseCase).updateEdgeLayouts(GRAPH, null, edgeLayouts);
        assertThat(response).isEqualTo(Response.SUCCESS);
    }
}
