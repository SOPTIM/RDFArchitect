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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.graph.InvalidSchemaNameException;
import org.rdfarchitect.services.dl.update.SyncDiagramLayoutUseCase;
import org.rdfarchitect.services.update.graph.DeleteGraphService;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

class DeleteGraphServiceTest {

    private DeleteGraphService deleteGraphService;
    private DatabasePort mockDatabasePort;
    private WorkspaceTransaction transaction;

    @BeforeEach
    void setUp() {
        mockDatabasePort = mock(DatabasePort.class);
        transaction = mock(WorkspaceTransaction.class);
        when(mockDatabasePort.beginTransaction(anyString(), any(ReadWrite.class)))
                .thenReturn(transaction);
        deleteGraphService =
                new DeleteGraphService(mockDatabasePort, mock(SyncDiagramLayoutUseCase.class));
    }

    @Test
    void deleteGraph_callsDeleteGraph() {
        var graphIdentifier = new GraphIdentifier("ds", "my-graph");
        deleteGraphService.deleteGraph(graphIdentifier);
        verify(transaction).deleteGraph(graphIdentifier.schemaName());
        verify(transaction).commit(anyString());
    }

    @Test
    void replaceGraph_callsDeleteAndCreateGraph() {
        var graphIdentifier = new GraphIdentifier("ds", "my-graph");
        var mockFile =
                new MockMultipartFile(
                        "graph.ttl",
                        "graph.ttl",
                        "text/turtle",
                        "@prefix ex: <http://example.com/> . ex:a ex:b ex:c ."
                                .getBytes(StandardCharsets.UTF_8));

        deleteGraphService.replaceGraph(graphIdentifier, mockFile);

        verify(transaction).deleteGraph(graphIdentifier.schemaName());
        verify(transaction).createGraph(eq(graphIdentifier.schemaName()), any(Graph.class));
    }

    @Test
    void replaceGraph_withoutFile_createsEmptyGraph() {
        var graphIdentifier = new GraphIdentifier("ds", "my-graph");

        deleteGraphService.replaceGraph(graphIdentifier, null);

        verify(transaction).deleteGraph(graphIdentifier.schemaName());
        verify(transaction)
                .createGraph(eq(graphIdentifier.schemaName()), argThat(graph -> graph.isEmpty()));
    }

    @Test
    void replaceGraph_newSchemaWithInvalidName_isRejected() {
        var graphIdentifier = new GraphIdentifier("ds", "a|b");

        assertThatExceptionOfType(InvalidSchemaNameException.class)
                .isThrownBy(() -> deleteGraphService.replaceGraph(graphIdentifier, null));

        verify(transaction, never()).createGraph(anyString(), any());
    }

    @Test
    void replaceGraph_existingSchema_keepsItsName() {
        when(transaction.schemaNames()).thenReturn(List.of("legacy:name"));

        deleteGraphService.replaceGraph(new GraphIdentifier("ds", "legacy:name"), null);

        verify(transaction).createGraph(eq("legacy:name"), any(Graph.class));
    }

    @Test
    void replaceGraph_newSchemaNameWithSurroundingWhitespace_createsTheTrimmedName() {
        deleteGraphService.replaceGraph(new GraphIdentifier("ds", " my-graph "), null);

        verify(transaction).createGraph(eq("my-graph"), any(Graph.class));
    }
}
