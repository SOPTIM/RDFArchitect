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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes the files of the parts into the workspace file, below the folder of their schema. */
public final class PartWriter {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ZipOutputStream zip;
    private final List<String> schemaNames;
    private final String schemaName;

    public PartWriter(ZipOutputStream zip, List<String> schemaNames) {
        this(zip, schemaNames, null);
    }

    private PartWriter(ZipOutputStream zip, List<String> schemaNames, String schemaName) {
        this.zip = zip;
        this.schemaNames = schemaNames;
        this.schemaName = schemaName;
    }

    /** Writes into the folder of the schema instead of the top level. */
    public PartWriter inSchema(String schemaName) {
        return new PartWriter(zip, schemaNames, schemaName);
    }

    /** The schemas written to the workspace file. */
    public List<String> schemaNames() {
        return schemaNames;
    }

    /** Writes a graph as Turtle. */
    public void writeRdf(String name, Graph graph) {
        write(name, () -> RDFDataMgr.write(zip, graph, RDFFormat.TURTLE));
    }

    /** Writes a value as indented JSON. */
    public void writeJson(String name, Object value) {
        write(name, () -> zip.write(JSON.writeValueAsBytes(value)));
    }

    private void write(String name, Content content) {
        var path = schemaName == null ? name : WorkspaceArchiveFormat.schemaEntry(schemaName, name);
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
