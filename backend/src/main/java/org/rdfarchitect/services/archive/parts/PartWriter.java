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

package org.rdfarchitect.services.archive.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes the files of the parts into the workspace file, below the folder of their graph. */
public final class PartWriter {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ZipOutputStream zip;
    private final Map<String, String> folderByGraphUri;
    private final String folder;

    public PartWriter(ZipOutputStream zip, Map<String, String> folderByGraphUri) {
        this(zip, folderByGraphUri, null);
    }

    private PartWriter(ZipOutputStream zip, Map<String, String> folderByGraphUri, String folder) {
        this.zip = zip;
        this.folderByGraphUri = folderByGraphUri;
        this.folder = folder;
    }

    /** Writes into the folder of the graph instead of the top level. */
    public PartWriter inGraph(String graphUri) {
        return new PartWriter(zip, folderByGraphUri, folderByGraphUri.get(graphUri));
    }

    /** The folder each graph is stored in, by graph URI. */
    public Map<String, String> folderByGraphUri() {
        return folderByGraphUri;
    }

    public void writeRdf(String name, Graph graph) {
        write(name, () -> RDFDataMgr.write(zip, graph, RDFFormat.TURTLE));
    }

    public void writeJson(String name, Object value) {
        write(name, () -> zip.write(JSON.writeValueAsBytes(value)));
    }

    private void write(String name, Content content) {
        var path = folder == null ? name : WorkspaceArchiveFormat.graphEntry(folder, name);
        try {
            zip.putNextEntry(new ZipEntry(path));
            content.write();
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface Content {
        void write() throws IOException;
    }
}
