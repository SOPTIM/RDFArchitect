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

import { listPackages, PackageDto } from "../api/generated";

type PackageListInfo = {
    internal: PackageDto[];
    external: PackageDto[];
};

type GraphPackageState = {
    data: PackageListInfo | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type PackagesState = {
    byGraph: Map<string, GraphPackageState>;
};

type GraphKey = `${string}::${string}`;

export const packageStore = createPackageStore();

function makeKey(datasetName: string, graphURI: string): GraphKey {
    return `${datasetName}::${graphURI}`;
}

function createEmptyGraphState(): GraphPackageState {
    return {
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    };
}

function createPackageStore() {
    const store = writable<PackagesState>({
        byGraph: new Map(),
    });

    const { subscribe, update } = store;

    function getGraphState(state: PackagesState, key: GraphKey): GraphPackageState {
        return state.byGraph.get(key) ?? createEmptyGraphState();
    }

    function setGraphState(
        state: PackagesState,
        key: GraphKey,
        next: GraphPackageState,
    ): PackagesState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    async function load(datasetName: string, graphURI: string, force = false) {
        if (!datasetName || !graphURI) return;

        const key = makeKey(datasetName, graphURI);
        const state = get(store);
        const graphState = getGraphState(state, key);

        if (!force && graphState.data !== null) return;
        if (graphState.pending !== null) return graphState.pending;

        const promise = (async () => {
            try {
                const { data, error } = await listPackages({
                    path: { datasetName, graphURI },
                });

                if (error) {
                    update((s) =>
                        setGraphState(s, key, {
                            ...getGraphState(s, key),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update((s) =>
                    setGraphState(s, key, {
                        data: {
                            internal: data?.internalPackageList ?? [],
                            external: data?.externalPackageList ?? [],
                        },
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                update((s) =>
                    setGraphState(s, key, {
                        ...getGraphState(s, key),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update((s) =>
            setGraphState(s, key, {
                ...getGraphState(s, key),
                pending: promise,
            }),
        );

        return promise;
    }

    function getPackages(datasetName: string, graphURI: string): PackageListInfo | null {
        return getGraphState(get(store), makeKey(datasetName, graphURI)).data;
    }

    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeKey(datasetName, graphURI);
        update((s) => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    function invalidateDataset(datasetName: string) {
        update((s) => {
            const byGraph = new Map(s.byGraph);
            for (const key of byGraph.keys()) {
                if (key.startsWith(`${datasetName}::`)) {
                    byGraph.delete(key);
                }
            }
            return { ...s, byGraph };
        });
    }

    function invalidateAll() {
        update(() => ({ byGraph: new Map() }));
    }

    return {
        subscribe,
        load,
        getPackages,
        invalidateGraph,
        invalidateDataset,
        invalidateAll,
    };
}