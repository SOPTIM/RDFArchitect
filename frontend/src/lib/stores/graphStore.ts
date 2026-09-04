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

import { writable } from "svelte/store";

import { loadSlot } from "./storeHelpers";
import { describeError } from "./storeLogging";
import {
    type AsyncListSlot,
    createEmptyListSlot,
    type Result,
} from "./storeTypes";
import {
    listGraphs,
    deleteGraph,
    replaceGraph,
    renameGraph as sdkRenameGraph,
    cancelImport as sdkCancelImport,
    getImportStatus as sdkGetImportStatus,
    type GraphDto,
    type ImportJobStatus,
} from "../api/generated";
import { PUBLIC_BACKEND_URL } from "../config/runtime";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type WorkspaceState = {
    graphs: Map<string, AsyncListSlot<GraphDto>>;
};

const LOG_PREFIX = "[graphStore]";

export const graphStore = createGraphStore();

/** Status codes the import endpoint answers with, mapped to what went wrong. */
const IMPORT_START_MESSAGES: Record<number, string> = {
    409: "Another import is still running. Wait for it to finish and try again.",
};

function createGraphStore() {
    const store = writable<WorkspaceState>({
        graphs: new Map(),
    });

    const { subscribe, update } = store;

    function getWorkspaceState(
        state: WorkspaceState,
        workspaceName: string,
    ): AsyncListSlot<GraphDto> {
        return state.graphs.get(workspaceName) ?? createEmptyListSlot();
    }

    function setWorkspaceState(
        state: WorkspaceState,
        workspaceName: string,
        next: AsyncListSlot<GraphDto>,
    ): WorkspaceState {
        const byWorkspace = new Map(state.graphs);
        byWorkspace.set(workspaceName, next);
        return { ...state, graphs: byWorkspace };
    }

    async function getGraphs(
        workspaceName: string,
        force = false,
    ): Promise<GraphDto[] | null> {
        if (!workspaceName) return null;
        return loadSlot(
            store,
            s => getWorkspaceState(s, workspaceName),
            (s, patch) =>
                setWorkspaceState(s, workspaceName, {
                    ...getWorkspaceState(s, workspaceName),
                    ...patch,
                }),
            () => listGraphs({ path: { datasetName: workspaceName } }),
            LOG_PREFIX,
            `graphs for workspace="${workspaceName}"`,
            force,
        );
    }

    async function addEmptyGraph(
        workspaceName: string,
        graphURI: string,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Adding empty graph "${graphURI}" to workspace "${workspaceName}"`,
        );

        const { error } = await replaceGraph({
            path: { datasetName: workspaceName, graphURI },
        });

        if (error) {
            console.error(
                `Failed to create empty graph "${graphURI}" to workspace "${workspaceName}"`,
            );
            toastStore.error(
                "Create failed",
                `Could not create schema "${graphURI}".`,
            );
            return { error };
        }

        invalidateWorkspace(workspaceName);
        console.log(`${LOG_PREFIX} Added empty graph "${graphURI}"`);
        toastStore.success(
            "Schema created",
            `"${graphURI}" was added to "${workspaceName}".`,
        );
        return { error: null };
    }

    /**
     * Starts an import and returns the id of the job that runs it. The import itself happens in the
     * background; follow it with {@link getImportStatus} and stop it with {@link cancelImport}.
     *
     * Uploading is done with an XMLHttpRequest rather than the generated client, because that is the
     * only way to report how much of the upload has gone through.
     */
    async function startImport(
        workspaceName: string,
        files: File[],
        graphUris: string[],
        options: {
            onUploadProgress?: (percent: number) => void;
            signal?: AbortSignal;
        } = {},
    ): Promise<Result<{ jobId: string }>> {
        console.log(
            `${LOG_PREFIX} Starting import into workspace "${workspaceName}"`,
        );

        if (!files || files.length === 0) {
            const error = new Error(
                "At least one file is required for import.",
            );
            console.error(`${LOG_PREFIX} ${error.message}`);
            return { error };
        }

        try {
            const jobId = await uploadImport(
                workspaceName,
                files,
                graphUris,
                options,
            );
            console.log(`${LOG_PREFIX} Import job "${jobId}" started`);
            return { error: null, data: { jobId } };
        } catch (error) {
            console.error(`${LOG_PREFIX} Failed to start the import`, error);
            return { error };
        }
    }

    async function getImportStatus(
        workspaceName: string,
        jobId: string,
    ): Promise<Result<ImportJobStatus>> {
        const { data, error } = await sdkGetImportStatus({
            path: { datasetName: workspaceName, jobId },
        });

        if (error || !data) {
            console.error(
                `${LOG_PREFIX} Failed to read the status of import job "${jobId}"`,
                await describeError(error),
            );
            return { error: error ?? new Error("Import job is unknown.") };
        }

        return { error: null, data };
    }

    async function cancelImport(
        workspaceName: string,
        jobId: string,
    ): Promise<Result> {
        console.log(`${LOG_PREFIX} Cancelling import job "${jobId}"`);

        const { error } = await sdkCancelImport({
            path: { datasetName: workspaceName, jobId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Failed to cancel import job "${jobId}"`,
                await describeError(error),
            );
            return { error };
        }

        return { error: null };
    }

    async function renameGraph(
        workspaceName: string,
        oldGraphURI: string,
        newGraphURI: string,
        newKeyword: string | null = null,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Renaming graph "${oldGraphURI}" to "${newGraphURI}" in workspace "${workspaceName}"`,
        );

        const { error } = await sdkRenameGraph({
            path: { datasetName: workspaceName, graphURI: oldGraphURI },
            query: {
                newGraphURI,
                ...(newKeyword !== null ? { newKeyword } : {}),
            },
        });

        if (error) {
            const isConflict = (error as { status?: number })?.status === 409;
            console.error(
                `${LOG_PREFIX} Failed to rename graph "${oldGraphURI}"`,
                await describeError(error),
            );
            toastStore.error(
                "Rename failed",
                isConflict
                    ? `A schema with the uri "${newGraphURI}" already exists.`
                    : `Could not rename schema "${oldGraphURI}".`,
            );
            return { error };
        }

        invalidateWorkspace(workspaceName);
        console.log(
            `${LOG_PREFIX} Renamed graph "${oldGraphURI}" to "${newGraphURI}"`,
        );
        toastStore.success(
            "Schema renamed",
            `"${oldGraphURI}" is now "${newGraphURI}".`,
        );
        return { error: null };
    }

    async function removeGraph(
        workspaceName: string,
        graphURI: string,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Deleting graph "${graphURI}" from workspace "${workspaceName}"`,
        );

        const { error } = await deleteGraph({
            path: { datasetName: workspaceName, graphURI },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not delete graph "${graphURI}" from workspace "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                "Delete failed",
                `Could not delete schema "${graphURI}".`,
            );
            return { error };
        }

        update(s => {
            const dsState = getWorkspaceState(s, workspaceName);
            if (!dsState) return s;
            const nextData =
                dsState.data?.filter(g => {
                    const uri = `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}`;
                    return uri !== graphURI;
                }) ?? null;

            return setWorkspaceState(s, workspaceName, {
                ...dsState,
                data: nextData,
            });
        });

        console.log(
            `${LOG_PREFIX} Deleted graph "${graphURI}" from workspace "${workspaceName}"`,
        );
        toastStore.success("Schema deleted", `"${graphURI}" was removed.`);

        return { error: null };
    }

    function invalidateWorkspace(workspaceName: string) {
        update(s => {
            const byWorkspace = new Map(s.graphs);
            byWorkspace.delete(workspaceName);
            return { ...s, graphs: byWorkspace };
        });
    }

    return {
        subscribe,
        getGraphs,
        addEmptyGraph,
        renameGraph,
        startImport,
        getImportStatus,
        cancelImport,
        remove: removeGraph,
        invalidateWorkspace,
    };
}

/**
 * Uploads the files of an import and resolves with the id of the job that was started.
 *
 * @throws Error carrying the http status of the failed request in `status`
 */
function uploadImport(
    workspaceName: string,
    files: File[],
    graphUris: string[],
    options: {
        onUploadProgress?: (percent: number) => void;
        signal?: AbortSignal;
    },
): Promise<string> {
    const query = graphUris
        .map(graphUri => `graphUris=${encodeURIComponent(graphUri ?? "")}`)
        .join("&");
    const url =
        `${PUBLIC_BACKEND_URL}/api/datasets/${encodeURIComponent(workspaceName)}` +
        `/graphs/content/imports${query ? `?${query}` : ""}`;

    const formData = new FormData();
    for (const file of files) {
        formData.append("files", file);
    }

    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open("POST", url);
        request.withCredentials = true;
        request.responseType = "json";

        request.upload.onprogress = event => {
            if (event.lengthComputable) {
                options.onUploadProgress?.((event.loaded / event.total) * 100);
            }
        };
        request.onload = () => {
            const jobId = request.response?.jobId;
            if (request.status === 202 && jobId) {
                resolve(jobId);
                return;
            }
            reject(importStartError(request.status, request.response));
        };
        request.onerror = () =>
            reject(new Error("The import could not be reached."));
        request.onabort = () => reject(new Error("The upload was cancelled."));

        options.signal?.addEventListener("abort", () => request.abort(), {
            once: true,
        });
        request.send(formData);
    });
}

function importStartError(
    status: number,
    response: unknown,
): Error & { status: number } {
    const detail = (response as { detail?: string } | null)?.detail;
    const error = new Error(
        IMPORT_START_MESSAGES[status] ??
            detail ??
            "The import could not be started.",
    ) as Error & { status: number };
    error.status = status;
    return error;
}

export { createGraphStore };
