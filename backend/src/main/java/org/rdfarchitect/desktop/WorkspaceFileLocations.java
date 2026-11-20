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

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.exception.database.ResourceNotFoundException;
import org.rdfarchitect.models.changelog.WorkspaceChangeLogEntry;

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
 * <p>A location is kept with the version of the workspace it was opened or saved at, and only holds
 * while that version is part of the workspace's history. So a workspace deleted and created anew
 * under the same name, whose history starts over, never writes over the file of the one before.
 */
public class WorkspaceFileLocations {

    private record Key(String sessionId, String workspaceName) {}

    private record Location(Path path, UUID version) {}

    private final DatabasePort databasePort;
    private final Map<Key, Location> locations = new ConcurrentHashMap<>();

    public WorkspaceFileLocations(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /** Records that the workspace, at the given version, is the content of the file. */
    public void remember(String workspaceName, Path path, UUID version) {
        locations.put(key(workspaceName), new Location(path, version));
    }

    /** Returns the file of the workspace, if it has one that still belongs to it. */
    public Optional<Path> locationOf(String workspaceName) {
        var key = key(workspaceName);
        var location = locations.get(key);
        if (location == null) {
            return Optional.empty();
        }
        if (!isInHistory(workspaceName, location.version())) {
            locations.remove(key, location);
            return Optional.empty();
        }
        return Optional.of(location.path());
    }

    /**
     * Returns the workspaces of the current session that were given a file. Not checked against
     * their history, which would wait for every write to the workspace, so {@link #locationOf} can
     * still find one of them stale.
     */
    public List<String> workspacesWithFile() {
        var sessionId = SessionContext.getSessionId();
        return locations.keySet().stream()
                .filter(key -> key.sessionId().equals(sessionId))
                .map(Key::workspaceName)
                .sorted()
                .toList();
    }

    private boolean isInHistory(String workspaceName, UUID version) {
        try {
            return databasePort.listChanges(workspaceName).stream()
                    .map(WorkspaceChangeLogEntry::changeId)
                    .anyMatch(version::equals);
        } catch (ResourceNotFoundException _) {
            return false;
        }
    }

    private static Key key(String workspaceName) {
        return new Key(SessionContext.getSessionId(), workspaceName);
    }
}
