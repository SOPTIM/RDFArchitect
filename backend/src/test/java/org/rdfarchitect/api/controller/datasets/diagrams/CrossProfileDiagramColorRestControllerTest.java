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
import org.rdfarchitect.api.dto.crossProfileDiagram.CrossProfileDiagramColorDataDTO;
import org.rdfarchitect.services.diagrams.CrossProfileColorUseCase;
import org.springframework.http.HttpHeaders;

import java.util.Map;

class CrossProfileDiagramColorRestControllerTest {

    private CrossProfileColorUseCase colorUseCase;
    private CrossProfileDiagramColorRestController controller;

    @BeforeEach
    void setUp() {
        colorUseCase = mock(CrossProfileColorUseCase.class);
        controller = new CrossProfileDiagramColorRestController(colorUseCase);
    }

    @Test
    void getCrossProfileColors_returnsDTOFromUseCase() {
        var expectedDTO = new CrossProfileDiagramColorDataDTO(Map.of("graph-a", "#ff0000"));
        when(colorUseCase.getCrossProfileColors("my-dataset")).thenReturn(expectedDTO);

        var result = controller.getCrossProfileColors(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(expectedDTO);
        verify(colorUseCase).getCrossProfileColors("my-dataset");
    }

    @Test
    void updateCrossProfileColors_invokesUseCaseWithPayload() {
        var colorData = new CrossProfileDiagramColorDataDTO(Map.of("graph-a", "#00ff00"));

        controller.updateCrossProfileColors(HttpHeaders.ORIGIN, "my-dataset", colorData);

        verify(colorUseCase).replaceCrossProfileColors("my-dataset", colorData);
    }
}
