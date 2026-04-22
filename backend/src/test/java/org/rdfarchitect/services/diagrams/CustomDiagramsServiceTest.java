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

package org.rdfarchitect.services.diagrams;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContext;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomDiagramsServiceTest {

    private DatabasePort databasePort;
    private CustomDiagramService service;

    private GraphIdentifier graphIdentifier;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        service = new CustomDiagramService(databasePort);

        graphIdentifier = mock(GraphIdentifier.class);
        when(graphIdentifier.getGraphUri()).thenReturn("http://example.org#graph");
        when(graphIdentifier.getDatasetName()).thenReturn("dataset");
    }

    @Test
    void getCustomDiagramsForGraph_diagramExists_returnsDiagram() {
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        var graphWithContext = mockGraph(map);

        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graphWithContext);

        var result = service.getCustomDiagramsForGraph(graphIdentifier);

        assertThat(result).containsExactly(diagram);
    }

    @Test
    void getCustomDiagramsForDataset_diagramExists_returnsDiagram() {
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        var result = service.getCustomDiagramsForDataset("dataset");

        assertThat(result).containsExactly(diagram);
    }

    @Test
    void deleteCustomDiagram_diagramExistsInDataset_removesFromMap() {
        var diagramId = UUID.randomUUID();
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, new CustomDiagram(diagramId));

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        service.deleteCustomDiagram("dataset", diagramId.toString());

        assertThat(map).doesNotContainKey(diagramId);
    }

    @Test
    void deleteCustomDiagram_diagramExistsInGraph_removesFromMap() {
        var diagramId = UUID.randomUUID();
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, new CustomDiagram(diagramId));

        var graph = mockGraph(map);

        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        service.deleteCustomDiagram(graphIdentifier, diagramId.toString());

        assertThat(map).doesNotContainKey(diagramId);
    }

    @Test
    void replaceCustomDiagram_newDiagramForDataset_replacesInMap() {
        var diagramId = UUID.randomUUID();
        var newDiagram = new CustomDiagram(diagramId);

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        service.replaceCustomDiagram("dataset", diagramId.toString(), newDiagram);

        assertThat(map).containsEntry(diagramId, newDiagram);
    }

    @Test
    void replaceCustomDiagram_newDiagramForGraph_replacesInMap() {
        var diagramId = UUID.randomUUID();
        var newDiagram = new CustomDiagram(diagramId);

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        var graph = mockGraph(map);

        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        service.replaceCustomDiagram(graphIdentifier, diagramId.toString(), newDiagram);

        assertThat(map).containsEntry(diagramId, newDiagram);
    }

    @Test
    void addToDiagram_classAddedToDatasetDiagram_diagramContainsClass() {
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(new ArrayList<>());

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        var classInDiagram = new ClassInDiagram(UUID.randomUUID(), new URI("http://example.org#graph"));

        service.addToDiagram("dataset", diagramId.toString(), List.of(classInDiagram));

        assertThat(diagram.getClasses()).containsExactly(classInDiagram);
    }

    @Test
    void addToDiagram_nonExistingDatasetDiagram_doesNotFail() {
        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(new ConcurrentHashMap<>());

        assertDoesNotThrow(() -> service.addToDiagram("dataset", UUID.randomUUID().toString(), List.of()));
    }

    @Test
    void removeFromDiagram_classRemovedFromDatasetDiagramByUuid_diagramIsEmpty() {
        var classId = UUID.randomUUID();
        var classInDiagram = new ClassInDiagram(classId, new URI("http://example.org#graph"));

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(new ArrayList<>(List.of(classInDiagram)));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        service.removeFromDiagram("dataset", diagramId.toString(), classId);

        assertThat(diagram.getClasses()).isEmpty();
    }

    @Test
    void removeFromDiagram_classRemovedFromGraphDiagram_diagramIsEmpty() {
        var classId = UUID.randomUUID();
        var classInDiagram = new ClassInDiagram(classId, new URI("http://example.org#graph"));

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(new ArrayList<>(List.of(classInDiagram)));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        var graph = mockGraph(map);

        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        service.removeFromDiagram(graphIdentifier, diagramId.toString(), classId);

        assertThat(diagram.getClasses()).isEmpty();
    }

    @Test
    void removeFromAllDiagrams_classInGraphAndDatasetDiagram_removedFromBoth() {
        var classId = UUID.randomUUID();
        var classInDiagram = new ClassInDiagram(classId, new URI("http://example.org#graph"));

        var diagramId1 = UUID.randomUUID();
        var graphDiagram = new CustomDiagram(diagramId1);
        graphDiagram.setClasses(new ArrayList<>(List.of(classInDiagram)));

        var diagramId2 = UUID.randomUUID();
        var datasetDiagram = new CustomDiagram(diagramId2);
        datasetDiagram.setClasses(new ArrayList<>(List.of(classInDiagram)));

        var graphMap = new ConcurrentHashMap<UUID, CustomDiagram>();
        graphMap.put(UUID.randomUUID(), graphDiagram);

        var datasetMap = new ConcurrentHashMap<UUID, CustomDiagram>();
        datasetMap.put(UUID.randomUUID(), datasetDiagram);

        var graph = mockGraph(graphMap);

        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(datasetMap);

        service.removeFromAllDiagrams(graphIdentifier, classId);

        assertThat(graphDiagram.getClasses()).isEmpty();
        assertThat(datasetDiagram.getClasses()).isEmpty();
    }

    private GraphWithContext mockGraph(ConcurrentHashMap<UUID, CustomDiagram> diagrams) {
        GraphWithContext graph = mock(GraphWithContext.class);
        when(graph.getCustomDiagrams()).thenReturn(diagrams);
        return graph;
    }
}
