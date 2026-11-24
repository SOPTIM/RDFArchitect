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

import { editorState, forceReloadTrigger } from "$lib/sharedState.svelte.js";
import { graphStore } from "$lib/stores/graphStore.ts";
import { versionControlStore } from "$lib/stores/versionControlStore.ts";
import { workspaceState } from "$lib/workspaceState.svelte.js";

import {
    ImportProgress,
    importedSchemaNamesOf,
} from "./importProgress.svelte.js";
import { ImportStatusPoller } from "./importStatusPoller.js";
import { hasInvalidCharacters } from "./workspaceName.js";

/**
 * Opens the workspace an import wrote into, on its first imported graph.
 *
 * Everything cached under the workspace's name is dropped, since a new workspace can take the name
 * of one that was renamed or deleted, and the caches are kept by name.
 */
export function openImportedWorkspace(workspaceName, importedSchemaNames) {
    versionControlStore.invalidateWorkspace(workspaceName);
    workspaceState.activate(workspaceName);

    editorState.selectedGraph.updateValue(importedSchemaNames[0] || null);
    editorState.selectedDiagram.updateValue({ type: null, id: null });
    editorState.selectedClassWorkspace.updateValue(null);
    editorState.selectedClassGraph.updateValue(null);
    editorState.selectedClass.updateValue({ type: null, id: null });
    forceReloadTrigger.trigger();
}

/** Why the name cannot be that of a new workspace, empty while it can. */
export function newWorkspaceNameError(name, existingNames) {
    const trimmed = name.trim();
    if (trimmed === "") {
        return "Enter a name for the new workspace";
    }
    if (existingNames.includes(trimmed)) {
        return `A workspace named "${trimmed}" already exists`;
    }
    if (hasInvalidCharacters(trimmed)) {
        return "Name must not contain / \\ ? or #";
    }
    return "";
}

/**
 * Runs the import jobs of one dialog, one at a time: uploads the files, polls the backend until
 * the job is done and passes on a cancellation.
 */
export class ImportJobRunner {
    /** Set while an import runs and until its result is dismissed; drives the progress panel. */
    progress = $state(null);
    /** The workspace the running import writes into. */
    workspaceName = null;
    jobId = null;

    #poller = new ImportStatusPoller();
    #onStatus;

    /** @param {(progress: ImportProgress) => void} [onStatus] called with every status polled */
    constructor(onStatus = () => {}) {
        this.#onStatus = onStatus;
    }

    /**
     * Starts an import into the workspace and follows it.
     *
     * @param {string} workspaceName
     * @param {(options: {onUploadProgress: (percent: number) => void, signal: AbortSignal}) =>
     *     Promise<{data?: {jobId: string}, error?: Error|null}>} upload starts the job
     */
    async start(workspaceName, upload) {
        if (this.progress !== null) {
            return;
        }
        const progress = new ImportProgress();
        this.progress = progress;
        this.workspaceName = workspaceName;

        const { data, error } = await upload({
            onUploadProgress: percent => progress.uploadProgress(percent),
            signal: progress.signal,
        });
        if (this.progress !== progress) {
            // Dismissed while the files were still going up, so nobody follows the job any more.
            if (data) {
                await graphStore.cancelImport(workspaceName, data.jobId);
            }
            return;
        }
        if (error || !data) {
            if (progress.cancelling) {
                progress.cancelled();
            } else {
                progress.fail(
                    error?.message ?? "The import could not be started.",
                );
            }
            return;
        }

        progress.uploaded();
        this.jobId = data.jobId;
        if (progress.cancelling) {
            // Cancelled while the files were still going up, so the job needs to hear about it.
            await graphStore.cancelImport(workspaceName, data.jobId);
        }
        this.#follow(progress);
    }

    cancel() {
        this.progress?.cancel();
        if (this.workspaceName && this.jobId) {
            void graphStore.cancelImport(this.workspaceName, this.jobId);
        }
    }

    /**
     * Ends the dialog's part in the current import: cancels it while it still runs and forgets
     * it. Cancelling only stops the job where it is, so what it already wrote is still reported.
     *
     * @returns {Promise<{workspaceName: string, state: string, importedSchemaNames: string[]}|null>}
     *     how the import ended, `null` if there was none
     */
    async dismiss() {
        this.#poller.stop();
        const progress = this.progress;
        const jobId = this.jobId;
        const workspaceName = this.workspaceName;
        this.progress = null;
        this.jobId = null;
        this.workspaceName = null;

        if (!progress) {
            return null;
        }
        let state = progress.jobState;
        let importedSchemaNames = progress.finished
            ? progress.importedSchemaNames
            : importedSchemaNamesOf({ files: progress.files });
        if (!progress.finished) {
            progress.cancel();
            if (workspaceName && jobId) {
                await graphStore.cancelImport(workspaceName, jobId);
                // The last poll can predate the file the job was on when it was told to stop, so
                // the status is read once more to catch what still made it through.
                const { data } = await graphStore.getImportStatus(
                    workspaceName,
                    jobId,
                );
                if (data) {
                    state = data.state;
                    importedSchemaNames = importedSchemaNamesOf(data);
                }
            }
        }
        return { workspaceName, state, importedSchemaNames };
    }

    /**
     * Follows the job until it is done, the prefix question included: polling is what tells the
     * backend somebody is still there to answer it.
     */
    #follow(progress) {
        const workspaceName = this.workspaceName;
        const jobId = this.jobId;
        this.#poller.follow(
            () => graphStore.getImportStatus(workspaceName, jobId),
            progress,
            () => this.#onStatus(progress),
        );
    }
}
