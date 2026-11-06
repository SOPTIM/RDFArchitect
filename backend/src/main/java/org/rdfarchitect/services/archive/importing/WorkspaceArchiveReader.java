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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.rdfarchitect.database.inmemory.Workspace;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.services.archive.ArchivedDiagrams;
import org.rdfarchitect.services.archive.ArchivedGraphDiagram;
import org.rdfarchitect.services.archive.ArchivedWorkspaceDiagram;
import org.rdfarchitect.services.archive.FormatVersion;
import org.rdfarchitect.services.archive.LayoutIris;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.WorkspaceArchiveManifest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

public final class WorkspaceArchiveReader {

    static final int MAX_ENTRIES = 10_000;
    static final long MAX_UNPACKED_SIZE = FileUtils.ONE_GB;

    // Newer minor versions may add fields.
    private static final ObjectMapper JSON =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Pattern FOLDER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final Map<String, byte[]> entries;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();

    private WorkspaceArchiveReader(Map<String, byte[]> entries) {
        this.entries = entries;
    }

    public static WorkspaceArchive read(InputStream archive) {
        return read(archive, MAX_UNPACKED_SIZE);
    }

    static WorkspaceArchive read(InputStream archive, long maxUnpackedSize) {
        return new WorkspaceArchiveReader(unzip(archive, maxUnpackedSize)).read();
    }

    private static Map<String, byte[]> unzip(InputStream archive, long maxUnpackedSize) {
        var entries = new HashMap<String, byte[]>();
        var remaining = maxUnpackedSize;
        try (var zip = new ZipInputStream(archive)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entries.size() >= MAX_ENTRIES) {
                    throw invalid("The archive holds more than " + MAX_ENTRIES + " files.");
                }
                var content = zip.readNBytes((int) Math.min(remaining, Integer.MAX_VALUE - 8));
                if (zip.read() != -1) {
                    throw invalid(
                            "The archive unpacks to more than %d MB."
                                    .formatted(maxUnpackedSize / FileUtils.ONE_MB));
                }
                remaining -= content.length;
                if (entries.put(entry.getName(), content) != null) {
                    throw invalid(entry.getName() + ": the archive holds this file twice.");
                }
            }
        } catch (IOException e) {
            throw invalid("The file is not a readable zip archive: " + e.getMessage());
        }
        return entries;
    }

    private WorkspaceArchive read() {
        var manifest = readManifest();
        if (manifest == null || !isReadable(manifest.formatVersion(), manifest.appVersion())) {
            throw new InvalidWorkspaceArchiveException(problems);
        }

        var graphUriByFolder = readGraphEntries(manifest.graphs());

        var graphs = new ArrayList<WorkspaceArchive.ArchivedGraph>();
        graphUriByFolder.forEach((folder, uri) -> graphs.add(readGraph(folder, uri)));
        var layout = readLayout(WorkspaceArchiveFormat.LAYOUT);
        var diagrams = readWorkspaceDiagrams(graphUriByFolder);
        var colors = readColors(graphUriByFolder);
        var prefixes = readNamespaces();

        if (!problems.isEmpty()) {
            throw new InvalidWorkspaceArchiveException(problems);
        }
        return new WorkspaceArchive(
                prefixes,
                manifest.readOnly(),
                diagrams.crossProfileDiagramId(),
                diagrams.defaultPackageMRID(),
                graphs,
                layout,
                diagrams.customDiagrams(),
                colors,
                notices);
    }

    private WorkspaceArchiveManifest readManifest() {
        var content = entries.get(WorkspaceArchiveFormat.MANIFEST);
        if (content == null) {
            problems.add(
                    WorkspaceArchiveFormat.MANIFEST
                            + " is missing, so this is not a workspace archive. Schema files and"
                            + " plain zip archives of them are imported as schemas instead.");
            return null;
        }
        try {
            return JSON.readValue(content, WorkspaceArchiveManifest.class);
        } catch (IOException e) {
            problems.add(WorkspaceArchiveFormat.MANIFEST + ": " + describe(e));
            return null;
        }
    }

    private boolean isReadable(String formatVersion, String appVersion) {
        FormatVersion version;
        try {
            version = FormatVersion.parse(formatVersion);
        } catch (IllegalArgumentException e) {
            problems.add(
                    WorkspaceArchiveFormat.MANIFEST + ": formatVersion " + e.getMessage() + ".");
            return false;
        }
        var current = FormatVersion.CURRENT;
        var writtenBy = appVersion == null ? "" : " (RDFArchitect " + appVersion + ")";
        if (version.major() != current.major()) {
            problems.add(
                    "The archive was written in format %s%s. This version of RDFArchitect reads format %s."
                            .formatted(version, writtenBy, current));
            return false;
        }
        if (version.minor() > current.minor()) {
            notices.add(
                    ("The archive was written in format %s by a newer version of RDFArchitect%s."
                                    + " Anything this version (format %s) does not know about was"
                                    + " left out.")
                            .formatted(version, writtenBy, current));
        }
        return true;
    }

    private PrefixMapping readNamespaces() {
        var path = WorkspaceArchiveFormat.NAMESPACES;
        var prefixes = readJson(path, new TypeReference<Map<String, String>>() {});
        var mapping = new PrefixMappingImpl();
        if (prefixes == null) {
            return mapping;
        }
        prefixes.forEach(
                (prefix, namespace) -> {
                    var problem =
                            path
                                    + ": \"%s\" -> \"%s\" is not a valid namespace prefix."
                                            .formatted(prefix, namespace);
                    if (namespace == null) {
                        problems.add(problem);
                        return;
                    }
                    try {
                        mapping.setNsPrefix(prefix, namespace);
                    } catch (PrefixMapping.IllegalPrefixException _) {
                        problems.add(problem);
                    }
                });
        return mapping;
    }

    private Map<String, String> readGraphEntries(List<WorkspaceArchiveManifest.GraphEntry> graphs) {
        var graphUriByFolder = new LinkedHashMap<String, String>();
        if (graphs == null) {
            problems.add(WorkspaceArchiveFormat.MANIFEST + ": \"graphs\" is missing.");
            return graphUriByFolder;
        }
        var uris = new HashSet<String>();
        for (var graph : graphs) {
            if (graph == null) {
                problems.add(WorkspaceArchiveFormat.MANIFEST + ": a graph entry is empty.");
                continue;
            }
            var where = WorkspaceArchiveFormat.MANIFEST + ": graph \"" + graph.folder() + "\"";
            var valid = true;
            if (graph.folder() == null || !FOLDER_NAME.matcher(graph.folder()).matches()) {
                problems.add(
                        where
                                + ": \"folder\" has to be a folder name made of letters, digits,"
                                + " '.', '-' and '_'.");
                valid = false;
            } else if (graphUriByFolder.containsKey(graph.folder())) {
                problems.add(where + ": two graphs are stored in this folder.");
                valid = false;
            }
            if (graph.uri() == null || !Workspace.isValidGraphName(graph.uri())) {
                problems.add(where + ": \"" + graph.uri() + "\" is not a valid graph URI.");
                valid = false;
            } else if (!uris.add(graph.uri())) {
                problems.add(where + ": the graph URI " + graph.uri() + " is used twice.");
                valid = false;
            }
            if (valid) {
                graphUriByFolder.put(graph.folder(), graph.uri());
            }
        }
        return graphUriByFolder;
    }

    private WorkspaceArchive.ArchivedGraph readGraph(String folder, String uri) {
        var diagrams = readGraphDiagrams(folder, uri);
        return new WorkspaceArchive.ArchivedGraph(
                uri,
                diagrams.defaultPackageMRID(),
                readRdf(WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.SCHEMA)),
                readRdf(WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.SHACL)),
                readLayout(
                        WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.LAYOUT)),
                diagrams.customDiagrams());
    }

    private Graph readLayout(String path) {
        var layout = readRdf(path);
        return layout == null ? null : LayoutIris.fromArchived(layout);
    }

    private Graph readRdf(String path) {
        var content = entry(path);
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
            problems.add(path + ": " + e.getMessage());
            return null;
        }
    }

    private record Diagrams(
            UUID crossProfileDiagramId,
            UUID defaultPackageMRID,
            List<CustomDiagram> customDiagrams) {}

    private Diagrams readWorkspaceDiagrams(Map<String, String> graphUriByFolder) {
        return readDiagrams(
                WorkspaceArchiveFormat.DIAGRAMS,
                new TypeReference<ArchivedDiagrams<ArchivedWorkspaceDiagram>>() {},
                true,
                ArchivedWorkspaceDiagram::diagramId,
                (where, diagram) -> {
                    if (diagram.classes() == null) {
                        problems.add(where + ": \"classes\" is missing.");
                        return null;
                    }
                    var valid = true;
                    for (var classes : diagram.classes().entrySet()) {
                        var folder = classes.getKey();
                        if (!graphUriByFolder.containsKey(folder)) {
                            problems.add(
                                    where + ": \"" + folder + "\" is not a graph of the archive.");
                            valid = false;
                        } else if (classes.getValue() == null
                                || classes.getValue().contains(null)) {
                            problems.add(where + ": a class of \"" + folder + "\" has no UUID.");
                            valid = false;
                        }
                    }
                    return valid ? diagram.toCustomDiagram(graphUriByFolder) : null;
                });
    }

    private Diagrams readGraphDiagrams(String folder, String graphUri) {
        return readDiagrams(
                WorkspaceArchiveFormat.graphEntry(folder, WorkspaceArchiveFormat.DIAGRAMS),
                new TypeReference<ArchivedDiagrams<ArchivedGraphDiagram>>() {},
                false,
                ArchivedGraphDiagram::diagramId,
                (where, diagram) -> {
                    if (diagram.classes() == null) {
                        problems.add(where + ": \"classes\" is missing.");
                        return null;
                    }
                    if (diagram.classes().contains(null)) {
                        problems.add(where + ": a class has no UUID.");
                        return null;
                    }
                    return diagram.toCustomDiagram(graphUri);
                });
    }

    private <D> Diagrams readDiagrams(
            String path,
            TypeReference<ArchivedDiagrams<D>> type,
            boolean ofWorkspace,
            Function<D, UUID> idOf,
            BiFunction<String, D, CustomDiagram> restore) {
        var archived = readJson(path, type);
        if (archived == null) {
            return new Diagrams(null, null, List.of());
        }
        if (ofWorkspace && archived.crossProfileDiagramId() == null) {
            problems.add(path + ": \"crossProfileDiagramId\" is missing.");
        }
        if (archived.defaultPackageMRID() == null) {
            problems.add(path + ": \"defaultPackageMRID\" is missing.");
        }
        if (archived.customDiagrams() == null) {
            problems.add(path + ": \"customDiagrams\" is missing.");
            return new Diagrams(null, null, List.of());
        }
        var diagrams = new ArrayList<CustomDiagram>();
        var ids = new HashSet<UUID>();
        for (var diagram : archived.customDiagrams()) {
            var id = diagram == null ? null : idOf.apply(diagram);
            if (id == null) {
                problems.add(path + ": a diagram has no \"diagramId\".");
                continue;
            }
            var where = path + ": diagram " + id;
            if (!ids.add(id)) {
                problems.add(where + " is there twice.");
                continue;
            }
            try {
                var restored = restore.apply(where, diagram);
                if (restored != null) {
                    diagrams.add(restored);
                }
            } catch (IllegalArgumentException e) {
                problems.add(where + ": " + e.getMessage());
            }
        }
        return new Diagrams(
                archived.crossProfileDiagramId(), archived.defaultPackageMRID(), diagrams);
    }

    private Map<String, String> readColors(Map<String, String> graphUriByFolder) {
        var path = WorkspaceArchiveFormat.COLORS;
        var archived = readJson(path, new TypeReference<Map<String, String>>() {});
        var colors = new LinkedHashMap<String, String>();
        if (archived == null) {
            return colors;
        }
        archived.forEach(
                (folder, color) -> {
                    var graphUri = graphUriByFolder.get(folder);
                    if (graphUri == null) {
                        problems.add(path + ": \"" + folder + "\" is not a graph of the archive.");
                    } else if (color == null || color.isBlank()) {
                        problems.add(path + ": the graph \"" + folder + "\" has no colour.");
                    } else {
                        colors.put(graphUri, color);
                    }
                });
        return colors;
    }

    private <T> T readJson(String path, TypeReference<T> type) {
        var content = entry(path);
        if (content == null) {
            return null;
        }
        try {
            var value = JSON.readValue(content, type);
            if (value == null) {
                problems.add(path + ": the file is empty.");
            }
            return value;
        } catch (IOException e) {
            problems.add(path + ": " + describe(e));
            return null;
        }
    }

    private byte[] entry(String path) {
        var content = entries.get(path);
        if (content == null) {
            problems.add(path + " is missing.");
        }
        return content;
    }

    private static String describe(IOException e) {
        return e instanceof JacksonException jackson
                ? jackson.getOriginalMessage()
                        + (jackson.getLocation() == null
                                ? ""
                                : " (line %d, column %d)"
                                        .formatted(
                                                jackson.getLocation().getLineNr(),
                                                jackson.getLocation().getColumnNr()))
                : e.getMessage();
    }

    private static InvalidWorkspaceArchiveException invalid(String problem) {
        return new InvalidWorkspaceArchiveException(List.of(problem));
    }
}
