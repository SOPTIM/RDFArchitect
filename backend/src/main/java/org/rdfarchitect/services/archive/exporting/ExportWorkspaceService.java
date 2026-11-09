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

package org.rdfarchitect.services.archive.exporting;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.services.archive.GraphFolderNames;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.WorkspaceArchiveManifest;
import org.rdfarchitect.services.archive.parts.GraphScope;
import org.rdfarchitect.services.archive.parts.PartWriter;
import org.rdfarchitect.services.archive.parts.WorkspaceFileParts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.zip.ZipOutputStream;

@Service
public class ExportWorkspaceService implements ExportWorkspaceUseCase {

    private final DatabasePort databasePort;
    private final String appVersion;

    public ExportWorkspaceService(DatabasePort databasePort, AppVersionResolver versionResolver) {
        this.databasePort = databasePort;
        this.appVersion = versionResolver.resolveVersion();
    }

    @Override
    public byte[] exportWorkspace(String workspaceName) {
        var readOnly = databasePort.isReadOnly(workspaceName);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes);
                var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            var folders = GraphFolderNames.assign(transaction.graphUris());
            var out = new PartWriter(zip, folders);
            var graphEntries = new ArrayList<WorkspaceArchiveManifest.GraphEntry>();
            folders.forEach(
                    (graphUri, folder) -> {
                        var scope = new GraphScope(transaction, graphUri);
                        var graphOut = out.inGraph(graphUri);
                        WorkspaceFileParts.OF_GRAPH.forEach(part -> part.write(scope, graphOut));
                        graphEntries.add(new WorkspaceArchiveManifest.GraphEntry(folder, graphUri));
                    });
            WorkspaceFileParts.OF_WORKSPACE.forEach(part -> part.write(transaction, out));
            var manifest =
                    new WorkspaceArchiveManifest(
                            WorkspaceArchiveFormat.FORMAT_VERSION,
                            appVersion,
                            Instant.now().toString(),
                            workspaceName,
                            readOnly,
                            graphEntries);
            out.writeJson(WorkspaceArchiveFormat.MANIFEST, manifest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
