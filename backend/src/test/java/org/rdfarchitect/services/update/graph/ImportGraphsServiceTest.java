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

package org.rdfarchitect.services.update.graph;

import org.apache.jena.graph.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContext;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportGraphsServiceTest {

    private ImportGraphsUseCase importGraphsUseCase;
    private ChangeLogUseCase changeLogUseCaseMock;
    private DatabasePort databasePortMock;

    @BeforeEach
    void setUp() {
        changeLogUseCaseMock = mock(ChangeLogUseCase.class);
        var createDiagramLayoutUseCaseMock = mock(CreateDiagramLayoutUseCase.class);
        databasePortMock = mock(DatabasePort.class);
        importGraphsUseCase = new ImportGraphsService(changeLogUseCaseMock, createDiagramLayoutUseCaseMock, databasePortMock);
    }

    @Test
    void importGraphs_sameFileNameTwice_autoGeneratesUniqueGraphUris() {
        var datasetName = "ds";

        var file1 = new MockMultipartFile(
                  "graph",
                  "graph.ttl",
                  "text/turtle",
                  "@prefix ex: <http://example.com/> . ex:a ex:b ex:c .".getBytes(StandardCharsets.UTF_8)
        );
        var file2 = new MockMultipartFile(
                  "graph",
                  "graph.ttl",
                  "text/turtle",
                  "@prefix ex: <http://example.com/> . ex:d ex:e ex:f .".getBytes(StandardCharsets.UTF_8)
        );

        when(databasePortMock.listGraphUris(datasetName))
                  .thenThrow(new RuntimeException("dataset does not exist"));

        var graphWithContextMock = mock(GraphWithContext.class);
        when(databasePortMock.getGraphWithContext(any(GraphIdentifier.class))).thenReturn(graphWithContextMock);
        var graphMock = mock(GraphRewindableWithUUIDs.class, RETURNS_DEEP_STUBS);
        when(graphWithContextMock.getRdfGraph()).thenReturn(graphMock);

        var result = importGraphsUseCase.importGraphs(datasetName, List.of(file1, file2), null);

        assertThat(result).containsExactly(
                  RDFA.GRAPH_URI + "graph",
                  RDFA.GRAPH_URI + "graph_1"
                                          );

        var captor = ArgumentCaptor.forClass(GraphIdentifier.class);

        verify(databasePortMock, times(2)).createGraph(captor.capture(), any(Graph.class));

        assertThat(captor.getAllValues())
                  .extracting(GraphIdentifier::getGraphUri)
                  .containsExactly(
                            RDFA.GRAPH_URI + "graph",
                            RDFA.GRAPH_URI + "graph_1"
                                  );

        verify(changeLogUseCaseMock, times(2)).recordChange(any(GraphIdentifier.class), any());
    }
}
