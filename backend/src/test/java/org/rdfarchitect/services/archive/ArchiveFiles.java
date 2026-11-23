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

package org.rdfarchitect.services.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ArchiveFiles {

    public static final ObjectMapper JSON = new ObjectMapper();

    private ArchiveFiles() {}

    public static Map<String, String> unzip(byte[] archive) {
        var files = new TreeMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    public static byte[] zip(Map<String, String> files) {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static ObjectNode json(String content) {
        return (ObjectNode) tree(content);
    }

    public static JsonNode tree(String content) {
        try {
            return JSON.readTree(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String json(JsonNode node) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Graph turtle(String content) {
        return RDFParser.fromString(content, Lang.TURTLE).resolveURIs(false).toGraph();
    }

    public static void assertContainsEverythingOf(
            Map<String, String> expected, Map<String, String> actual) {
        assertThat(actual).containsKeys(expected.keySet().toArray(String[]::new));
        for (var path : expected.keySet()) {
            if (path.endsWith(".ttl")) {
                assertThat(turtle(actual.get(path)).isIsomorphicWith(turtle(expected.get(path))))
                        .as("%s is the same graph", path)
                        .isTrue();
            } else if (path.equals(WorkspaceArchiveFormat.MANIFEST)) {
                assertThat(workspaceDescription(actual.get(path)))
                        .as(path)
                        .isEqualTo(workspaceDescription(expected.get(path)));
            } else {
                assertThat(tree(actual.get(path))).as(path).isEqualTo(tree(expected.get(path)));
            }
        }
    }

    private static JsonNode workspaceDescription(String manifest) {
        var description = json(manifest);
        description.remove(List.of("exportedAt", "appVersion", "name", "formatVersion"));
        return description;
    }
}
