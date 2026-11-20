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
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SavedWorkspacesTest {

    private static final String WORKSPACE = "draft";
    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    private DatabasePort databasePort;
    private SavedWorkspaces savedWorkspaces;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        savedWorkspaces = new SavedWorkspaces(databasePort);
        databasePort.createDataset(WORKSPACE);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private void addGraph(String graphUri) {
        try (var transaction = databasePort.beginTransaction(WORKSPACE, ReadWrite.WRITE)) {
            transaction.createGraph(graphUri, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.commit("Add " + graphUri);
        }
    }

    @Test
    void anEmptyWorkspaceHasNothingToLose() {
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();
    }

    @Test
    void aChangedWorkspaceIsUnsavedUntilItIsSaved() {
        addGraph("http://example.org/graphs/a");
        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);

        savedWorkspaces.markSaved(WORKSPACE, databasePort.currentChangeId(WORKSPACE));
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();

        addGraph("http://example.org/graphs/b");
        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
    }

    @Test
    void undoingBackToTheSavedVersionMakesItSavedAgain() {
        addGraph("http://example.org/graphs/a");
        assertThat(savedWorkspaces.markSavedNow(WORKSPACE)).isTrue();
        addGraph("http://example.org/graphs/b");

        databasePort.undo(WORKSPACE);
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();

        databasePort.redo(WORKSPACE);
        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
    }

    @Test
    void aSaveCountsInItsOwnSessionOnly() {
        addGraph("http://example.org/graphs/a");
        savedWorkspaces.markSavedNow(WORKSPACE);

        SessionContext.setSessionId("another session");
        databasePort.createDataset(WORKSPACE);
        addGraph("http://example.org/graphs/a");

        assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
    }

    @Test
    void aWorkspaceBeingWrittenToCountsAsUnsavedWithoutWaiting() throws Exception {
        addGraph("http://example.org/graphs/a");
        savedWorkspaces.markSavedNow(WORKSPACE);

        var sessionId = SessionContext.getSessionId();
        var writing = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var writer =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    SessionContext.setSessionId(sessionId);
                                    try (var transaction =
                                            databasePort.beginTransaction(
                                                    WORKSPACE, ReadWrite.WRITE)) {
                                        writing.countDown();
                                        finish.await();
                                        transaction.abort();
                                    } catch (InterruptedException _) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        SessionContext.clear();
                                    }
                                });
        try {
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(databasePort.currentChangeId(WORKSPACE)).isNull();
            assertThat(savedWorkspaces.unsavedWorkspaces()).containsExactly(WORKSPACE);
            assertThat(savedWorkspaces.markSavedNow(WORKSPACE)).isFalse();
        } finally {
            finish.countDown();
            writer.join();
        }
        assertThat(savedWorkspaces.unsavedWorkspaces()).isEmpty();
    }
}
