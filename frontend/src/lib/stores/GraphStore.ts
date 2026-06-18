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

import { writable, get } from "svelte/store";

import { describeError } from "./StoreLogging";
import {
    listGraphs,
    deleteGraph,
    Uri,
    replaceGraphs,
    GraphBulkImportResponse,
    replaceGraph,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type GraphURIState = {
    data: Uri[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type DatasetState = {
    graphs: Map<string, GraphURIState>;
};

type Result<T = void> = { error: unknown; data?: T };

const LOG_PREFIX = "[graphStore]";

export const graphStore = createGraphStore();

function createEmptyDatasetState(): GraphURIState {
    return {
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    };
}

function createGraphStore() {
    const store = writable<DatasetState>({
        graphs: new Map(),
    });

    const { subscribe, update } = store;

    function getDatasetState(
        state: DatasetState,
        datasetName: string,
    ): GraphURIState {
        return state.graphs.get(datasetName) ?? createEmptyDatasetState();
    }

    function setDatasetState(
        state: DatasetState,
        datasetName: string,
        next: GraphURIState,
    ): DatasetState {
        const byDataset = new Map(state.graphs);
        byDataset.set(datasetName, next);
        return { ...state, graphs: byDataset };
    }

    async function load(datasetName: string, force = false) {
        if (!datasetName) return;

        const state = get(store);
        const dsState = getDatasetState(state, datasetName);

        if (!force && dsState.data !== null) return;
        if (dsState.pending !== null) return dsState.pending;

        const promise = (async () => {
            try {
                const { data, error } = await listGraphs({
                    path: { datasetName },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load graphs for dataset "${datasetName}":`,
                        await describeError(error),
                    );
                    update(s =>
                        setDatasetState(s, datasetName, {
                            ...getDatasetState(s, datasetName),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setDatasetState(s, datasetName, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading graphs for dataset "${datasetName}":`,
                    err,
                );
                update(s =>
                    setDatasetState(s, datasetName, {
                        ...getDatasetState(s, datasetName),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setDatasetState(s, datasetName, {
                ...getDatasetState(s, datasetName),
                pending: promise,
            }),
        );

        return promise;
    }

    function getGraphs(datasetName: string): Uri[] | null {
        return getDatasetState(get(store), datasetName).data;
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
                    : "No graphs were imported.",
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
            const nextData =
                dsState.data?.filter(g => {
                    const uri = `${g.prefix ?? ""}${g.suffix}`;
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

    function invalidateAll() {
        update(() => ({ graphs: new Map() }));
    }

    return {
        subscribe,
        load,
        getGraphs,
        addEmptyGraph,
        importGraphs,
        remove: removeGraph,
        invalidateDataset,
        invalidateAll,
    };
}
