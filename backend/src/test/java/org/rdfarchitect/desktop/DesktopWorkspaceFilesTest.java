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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

class DesktopWorkspaceFilesTest {

    @TempDir Path directory;

    private DatabasePort databasePort;
    private DesktopWorkspaceFiles workspaceFiles;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createDataset("Draft");
        var export = mock(ExportWorkspaceUseCase.class);
        when(export.exportWorkspace("Draft")).thenReturn("new".getBytes(StandardCharsets.UTF_8));
        workspaceFiles = new DesktopWorkspaceFiles(databasePort, export);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private List<Path> filesInDirectory() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.toList();
        }
    }

    @Test
    void replacesTheFileAndLeavesNothingElseBehind() throws IOException {
        var file = directory.resolve("Draft.rdfa");
        Files.writeString(file, "old");

        var revision = workspaceFiles.writeTo("Draft", file);

        assertThat(Files.readString(file)).isEqualTo("new");
        assertThat(filesInDirectory()).containsExactly(file);
        assertThat(revision).isEqualTo(WorkspaceRevision.current(databasePort, "Draft"));
    }

    @Test
    void leavesNothingBehindWhenTheFileCannotBeReplaced() throws IOException {
        var file = directory.resolve("Draft.rdfa");
        Files.createDirectories(file.resolve("in the way"));

        assertThatThrownBy(() -> workspaceFiles.writeTo("Draft", file))
                .isInstanceOf(IOException.class);

        assertThat(filesInDirectory()).containsExactly(file);
    }
}
