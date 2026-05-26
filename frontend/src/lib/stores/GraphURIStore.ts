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

import { listGraphs, deleteGraph, Uri } from "../api/generated";

type GraphURIState = {
    data: Uri[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type DatasetState = {
    graphs: Map<string, GraphURIState>;
};

export const graphURIStore = createGrapURIStore();

function createEmptyDatasetState(): GraphURIState {
    return {
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    };
}

function createGrapURIStore() {
    const store = writable<DatasetState>({
        graphs: new Map(),
    });

    const { subscribe, update } = store;

    function getDatasetState(state: DatasetState, datasetName: string): GraphURIState {
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
                    update((s) =>
                        setDatasetState(s, datasetName, {
                            ...getDatasetState(s, datasetName),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update((s) =>
                    setDatasetState(s, datasetName, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                update((s) =>
                    setDatasetState(s, datasetName, {
                        ...getDatasetState(s, datasetName),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update((s) =>
            setDatasetState(s, datasetName, {
                ...getDatasetState(s, datasetName),
                pending: promise,
            }),
        );

        return promise;
    }

    function getGraphURIs(datasetName: string): Uri[] | null {
        return getDatasetState(get(store), datasetName).data;
    }

    function getStateForDataset(datasetName: string): GraphURIState {
        return getDatasetState(get(store), datasetName);
    }

    async function removeGraph(datasetName: string, graphURI: string) {
        const { error } = await deleteGraph({
            path: { datasetName, graphURI },
        });

        if (error) return { error };

        update((s) => {
            const dsState = getDatasetState(s, datasetName);
            const nextData =
                dsState.data?.filter((g) => {
                    const uri = `${g.prefix ?? ""}${g.suffix}`;
                    return uri !== graphURI;
                }) ?? null;

            return setDatasetState(s, datasetName, {
                ...dsState,
                data: nextData,
            });
        });

        return { error: null };
    }

    function invalidateDataset(datasetName: string) {
        update((s) => {
            const byDataset = new Map(s.graphs);
            byDataset.delete(datasetName);
            return { ...s, byDataset };
        });
    }

    function invalidateAll() {
        update(() => ({ graphs: new Map() }));
    }

    return {
        subscribe,
        load,
        getGraphURIs,
        getStateForDataset,
        remove: removeGraph,
        invalidateDataset,
        invalidateAll,
    };
}