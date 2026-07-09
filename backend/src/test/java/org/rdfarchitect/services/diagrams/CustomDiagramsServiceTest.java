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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class CustomDiagramsServiceTest {

    private DatabasePort databasePort;
    private CustomCustomDiagramService service;

    private GraphIdentifier graphIdentifier;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        service = new CustomCustomDiagramService(databasePort);

        graphIdentifier = mock(GraphIdentifier.class);
        when(graphIdentifier.graphUri()).thenReturn("http://example.org#graph");
        when(graphIdentifier.datasetName()).thenReturn("dataset");
    }

    @Test
    void getCustomDiagramsForGraph_diagramExists_returnsDiagram() {
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        var graph = mockGraph(map);
        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

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

        service.deleteCustomDatasetDiagram("dataset", diagramId.toString());

        assertThat(map).doesNotContainKey(diagramId);
    }

    @Test
    void deleteCustomDiagram_diagramExistsInGraph_removesFromMap() {
        var diagramId = UUID.randomUUID();
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, new CustomDiagram(diagramId));

        var graph = mockGraph(map);
        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        service.deleteCustomGraphDiagram(graphIdentifier, diagramId.toString());

        assertThat(map).doesNotContainKey(diagramId);
    }

    @Test
    void replaceCustomDiagram_newDiagramForDataset_replacesInMap() {
        var diagramId = UUID.randomUUID();
        var newDiagram = new CustomDiagram(diagramId);
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        service.replaceCustomDatasetDiagram("dataset", diagramId.toString(), newDiagram);

        assertThat(map).containsEntry(diagramId, newDiagram);
    }

    @Test
    void replaceCustomDiagram_newDiagramForGraph_replacesInMap() {
        var diagramId = UUID.randomUUID();
        var newDiagram = new CustomDiagram(diagramId);
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();

        var graph = mockGraph(map);
        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);

        service.replaceCustomGraphDiagram(graphIdentifier, diagramId.toString(), newDiagram);

        assertThat(map).containsEntry(diagramId, newDiagram);
    }

    @Test
    void removeFromDiagram_classRemovedFromDatasetDiagram_diagramIsEmpty() {
        var classId = UUID.randomUUID();
        var classInDiagram = new ClassInDiagram(classId, new URI("http://example.org#graph"));
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(new ArrayList<>(List.of(classInDiagram)));
        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(map);

        service.removeFromCustomDatasetDiagram("dataset", diagramId.toString(), classId);

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

        service.removeFromCustomGraphDiagram(graphIdentifier, diagramId.toString(), classId);

        assertThat(diagram.getClasses()).isEmpty();
    }

    @Test
    void removeFromAllDiagrams_classInGraphAndDatasetDiagram_removedFromBoth() {
        var classId = UUID.randomUUID();
        var classInDiagram = new ClassInDiagram(classId, new URI("http://example.org#graph"));

        var graphDiagram = new CustomDiagram(UUID.randomUUID());
        graphDiagram.setClasses(new ArrayList<>(List.of(classInDiagram)));
        var graphMap = new ConcurrentHashMap<UUID, CustomDiagram>();
        graphMap.put(graphDiagram.getDiagramId(), graphDiagram);

        var datasetDiagram = new CustomDiagram(UUID.randomUUID());
        datasetDiagram.setClasses(new ArrayList<>(List.of(classInDiagram)));
        var datasetMap = new ConcurrentHashMap<UUID, CustomDiagram>();
        datasetMap.put(datasetDiagram.getDiagramId(), datasetDiagram);

        var graph = mockGraph(graphMap);
        when(databasePort.getGraphWithContext(graphIdentifier)).thenReturn(graph);
        when(databasePort.getDatasetDiagrams("dataset")).thenReturn(datasetMap);

        service.removeFromAllDiagrams(graphIdentifier, classId);

        assertThat(graphDiagram.getClasses()).isEmpty();
        assertThat(datasetDiagram.getClasses()).isEmpty();
    }

    private GraphContext mockGraph(ConcurrentHashMap<UUID, CustomDiagram> diagrams) {
        GraphContext graph = mock(GraphContext.class);
        when(graph.begin(any(ReadWrite.class))).thenReturn(graph);
        when(graph.getCustomDiagrams()).thenReturn(diagrams);
        return graph;
    }
}
