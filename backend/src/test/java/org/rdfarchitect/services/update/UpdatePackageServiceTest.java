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

import org.apache.jena.shared.PrefixMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.cim.changelog.ChangeLogEntry;
import org.rdfarchitect.cim.data.dto.CIMPackage;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.queries.update.CIMUpdates;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.DeltaCompressible;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.ChangeLogUseCase;
import org.rdfarchitect.services.update.packages.UpdatePackageService;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

class UpdatePackageServiceTest {

    private UpdatePackageService service;
    private GraphRewindableWithUUIDs mockGraph;
    private final PackageMapper mapper = Mappers.getMapper(PackageMapper.class);
    private DatabasePort databasePort;
    private ChangeLogUseCase changeLogUseCase;

    @BeforeEach
    void setUp() {
        databasePort = mock(DatabasePort.class);
        changeLogUseCase = mock(ChangeLogUseCase.class);

        service = new UpdatePackageService(databasePort, mapper, changeLogUseCase);
        mockGraph = mock(GraphRewindableWithUUIDs.class);
        when(databasePort.getGraph(any())).thenReturn(mockGraph);
        when(databasePort.getPrefixMapping(anyString())).thenReturn(mock(PrefixMapping.class));

        var dummyDelta = mock(DeltaCompressible.class);
        when(mockGraph.getLastDelta()).thenReturn(dummyDelta);
    }

    @Test
    void addPackage_packageWithoutComment_addsPackage() {
        var dto = PackageDTO.builder()
                            .prefix("http://example.com#")
                            .label("TestPackage")
                            .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic.when(() ->
                                        CIMUpdates.insertPackage(eq(mockGraph), any(), captor.capture())
                             ).thenAnswer(invocation -> null);

            service.addPackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraph).commit();
            verify(mockGraph).end();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://example.com#Package_TestPackage"));
            assertThat(captured.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en"));
            assertThat(captured.getComment()).isNull();
            assertThat(captured.getBelongsToCategory()).isNull();
        }
    }

    @Test
    void addPackage_packageWithComment_addsPackage() {
        var dto = PackageDTO.builder()
                            .prefix("http://example.com#")
                            .label("TestPackage")
                            .comment("This is a test package")
                            .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic.when(() ->
                                        CIMUpdates.insertPackage(eq(mockGraph), any(), captor.capture())
                             ).thenAnswer(invocation -> null);

            service.addPackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraph).commit();
            verify(mockGraph).end();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://example.com#Package_TestPackage"));
            assertThat(captured.getLabel()).isEqualTo(new RDFSLabel("TestPackage", "en"));
            assertThat(captured.getBelongsToCategory()).isNull();
            assertThat(captured.getUuid()).isNotNull();
            assertThat(captured.getComment()).isEqualTo(new RDFSComment(
                      "This is a test package",
                      new URI("http://www.w3.org/2001/XMLSchema#String")
            ));
        }
    }

    @Test
    void addPackage_exceptionDuringTransaction_closesGraph() {
        var dto = PackageDTO.builder()
                            .prefix("http://example.com#")
                            .label("TestPackage")
                            .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            mockedStatic.when(() ->
                                        CIMUpdates.insertPackage(any(), any(), any())
                             ).thenThrow(new RuntimeException("Simulated failure"));

            try {
                service.addPackage(new GraphIdentifier("default", "test"), dto);
            } catch (Exception e) {
                // expected
            }

            verify(mockGraph).end();
        }
    }

    @Test
    void replacePackage_validPackage_replacesPackage() {
        var dto = PackageDTO.builder()
                            .prefix("http://other.org#")
                            .label("otherPackage")
                            .uuid(UUID.randomUUID())
                            .build();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            ArgumentCaptor<CIMPackage> captor = ArgumentCaptor.forClass(CIMPackage.class);
            mockedStatic.when(() ->
                                        CIMUpdates.replacePackage(eq(mockGraph), any(), captor.capture())
                             ).thenAnswer(invocation -> null);

            service.replacePackage(new GraphIdentifier("default", "test"), dto);

            verify(mockGraph).commit();
            verify(mockGraph).end();

            CIMPackage captured = captor.getValue();
            assertThat(captured.getUri()).isEqualTo(new URI("http://other.org#Package_otherPackage"));
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

            mockedStatic.verify(() ->
                                          CIMUpdates.deletePackage(eq(mockGraph), any(), eq(packageUuid.toString()))
                               );
        }

        verify(mockGraph).commit();
        verify(mockGraph).end();

        ArgumentCaptor<ChangeLogEntry> captor = ArgumentCaptor.forClass(ChangeLogEntry.class);
        verify(changeLogUseCase).recordChange(eq(graphIdentifier), captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("Deleted package " + packageUuid);
    }

    @Test
    void deletePackage_failureDuringDelete_alwaysEndsGraphTransaction() {
        var graphIdentifier = new GraphIdentifier("default", "test");
        UUID packageUuid = UUID.randomUUID();

        try (MockedStatic<CIMUpdates> mockedStatic = mockStatic(CIMUpdates.class)) {
            mockedStatic.when(() ->
                                        CIMUpdates.deletePackage(any(), any(), anyString())
                             ).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> service.deletePackage(graphIdentifier, packageUuid))
                      .isInstanceOf(RuntimeException.class);

            verify(mockGraph).end();
        }
    }
}
