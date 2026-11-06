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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.DiagramLayout;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.services.update.graph.ImportProgressListener;
import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
            if (restored && contents.readOnly()) {
                databasePort.disableEditing(workspaceName);
            }
        } finally {
            if (!restored) {
                databasePort.deleteDataset(workspaceName);
            }
        }
        if (!restored) {
            return new WorkspaceImportResult(List.of(), contents.notices());
        }
        return new WorkspaceImportResult(
                graphs.stream().map(WorkspaceArchive.ArchivedGraph::uri).toList(),
                contents.notices());
    }

    private boolean restore(
            String workspaceName, WorkspaceArchive contents, ImportProgressListener listener) {
        var graphs = contents.graphs();
        var restored = false;
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.WRITE)) {
            for (var index = 0; index < graphs.size(); index++) {
                if (listener.isCancelled()) {
                    transaction.abort();
                    return false;
                }
                listener.started(index);
                restoreGraph(transaction, graphs.get(index));
                listener.finished(index, Outcome.IMPORTED, graphs.get(index).uri());
            }
            restoreLayout(transaction.layout(), contents.layout(), contents.defaultPackageMRID());
            restoreDiagrams(transaction.diagrams(), contents.diagrams());
            contents.colors().forEach(transaction.crossProfileColors()::setColor);
            transaction.restoreCrossProfileDiagramId(contents.crossProfileDiagramId());
            // Last, because creating a graph adds the prefixes of its schema file.
            transaction.setPrefixes(contents.prefixes());
            if (listener.isCancelled()) {
                transaction.abort();
                return false;
            }
            transaction.commitWithoutHistory();
            restored = true;
        } finally {
            if (!restored) {
                skipAll(listener, graphs.size());
            }
        }
        return true;
    }

    // Graphs reported as imported are rolled back with the rest.
    private static void skipAll(ImportProgressListener listener, int graphCount) {
        for (var index = 0; index < graphCount; index++) {
            listener.finished(index, Outcome.SKIPPED, null);
        }
    }

    private static void restoreGraph(
            WorkspaceTransaction transaction, WorkspaceArchive.ArchivedGraph graph) {
        transaction.createGraph(graph.uri(), graph.schema());
        var restored = transaction.graph(graph.uri());
        var shaclModel = ModelFactory.createModelForGraph(restored.getCustomSHACL());
        var shacl = GraphUtils.normalizeBlankNodes(graph.shacl());
        shaclModel.add(ModelFactory.createModelForGraph(shacl));
        shaclModel.setNsPrefixes(shacl.getPrefixMapping());
        restoreLayout(restored.getDiagramLayout(), graph.layout(), graph.defaultPackageMRID());
        restoreDiagrams(restored.getCustomDiagrams(), graph.diagrams());
    }

    private static void restoreLayout(DiagramLayout layout, Graph archived, UUID defaultPackage) {
        layout.getDiagramLayoutModel().add(ModelFactory.createModelForGraph(archived));
        layout.restoreDefaultPackageMRID(new MRID(defaultPackage));
    }

    private static void restoreDiagrams(
            Map<UUID, CustomDiagram> target, List<CustomDiagram> diagrams) {
        diagrams.forEach(diagram -> target.put(diagram.getDiagramId(), diagram));
    }
}
