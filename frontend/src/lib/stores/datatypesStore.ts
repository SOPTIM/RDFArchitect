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

import { type GraphKey, makeGraphKey } from "./storeHelpers";
import { describeError } from "./storeLogging";
import { type AsyncSlot, createEmptySlot } from "./storeTypes";
import {
    listPrimitives,
    listDatatypes,
    listStereotypes,
    type ClassUmlAdaptedDto,
    type Uri,
} from "../api/generated";

// =============================================================================
// Types
// =============================================================================

type GraphVocabulary = {
    primitives: AsyncSlot<Uri[]>;
    datatypes: AsyncSlot<ClassUmlAdaptedDto[]>;
    stereotypes: AsyncSlot<string[]>;
};

type VocabState = {
    byGraph: Map<GraphKey, GraphVocabulary>;
};

const LOG_PREFIX = "[datatypesStore]";

export const datatypesStore = createVocabStore();

// =============================================================================
// Helpers
// =============================================================================

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
     * (workspace, graph) and per slot, coalesces concurrent fetches.
     */
    async function loadSlot<K extends keyof GraphVocabulary>(
        workspaceName: string,
        graphURI: string,
        slot: K,
        label: string,
        fetcher: () => Promise<{
            data?: NonNullable<GraphVocabulary[K]["data"]>;
            error?: unknown;
        }>,
        force: boolean,
    ): Promise<GraphVocabulary[K]["data"]> {
        if (!workspaceName || !graphURI)
            return null as GraphVocabulary[K]["data"];

        const key = makeGraphKey(workspaceName, graphURI);
        const slotState = getGraphVocabulary(get(store), key)[slot];

        if (!force && slotState.data !== null) {
            return slotState.data as GraphVocabulary[K]["data"];
        }

        if (slotState.pending !== null) {
            await slotState.pending;
            const updated = getGraphVocabulary(get(store), key)[slot];
            return updated.data as GraphVocabulary[K]["data"];
        }

        console.log(
            `${LOG_PREFIX} Loading ${label} for workspace="${workspaceName}", graph="${graphURI}", force=${force}`,
        );

        const promise = (async (): Promise<GraphVocabulary[K]["data"]> => {
            try {
                const { data, error } = await fetcher();

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load ${label} for workspace="${workspaceName}", graph="${graphURI}":`,
                        await describeError(error),
                    );
                    update(s =>
                        patchSlot(s, key, slot, { pending: null, error }),
                    );
                    return null as GraphVocabulary[K]["data"];
                }

                const result = data ?? null;
                update(s =>
                    patchSlot(s, key, slot, {
                        data: result,
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded ${(data ?? []).length} ${label} for workspace="${workspaceName}", graph="${graphURI}"`,
                );

                return result as GraphVocabulary[K]["data"];
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading ${label} for workspace="${workspaceName}", graph="${graphURI}":`,
                    err,
                );
                update(s =>
                    patchSlot(s, key, slot, { pending: null, error: err }),
                );
                return null as GraphVocabulary[K]["data"];
            }
        })();

        update(s => patchSlot(s, key, slot, { pending: promise as never }));

        return promise;
    }

    // ----- Getters -----

    function getPrimitives(
        workspaceName: string,
        graphURI: string,
        force = false,
    ): Promise<ClassUmlAdaptedDto[] | null> {
        return loadSlot(
            workspaceName,
            graphURI,
            "primitives",
            "primitives",
            () =>
                listPrimitives({
                    path: { datasetName: workspaceName, graphURI },
                }),
            force,
        );
    }

    function getDatatypes(
        workspaceName: string,
        graphURI: string,
        force = false,
    ): Promise<ClassUmlAdaptedDto[] | null> {
        return loadSlot(
            workspaceName,
            graphURI,
            "datatypes",
            "datatypes",
            () =>
                listDatatypes({
                    path: { datasetName: workspaceName, graphURI },
                }),
            force,
        );
    }

    function getStereotypes(
        workspaceName: string,
        graphURI: string,
        force = false,
    ): Promise<string[] | null> {
        return loadSlot(
            workspaceName,
            graphURI,
            "stereotypes",
            "stereotypes",
            () =>
                listStereotypes({
                    path: { datasetName: workspaceName, graphURI },
                }),
            force,
        );
    }

    // =========================================================================
    // INVALIDATION
    // =========================================================================

    /** Marks a graph's vocabularies as stale; next loader call will refetch. */
    function invalidateGraph(workspaceName: string, graphURI: string) {
        const key = makeGraphKey(workspaceName, graphURI);
        console.log(`${LOG_PREFIX} Invalidating graph cache key="${key}"`);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    /** Marks all graphs of a workspace as stale. */
    function invalidateWorkspace(workspaceName: string) {
        const prefix = `${workspaceName}::`;
        console.log(
            `${LOG_PREFIX} Invalidating workspace cache workspace="${workspaceName}"`,
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

        // getters
        getPrimitives,
        getDatatypes,
        getStereotypes,

        // Invalidation
        invalidateGraph,
        invalidateWorkspace,
    };
}
export { createVocabStore as createDatatypesStore };
