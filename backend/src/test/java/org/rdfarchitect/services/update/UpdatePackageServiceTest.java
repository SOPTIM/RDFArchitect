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

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.queries.update.CIMUpdates;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.rdfarchitect.services.dl.update.packagelayout.UpdatePackageLayoutService;
import org.rdfarchitect.services.update.packages.UpdatePackageService;

import java.util.UUID;

class UpdatePackageServiceTest {

    private UpdatePackageService service;
    private GraphRewindable mockGraph;
    private GraphContext mockGraphWithContext;
    private final PackageMapper mapper = Mappers.getMapper(PackageMapper.class);

    @BeforeEach
    void setUp() {
        DatabasePort databasePort = mock(DatabasePort.class);
        var mockUpdatePackageLayoutService = mock(UpdatePackageLayoutService.class);
        service =
                new UpdatePackageService(
                        databasePort,
                        mapper,
                        mockUpdatePackageLayoutService,
                        mockUpdatePackageLayoutService,
                        mockUpdatePackageLayoutService);
        mockGraph = mock(GraphRewindable.class);
        mockGraphWithContext = mock(GraphContext.class);
        when(mockGraphWithContext.begin(any(ReadWrite.class))).thenReturn(mockGraphWithContext);
        when(databasePort.getGraphWithContext(any())).thenReturn(mockGraphWithContext);
        when(mockGraphWithContext.getRdfGraph()).thenReturn(mockGraph);
        when(databasePort.getPrefixMapping(anyString())).thenReturn(mock(PrefixMapping.class));
        var dummyDelta = mock(DeltaCompressible.class);
        when(mockGraph.getLastDelta()).thenReturn(dummyDelta);
    }

    @Test
    void addPackage_packageWithoutComment_addsPackage() {
        var dto = PackageDTO.builder().prefix("http://example.com#").label("TestPackage").build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic
                    .when(() -> CIMUpdates.insertPackage(eq(mockGraph), any(), captor.capture()))
                    .thenAnswer(_ -> null);

            service.addPackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraphWithContext).commit(anyString());
            verify(mockGraphWithContext).close();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://example.com#TestPackage"));
            assertThat(captured.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en"));
            assertThat(captured.getComment()).isNull();
            assertThat(captured.getBelongsToCategory()).isNull();
        }
    }

    @Test
    void addPackage_packageWithComment_addsPackage() {
        var dto =
                PackageDTO.builder()
                        .prefix("http://example.com#")
                        .label("TestPackage")
                        .comment("This is a test package")
                        .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic
                    .when(() -> CIMUpdates.insertPackage(eq(mockGraph), any(), captor.capture()))
                    .thenAnswer(_ -> null);

            service.addPackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraphWithContext).commit(anyString());
            verify(mockGraphWithContext).close();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://example.com#TestPackage"));
            assertThat(captured.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en"));
            assertThat(captured.getBelongsToCategory()).isNull();
            assertThat(captured.getUuid()).isNotNull();
            assertThat(captured.getComment())
                    .isEqualTo(
                            new RDFSComment(
                                    "This is a test package",
                                    new URI("http://www.w3.org/2001/XMLSchema#string")));
        }
    }

    @Test
    void addPackage_exceptionDuringTransaction_closesGraph() {
        var dto = PackageDTO.builder().prefix("http://example.com#").label("TestPackage").build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            mockedStatic
                    .when(() -> CIMUpdates.insertPackage(any(), any(), any()))
                    .thenThrow(new RuntimeException("Simulated failure"));

            try {
                service.addPackage(new GraphIdentifier("default", "test"), dto);
            } catch (Exception _) {
                // expected
            }

            verify(mockGraphWithContext).close();
        }
    }

    @Test
    void addPackage_classWithSameIriExists_throwsConflict() {
        var dto = PackageDTO.builder().prefix("http://example.com#").label("ExistingClass").build();
        when(mockGraph.contains(
                        NodeFactory.createURI("http://example.com#ExistingClass"),
                        RDF.type.asNode(),
                        RDFS.Class.asNode()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addPackage(new GraphIdentifier("default", "test"), dto))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("class with the same IRI");

        verify(mockGraphWithContext, never()).commit(anyString());
        verify(mockGraphWithContext).close();
    }

    @Test
    void replacePackage_validPackage_replacesPackage() {
        var dto =
                PackageDTO.builder()
                        .prefix("http://other.org#")
                        .label("otherPackage")
                        .uuid(UUID.randomUUID())
                        .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic
                    .when(() -> CIMUpdates.replacePackage(eq(mockGraph), any(), captor.capture()))
                    .thenAnswer(_ -> null);

            service.replacePackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraphWithContext).commit(anyString());
            verify(mockGraphWithContext).close();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://other.org#otherPackage"));
            assertThat(captured.getLabel()).isEqualTo(new RDFSLabel("otherPackage", "en"));
            assertThat(captured.getUuid()).isEqualTo(dto.getUuid());
        }
    }

    @Test
    void deletePackage_validUuid_deletesPackageAndRecordsChange() {
        var graphIdentifier = new GraphIdentifier("default", "test");
        UUID packageUuid = UUID.randomUUID();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            service.deletePackage(graphIdentifier, packageUuid);

            mockedStatic.verify(
                    () -> CIMUpdates.deletePackage(eq(mockGraph), any(), eq(packageUuid)));
        }

        verify(mockGraphWithContext).commit("Deleted package " + packageUuid);
        verify(mockGraphWithContext).close();
    }

    @Test
    void deletePackage_failureDuringDelete_alwaysEndsGraphTransaction() {
        var graphIdentifier = new GraphIdentifier("default", "test");
        UUID packageUuid = UUID.randomUUID();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            mockedStatic
                    .when(() -> CIMUpdates.deletePackage(any(), any(), any(UUID.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> service.deletePackage(graphIdentifier, packageUuid))
                    .isInstanceOf(RuntimeException.class);

            verify(mockGraphWithContext).close();
        }
    }
}
