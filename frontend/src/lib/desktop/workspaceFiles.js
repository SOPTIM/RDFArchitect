/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/**
 * The file each workspace was opened from or last saved to, as a File System Access handle, so
 * Save can write to it again without asking. Only where the browser has that API (Chromium); kept
 * in memory, so a reload asks once more.
 *
 * Keyed by workspace name. A workspace that is deleted or created anew under a name must not
 * inherit the file of the one before, so the workspace store forgets the name on both.
 */
const handles = new Map();

export const workspaceFiles = {
    /** @returns {FileSystemFileHandle | undefined} */
    get(workspaceName) {
        return handles.get(workspaceName);
    },

    remember(workspaceName, handle) {
        if (handle) {
            handles.set(workspaceName, handle);
        }
    },

    forget(workspaceName) {
        handles.delete(workspaceName);
    },

    rename(workspaceName, newName) {
        const handle = handles.get(workspaceName);
        handles.delete(workspaceName);
        handles.delete(newName);
        if (handle) {
            handles.set(newName, handle);
        }
    },

    /** The name of the file the workspace is saved in, if it has one. */
    fileName(workspaceName) {
        return handles.get(workspaceName)?.name ?? null;
    },
};
