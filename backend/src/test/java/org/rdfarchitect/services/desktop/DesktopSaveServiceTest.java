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

package org.rdfarchitect.services.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.desktop.DesktopWorkspaceFiles;
import org.rdfarchitect.desktop.OpenRequests;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.rdfarchitect.desktop.WorkspaceFileLocations;
import org.rdfarchitect.desktop.WorkspaceRevision;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;
import org.rdfarchitect.services.desktop.MarkOpenedFromRequestUseCase.Result;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DesktopSaveServiceTest {

    private static final String WORKSPACE = "Draft";
    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    @TempDir Path directory;

    private DatabasePort databasePort;
    private SavedWorkspaces savedWorkspaces;
    private WorkspaceFileLocations fileLocations;
    private OpenRequests openRequests;
    private DesktopWorkspaceFiles workspaceFiles;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createDataset(WORKSPACE);
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.WRITE)) {
            transaction.createGraph("a", RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.commit("Add a");
        }
        savedWorkspaces = spy(new SavedWorkspaces(databasePort));
        fileLocations = new WorkspaceFileLocations(databasePort);
        openRequests = new OpenRequests();
        var export = mock(ExportWorkspaceUseCase.class);
        when(export.exportWorkspace(WORKSPACE)).thenReturn("file".getBytes(StandardCharsets.UTF_8));
        workspaceFiles = new DesktopWorkspaceFiles(databasePort, export);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private DesktopSaveService service() {
        return new DesktopSaveService(workspaceFiles, savedWorkspaces, fileLocations, openRequests);
    }

    private String openRequest() throws Exception {
        var file = Files.writeString(directory.resolve("Draft.rdfa"), "file");
        return openRequests.add(file).id();
    }

    @Test
    void markOpenedFromRequest_workspaceBeingWritten_keepsTheRequestForAnotherTry()
            throws Exception {
        var requestId = openRequest();
        when(savedWorkspaces.markSavedNow(WORKSPACE)).thenReturn(false).thenCallRealMethod();
        var service = service();

        assertThat(service.markOpenedFromRequest(WORKSPACE, requestId)).isEqualTo(Result.BUSY);
        assertThat(openRequests.get(requestId)).isPresent();
        assertThat(fileLocations.locationOf(WORKSPACE)).isEmpty();

        assertThat(service.markOpenedFromRequest(WORKSPACE, requestId)).isEqualTo(Result.RECORDED);
        assertThat(openRequests.get(requestId)).isEmpty();
        assertThat(fileLocations.locationOf(WORKSPACE)).isPresent();
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();
    }

    @Test
    void markOpenedFromRequest_unknownRequest_recordsNothing() {
        assertThat(service().markOpenedFromRequest(WORKSPACE, "missing"))
                .isEqualTo(Result.UNKNOWN_REQUEST);
        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
    }

    @Test
    void markWorkspaceSaved_versionOfTheDownload_countsAsSaved() {
        var service = service();
        var file = service.getWorkspaceFile(WORKSPACE);

        assertThat(service.markWorkspaceSaved(WORKSPACE, file.version())).isTrue();

        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();
        assertThat(file.workspaceId()).isEqualTo(databasePort.workspaceId(WORKSPACE));
    }

    @Test
    void markWorkspaceSaved_editingDisabledAfterTheDownload_staysUnsaved() {
        var service = service();
        var file = service.getWorkspaceFile(WORKSPACE);
        databasePort.disableEditing(WORKSPACE);

        service.markWorkspaceSaved(WORKSPACE, file.version());

        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
    }

    @Test
    void markWorkspaceSaved_notAVersion_isRefused() {
        var service = service();

        assertThatThrownBy(() -> service.markWorkspaceSaved(WORKSPACE, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markWorkspaceSaved(WORKSPACE, "nonsense:editable"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markWorkspaceSaved_fileOfItsOwn_forgetsTheHandedOverFile() throws Exception {
        fileLocations.remember(WORKSPACE, directory.resolve("Draft.rdfa"));

        assertThat(service().markWorkspaceSaved(WORKSPACE, null)).isTrue();

        assertThat(fileLocations.locationOf(WORKSPACE)).isEmpty();
    }

    @Test
    void saveWorkspaceToFile_noFile_savesNothing() {
        assertThat(service().saveWorkspaceToFile(WORKSPACE)).isFalse();
    }

    @Test
    void saveWorkspaceToFile_handedOverFile_writesItAndCountsAsSaved() throws Exception {
        var file = directory.resolve("Draft.rdfa");
        fileLocations.remember(WORKSPACE, file);

        assertThat(service().saveWorkspaceToFile(WORKSPACE)).isTrue();

        assertThat(file).hasContent("file");
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();
    }

    @Test
    void saveWorkspaceToFile_twoAtOnce_writesOneAfterTheOther() throws Exception {
        var file = directory.resolve("Draft.rdfa");
        fileLocations.remember(WORKSPACE, file);
        var revision = WorkspaceRevision.current(databasePort, WORKSPACE);
        var writes = new AtomicInteger();
        var firstWriting = new CountDownLatch(1);
        var finishFirst = new CountDownLatch(1);
        workspaceFiles = mock(DesktopWorkspaceFiles.class);
        when(workspaceFiles.writeTo(WORKSPACE, file))
                .thenAnswer(
                        _ -> {
                            if (writes.incrementAndGet() == 1) {
                                firstWriting.countDown();
                                finishFirst.await();
                            }
                            return revision;
                        });
        var service = service();
        var sessionId = SessionContext.getSessionId();
        Runnable save =
                () -> {
                    SessionContext.setSessionId(sessionId);
                    try {
                        service.saveWorkspaceToFile(WORKSPACE);
                    } finally {
                        SessionContext.clear();
                    }
                };

        var first = Thread.ofPlatform().start(save);
        assertThat(firstWriting.await(5, TimeUnit.SECONDS)).isTrue();
        var second = Thread.ofPlatform().start(save);
        try {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }

            assertThat(second.getState()).isEqualTo(Thread.State.BLOCKED);
            assertThat(writes).hasValue(1);
        } finally {
            finishFirst.countDown();
            first.join();
            second.join();
        }
        assertThat(writes).hasValue(2);
    }
}
