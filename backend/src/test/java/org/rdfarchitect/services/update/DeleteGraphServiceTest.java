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

package org.rdfarchitect.services.update;

import static org.mockito.Mockito.*;

import org.apache.jena.graph.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.rdfarchitect.services.update.graph.DeleteGraphService;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

class DeleteGraphServiceTest {

    private DeleteGraphService deleteGraphService;
    private DatabasePort mockDatabasePort;

    @BeforeEach
    void setUp() {
        mockDatabasePort = mock(DatabasePort.class);
        var mockCreateDiagramLayoutUseCase = mock(CreateDiagramLayoutUseCase.class);
        deleteGraphService =
                new DeleteGraphService(mockDatabasePort, mockCreateDiagramLayoutUseCase);
    }

    @Test
    void deleteGraph_callsDeleteGraph() {
        var graphIdentifier = new GraphIdentifier("default", "http://example.com/graph");
        deleteGraphService.deleteGraph(graphIdentifier);
        verify(mockDatabasePort).deleteGraph(graphIdentifier);
    }

    @Test
    void replaceGraph_callsDeleteAndCreateGraph() {
        var graphIdentifier = new GraphIdentifier("default", "http://example.com/graph");
        var mockFile =
                new MockMultipartFile(
                        "graph.ttl",
                        "graph.ttl",
                        "text/turtle",
                        "@prefix ex: <http://example.com/> . ex:a ex:b ex:c ."
                                .getBytes(StandardCharsets.UTF_8));

        deleteGraphService.replaceGraph(graphIdentifier, mockFile);

        verify(mockDatabasePort).deleteGraph(graphIdentifier);
        verify(mockDatabasePort).createGraph(eq(graphIdentifier), any(Graph.class));
    }

    @Test
    void replaceGraph_withoutFile_createsEmptyGraph() {
        var graphIdentifier = new GraphIdentifier("default", "http://example.com/graph");

        deleteGraphService.replaceGraph(graphIdentifier, null);

        verify(mockDatabasePort).deleteGraph(graphIdentifier);
        verify(mockDatabasePort).createEmptyGraph(graphIdentifier);
        verify(mockDatabasePort, never()).createGraph(any(), any());
    }
}
