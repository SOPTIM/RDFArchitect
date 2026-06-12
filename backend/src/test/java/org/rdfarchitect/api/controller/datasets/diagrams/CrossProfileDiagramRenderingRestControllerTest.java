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
import org.rdfarchitect.api.dto.crossProfileDiagram.CrossProfileDiagramDTO;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.services.diagrams.GetCustomDiagramsUseCase;
import org.rdfarchitect.services.rendering.DiagramToCIMCollectionConverterUseCase;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

class CrossProfileDiagramRenderingRestControllerTest {

    private DiagramToCIMCollectionConverterUseCase converter;
    private RenderCIMCollectionUseCase renderer;
    private GetCustomDiagramsUseCase getCustomDiagramsUseCase;
    private DatabasePort databasePort;
    private CrossProfileDiagramRenderingRestController controller;

    @BeforeEach
    void setUp() {
        converter = mock(DiagramToCIMCollectionConverterUseCase.class);
        renderer = mock(RenderCIMCollectionUseCase.class);
        getCustomDiagramsUseCase = mock(GetCustomDiagramsUseCase.class);
        databasePort = mock(DatabasePort.class);
        controller =
                new CrossProfileDiagramRenderingRestController(
                        converter, renderer, getCustomDiagramsUseCase, databasePort);
    }

    @Test
    void getCrossProfileRenderingData_orchestratesUseCasesAndReturnsRenderingDTO() {
        var diagramUUID = UUID.randomUUID();
        var diagram = new CrossProfileDiagramDTO(diagramUUID, List.of());
        var cimCollection = mock(CIMCollection.class);
        var expectedRendering = mock(RenderingDataDTO.class);

        when(getCustomDiagramsUseCase.getCrossProfileDiagram("my-dataset", true, true))
                .thenReturn(diagram);
        when(converter.convert(diagram)).thenReturn(cimCollection);
        when(databasePort.getCrossProfileDiagramUUID("my-dataset")).thenReturn(diagramUUID);
        when(renderer.renderGlobalUML(cimCollection, "my-dataset", diagramUUID))
                .thenReturn(expectedRendering);

        var result = controller.getCrossProfileRenderingData(HttpHeaders.ORIGIN, "my-dataset");

        assertThat(result).isEqualTo(expectedRendering);
        verify(getCustomDiagramsUseCase).getCrossProfileDiagram("my-dataset", true, true);
        verify(converter).convert(diagram);
        verify(databasePort).getCrossProfileDiagramUUID("my-dataset");
        verify(renderer).renderGlobalUML(cimCollection, "my-dataset", diagramUUID);
    }
}
