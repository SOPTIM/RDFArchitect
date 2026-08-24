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
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.models.dto.rendering.RenderMergedDiagramUseCase;
import org.springframework.http.HttpHeaders;

class CrossProfileDiagramRenderingRestControllerTest {

    private RenderMergedDiagramUseCase renderer;
    private CrossProfileDiagramRenderingRestController controller;

    @BeforeEach
    void setUp() {
        renderer = mock(RenderMergedDiagramUseCase.class);
        controller = new CrossProfileDiagramRenderingRestController(renderer);
    }

    @Test
    void getCrossProfileRenderingData_validDataset_returnsRenderingDTO() {
        var expectedRendering = mock(RenderingDataDTO.class);
        when(renderer.renderCrossProfileDiagram("my-dataset")).thenReturn(expectedRendering);

        var result = controller.getCrossProfileRenderingData(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(expectedRendering);
        verify(renderer).renderCrossProfileDiagram("my-dataset");
    }
}
