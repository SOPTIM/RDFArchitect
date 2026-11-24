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

package org.rdfarchitect.desktop;

import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.database.ResourceNotFoundException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The file on disk each workspace was opened from or saved to by the backend itself, so that Save
 * can write to it again. The browser can only do that for a file it chose itself (the File System
 * Access API); a file the operating system handed over, by a double-click, is known here instead.
 *
 * <p>A location belongs to the workspace, not to its name: it is kept by {@link
 * DatabasePort#workspaceId}, so it stays through every change and rename, and a workspace deleted
 * and created anew under the same name never writes over the file of the one before.
 */
public class WorkspaceFileLocations {

    private final DatabasePort databasePort;
    private final Map<UUID, Path> locations = new ConcurrentHashMap<>();

    public WorkspaceFileLocations(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /** Records that the workspace is the content of the file. */
    public void remember(String workspaceName, Path path) {
        locations.put(databasePort.workspaceId(workspaceName), path);
    }

    /** Forgets the file of the workspace, which was saved to or opened from another one. */
    public void forget(String workspaceName) {
        idOf(workspaceName).ifPresent(locations::remove);
    }

    /** Returns the file of the workspace, if it has one. */
    public Optional<Path> locationOf(String workspaceName) {
        return idOf(workspaceName).map(locations::get);
    }

    /** Returns the workspaces of the current session that have a file. */
    public List<String> workspacesWithFile() {
        return databasePort.listDatasets().stream()
                .filter(
                        workspaceName ->
                                idOf(workspaceName).map(locations::containsKey).orElse(false))
                .sorted()
                .toList();
    }

    private Optional<UUID> idOf(String workspaceName) {
        try {
            return Optional.of(databasePort.workspaceId(workspaceName));
        } catch (ResourceNotFoundException _) {
            return Optional.empty();
        }
    }
}
