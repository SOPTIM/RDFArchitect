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

package org.rdfarchitect.services.archive.importing;

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.ReadWrite;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.services.archive.parts.GraphScope;
import org.rdfarchitect.services.update.graph.ImportProgressListener;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ImportWorkspaceService implements ImportWorkspaceUseCase {

    private final DatabasePort databasePort;

    @Override
    public WorkspaceImportResult importWorkspace(
            String workspaceName, InputStream archive, ImportProgressListener listener) {
        var contents = WorkspaceArchiveReader.read(archive);
        var graphs = contents.graphs();
        listener.planned(
                IntStream.range(0, graphs.size())
                        .mapToObj(index -> new PlannedImport(index, graphs.get(index).uri(), -1))
                        .toList());

        databasePort.createDataset(workspaceName);
        var restored = false;
        try {
            restored = restore(workspaceName, contents, listener);
        } finally {
            if (!restored) {
                databasePort.deleteDataset(workspaceName);
            }
        }
        if (!restored) {
            return new WorkspaceImportResult(List.of(), contents.notices(), true);
        }
        if (contents.readOnly()) {
            databasePort.disableEditing(workspaceName);
        }
        return new WorkspaceImportResult(
                graphs.stream().map(WorkspaceArchive.ArchivedGraph::uri).toList(),
                contents.notices(),
                false);
    }

    /** Returns {@code false} if the import was cancelled before it was committed. */
    private boolean restore(
            String workspaceName, WorkspaceArchive contents, ImportProgressListener listener) {
        var graphs = contents.graphs();
        var committed = false;
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            for (var index = 0; index < graphs.size(); index++) {
                if (listener.isCancelled()) {
                    transaction.abort();
                    return false;
                }
                listener.started(index);
                var graph = graphs.get(index);
                var scope = new GraphScope(transaction, graph.uri());
                graph.parts().forEach(part -> part.restore(scope));
                listener.finished(index, Outcome.IMPORTED, graph.uri());
            }
            contents.workspaceParts().forEach(part -> part.restore(transaction));
            if (listener.isCancelled()) {
                transaction.abort();
                return false;
            }
            transaction.commitWithoutHistory();
            committed = true;
            return true;
        } finally {
            if (!committed) {
                skipAll(listener, graphs.size());
            }
        }
    }

    // The graphs reported as imported so far were rolled back with the rest of the workspace.
    private static void skipAll(ImportProgressListener listener, int graphCount) {
        for (var index = 0; index < graphCount; index++) {
            listener.finished(index, Outcome.SKIPPED, null);
        }
    }
}
