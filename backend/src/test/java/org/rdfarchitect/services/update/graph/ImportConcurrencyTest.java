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

import static org.assertj.core.api.Assertions.*;

import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.GraphCompressionConfig;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The workspace lock covers a whole workspace, so an import that held it while parsing would freeze
 * the editor of that session for the length of the upload. The import must therefore build the
 * graph outside the transaction and take the write lock only to hang it in.
 */
class ImportConcurrencyTest {

    private static final String WORKSPACE = "workspace";

    private static final String TURTLE = "@prefix ex: <http://example.com/> . ex:a ex:b ex:c .";

    private static final String OTHER_TURTLE =
            "@prefix ex: <http://example.com/> . ex:d ex:e ex:f .";

    private DatabasePort databasePort;
    private ImportGraphsUseCase importGraphs;
    private int originalTimeout;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        importGraphs = new ImportGraphsService(databasePort);
        originalTimeout = GraphCompressionConfig.getLockTimeoutSeconds();
    }

    @Test
    void importGraphs_whileParsing_leavesTheWorkspaceReadable() throws InterruptedException {
        // A short timeout turns "the reader waits for the import" into a failure rather than a
        // test that passes by being patient.
        new GraphCompressionConfig().setLockTimeoutSeconds(2);
        try {
            databasePort.createWorkspaceIfAbsent(WORKSPACE);
            var parsingStarted = new CountDownLatch(1);
            var readerDone = new CountDownLatch(1);
            var file = new BlockingMultipartFile(TURTLE, parsingStarted, readerDone);

            var readSucceeded = new AtomicBoolean(false);
            var readFailure = new AtomicReference<Throwable>();
            var reader =
                    new Thread(
                            () -> {
                                try {
                                    assertThat(parsingStarted.await(5, TimeUnit.SECONDS)).isTrue();
                                    try (var transaction =
                                            databasePort.beginTransaction(
                                                    WORKSPACE, ReadWrite.READ)) {
                                        transaction.graphUris();
                                        readSucceeded.set(true);
                                    }
                                } catch (Throwable t) {
                                    readFailure.set(t);
                                } finally {
                                    readerDone.countDown();
                                }
                            });
            reader.start();

            var result =
                    importGraphs.importGraphs(
                            WORKSPACE, List.of(file), null, ImportProgressListener.NOOP);

            reader.join(10_000);
            assertThat(readFailure.get()).isNull();
            assertThat(readSucceeded)
                    .as("a read transaction while the import was parsing")
                    .isTrue();
            assertThat(result.failedFileNames()).isEmpty();
            assertThat(databasePort.listGraphUris(WORKSPACE)).hasSize(1);
        } finally {
            new GraphCompressionConfig().setLockTimeoutSeconds(originalTimeout);
        }
    }

    @Test
    void importGraphs_swap_doesTakeTheWriteLock() throws InterruptedException {
        // The counterpart: the import is not lock-free, it just narrows the window. With a reader
        // holding the workspace, the swap has to wait.
        new GraphCompressionConfig().setLockTimeoutSeconds(1);
        try {
            databasePort.createWorkspaceIfAbsent(WORKSPACE);
            var readerHoldsLock = new CountDownLatch(1);
            var importFinished = new CountDownLatch(1);

            var reader =
                    new Thread(
                            () -> {
                                try (var transaction =
                                        databasePort.beginTransaction(WORKSPACE, ReadWrite.READ)) {
                                    readerHoldsLock.countDown();
                                    assertThat(importFinished.await(5, TimeUnit.SECONDS)).isTrue();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            reader.start();
            assertThat(readerHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

            var file = new BlockingMultipartFile(TURTLE, null, null);
            var result =
                    importGraphs.importGraphs(
                            WORKSPACE, List.of(file), null, ImportProgressListener.NOOP);

            importFinished.countDown();
            reader.join(10_000);
            assertThat(result.failedFileNames()).hasSize(1);
        } finally {
            new GraphCompressionConfig().setLockTimeoutSeconds(originalTimeout);
        }
    }

    @Test
    void importGraphs_severalFiles_areOneChangeInTheHistory() {
        databasePort.createWorkspaceIfAbsent(WORKSPACE);

        var result =
                importGraphs.importGraphs(
                        WORKSPACE,
                        List.of(
                                new BlockingMultipartFile(TURTLE, null, null),
                                new BlockingMultipartFile(OTHER_TURTLE, null, null)),
                        List.of("http://example.org/one", "http://example.org/two"),
                        ImportProgressListener.NOOP);

        assertThat(result.importedGraphUris()).hasSize(2);
        assertThat(databasePort.listChanges(WORKSPACE))
                .first()
                .satisfies(
                        entry -> {
                            assertThat(entry.message()).isEqualTo("imported 2 graphs");
                            assertThat(entry.affectedGraphUris()).hasSize(2);
                        });
    }

    @Test
    void undo_ofAnImportOfSeveralFiles_takesAllOfThemBack() {
        databasePort.createWorkspaceIfAbsent(WORKSPACE);
        importGraphs.importGraphs(
                WORKSPACE,
                List.of(
                        new BlockingMultipartFile(TURTLE, null, null),
                        new BlockingMultipartFile(OTHER_TURTLE, null, null)),
                List.of("http://example.org/one", "http://example.org/two"),
                ImportProgressListener.NOOP);

        databasePort.undo(WORKSPACE);

        assertThat(databasePort.listGraphUris(WORKSPACE)).isEmpty();
    }

    /**
     * A file whose parsing stops halfway until another thread says it may continue, so that "while
     * the import is parsing" is a defined moment instead of a race the test hopes to win.
     */
    private record BlockingMultipartFile(
            String content, CountDownLatch parsingStarted, CountDownLatch mayContinue)
            implements MultipartFile {

        @Override
        public String getName() {
            return "graph";
        }

        @Override
        public String getOriginalFilename() {
            return "graph.ttl";
        }

        @Override
        public String getContentType() {
            return "text/turtle";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return getBytes().length;
        }

        @Override
        public byte[] getBytes() {
            return content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            if (parsingStarted == null) {
                return new ByteArrayInputStream(getBytes());
            }
            return new ByteArrayInputStream(getBytes()) {
                private boolean signalled;

                @Override
                public synchronized int read(byte[] b, int off, int len) {
                    pauseOnce();
                    return super.read(b, off, len);
                }

                private void pauseOnce() {
                    if (signalled) {
                        return;
                    }
                    signalled = true;
                    parsingStarted.countDown();
                    try {
                        mayContinue.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
        }

        @Override
        public void transferTo(java.io.File destination) throws IOException {
            throw new IOException("not supported in this test");
        }
    }
}
