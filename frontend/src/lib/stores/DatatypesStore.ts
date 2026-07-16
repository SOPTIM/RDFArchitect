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
    listPrimitives,
    listDatatypes,
    listStereotypes,
    ClassUmlAdaptedDto,
    Uri,
} from "../api/generated";

// =============================================================================
// Types
// =============================================================================

type AsyncSlot<T> = {
    data: T | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type GraphVocabulary = {
    primitives: AsyncSlot<Uri[]>;
    datatypes: AsyncSlot<ClassUmlAdaptedDto[]>;
    stereotypes: AsyncSlot<string[]>;
};

type GraphKey = `${string}::${string}`;

type VocabState = {
    byGraph: Map<GraphKey, GraphVocabulary>;
};

const LOG_PREFIX = "[datatypesStore]";

export const datatypesStore = createVocabStore();

// =============================================================================
// Helpers
// =============================================================================

function makeKey(datasetName: string, graphURI: string): GraphKey {
    return `${datasetName}::${graphURI}`;
}

function createEmptySlot<T>(): AsyncSlot<T> {
    return { data: null, fetchedAt: null, pending: null, error: null };
}

function createEmptyGraphVocabulary(): GraphVocabulary {
    return {
        primitives: createEmptySlot(),
        datatypes: createEmptySlot(),
        stereotypes: createEmptySlot(),
    };
}

function createVocabStore() {
    const store = writable<VocabState>({
        byGraph: new Map(),
    });
    const { subscribe, update } = store;

    // ----- State accessors -----

    function getGraphVocabulary(
        state: VocabState,
        key: GraphKey,
    ): GraphVocabulary {
        return state.byGraph.get(key) ?? createEmptyGraphVocabulary();
    }

    function setGraphVocabulary(
        state: VocabState,
        key: GraphKey,
        next: GraphVocabulary,
    ): VocabState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    function patchSlot<K extends keyof GraphVocabulary>(
        state: VocabState,
        key: GraphKey,
        slot: K,
        patch: Partial<AsyncSlot<unknown>>,
    ): VocabState {
        const current = getGraphVocabulary(state, key);
        return setGraphVocabulary(state, key, {
            ...current,
            [slot]: { ...current[slot], ...patch },
        });
    }

    // =========================================================================
    // GRAPH-SCOPED VOCABULARIES
    // =========================================================================

    /**
     * Generic loader for a slot in a graph's vocabulary. Caches data per
     * (dataset, graph) and per slot, coalesces concurrent fetches.
     */
    async function loadSlot<K extends keyof GraphVocabulary>(
        datasetName: string,
        graphURI: string,
        slot: K,
        label: string,
        fetcher: () => Promise<{
            data?: NonNullable<GraphVocabulary[K]["data"]>;
            error?: unknown;
        }>,
        force: boolean,
    ): Promise<void> {
        if (!datasetName || !graphURI) return;

        const key = makeKey(datasetName, graphURI);
        const slotState = getGraphVocabulary(get(store), key)[slot];

        if (!force && slotState.data !== null) return;
        if (slotState.pending !== null) return slotState.pending;

        console.log(
            `${LOG_PREFIX} Loading ${label} for dataset="${datasetName}", graph="${graphURI}", force=${force}`,
        );

        const promise = (async () => {
            try {
                const { data, error } = await fetcher();

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load ${label} for dataset="${datasetName}", graph="${graphURI}":`,
                        await describeError(error),
                    );
                    update(s =>
                        patchSlot(s, key, slot, { pending: null, error }),
                    );
                    return;
                }

                update(s =>
                    patchSlot(s, key, slot, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded ${(data ?? []).length} ${label} for dataset="${datasetName}", graph="${graphURI}"`,
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading ${label} for dataset="${datasetName}", graph="${graphURI}":`,
                    err,
                );
                update(s =>
                    patchSlot(s, key, slot, { pending: null, error: err }),
                );
            }
        })();

        update(s => patchSlot(s, key, slot, { pending: promise }));

        return promise;
    }

    // ----- Primitives -----

    function loadPrimitives(
        datasetName: string,
        graphURI: string,
        force = false,
    ) {
        return loadSlot(
            datasetName,
            graphURI,
            "primitives",
            "primitives",
            () =>
                listPrimitives({
                    path: { datasetName, graphURI },
                }),
            force,
        );
    }

    function getPrimitives(
        datasetName: string,
        graphURI: string,
    ): Uri[] | null {
        return getGraphVocabulary(get(store), makeKey(datasetName, graphURI))
            .primitives.data;
    }

    // ----- Datatypes -----

    function loadDatatypes(
        datasetName: string,
        graphURI: string,
        force = false,
    ) {
        return loadSlot(
            datasetName,
            graphURI,
            "datatypes",
            "datatypes",
            () =>
                listDatatypes({
                    path: { datasetName, graphURI },
                }),
            force,
        );
    }

    function getDatatypes(
        datasetName: string,
        graphURI: string,
    ): ClassUmlAdaptedDto[] | null {
        return getGraphVocabulary(get(store), makeKey(datasetName, graphURI))
            .datatypes.data;
    }

    // ----- Stereotypes -----

    function loadStereotypes(
        datasetName: string,
        graphURI: string,
        force = false,
    ) {
        return loadSlot(
            datasetName,
            graphURI,
            "stereotypes",
            "stereotypes",
            () =>
                listStereotypes({
                    path: { datasetName, graphURI },
                }),
            force,
        );
    }

    function getStereotypes(
        datasetName: string,
        graphURI: string,
    ): string[] | null {
        return getGraphVocabulary(get(store), makeKey(datasetName, graphURI))
            .stereotypes.data;
    }

    // ----- Convenience: load all vocabularies for a graph at once -----

    /**
     * Loads (or reuses cached) all graph-scoped vocabularies for a given
     * graph in parallel. Useful for the class editor which needs all three
     * at once.
     */
    async function loadForGraph(
        datasetName: string,
        graphURI: string,
        force = false,
    ): Promise<void> {
        await Promise.all([
            loadPrimitives(datasetName, graphURI, force),
            loadDatatypes(datasetName, graphURI, force),
            loadStereotypes(datasetName, graphURI, force),
        ]);
    }

    // =========================================================================
    // INVALIDATION
    // =========================================================================

    /** Marks a graph's vocabularies as stale; next loader call will refetch. */
    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeKey(datasetName, graphURI);
        console.log(`${LOG_PREFIX} Invalidating graph cache key="${key}"`);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    /** Marks all graphs of a dataset as stale. */
    function invalidateDataset(datasetName: string) {
        const prefix = `${datasetName}::`;
        console.log(
            `${LOG_PREFIX} Invalidating dataset cache dataset="${datasetName}"`,
        );
        update(s => {
            const byGraph = new Map(s.byGraph);
            for (const k of byGraph.keys()) {
                if (k.startsWith(prefix)) byGraph.delete(k);
            }
            return { ...s, byGraph };
        });
    }

    return {
        subscribe,

        // loaders
        loadForGraph,

        // getters
        getPrimitives,
        getDatatypes,
        getStereotypes,

        // Invalidation
        invalidateGraph,
        invalidateDataset,
    };
}
