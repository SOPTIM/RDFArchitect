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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.context.UserSettings;
import org.rdfarchitect.context.UserSettingsContext;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportResult;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.FileState;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.JobState;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class ImportJobServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix ex:   <http://example.com#> .

            ex:Gadget a rdfs:Class ; rdfs:label "Gadget"@en .
            """;

    private ImportJobService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId("session-a");
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
        UserSettingsContext.clear();
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void startImport_realImport_reportsEveryFileAndTheImportedGraphs() {
        service = new ImportJobService(realImportService());

        var jobId =
                service.startImport(
                        "ds", List.of(graphFile("first.ttl"), graphFile("second.ttl")), null);

        var status = awaitFinished(jobId);
        assertThat(status.state()).isEqualTo(JobState.COMPLETED);
        assertThat(status.files())
                .extracting(file -> file.fileName() + ":" + file.state())
                .containsExactly("first.ttl:IMPORTED", "second.ttl:IMPORTED");
        assertThat(status.importedGraphUris())
                .containsExactly(RDFA.GRAPH_URI + "first", RDFA.GRAPH_URI + "second");
        assertThat(status.failedImports()).isEmpty();
    }

    @Test
    void startImport_runsWithTheSessionAndSettingsOfTheRequestThatStartedIt() {
        // The database is looked up per session and comments are normalized per user setting, both
        // through thread locals of the request thread, so the import thread has to inherit them.
        UserSettingsContext.set(new UserSettings(true, false));
        var observedSession = new AtomicReference<String>();
        var observedSettings = new AtomicReference<UserSettings>();
        service =
                new ImportJobService(
                        (datasetName, files, graphUris, listener) -> {
                            observedSession.set(SessionContext.getSessionId());
                            observedSettings.set(UserSettingsContext.get());
                            return new ImportResult();
                        });

        var jobId = service.startImport("ds", List.of(graphFile("first.ttl")), null);

        awaitFinished(jobId);
        assertThat(observedSession.get()).isEqualTo("session-a");
        assertThat(observedSettings.get()).isEqualTo(new UserSettings(true, false));
    }

    @Test
    void startImport_uploadedFilesAreTakenOverSoTheySurviveTheRequest() throws Exception {
        var received = new AtomicReference<MultipartFile>();
        service =
                new ImportJobService(
                        (datasetName, files, graphUris, listener) -> {
                            received.set(files.getFirst());
                            return new ImportResult();
                        });

        var jobId = service.startImport("ds", List.of(graphFile("first.ttl")), null);

        awaitFinished(jobId);
        assertThat(received.get()).isInstanceOf(InMemoryMultipartFile.class);
        assertThat(received.get().getBytes()).isEqualTo(SCHEMA.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void startImport_whileAnotherImportOfTheSameSessionRuns_isRejected() throws Exception {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        service = new ImportJobService(blockingImportService(running, release));

        service.startImport("ds", List.of(graphFile("first.ttl")), null);
        assertThat(running.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.startImport("ds", List.of(graphFile("second.ttl")), null))
                .isInstanceOf(ResourceConflictException.class);

        release.countDown();
    }

    @Test
    void startImport_whileAnotherSessionImports_isAllowed() throws Exception {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        service = new ImportJobService(blockingImportService(running, release));

        service.startImport("ds", List.of(graphFile("first.ttl")), null);
        assertThat(running.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        SessionContext.setSessionId("session-b");
        assertThat(service.startImport("ds", List.of(graphFile("second.ttl")), null)).isNotNull();

        release.countDown();
    }

    @Test
    void getStatus_jobOfAnotherSession_isNotVisible() {
        service = new ImportJobService(realImportService());
        var jobId = service.startImport("ds", List.of(graphFile("first.ttl")), null);
        awaitFinished(jobId);

        SessionContext.setSessionId("session-b");

        assertThat(service.getStatus(jobId)).isEmpty();
        assertThat(service.cancel(jobId)).isFalse();
    }

    @Test
    void getStatus_unknownJob_isEmpty() {
        service = new ImportJobService(realImportService());

        assertThat(service.getStatus(UUID.randomUUID())).isEmpty();
    }

    @Test
    void cancel_runningImport_stopsItAndReportsTheJobAsCancelled() {
        service =
                new ImportJobService(
                        (datasetName, files, graphUris, listener) -> {
                            listener.planned(List.of(new PlannedImport(0, "first.ttl", 1)));
                            await().pollInSameThread().atMost(TIMEOUT).until(listener::isCancelled);
                            listener.finished(0, ImportProgressListener.Outcome.SKIPPED, null);
                            return new ImportResult();
                        });
        var jobId = service.startImport("ds", List.of(graphFile("first.ttl")), null);

        await().pollInSameThread()
                .atMost(TIMEOUT)
                .until(() -> !service.getStatus(jobId).orElseThrow().files().isEmpty());
        assertThat(service.cancel(jobId)).isTrue();

        var status = awaitFinished(jobId);
        assertThat(status.state()).isEqualTo(JobState.CANCELLED);
        assertThat(status.files())
                .singleElement()
                .extracting(file -> file.state())
                .isEqualTo(FileState.SKIPPED);
    }

    @Test
    void startImport_importThrowing_reportsTheJobAsFailed() {
        service =
                new ImportJobService(
                        (datasetName, files, graphUris, listener) -> {
                            throw new IllegalStateException("import blew up");
                        });

        var jobId = service.startImport("ds", List.of(graphFile("first.ttl")), null);

        var status = awaitFinished(jobId);
        assertThat(status.state()).isEqualTo(JobState.FAILED);
        assertThat(status.errorMessage()).isEqualTo("import blew up");
    }

    /**
     * Awaitility runs a condition on a thread of its own, which would not see the session of this
     * test, so every wait that looks at the job has to poll on the calling thread.
     */
    private ImportJobUseCase.ImportJobStatus awaitFinished(UUID jobId) {
        await().pollInSameThread()
                .atMost(TIMEOUT)
                .until(
                        () ->
                                service.getStatus(jobId)
                                        .filter(status -> status.state() != JobState.RUNNING)
                                        .isPresent());
        return service.getStatus(jobId).orElseThrow();
    }

    private ImportGraphsUseCase realImportService() {
        return new ImportGraphsService(
                new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig())));
    }

    private ImportGraphsUseCase blockingImportService(
            CountDownLatch running, CountDownLatch release) {
        return (datasetName, files, graphUris, listener) -> {
            running.countDown();
            try {
                release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new ImportResult();
        };
    }

    private MultipartFile graphFile(String fileName) {
        return new MockMultipartFile(
                "files", fileName, "text/turtle", SCHEMA.getBytes(StandardCharsets.UTF_8));
    }
}
