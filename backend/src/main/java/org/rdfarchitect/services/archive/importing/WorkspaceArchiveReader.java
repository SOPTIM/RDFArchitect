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

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.io.FileUtils;
import org.rdfarchitect.database.SchemaNames;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.exception.graph.InvalidSchemaNameException;
import org.rdfarchitect.services.archive.FormatVersion;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.WorkspaceArchiveManifest;
import org.rdfarchitect.services.archive.parts.GraphScope;
import org.rdfarchitect.services.archive.parts.PartReader;
import org.rdfarchitect.services.archive.parts.ReadPart;
import org.rdfarchitect.services.archive.parts.WorkspaceFileParts;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * Reads a workspace file and checks it as a whole: the manifest, then every part of every schema
 * and of the workspace. Every problem found is reported at once, nothing is stored.
 */
public final class WorkspaceArchiveReader {

    // As many as the schema import takes from a zip.
    static final int MAX_ENTRIES = 1000;
    static final long MAX_UNPACKED_SIZE = FileUtils.ONE_GB;

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
                    throw invalid("The workspace file holds more than " + MAX_ENTRIES + " files.");
                }
                var content = zip.readNBytes((int) Math.min(remaining, Integer.MAX_VALUE - 8));
                if (zip.read() != -1) {
                    throw invalid(
                            "The workspace file unpacks to more than %d MB."
                                    .formatted(maxUnpackedSize / FileUtils.ONE_MB));
                }
                remaining -= content.length;
                if (entries.put(entry.getName(), content) != null) {
                    throw invalid(entry.getName() + ": the workspace file holds this file twice.");
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

        var schemaNames = readSchemaNames(manifest.schemas());
        var in = new PartReader(entries, schemaNames, problems);
        var graphs = new ArrayList<WorkspaceArchive.ArchivedGraph>();
        for (var schemaName : schemaNames) {
            var schemaIn = in.inSchema(schemaName);
            graphs.add(
                    new WorkspaceArchive.ArchivedGraph(
                            schemaName,
                            WorkspaceFileParts.OF_GRAPH.stream()
                                    .<ReadPart<GraphScope, ?>>map(
                                            part -> ReadPart.read(part, schemaIn))
                                    .toList()));
        }
        var workspaceParts =
                WorkspaceFileParts.OF_WORKSPACE.stream()
                        .<ReadPart<WorkspaceTransaction, ?>>map(part -> ReadPart.read(part, in))
                        .toList();

        if (!problems.isEmpty()) {
            throw new InvalidWorkspaceArchiveException(problems);
        }
        return new WorkspaceArchive(manifest.readOnly(), graphs, workspaceParts, notices);
    }

    private WorkspaceArchiveManifest readManifest() {
        if (!entries.containsKey(WorkspaceArchiveFormat.MANIFEST)) {
            problems.add(
                    WorkspaceArchiveFormat.MANIFEST
                            + " is missing, so this is not a workspace file. Schema files and"
                            + " plain zip archives of them are imported as schemas instead.");
            return null;
        }
        return new PartReader(entries, Set.of(), problems)
                .readJson(WorkspaceArchiveFormat.MANIFEST, new TypeReference<>() {});
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
                    "The workspace file was written in format %s%s. This version of RDFArchitect reads format %s."
                            .formatted(version, writtenBy, current));
            return false;
        }
        if (version.minor() > current.minor()) {
            notices.add(
                    ("The workspace file was written in format %s by a newer version of RDFArchitect%s."
                                    + " Anything this version (format %s) does not know about was"
                                    + " left out.")
                            .formatted(version, writtenBy, current));
        }
        return true;
    }

    /** The schemas the manifest lists, without the ones whose name is invalid or taken twice. */
    private Set<String> readSchemaNames(List<String> listed) {
        var schemaNames = new LinkedHashSet<String>();
        for (var schemaName : listed) {
            var where = WorkspaceArchiveFormat.MANIFEST + ": schema \"" + schemaName + "\"";
            if (!isValidSchemaName(schemaName)) {
                problems.add(where + ": not a valid schema name.");
            } else if (!schemaNames.add(schemaName)) {
                problems.add(where + ": listed twice.");
            }
        }
        return schemaNames;
    }

    private static boolean isValidSchemaName(String name) {
        try {
            return SchemaNames.validate(name).equals(name);
        } catch (InvalidSchemaNameException _) {
            return false;
        }
    }

    private static InvalidWorkspaceArchiveException invalid(String problem) {
        return new InvalidWorkspaceArchiveException(List.of(problem));
    }
}
