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
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

class CrossProfileDiagramRestControllerTest {

    private GetCustomDiagramsUseCase getCustomDiagramsUseCase;
    private CrossProfileDiagramRestController controller;

    @BeforeEach
    void setUp() {
        getCustomDiagramsUseCase = mock(GetCustomDiagramsUseCase.class);
        controller = new CrossProfileDiagramRestController(getCustomDiagramsUseCase);
    }

    @Test
    void getCrossProfileRenderingData_validDataset_returnsDTOFromUseCase() {
        var expectedDTO = new CrossProfileDiagramDTO(UUID.randomUUID(), List.of());
        when(getCustomDiagramsUseCase.getCrossProfileDiagram("my-dataset", false, false))
                .thenReturn(expectedDTO);

        var result = controller.getCrossProfileDiagram(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(expectedDTO);
        verify(getCustomDiagramsUseCase).getCrossProfileDiagram("my-dataset", false, false);
    }
}
