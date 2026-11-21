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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidNullException;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Reads the files of the parts from the unpacked workspace file, below the folder of their graph,
 * and collects every problem the parts find instead of stopping at the first.
 */
public final class PartReader {

    // Newer minor versions may add fields. Every field is required unless it opts out with
    // @JsonSetter(nulls = Nulls.SET), so a missing or null one fails like broken JSON does.
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.FAIL, Nulls.FAIL));

    private final Map<String, byte[]> entries;
    private final Map<String, String> graphUriByFolder;
    private final List<String> problems;
    private final String folder;

    /** Reads the top level of the unpacked file, collecting the problems into {@code problems}. */
    public PartReader(
            Map<String, byte[]> entries,
            Map<String, String> graphUriByFolder,
            List<String> problems) {
        this(entries, graphUriByFolder, problems, null);
    }

    private PartReader(
            Map<String, byte[]> entries,
            Map<String, String> graphUriByFolder,
            List<String> problems,
            String folder) {
        this.entries = entries;
        this.graphUriByFolder = graphUriByFolder;
        this.problems = problems;
        this.folder = folder;
    }

    /** Reads from the folder of a graph instead of the top level. */
    public PartReader inGraph(String folder) {
        return new PartReader(entries, graphUriByFolder, problems, folder);
    }

    /** The graph whose folder this reads from, {@code null} at the top level. */
    public String graphUri() {
        return folder == null ? null : graphUriByFolder.get(folder);
    }

    /** The graph stored in each folder, by folder name. */
    public Map<String, String> graphUriByFolder() {
        return graphUriByFolder;
    }

    /** Reports a problem with the file of the given name. */
    public void problem(String name, String message) {
        problems.add(path(name) + ": " + message);
    }

    /**
     * @return the graph, {@code null} if the file is missing or no Turtle
     */
    public Graph readRdf(String name) {
        var content = entry(name);
        if (content == null) {
            return null;
        }
        try {
            return RDFParser.source(new ByteArrayInputStream(content))
                    .lang(Lang.TURTLE)
                    // Keep IRIs as written instead of resolving them against a base.
                    .resolveURIs(false)
                    .errorHandler(ErrorHandlerFactory.errorHandlerNoLogging)
                    .toGraph();
        } catch (RiotException e) {
            problem(name, e.getMessage());
            return null;
        }
    }

    /**
     * @return the value, {@code null} if the file is missing, empty or does not fit the type
     */
    public <T> T readJson(String name, TypeReference<T> type) {
        var content = entry(name);
        if (content == null) {
            return null;
        }
        try {
            var value = JSON.readValue(content, type);
            if (value == null) {
                problem(name, "the file is empty.");
            }
            return value;
        } catch (IOException e) {
            problem(name, describe(e));
            return null;
        }
    }

    private byte[] entry(String name) {
        var content = entries.get(path(name));
        if (content == null) {
            problems.add(path(name) + " is missing.");
        }
        return content;
    }

    private String path(String name) {
        return folder == null ? name : WorkspaceArchiveFormat.graphEntry(folder, name);
    }

    private static String describe(IOException e) {
        return e instanceof JacksonException jackson
                ? messageOf(jackson)
                        + (jackson.getLocation() == null
                                ? ""
                                : " (line %d, column %d)"
                                        .formatted(
                                                jackson.getLocation().getLineNr(),
                                                jackson.getLocation().getColumnNr()))
                : e.getMessage();
    }

    private static String messageOf(JacksonException e) {
        if (!(e instanceof InvalidNullException invalidNull)) {
            return e.getOriginalMessage();
        }
        var property = invalidNull.getPropertyName();
        return (property == null ? "a value" : "\"" + property.getSimpleName() + "\"")
                + " is missing or null";
    }
}
