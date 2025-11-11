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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.update.graph.UpdateGraphService;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.Mockito.*;

class UpdateGraphServiceTest {

    private UpdateGraphService updateGraphService;
    private DatabasePort mockDatabasePort;

    @BeforeEach
    void setUp() {
        mockDatabasePort = mock(DatabasePort.class);
        updateGraphService = new UpdateGraphService(mockDatabasePort);
    }

    @Test
    void deleteGraph_callsDeleteGraph() {
        var graphIdentifier = new GraphIdentifier("default", "http://example.com/graph");
        updateGraphService.deleteGraph(graphIdentifier);
        verify(mockDatabasePort).deleteGraph(graphIdentifier);
    }

    @Test
    void replaceGraph_callsDeleteAndCreateGraph() {
        var graphIdentifier = new GraphIdentifier("default", "http://example.com/graph");
        var mockFile = mock(MultipartFile.class);

        updateGraphService.replaceGraph(graphIdentifier, mockFile);

        verify(mockDatabasePort).deleteGraph(graphIdentifier);
        verify(mockDatabasePort).createGraph(graphIdentifier, mockFile);
    }
}
