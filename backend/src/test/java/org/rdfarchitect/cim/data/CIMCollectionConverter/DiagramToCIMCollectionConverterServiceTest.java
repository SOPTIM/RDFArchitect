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

package org.rdfarchitect.cim.data.CIMCollectionConverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.GraphWithContext;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.rendering.DiagramToCIMCollectionConverterService;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiagramToCIMCollectionConverterServiceTest {

    private DatabasePort databasePort;
    private GraphToCIMCollectionConverterService converter;
    private DiagramToCIMCollectionConverterService service;

    private GraphIdentifier graphIdentifier;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        converter = mock(GraphToCIMCollectionConverterService.class);
        service = new DiagramToCIMCollectionConverterService(databasePort, converter);

        graphIdentifier = new GraphIdentifier("dataset", "http://example.org#graph");
    }

    @Test
    void convert_diagramFromGraph_returnsCIMCollection() {
        var class1 = new ClassInDiagram(UUID.randomUUID(), new URI("http://example.org#graph"));
        var class2 = new ClassInDiagram(UUID.randomUUID(), new URI("http://example.org#graph"));

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(List.of(class1, class2));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        var graph = mockGraph(map);

        when(databasePort.getGraphWithContext(graphIdentifier))
                  .thenReturn(graph);

        var expected = new CIMCollection();
        when(converter.convert(eq(graphIdentifier), any()))
                  .thenReturn(expected);

        var result = service.convert(graphIdentifier, diagramId.toString());

        assertThat(result).isSameAs(expected);

        // verify filter content
        ArgumentCaptor<GraphFilter> captor = ArgumentCaptor.forClass(GraphFilter.class);
        verify(converter).convert(eq(graphIdentifier), captor.capture());

        var filter = captor.getValue();
        assertThat(filter.getAllowedUUIDs())
                  .containsExactlyInAnyOrder(
                            class1.getUuid().toString(),
                            class2.getUuid().toString()
                                            );
        assertThat(filter.isIncludeRelationsToExternalPackages()).isFalse();
    }

    @Test
    void convert_graphDiagramNotFound_throwsIllegalArgumentException() {
        var diagramId = UUID.randomUUID();
        var emptyDiagrams = new ConcurrentHashMap<UUID, CustomDiagram>();

        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(emptyDiagrams);

        var idString = diagramId.toString();
        assertThatThrownBy(() ->
                                     service.convert("dataset", idString))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("Diagram with ID");
    }

    @Test
    void convert_datasetDiagramSingleGraph_returnsCIMCollection() {
        var graphUri = new URI("http://example.org#graph");

        var class1 = new ClassInDiagram(UUID.randomUUID(), graphUri);
        var class2 = new ClassInDiagram(UUID.randomUUID(), graphUri);

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(List.of(class1, class2));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(map);

        var cimClass1 = createTestClass("TestClass1");
        var cimClass2 = createTestClass("TestClass2");

        var partial = new CIMCollection();
        partial.getClasses().add(cimClass1);
        partial.getClasses().add(cimClass2);

        when(converter.convert(any(), any()))
                  .thenReturn(partial);

        var result = service.convert("dataset", diagramId.toString());

        assertThat(result.getClasses()).containsExactly(cimClass1, cimClass2);
        verify(converter, times(1)).convert(any(), any());
    }

    @Test
    void convert_datasetDiagramMultipleGraphs_mergesCollections() {
        var uri1 = new URI("http://example.org#graph1");
        var uri2 = new URI("http://example.org#graph2");

        var class1 = new ClassInDiagram(UUID.randomUUID(), uri1);
        var class2 = new ClassInDiagram(UUID.randomUUID(), uri2);

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(List.of(class1, class2));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(map);

        var cimClassA = createTestClass("ClassA");
        var cimClassB = createTestClass("ClassB");

        var partial1 = new CIMCollection();
        partial1.getClasses().add(cimClassA);

        var partial2 = new CIMCollection();
        partial2.getClasses().add(cimClassB);

        when(converter.convert(any(), any()))
                  .thenReturn(partial1, partial2);

        var result = service.convert("dataset", diagramId.toString());

        assertThat(result.getClasses())
                  .containsExactlyInAnyOrder(cimClassA, cimClassB);

        verify(converter, times(2)).convert(any(), any());
    }

    @Test
    void convert_multipleGraphsInDiagram_buildsCorrectGraphIdentifiers() {
        var uri1 = new URI("http://example.org#graph1");
        var uri2 = new URI("http://example.org#graph2");

        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(List.of(
                  new ClassInDiagram(UUID.randomUUID(), uri1),
                  new ClassInDiagram(UUID.randomUUID(), uri2)
                                  ));

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(map);

        when(converter.convert(any(), any()))
                  .thenReturn(new CIMCollection());

        service.convert("dataset", diagramId.toString());

        ArgumentCaptor<GraphIdentifier> captor =
                  ArgumentCaptor.forClass(GraphIdentifier.class);

        verify(converter, times(2))
                  .convert(captor.capture(), any());

        var identifiers = captor.getAllValues();

        assertThat(identifiers)
                  .extracting(GraphIdentifier::getGraphUri)
                  .containsExactlyInAnyOrder("http://example.org#graph1", "http://example.org#graph2");
    }

    @Test
    void convert_datasetDiagramNotFound_throwsIllegalArgumentException() {
        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(new ConcurrentHashMap<>());

        var id = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                                     service.convert("dataset", id))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convert_emptyDiagram_returnsEmptyCollection() {
        var diagramId = UUID.randomUUID();
        var diagram = new CustomDiagram(diagramId);
        diagram.setClasses(Collections.emptyList());

        var map = new ConcurrentHashMap<UUID, CustomDiagram>();
        map.put(diagramId, diagram);

        when(databasePort.getDatasetDiagrams("dataset"))
                  .thenReturn(map);

        var result = service.convert("dataset", diagramId.toString());

        assertThat(result.getClasses()).isEmpty();
        verify(converter, never()).convert(any(), any());
    }

    @Test
    void convert_invalidUUID_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                                     service.convert("dataset", "not-a-uuid"))
                  .isInstanceOf(IllegalArgumentException.class);
    }

    private GraphWithContext mockGraph(ConcurrentHashMap<UUID, CustomDiagram> diagrams) {
        GraphWithContext graph = mock(GraphWithContext.class);
        when(graph.getCustomDiagrams()).thenReturn(diagrams);
        return graph;
    }

    private CIMClass createTestClass(String label) {
        return CIMClass.builder()
                       .uuid(UUID.randomUUID())
                       .uri(new URI("http://example.org#" + label))
                       .label(new RDFSLabel(label))
                       .build();
    }
}