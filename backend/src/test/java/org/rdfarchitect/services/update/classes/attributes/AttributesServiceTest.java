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

package org.rdfarchitect.services.update.classes.attributes;

import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.attributes.AttributeMapper;
import org.rdfarchitect.cim.changelog.ChangeLogEntry;
import org.rdfarchitect.cim.data.dto.CIMAttribute;
import org.rdfarchitect.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.RDFA;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemorySparqlExecutioner;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttributesServiceTest {

    private static final GraphIdentifier GRAPH_IDENTIFIER = new GraphIdentifier("default", "http://graph");

    private DatabasePort databasePort;
    private AttributeMapper attributeMapper;
    private ChangeLogUseCase changeLogUseCase;

    private AttributesService service;
    private GraphRewindableWithUUIDs graph;
    private PrefixMapping prefixMapping;
    private AttributeDTO attributeDTO;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        attributeMapper = mock(AttributeMapper.class);
        changeLogUseCase = mock(ChangeLogUseCase.class);
        service = new AttributesService(databasePort, attributeMapper, changeLogUseCase);

        graph = mock(GraphRewindableWithUUIDs.class);
        prefixMapping = mock(PrefixMapping.class);
        when(databasePort.getGraph(GRAPH_IDENTIFIER)).thenReturn(graph);
        when(databasePort.getPrefixMapping(GRAPH_IDENTIFIER.getDatasetName())).thenReturn(prefixMapping);
        when(graph.isInTransaction()).thenReturn(true);
        when(graph.getLastDelta()).thenReturn(mock(DeltaCompressible.class));

        attributeDTO = AttributeDTO.builder()
                                   .uuid(UUID.randomUUID())
                                   .build();
    }

    @Test
    void createAttribute_startsSingleWriteTransactionAndExecutesInCurrentTransaction() {
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(null)
                                       .build();
        var updateBuilder = mock(UpdateBuilder.class);
        var update = mock(Update.class);
        when(attributeMapper.toCIMObject(attributeDTO)).thenReturn(cimAttribute);
        when(updateBuilder.build()).thenReturn(update);

        try (MockedStatic<AttributeFixedDefaultResolver> resolver = org.mockito.Mockito.mockStatic(AttributeFixedDefaultResolver.class);
             MockedStatic<CIMUpdates> updates = org.mockito.Mockito.mockStatic(CIMUpdates.class);
             MockedStatic<InMemorySparqlExecutioner> executioner = org.mockito.Mockito.mockStatic(InMemorySparqlExecutioner.class)) {

            updates.when(() ->
                                 CIMUpdates.insertAttribute(eq(prefixMapping), eq(GRAPH_IDENTIFIER.getGraphUri()), any(CIMAttribute.class))
                        ).thenReturn(updateBuilder);

            var createdUuid = service.createAttribute(GRAPH_IDENTIFIER, attributeDTO);

            assertThat(createdUuid).isNotNull();
            assertThat(cimAttribute.getUuid()).isEqualTo(createdUuid);

            verify(graph).begin(TxnType.WRITE);
            verify(graph).commit();
            verify(graph).end();

            resolver.verify(() -> AttributeFixedDefaultResolver.apply(graph, cimAttribute));
            updates.verify(() ->
                                   CIMUpdates.insertAttribute(
                                             eq(prefixMapping),
                                             eq(GRAPH_IDENTIFIER.getGraphUri()),
                                             argThat((CIMAttribute attribute) -> attribute != null && createdUuid.equals(attribute.getUuid()))
                                   )
                          );
            executioner.verify(() ->
                                       InMemorySparqlExecutioner.executeSingleUpdateInCurrentTransaction(
                                                 graph,
                                                 update,
                                                 GRAPH_IDENTIFIER.getGraphUri()
                                       )
                              );
        }

        verify(changeLogUseCase).recordChange(eq(GRAPH_IDENTIFIER), any(ChangeLogEntry.class));
    }

    @Test
    void replaceAttribute_startsSingleWriteTransactionAndExecutesUpdateRequestInCurrentTransaction() {
        var attributeUuid = UUID.randomUUID();
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(attributeUuid)
                                       .build();
        var updateRequest = mock(UpdateRequest.class);
        when(attributeMapper.toCIMObject(attributeDTO)).thenReturn(cimAttribute);

        try (MockedStatic<AttributeFixedDefaultResolver> resolver = org.mockito.Mockito.mockStatic(AttributeFixedDefaultResolver.class);
             MockedStatic<CIMUpdates> updates = org.mockito.Mockito.mockStatic(CIMUpdates.class);
             MockedStatic<InMemorySparqlExecutioner> executioner = org.mockito.Mockito.mockStatic(InMemorySparqlExecutioner.class)) {

            updates.when(() ->
                                 CIMUpdates.replaceAttribute(eq(prefixMapping), eq(GRAPH_IDENTIFIER.getGraphUri()), same(cimAttribute))
                        ).thenReturn(updateRequest);

            var replacedUuid = service.replaceAttribute(GRAPH_IDENTIFIER, attributeDTO);

            assertThat(replacedUuid).isEqualTo(attributeUuid);

            verify(graph).begin(TxnType.WRITE);
            verify(graph).commit();
            verify(graph).end();

            resolver.verify(() -> AttributeFixedDefaultResolver.apply(graph, cimAttribute));
            updates.verify(() -> CIMUpdates.replaceAttribute(eq(prefixMapping), eq(GRAPH_IDENTIFIER.getGraphUri()), same(cimAttribute)));
            executioner.verify(() ->
                                       InMemorySparqlExecutioner.executeSingleUpdateInCurrentTransaction(
                                                 graph,
                                                 updateRequest,
                                                 GRAPH_IDENTIFIER.getGraphUri()
                                       )
                              );
        }

        verify(changeLogUseCase).recordChange(eq(GRAPH_IDENTIFIER), any(ChangeLogEntry.class));
    }

    @Test
    void replaceAllAttributes_startsSingleWriteTransactionAndExecutesUpdateRequestInCurrentTransaction() {
        var classUuid = UUID.randomUUID().toString();
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(UUID.randomUUID())
                                       .build();
        var cimAttributes = List.of(cimAttribute);
        var dtoList = List.of(attributeDTO);
        var updateRequest = mock(UpdateRequest.class);
        when(attributeMapper.toCIMObjectList(dtoList)).thenReturn(cimAttributes);

        try (MockedStatic<AttributeFixedDefaultResolver> resolver = org.mockito.Mockito.mockStatic(AttributeFixedDefaultResolver.class);
             MockedStatic<CIMUpdates> updates = org.mockito.Mockito.mockStatic(CIMUpdates.class);
             MockedStatic<InMemorySparqlExecutioner> executioner = org.mockito.Mockito.mockStatic(InMemorySparqlExecutioner.class)) {

            updates.when(() ->
                                 CIMUpdates.replaceAttributes(
                                           eq(prefixMapping),
                                           eq(GRAPH_IDENTIFIER.getGraphUri()),
                                           eq(classUuid),
                                           eq(cimAttributes)
                                 )
                        ).thenReturn(updateRequest);

            service.replaceAllAttributes(GRAPH_IDENTIFIER, classUuid, dtoList);

            verify(graph).begin(TxnType.WRITE);
            verify(graph).commit();
            verify(graph).end();

            resolver.verify(() -> AttributeFixedDefaultResolver.apply(graph, cimAttributes));
            updates.verify(() -> CIMUpdates.replaceAttributes(eq(prefixMapping), eq(GRAPH_IDENTIFIER.getGraphUri()), eq(classUuid), eq(cimAttributes)));
            executioner.verify(() ->
                                       InMemorySparqlExecutioner.executeSingleUpdateInCurrentTransaction(
                                                 graph,
                                                 updateRequest,
                                                 GRAPH_IDENTIFIER.getGraphUri()
                                       )
                              );
        }

        verify(changeLogUseCase).recordChange(eq(GRAPH_IDENTIFIER), any(ChangeLogEntry.class));
    }

    @Test
    void replaceAttribute_withMalformedExistingUriFixedValue_failsFastAndEndsTransaction() {
        var realGraph = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 5, 1);
        var attributeUuid = UUID.randomUUID();
        var cimAttribute = CIMAttribute.builder()
                                       .uuid(attributeUuid)
                                       .fixedValue(new CIMSIsFixed("fixedValue"))
                                       .build();
        when(databasePort.getGraph(GRAPH_IDENTIFIER)).thenReturn(realGraph);
        when(attributeMapper.toCIMObject(attributeDTO)).thenReturn(cimAttribute);

        realGraph.begin(TxnType.WRITE);
        try {
            var model = ModelFactory.createModelForGraph(realGraph);
            var attributeResource = model.createResource("http://example.com#Class.attribute");
            attributeResource.addProperty(RDFA.uuid, attributeUuid.toString());
            attributeResource.addProperty(CIMS.isFixed, model.createResource("http://example.com#fixedUri"));
            realGraph.commit();
        } finally {
            realGraph.end();
        }

        try (MockedStatic<CIMUpdates> updates = org.mockito.Mockito.mockStatic(CIMUpdates.class);
             MockedStatic<InMemorySparqlExecutioner> executioner = org.mockito.Mockito.mockStatic(InMemorySparqlExecutioner.class)) {

            assertThatThrownBy(() -> service.replaceAttribute(GRAPH_IDENTIFIER, attributeDTO))
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessageContaining("URI resources are not allowed");

            updates.verifyNoInteractions();
            executioner.verifyNoInteractions();
        }

        assertThat(realGraph.isInTransaction()).isFalse();
        verify(changeLogUseCase, never()).recordChange(any(), any());
        verify(databasePort, never()).getPrefixMapping(any());
    }
}
