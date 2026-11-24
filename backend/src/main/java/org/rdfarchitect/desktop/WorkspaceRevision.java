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

import java.util.UUID;

/**
 * Where a workspace stands, as far as a workspace file is concerned: the change it stands on in its
 * history, and whether editing is disabled, which the file records too and the history does not.
 * Two equal revisions write the same file.
 *
 * @param changeId the change the workspace stands on, see {@link DatabasePort#currentChangeId}
 * @param readOnly whether editing is disabled
 */
public record WorkspaceRevision(UUID changeId, boolean readOnly) {

    private static final String READ_ONLY = ":read-only";
    private static final String EDITABLE = ":editable";

    /**
     * Returns where the workspace stands now.
     *
     * @return the revision, or {@code null} while another thread is writing to the workspace
     */
    public static WorkspaceRevision current(DatabasePort databasePort, String workspaceName) {
        var changeId = databasePort.currentChangeId(workspaceName);
        if (changeId == null) {
            return null;
        }
        return new WorkspaceRevision(changeId, databasePort.isReadOnly(workspaceName));
    }

    /**
     * Reads a revision back from its {@link #version()}.
     *
     * @param version the text {@link #version()} returned
     * @return the revision
     * @throws IllegalArgumentException if the text is no version
     */
    public static WorkspaceRevision ofVersion(String version) {
        if (version.endsWith(READ_ONLY)) {
            return new WorkspaceRevision(changeIdOf(version, READ_ONLY), true);
        }
        if (version.endsWith(EDITABLE)) {
            return new WorkspaceRevision(changeIdOf(version, EDITABLE), false);
        }
        throw new IllegalArgumentException("Not a workspace version: " + version);
    }

    /** Returns the revision as text, which a window passes back once it has saved the file. */
    public String version() {
        return changeId + (readOnly ? READ_ONLY : EDITABLE);
    }

    private static UUID changeIdOf(String version, String suffix) {
        return UUID.fromString(version.substring(0, version.length() - suffix.length()));
    }
}
