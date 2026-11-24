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
 * Kept by the workspace's id from the backend (`desktopSession.workspaceIds`), not its name: the
 * id stays through renames, and a workspace deleted and created anew under the same name, in this
 * window or another, gets a new one and so never inherits the file of the one before.
 */
const handles = new Map();

export const workspaceFiles = {
    /**
     * Returns the file of the workspace.
     *
     * @param {string | null | undefined} workspaceId
     * @returns {FileSystemFileHandle | undefined}
     */
    get(workspaceId) {
        return workspaceId ? handles.get(workspaceId) : undefined;
    },

    /**
     * Records the file the workspace was opened from or saved to.
     *
     * @param {string | null | undefined} workspaceId
     * @param {FileSystemFileHandle | null} handle
     */
    remember(workspaceId, handle) {
        if (workspaceId && handle) {
            handles.set(workspaceId, handle);
        }
    },
};
