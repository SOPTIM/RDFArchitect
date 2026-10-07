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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Covers what the import reports about its progress, and how it reacts to being cancelled. */
class ImportProgressReportingTest {

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix ex:   <http://example.com#> .

            ex:Gadget a rdfs:Class ; rdfs:label "Gadget"@en .
            """;

    private ImportGraphsUseCase importGraphsUseCase;
    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        DatabasePort databasePort =
                new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        importGraphsUseCase = new ImportGraphsService(databasePort);
        listener = new RecordingListener();
    }

    @Test
    void importGraphs_zipArchive_plansOneEntryPerGraphFile() throws IOException {
        var archive =
                zip(
                        Map.of(
                                "first.ttl", SCHEMA,
                                "second.ttl", SCHEMA,
                                "readme.txt", "not a graph"));

        var result = importGraphsUseCase.importGraphs("ds", List.of(archive), null, listener);

        assertThat(listener.planned)
                .extracting(PlannedImport::fileName)
                .containsExactlyInAnyOrder("first.ttl", "second.ttl");
        assertThat(listener.planned).extracting(PlannedImport::index).containsExactly(0, 1);
        assertThat(listener.outcomes.values()).containsOnly(Outcome.IMPORTED);
        assertThat(result.importedGraphUris()).hasSize(2);
        assertThat(result.failedFileNames()).isEmpty();
    }

    @Test
    void importGraphs_cancelledAfterFirstFile_skipsTheRestAndKeepsWhatWasImported() {
        listener.cancelAfterFirstStart = true;

        var result =
                importGraphsUseCase.importGraphs(
                        "ds",
                        List.of(graphFile("first.ttl"), graphFile("second.ttl")),
                        null,
                        listener);

        // Skips are reported as they happen, imports once the graphs are in the workspace, so the
        // outcomes no longer arrive in file order. The panel keys them by index.
        assertThat(listener.outcomes)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(0, Outcome.IMPORTED, 1, Outcome.SKIPPED));
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "first");
        assertThat(result.failedFileNames()).isEmpty();
    }

    @Test
    void importGraphs_unreadableFile_reportsItAsFailedAndImportsTheOthers() {
        var broken =
                new MockMultipartFile(
                        "files",
                        "broken.ttl",
                        "text/turtle",
                        "this is not turtle at all <".getBytes(StandardCharsets.UTF_8));

        var result =
                importGraphsUseCase.importGraphs(
                        "ds", List.of(broken, graphFile("good.ttl")), null, listener);

        assertThat(listener.outcomes)
                .containsExactly(Map.entry(0, Outcome.FAILED), Map.entry(1, Outcome.IMPORTED));
        assertThat(result.failedFileNames()).containsExactly("broken.ttl");
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "good");
    }

    @Test
    void importGraphs_unusableZipArchive_failsOnlyThatArchive() {
        var notAnArchive =
                new MockMultipartFile(
                        "files",
                        "broken.zip",
                        "application/zip",
                        "definitely not a zip".getBytes(StandardCharsets.UTF_8));

        var result =
                importGraphsUseCase.importGraphs(
                        "ds", List.of(notAnArchive, graphFile("good.ttl")), null, listener);

        assertThat(listener.planned)
                .extracting(PlannedImport::fileName)
                .containsExactly("broken.zip", "good.ttl");
        assertThat(listener.outcomes)
                .containsExactly(Map.entry(0, Outcome.FAILED), Map.entry(1, Outcome.IMPORTED));
        assertThat(result.failedFileNames()).containsExactly("broken.zip");
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "good");
    }

    @Test
    void importGraphs_zipEntryUnreadableHalfWayThrough_leavesNoEntryUnreported() throws Exception {
        var archive =
                zip(new LinkedHashMap<>(Map.of("a.ttl", SCHEMA, "b.ttl", SCHEMA, "c.ttl", SCHEMA)));

        var result =
                importGraphsUseCase.importGraphs(
                        "ds", List.of(readableUntilImported(archive)), null, listener);

        assertThat(listener.planned).hasSize(3);
        // Whatever the archive still managed to give up, no planned entry may be left without an
        // outcome: the progress would wait for it forever and the result would not mention it.
        assertThat(listener.outcomes).hasSameSizeAs(listener.planned);
        assertThat(result.importedGraphUris().size() + result.failedFileNames().size())
                .isEqualTo(listener.planned.size());
    }

    @Test
    void importGraphs_zipTheScanCannotRead_importsNoEntryBehindTheQuestion() throws Exception {
        var archive =
                zip(new LinkedHashMap<>(Map.of("a.ttl", SCHEMA, "b.ttl", SCHEMA, "c.ttl", SCHEMA)));

        var result =
                importGraphsUseCase.importGraphs(
                        "ds", List.of(unscannable(archive)), null, listener);

        assertThat(listener.planned).hasSize(3);
        assertThat(listener.outcomes.values()).containsOnly(Outcome.FAILED);
        assertThat(result.importedGraphUris()).isEmpty();
        assertThat(result.failedFileNames()).containsExactlyInAnyOrder("a.ttl", "b.ttl", "c.ttl");
    }

    @Test
    void importGraphs_noListenerGiven_stillImports() {
        var result =
                importGraphsUseCase.importGraphs(
                        "ds", List.of(graphFile("first.ttl")), null, ImportProgressListener.NOOP);

        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "first");
    }

    private MultipartFile graphFile(String fileName) {
        return new MockMultipartFile(
                "files", fileName, "text/turtle", SCHEMA.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The archive as uploaded to the plan and the prefix scan, and a truncated one to every read
     * after those, so that the import pass is the one that runs into a broken stream.
     */
    private MultipartFile readableUntilImported(MultipartFile archive) throws IOException {
        var content = archive.getBytes();
        var truncated = Arrays.copyOf(content, content.length / 2);
        var reads = new AtomicInteger();
        return new MockMultipartFile(
                "files", archive.getOriginalFilename(), "application/zip", content) {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(reads.getAndIncrement() < 2 ? content : truncated);
            }
        };
    }

    /** The archive on every pass but the prefix scan, which runs into a stream it cannot read. */
    private MultipartFile unscannable(MultipartFile archive) throws IOException {
        var content = archive.getBytes();
        var reads = new AtomicInteger();
        return new MockMultipartFile(
                "files", archive.getOriginalFilename(), "application/zip", content) {
            @Override
            public InputStream getInputStream() {
                if (reads.getAndIncrement() != 1) {
                    return new ByteArrayInputStream(content);
                }
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("stream broke");
                    }
                };
            }
        };
    }

    private MultipartFile zip(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zipOutputStream = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return new MockMultipartFile(
                "files", "archive.zip", "application/zip", bytes.toByteArray());
    }

    /** Collects everything the import reports, and can cancel it once a file has been picked up. */
    private static final class RecordingListener implements ImportProgressListener {

        private final List<PlannedImport> planned = new ArrayList<>();
        private final Map<Integer, Outcome> outcomes = new LinkedHashMap<>();
        private boolean cancelAfterFirstStart;
        private boolean cancelled;

        @Override
        public void planned(List<PlannedImport> plannedImports) {
            planned.addAll(plannedImports);
        }

        @Override
        public void started(int index) {
            if (cancelAfterFirstStart) {
                cancelled = true;
            }
        }

        @Override
        public void finished(int index, Outcome outcome, String graphUri) {
            outcomes.put(index, outcome);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
