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

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Writes workspaces to workspace files for the desktop app's Save. */
@Service
@Profile("desktop")
public class DesktopWorkspaceFiles {

    /**
     * A workspace file together with the version of the workspace it holds.
     *
     * @param content the workspace file, see {@link WorkspaceArchiveFormat}
     * @param fileName the name to suggest for it
     * @param revision where the workspace stood when it was written
     * @param workspaceId the id of the workspace, see {@link DatabasePort#workspaceId}
     */
    public record WorkspaceFile(
            byte[] content, String fileName, WorkspaceRevision revision, UUID workspaceId) {}

    private final DatabasePort databasePort;
    private final ExportWorkspaceUseCase exportWorkspaceUseCase;

    public DesktopWorkspaceFiles(
            DatabasePort databasePort, ExportWorkspaceUseCase exportWorkspaceUseCase) {
        this.databasePort = databasePort;
        this.exportWorkspaceUseCase = exportWorkspaceUseCase;
    }

    /**
     * Writes the workspace into a file on disk, replacing it only once the new content is complete
     * (see {@link AtomicFiles#write}).
     *
     * @param workspaceName the workspace, in the current session
     * @param path the file to write
     * @return the version the file holds
     * @throws IOException if the file cannot be written
     */
    public WorkspaceRevision writeTo(String workspaceName, Path path) throws IOException {
        var file = write(workspaceName);
        AtomicFiles.write(path, file.content());
        return file.revision();
    }

    /**
     * Writes the workspace as it stands. The version is read in the same transaction as the file,
     * so no change can fall between the two and be taken for saved.
     *
     * @param workspaceName the workspace, in the current session
     * @return the file and the version it holds
     */
    public WorkspaceFile write(String workspaceName) {
        try (var _ = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            var revision = WorkspaceRevision.current(databasePort, workspaceName);
            var content = exportWorkspaceUseCase.exportWorkspace(workspaceName);
            return new WorkspaceFile(
                    content,
                    WorkspaceArchiveFormat.fileNameFor(workspaceName),
                    revision,
                    databasePort.workspaceId(workspaceName));
        }
    }
}
