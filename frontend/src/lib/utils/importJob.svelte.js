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
import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
import { graphStore } from "$lib/stores/graphStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

import { ImportProgress } from "./importProgress.svelte.js";

const POLL_INTERVAL_MS = 300;
const MAX_FAILED_POLLS = 3;

const INVALID_WORKSPACE_NAME_CHARACTERS = /[/\\?#]/;

/** Opens the workspace an import wrote into, on its first imported graph. */
export function openImportedWorkspace(workspaceName, importedGraphUris) {
    editorState.selectedWorkspace.updateValue(workspaceName);
    editorState.selectedGraph.updateValue(importedGraphUris[0] || null);
    editorState.selectedDiagram.updateValue({ type: null, id: null });
    editorState.selectedClassWorkspace.updateValue(null);
    editorState.selectedClassGraph.updateValue(null);
    editorState.selectedClass.updateValue({ type: null, id: null });

    graphStore.invalidateWorkspace(workspaceName);
    workspaceStore.invalidate();
    crossProfileStore.invalidateWorkspace(workspaceName);
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
    if (INVALID_WORKSPACE_NAME_CHARACTERS.test(trimmed)) {
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

    #pollTimeout = null;
    #failedPolls = 0;
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
        this.#failedPolls = 0;

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
        this.#poll();
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
     * @returns {Promise<{workspaceName: string, state: string, importedGraphUris: string[]}|null>}
     *     how the import ended, `null` if there was none
     */
    async dismiss() {
        this.#stopPolling();
        const progress = this.progress;
        const jobId = this.jobId;
        const workspaceName = this.workspaceName;
        this.progress = null;
        this.jobId = null;
        this.workspaceName = null;

        if (!progress) {
            return null;
        }
        let status = {
            state: progress.jobState,
            importedGraphUris: progress.importedGraphUris,
        };
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
                status = data ?? status;
            }
        }
        return {
            workspaceName,
            state: status.state,
            importedGraphUris: status.importedGraphUris ?? [],
        };
    }

    /**
     * Polls the backend until the job is done, the prefix question included: polling is what tells
     * the backend somebody is still there to answer it. A single timeout is kept in flight, so
     * dismissing the import or finishing it ends the polling.
     */
    #poll() {
        this.#stopPolling();
        this.#pollTimeout = setTimeout(async () => {
            const progress = this.progress;
            const jobId = this.jobId;
            if (!progress || !jobId) {
                return;
            }

            const { data, error } = await graphStore.getImportStatus(
                this.workspaceName,
                jobId,
            );

            if (this.progress !== progress) {
                return;
            }
            if (error || !data) {
                // A single missed poll is not worth giving up on, the import keeps running.
                this.#failedPolls += 1;
                if (this.#failedPolls > MAX_FAILED_POLLS) {
                    progress.fail(
                        "The progress of the import could not be read.",
                    );
                    return;
                }
                this.#poll();
                return;
            }

            this.#failedPolls = 0;
            progress.apply(data);
            this.#onStatus(progress);
            if (!progress.finished) {
                this.#poll();
            }
        }, POLL_INTERVAL_MS);
    }

    #stopPolling() {
        if (this.#pollTimeout !== null) {
            clearTimeout(this.#pollTimeout);
            this.#pollTimeout = null;
        }
    }
}
