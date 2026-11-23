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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which version of each workspace was last saved to a file, so the desktop app can warn
 * before work is lost.
 *
 * <p>The desktop backend holds every workspace in memory only and stops when its last window
 * closes, so a workspace that was changed since it was saved or opened is gone with it. A version
 * is a {@link WorkspaceRevision}: the change a workspace stands on in its history, and whether
 * editing is disabled. Undoing back to the saved version makes the workspace saved again, and a
 * workspace whose version cannot be read right now, because an import is writing to it, counts as
 * unsaved.
 *
 * <p>A workspace without any schema is never reported: there is nothing in it to lose. Versions are
 * kept per session and workspace name; a renamed workspace has to be saved again under its new
 * name, which is also what its file would say.
 */
public class SavedWorkspaces {

    private record Key(String sessionId, String workspaceName) {}

    private final DatabasePort databasePort;
    private final Map<Key, WorkspaceRevision> savedRevisions = new ConcurrentHashMap<>();

    public SavedWorkspaces(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /**
     * Records that the workspace was saved as it stood at the given revision.
     *
     * @param workspaceName the workspace, in the current session
     * @param revision where it stood when it was written to the file
     */
    public void markSaved(String workspaceName, WorkspaceRevision revision) {
        Objects.requireNonNull(revision, "revision");
        savedRevisions.put(key(workspaceName), revision);
    }

    /**
     * Records that the workspace was saved at the given change, for a file the browser wrote.
     * Whether editing is disabled is read now, moments after the browser wrote the file.
     *
     * @param workspaceName the workspace, in the current session
     * @param changeId the change it stood on when it was written to the file
     */
    public void markSaved(String workspaceName, UUID changeId) {
        Objects.requireNonNull(changeId, "changeId");
        markSaved(
                workspaceName,
                new WorkspaceRevision(changeId, databasePort.isReadOnly(workspaceName)));
    }

    /**
     * Records that the workspace as it stands now matches a file, for a workspace that was just
     * opened from one.
     *
     * @param workspaceName the workspace, in the current session
     * @return whether its version could be read; it cannot while the workspace is being written to
     */
    public boolean markSavedNow(String workspaceName) {
        var revision = WorkspaceRevision.current(databasePort, workspaceName);
        if (revision == null) {
            return false;
        }
        markSaved(workspaceName, revision);
        return true;
    }

    /** Returns the workspaces of the current session that hold changes no file has. */
    public List<String> unsavedWorkspaces() {
        return databasePort.listDatasets().stream().filter(this::isUnsaved).sorted().toList();
    }

    private boolean isUnsaved(String workspaceName) {
        try {
            var revision = WorkspaceRevision.current(databasePort, workspaceName);
            if (revision == null) {
                return true;
            }
            if (revision.equals(savedRevisions.get(key(workspaceName)))) {
                return false;
            }
            return !databasePort.listSchemaNames(workspaceName).isEmpty();
        } catch (ResourceNotFoundException _) {
            // Deleted since it was listed.
            return false;
        }
    }

    private static Key key(String workspaceName) {
        return new Key(SessionContext.getSessionId(), workspaceName);
    }
}
