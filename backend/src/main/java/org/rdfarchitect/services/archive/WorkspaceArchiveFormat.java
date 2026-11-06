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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkspaceArchiveFormat {

    public static final String FORMAT_VERSION = "1.0";

    // Not .zip: the schema import unpacks every zip and would take layout.ttl for a schema.
    public static final String FILE_EXTENSION = ".rdfa";

    public static final String MANIFEST = "manifest.json";
    public static final String SCHEMA = "schema.ttl";
    public static final String SHACL = "shacl.ttl";
    public static final String LAYOUT = "layout.ttl";
    public static final String DIAGRAMS = "diagrams.json";
    public static final String COLORS = "colors.json";
    public static final String NAMESPACES = "namespaces.json";

    // Folder names come from graph URIs, so they get a directory of their own to never clash
    // with the files at the top level.
    private static final String GRAPHS_DIRECTORY = "graphs/";

    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS =
            Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    public static String graphEntry(String folder, String file) {
        return GRAPHS_DIRECTORY + folder + "/" + file;
    }

    public static String fileNameFor(String workspaceName) {
        var safeName = UNSAFE_FILE_NAME_CHARACTERS.matcher(workspaceName).replaceAll("_").strip();
        return (safeName.isEmpty() ? "workspace" : safeName) + FILE_EXTENSION;
    }
}
