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

import { graphStore } from "$lib/stores/graphStore.ts";
import { workspaceArchiveMediaType } from "$lib/utils/fileUtils";
import { openImportedWorkspace } from "$lib/utils/importJob.svelte.js";
import { JobState } from "$lib/utils/importProgress.svelte.js";

import { fetchRecoveredWorkspace, markRestoredFrom } from "./desktopApi.js";

/**
 * Restoring the workspaces the desktop app kept when it stopped with unsaved changes (see
 * `WorkspaceRecovery`). A kept workspace is a workspace file, restored through the same import as
 * one the user opens.
 */

const POLL_INTERVAL_MS = 300;
const FINISHED = new Set([
    JobState.COMPLETED,
    JobState.CANCELLED,
    JobState.FAILED,
]);

/**
 * A name for the restored workspace that none of the existing ones has.
 *
 * @param {string} workspaceName the name the workspace had
 * @param {string[]} existingNames the names of the workspaces there are
 * @returns {string}
 */
export function restoredName(workspaceName, existingNames) {
    const taken = new Set(existingNames);
    if (!taken.has(workspaceName)) {
        return workspaceName;
    }
    for (let n = 1; ; n++) {
        const candidate =
            n === 1
                ? `${workspaceName} (recovered)`
                : `${workspaceName} (recovered ${n})`;
        if (!taken.has(candidate)) {
            return candidate;
        }
    }
}

/**
 * Restores a kept workspace as a new workspace and has the copy removed once the app keeps the
 * restored one. The workspace stays unsaved: no file of the user's holds it. A copy that cannot be
 * removed is offered again at the next start.
 *
 * @param {{id: string, workspaceName: string}} recovered
 * @param {string[]} existingNames the workspaces there are, so the restored one gets a name of its own
 * @returns {Promise<string>} the name the workspace was restored under
 * @throws {RecoveredWorkspaceGoneError} if the copy is gone, restored or discarded in another window
 */
export async function restoreRecoveredWorkspace(recovered, existingNames) {
    const content = await fetchRecoveredWorkspace(recovered.id);
    if (!content) {
        throw new RecoveredWorkspaceGoneError();
    }
    const name = restoredName(recovered.workspaceName, existingNames);
    const file = new File(
        [content],
        `${name}${workspaceArchiveMediaType.fileExtension}`,
        { type: workspaceArchiveMediaType.mimeType },
    );

    const started = await graphStore.startWorkspaceImport(name, file);
    if (started.error || !started.data) {
        throw started.error ?? new Error("The import could not be started.");
    }
    const status = await awaitImport(name, started.data.jobId);
    if (status.state !== JobState.COMPLETED) {
        throw new Error(
            status.errorMessage ??
                status.problems?.join(" ") ??
                `The import ended as ${status.state.toLowerCase()}.`,
        );
    }
    openImportedWorkspace(name, status.importedSchemaNames ?? []);
    try {
        await markRestoredFrom(name, recovered.id);
    } catch (error) {
        console.warn(
            `[recovery] Restored "${name}", but could not remove its copy`,
            error,
        );
    }
    return name;
}

async function awaitImport(workspaceName, jobId) {
    for (;;) {
        await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
        const { data, error } = await graphStore.getImportStatus(
            workspaceName,
            jobId,
        );
        if (error) {
            throw error;
        }
        if (FINISHED.has(data.state)) {
            return data;
        }
        if (data.state === JobState.AWAITING_PREFIX_RESOLUTION) {
            // A workspace of its own has no prefixes to clash with; nobody is here to answer.
            await graphStore.cancelImport(workspaceName, jobId);
            throw new Error("The import asked about namespace prefixes.");
        }
    }
}

/** Thrown when a kept workspace is gone, restored or discarded in another window. */
export class RecoveredWorkspaceGoneError extends Error {
    constructor() {
        super("The kept workspace is gone.");
        this.name = "RecoveredWorkspaceGoneError";
    }
}
