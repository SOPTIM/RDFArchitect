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

package org.rdfarchitect.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

class WorkspaceRecoveryTest {

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    @TempDir Path dataDirectory;

    private Path directory;
    private DatabasePort databasePort;
    private SavedWorkspaces savedWorkspaces;
    private ExportWorkspaceService export;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        directory = dataDirectory.resolve("recovery");
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        savedWorkspaces = new SavedWorkspaces(databasePort);
        export = new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"));
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private WorkspaceRecovery recovery() {
        var recovery = new WorkspaceRecovery(directory, databasePort, savedWorkspaces, export);
        recovery.noteSession();
        return recovery;
    }

    private void addGraph(String workspace, String graphUri) {
        if (!databasePort.listDatasets().contains(workspace)) {
            databasePort.createDataset(workspace);
        }
        try (var transaction = databasePort.beginTransaction(workspace, ReadWrite.WRITE)) {
            transaction.createGraph(graphUri, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.commit("Add " + graphUri);
        }
    }

    /** Runs a write the way the scheduler does: on a thread of its own, without a session. */
    private static void writeFromAnotherThread(WorkspaceRecovery recovery)
            throws InterruptedException {
        var thread = Thread.ofPlatform().start(recovery::writeUnsaved);
        thread.join();
    }

    private List<Path> copies() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void keepsACopyOfAnUnsavedWorkspaceOnly() throws Exception {
        var recovery = recovery();
        addGraph("Draft", "http://example.org/graphs/a");
        databasePort.createDataset("Empty");

        writeFromAnotherThread(recovery);

        assertThat(copies())
                .singleElement()
                .satisfies(
                        file ->
                                assertThat(file.getFileName().toString())
                                        .startsWith("Draft-")
                                        .endsWith(".rdfa"));
    }

    @Test
    void writesAgainOnlyAfterAChange() throws Exception {
        var recovery = recovery();
        addGraph("Draft", "http://example.org/graphs/a");
        writeFromAnotherThread(recovery);
        var copy = copies().getFirst();
        Files.writeString(copy, "marker");

        writeFromAnotherThread(recovery);
        assertThat(Files.readString(copy)).isEqualTo("marker");

        addGraph("Draft", "http://example.org/graphs/b");
        writeFromAnotherThread(recovery);
        assertThat(Files.size(copy)).isGreaterThan(100);
    }

    @Test
    void dropsTheCopyOnceTheWorkspaceIsSavedOrGone() throws Exception {
        var recovery = recovery();
        addGraph("Draft", "http://example.org/graphs/a");
        addGraph("Other", "http://example.org/graphs/a");
        writeFromAnotherThread(recovery);
        assertThat(copies()).hasSize(2);

        savedWorkspaces.markSavedNow("Draft");
        databasePort.deleteDataset("Other");
        writeFromAnotherThread(recovery);

        assertThat(copies()).isEmpty();
    }

    @Test
    void keepsNothingOfASessionWithoutAWindow() throws Exception {
        var recovery = new WorkspaceRecovery(directory, databasePort, savedWorkspaces, export);
        addGraph("Draft", "http://example.org/graphs/a");

        writeFromAnotherThread(recovery);

        assertThat(copies()).isEmpty();
    }

    @Test
    void offersWhatThePreviousRunKept() throws Exception {
        var previous = recovery();
        addGraph("Draft One", "http://example.org/graphs/a");
        previous.close();

        var next = recovery();
        var kept = next.previousRun();

        assertThat(kept)
                .singleElement()
                .satisfies(
                        workspace -> assertThat(workspace.workspaceName()).isEqualTo("Draft One"));
        var id = kept.getFirst().id();
        assertThat(next.read(id))
                .hasValueSatisfying(content -> assertThat(content).startsWith('P', 'K'));
        assertThat(copies()).isEmpty();

        assertThat(next.discard(id)).isTrue();
        assertThat(next.previousRun()).isEmpty();
        assertThat(next.read(id)).isEmpty();
    }

    @Test
    void readsNothingOutsideItsDirectory() throws Exception {
        Files.createDirectories(directory);
        Files.writeString(dataDirectory.resolve("secret.rdfa"), "secret");
        var recovery = recovery();

        assertThat(recovery.read("../../secret.rdfa")).isEmpty();
        assertThat(recovery.read("..\\\\secret.rdfa")).isEmpty();
        assertThat(recovery.read("secret.txt")).isEmpty();
        assertThat(recovery.discard("../../secret.rdfa")).isFalse();
        assertThat(dataDirectory.resolve("secret.rdfa")).exists();
    }

    @Test
    void keepsNothingWithoutADirectory() {
        var recovery = new WorkspaceRecovery(null, databasePort, savedWorkspaces, export);
        recovery.noteSession();
        addGraph("Draft", "http://example.org/graphs/a");

        recovery.writeUnsaved();

        assertThat(recovery.previousRun()).isEmpty();
        assertThat(recovery.read("Draft.rdfa")).isEmpty();
        assertThat(dataDirectory.resolve("recovery")).doesNotExist();
    }
}
