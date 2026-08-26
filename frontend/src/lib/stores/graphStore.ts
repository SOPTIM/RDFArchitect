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
    replaceGraphs,
    replaceGraph,
    renameGraph as sdkRenameGraph,
    type GraphBulkImportResponse,
    type ImportWarning,
    type GraphDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type WorkspaceState = {
    graphs: Map<string, AsyncListSlot<GraphDto>>;
};

const LOG_PREFIX = "[graphStore]";

export const graphStore = createGraphStore();

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

    async function importGraphs(
        workspaceName: string,
        files: File[],
        graphUris: string[],
    ): Promise<Result<GraphBulkImportResponse>> {
        console.log(
            `${LOG_PREFIX} Importing graphs into workspace "${workspaceName}"`,
        );

        if (!files || files.length === 0) {
            const error = new Error(
                "At least one file is required for import.",
            );
            console.error(`${LOG_PREFIX} ${error.message}`);
            toastStore.error("Import failed", "No files were selected.");
            return { error };
        }

        const { data, error } = await replaceGraphs({
            path: { datasetName: workspaceName },
            body: {
                files: files,
            },
            query: { graphUris },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Failed to import graphs`,
                await describeError(error),
            );
            toastStore.error(
                "Import failed",
                `Could not import into "${workspaceName}".`,
            );
            return { error };
        }

        invalidateWorkspace(workspaceName);

        const importedGraphUris = data?.importedGraphUris ?? [];
        const failedImports = data?.failedImports ?? [];
        const importedCount = importedGraphUris.length;

        if (importedCount === 0) {
            toastStore.error(
                "Import failed",
                failedImports.length > 0
                    ? `${failedImports.length} file(s) could not be imported.`
                    : "No schemas were imported.",
            );
            return { error: null, data };
        }

        if (failedImports.length > 0) {
            toastStore.warning(
                "Import partially succeeded",
                `${importedCount} graph(s) imported, ${failedImports.length} file(s) skipped.`,
            );
        } else {
            toastStore.success(
                "Import complete",
                `${importedCount} graph${importedCount === 1 ? "" : "s"} imported.`,
            );
        }

        notifyUndisplayableProperties(data?.warnings ?? []);

        return { error: null, data };
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
        importGraphs,
        remove: removeGraph,
        invalidateWorkspace,
    };
}

function notifyUndisplayableProperties(warnings: ImportWarning[]) {
    if (warnings.length === 0) return;

    const total = warnings.reduce(
        (sum, w) => sum + (w.undisplayableProperties?.length ?? 0),
        0,
    );
    if (total === 0) return;

    const details = warnings
        .map(
            w =>
                `${w.fileName}: ${(w.undisplayableProperties ?? []).join(", ")}`,
        )
        .join("; ");

    toastStore.warning(
        "Some properties could not be displayed",
        `${total} propert${total === 1 ? "y" : "ies"} ${
            total === 1 ? "is" : "are"
        } missing the CIM stereotype or association metadata RDFArchitect needs to show ${
            total === 1 ? "it" : "them"
        } (${details}).`,
    );
}
export { createGraphStore };
