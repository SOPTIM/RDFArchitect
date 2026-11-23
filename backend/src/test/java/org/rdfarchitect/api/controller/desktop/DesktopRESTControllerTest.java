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

package org.rdfarchitect.api.controller.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.desktop.ClientPresence;
import org.rdfarchitect.desktop.DesktopWorkspaceFiles;
import org.rdfarchitect.desktop.OpenRequests;
import org.rdfarchitect.desktop.SavedWorkspaces;
import org.rdfarchitect.desktop.WorkspaceFileLocations;
import org.rdfarchitect.desktop.WorkspaceRecovery;
import org.rdfarchitect.desktop.WorkspaceRevision;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DesktopRESTControllerTest {

    private static final Path FILE = Path.of("/data/Draft.rdfa");

    private final DesktopWorkspaceFiles workspaceFiles = mock(DesktopWorkspaceFiles.class);
    private DatabasePort databasePort;
    private DesktopRESTController controller;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createDataset("Draft");
        var fileLocations = new WorkspaceFileLocations(databasePort);
        fileLocations.remember("Draft", FILE);
        controller =
                new DesktopRESTController(
                        mock(ClientPresence.class),
                        new SavedWorkspaces(databasePort),
                        workspaceFiles,
                        mock(WorkspaceRecovery.class),
                        mock(OpenRequests.class),
                        fileLocations,
                        new AppVersionResolver("1.2.3"));
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void savesToAFileOneSaveAtATime() throws Exception {
        var revision = WorkspaceRevision.current(databasePort, "Draft");
        var writes = new AtomicInteger();
        var firstWriting = new CountDownLatch(1);
        var finishFirst = new CountDownLatch(1);
        when(workspaceFiles.writeTo("Draft", FILE))
                .thenAnswer(
                        _ -> {
                            if (writes.incrementAndGet() == 1) {
                                firstWriting.countDown();
                                finishFirst.await();
                            }
                            return revision;
                        });
        var sessionId = SessionContext.getSessionId();
        Runnable save =
                () -> {
                    SessionContext.setSessionId(sessionId);
                    try {
                        controller.saveToFile("Draft");
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
