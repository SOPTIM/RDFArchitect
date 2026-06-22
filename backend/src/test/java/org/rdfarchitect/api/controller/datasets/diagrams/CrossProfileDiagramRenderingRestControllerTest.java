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

package org.rdfarchitect.api.controller.datasets.diagrams;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.cross_profile_diagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.models.dto.rendering.RenderCrossProfileDiagramUseCase;
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

class CrossProfileDiagramRenderingRestControllerTest {

    private RenderCrossProfileDiagramUseCase renderer;
    private GetCustomDiagramsUseCase getCustomDiagramsUseCase;
    private CrossProfileDiagramRenderingRestController controller;

    @BeforeEach
    void setUp() {
        renderer = mock(RenderCrossProfileDiagramUseCase.class);
        getCustomDiagramsUseCase = mock(GetCustomDiagramsUseCase.class);
        controller =
                new CrossProfileDiagramRenderingRestController(renderer, getCustomDiagramsUseCase);
    }

    @Test
    void getCrossProfileRenderingData_validDataset_returnsRenderingDTO() {
        var diagramUUID = UUID.randomUUID();
        var diagram = new CrossProfileDiagramDTO(diagramUUID, List.of());
        var expectedRendering = mock(RenderingDataDTO.class);

        when(getCustomDiagramsUseCase.getCrossProfileDiagram("my-dataset", true, true))
                .thenReturn(diagram);
        when(renderer.renderCrossProfileDiagramUML(diagram, "my-dataset"))
                .thenReturn(expectedRendering);

        var result = controller.getCrossProfileRenderingData(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(expectedRendering);
        verify(getCustomDiagramsUseCase).getCrossProfileDiagram("my-dataset", true, true);
        verify(renderer).renderCrossProfileDiagramUML(diagram, "my-dataset");
    }
}
