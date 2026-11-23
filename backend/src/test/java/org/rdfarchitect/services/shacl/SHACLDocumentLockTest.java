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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.exception.database.InvalidContentException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Saving a document's text reads it before taking the workspace's write lock. A large file takes a
 * while to read, and every other request on the workspace would wait for it.
 */
class SHACLDocumentLockTest {

    private static final String WORKSPACE = "cgmes";
    private static final String SESSION = "SHACLDocumentLockTest";

    private final GraphCompressionConfig graphConfig = new GraphCompressionConfig();
    private int originalTimeout;
    private DatabasePort databasePort;
    private SHACLStoringService service;

    @BeforeEach
    void setUp() {
        originalTimeout = GraphCompressionConfig.getLockTimeoutSeconds();
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        SessionContext.setSessionId(SESSION);
        databasePort.createWorkspaceIfAbsent(WORKSPACE);
        databasePort.enableEditing(WORKSPACE);
        service = new SHACLStoringService(databasePort);
    }

    @AfterEach
    void tearDown() {
        graphConfig.setLockTimeoutSeconds(originalTimeout);
        SessionContext.clear();
    }

    @Test
    void textThatCannotBeReadIsRefusedWhileAnotherRequestReadsTheWorkspace()
            throws InterruptedException {
        var id =
                service.createShapesDocument(WORKSPACE, List.of(), "eq.ttl", null, "", Lang.TURTLE)
                        .getId();
        graphConfig.setLockTimeoutSeconds(1);
        var reading = new CountDownLatch(1);
        var done = new CountDownLatch(1);
        var reader =
                new Thread(
                        () -> {
                            SessionContext.setSessionId(SESSION);
                            try (var _ = databasePort.beginTransaction(WORKSPACE, ReadWrite.READ)) {
                                reading.countDown();
                                done.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                            } finally {
                                SessionContext.clear();
                            }
                        });
        reader.start();
        assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatExceptionOfType(InvalidContentException.class)
                    .isThrownBy(
                            () ->
                                    service.replaceShapesDocumentText(
                                            WORKSPACE, id, "<http://example.org/a> a", null));
        } finally {
            done.countDown();
            reader.join(5000);
        }
        assertThat(service.getShapesDocumentText(WORKSPACE, id)).isEmpty();
    }
}
