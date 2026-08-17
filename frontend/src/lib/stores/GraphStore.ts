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
import { describeError } from "./StoreLogging";
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
    type GraphBulkImportResponse,
    type ImportWarning,
    type GraphDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type DatasetState = {
    graphs: Map<string, AsyncListSlot<GraphDto>>;
};

const LOG_PREFIX = "[graphStore]";

export const graphStore = createGraphStore();

function createGraphStore() {
    const store = writable<DatasetState>({
        graphs: new Map(),
    });

    const { subscribe, update } = store;

    function getDatasetState(
        state: DatasetState,
        datasetName: string,
    ): AsyncListSlot<GraphDto> {
        return state.graphs.get(datasetName) ?? createEmptyListSlot();
    }

    function setDatasetState(
        state: DatasetState,
        datasetName: string,
        next: AsyncListSlot<GraphDto>,
    ): DatasetState {
        const byDataset = new Map(state.graphs);
        byDataset.set(datasetName, next);
        return { ...state, graphs: byDataset };
    }

    async function getGraphs(
        datasetName: string,
        force = false,
    ): Promise<GraphDto[] | null> {
        if (!datasetName) return null;
        return loadSlot(
            store,
            s => getDatasetState(s, datasetName),
            (s, patch) =>
                setDatasetState(s, datasetName, {
                    ...getDatasetState(s, datasetName),
                    ...patch,
                }),
            () => listGraphs({ path: { datasetName } }),
            LOG_PREFIX,
            `graphs for dataset="${datasetName}"`,
            force,
        );
    }

    async function addEmptyGraph(
        datasetName: string,
        graphURI: string,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Adding empty graph "${graphURI}" to dataset "${datasetName}"`,
        );

        const { error } = await replaceGraph({
            path: { datasetName, graphURI },
        });

        if (error) {
            console.error(
                `Failed to create empty graph "${graphURI}" to dataset "${datasetName}"`,
            );
            toastStore.error(
                "Create failed",
                `Could not create schema "${graphURI}".`,
            );
            return { error };
        }

        invalidateDataset(datasetName);
        console.log(`${LOG_PREFIX} Added empty graph "${graphURI}"`);
        toastStore.success(
            "Schema created",
            `"${graphURI}" was added to "${datasetName}".`,
        );
        return { error: null };
    }

    async function importGraphs(
        datasetName: string,
        files: File[],
        graphUris: string[],
    ): Promise<Result<GraphBulkImportResponse>> {
        console.log(
            `${LOG_PREFIX} Importing graphs into dataset "${datasetName}"`,
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
            path: { datasetName },
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
                `Could not import into "${datasetName}".`,
            );
            return { error };
        }

        invalidateDataset(datasetName);

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

    async function removeGraph(
        datasetName: string,
        graphURI: string,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Deleting graph "${graphURI}" from dataset "${datasetName}"`,
        );

        const { error } = await deleteGraph({
            path: { datasetName, graphURI },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not delete graph "${graphURI}" from dataset "${datasetName}":`,
                msg,
            );
            toastStore.error(
                "Delete failed",
                `Could not delete schema "${graphURI}".`,
            );
            return { error };
        }

        update(s => {
            const dsState = getDatasetState(s, datasetName);
            if (!dsState) return s;
            const nextData =
                dsState.data?.filter(g => {
                    const uri = `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}`;
                    return uri !== graphURI;
                }) ?? null;

            return setDatasetState(s, datasetName, {
                ...dsState,
                data: nextData,
            });
        });

        console.log(
            `${LOG_PREFIX} Deleted graph "${graphURI}" from dataset "${datasetName}"`,
        );
        toastStore.success("Schema deleted", `"${graphURI}" was removed.`);

        return { error: null };
    }

    function invalidateDataset(datasetName: string) {
        update(s => {
            const byDataset = new Map(s.graphs);
            byDataset.delete(datasetName);
            return { ...s, graphs: byDataset };
        });
    }

    return {
        subscribe,
        getGraphs,
        addEmptyGraph,
        importGraphs,
        remove: removeGraph,
        invalidateDataset,
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
