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

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { graphStore } from "$lib/stores/graphStore.ts";
import { workspaceArchiveMediaType } from "$lib/utils/fileUtils";
import { openImportedWorkspace } from "$lib/utils/importJob.svelte.js";
import { JobState } from "$lib/utils/importProgress.svelte.js";

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

function recoveryUrl(path = "") {
    return `${PUBLIC_BACKEND_URL}/api/desktop/recovery${path}`;
}

/**
 * The workspaces kept from the previous run, newest first.
 *
 * @returns {Promise<{id: string, workspaceName: string, savedAt: string}[]>}
 */
export async function listRecoveredWorkspaces() {
    const response = await fetch(recoveryUrl(), { credentials: "include" });
    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
    }
    return response.json();
}

/** Removes a kept workspace for good. */
export async function discardRecoveredWorkspace(id) {
    const response = await fetch(recoveryUrl(`/${encodeURIComponent(id)}`), {
        method: "DELETE",
        credentials: "include",
    });
    if (!response.ok && response.status !== 404) {
        throw new Error(`${response.status} ${response.statusText}`);
    }
}

/** A name for the restored workspace that none of the existing ones has. */
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
 * Restores a kept workspace as a new workspace and removes the copy once that worked. The
 * workspace stays unsaved: no file of the user's holds it.
 *
 * @param {{id: string, workspaceName: string}} recovered
 * @param {string[]} existingNames the workspaces there are, so the restored one gets a name of its own
 * @returns {Promise<string>} the name the workspace was restored under
 */
export async function restoreRecoveredWorkspace(recovered, existingNames) {
    const response = await fetch(
        recoveryUrl(`/${encodeURIComponent(recovered.id)}`),
        { credentials: "include" },
    );
    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
    }
    const name = restoredName(recovered.workspaceName, existingNames);
    const file = new File(
        [await response.blob()],
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
    openImportedWorkspace(name, status.importedGraphUris ?? []);
    await discardRecoveredWorkspace(recovered.id);
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
