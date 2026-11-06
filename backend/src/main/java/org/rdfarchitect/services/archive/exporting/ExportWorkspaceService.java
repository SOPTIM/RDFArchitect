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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.services.archive.ArchivedDiagrams;
import org.rdfarchitect.services.archive.ArchivedGraphDiagram;
import org.rdfarchitect.services.archive.ArchivedWorkspaceDiagram;
import org.rdfarchitect.services.archive.GraphFolderNames;
import org.rdfarchitect.services.archive.LayoutIris;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.WorkspaceArchiveManifest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportWorkspaceService implements ExportWorkspaceUseCase {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final DatabasePort databasePort;
    private final String appVersion;

    public ExportWorkspaceService(DatabasePort databasePort, AppVersionResolver versionResolver) {
        this.databasePort = databasePort;
        this.appVersion = versionResolver.resolveVersion();
    }

    @Override
    public byte[] exportWorkspace(String workspaceName) {
        var readOnly = databasePort.isReadOnly(workspaceName);
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out);
                var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            var folders = GraphFolderNames.assign(transaction.graphUris());
            var graphEntries = new ArrayList<WorkspaceArchiveManifest.GraphEntry>();
            for (var graph : folders.entrySet()) {
                graphEntries.add(writeGraph(zip, transaction, graph.getKey(), graph.getValue()));
            }
            writeWorkspace(zip, transaction, folders);
            var manifest =
                    new WorkspaceArchiveManifest(
                            WorkspaceArchiveFormat.FORMAT_VERSION,
                            appVersion,
                            Instant.now().toString(),
                            workspaceName,
                            readOnly,
                            graphEntries);
            writeJson(zip, WorkspaceArchiveFormat.MANIFEST, manifest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static WorkspaceArchiveManifest.GraphEntry writeGraph(
            ZipOutputStream zip, WorkspaceTransaction transaction, String graphUri, String folder)
            throws IOException {
        var graph = transaction.graph(graphUri);
        var layout = graph.getDiagramLayout();
        writeRdf(
                zip,
                WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.SCHEMA),
                graph.getRdfGraph());
        writeRdf(
                zip,
                WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.SHACL),
                graph.getCustomSHACL());
        writeRdf(
                zip,
                WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.LAYOUT),
                LayoutIris.toArchived(layout.getDiagramLayoutModel().getGraph()));
        writeJson(
                zip,
                WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.DIAGRAMS),
                new ArchivedDiagrams<>(
                        null,
                        layout.getDefaultPackageMRID().getUuid(),
                        sorted(graph.getCustomDiagrams().values())
                                .map(diagram -> ArchivedGraphDiagram.of(diagram, graphUri))
                                .toList()));
        return new WorkspaceArchiveManifest.GraphEntry(folder, graphUri);
    }

    private static void writeWorkspace(
            ZipOutputStream zip, WorkspaceTransaction transaction, Map<String, String> folders)
            throws IOException {
        writeRdf(
                zip,
                WorkspaceArchiveFormat.LAYOUT,
                LayoutIris.toArchived(transaction.layout().getDiagramLayoutModel().getGraph()));
        writeJson(
                zip,
                WorkspaceArchiveFormat.DIAGRAMS,
                new ArchivedDiagrams<>(
                        transaction.crossProfileDiagramId(),
                        transaction.layout().getDefaultPackageMRID().getUuid(),
                        sorted(transaction.diagrams().values())
                                .map(diagram -> ArchivedWorkspaceDiagram.of(diagram, folders))
                                .toList()));
        var colors = new LinkedHashMap<String, String>();
        folders.forEach(
                (graphUri, folder) -> {
                    var color = transaction.crossProfileColors().getColor(graphUri);
                    if (color != null) {
                        colors.put(folder, color);
                    }
                });
        writeJson(zip, WorkspaceArchiveFormat.COLORS, colors);
        writeJson(
                zip,
                WorkspaceArchiveFormat.NAMESPACES,
                new TreeMap<>(transaction.prefixes().getNsPrefixMap()));
    }

    private static Stream<CustomDiagram> sorted(Collection<CustomDiagram> diagrams) {
        return diagrams.stream().sorted(Comparator.comparing(CustomDiagram::getDiagramId));
    }

    private static void writeRdf(ZipOutputStream zip, String entry, Graph graph)
            throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        RDFDataMgr.write(zip, graph, RDFFormat.TURTLE);
        zip.closeEntry();
    }

    private static void writeJson(ZipOutputStream zip, String entry, Object value)
            throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(JSON.writeValueAsBytes(value));
        zip.closeEntry();
    }
}
