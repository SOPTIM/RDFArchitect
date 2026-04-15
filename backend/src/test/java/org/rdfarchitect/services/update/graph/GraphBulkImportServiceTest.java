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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphBulkImportServiceTest {

    private GraphBulkImportService graphBulkImportService;
    private ReplaceGraphUseCase mockReplaceGraphUseCase;
    private DatabasePort mockDatabasePort;

    @BeforeEach
    void setUp() {
        mockReplaceGraphUseCase = mock(ReplaceGraphUseCase.class);
        mockDatabasePort = mock(DatabasePort.class);
        graphBulkImportService = new GraphBulkImportService(mockReplaceGraphUseCase, mockDatabasePort);
    }

    @Test
    void importGraphs_sameFileNameTwice_autoGeneratesUniqueGraphUris() {
        // Arrange
        var datasetName = "ds";
        var file1 = mock(MultipartFile.class);
        var file2 = mock(MultipartFile.class);
        when(file1.getOriginalFilename()).thenReturn("graph.ttl");
        when(file2.getOriginalFilename()).thenReturn("graph.ttl");
        when(mockDatabasePort.listGraphUris(datasetName)).thenThrow(new RuntimeException("dataset does not exist"));

        // Act
        graphBulkImportService.importGraphs(datasetName, List.of(file1, file2), null);

        // Assert
        var captor = ArgumentCaptor.forClass(GraphIdentifier.class);
        verify(mockReplaceGraphUseCase, times(2)).replaceGraph(captor.capture(), any(MultipartFile.class));

        assertThat(captor.getAllValues()).extracting(GraphIdentifier::getGraphUri).containsExactly(
                  RDFA.GRAPH_URI + "graph",
                  RDFA.GRAPH_URI + "graph_1"
                                                                                                  );
    }
}

