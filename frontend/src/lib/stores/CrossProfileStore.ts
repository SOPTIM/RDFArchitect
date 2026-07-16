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
    getCrossProfileRenderingData,
    getCrossProfileColors,
    updateCrossProfileColors,
    getCrossProfileDiagram,
    getCrossProfileDiagramId,
    type CrossProfileDiagramDto,
    type CrossProfileDiagramColorDataDto,
    type RenderingDataDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type Result<T = void> = { error: unknown; data?: T };

type SlotState<T> = {
    data: T | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type StoreState = {
    ids: Map<string, SlotState<string>>;
    diagrams: Map<string, SlotState<CrossProfileDiagramDto>>;
};

const LOG_PREFIX = "[crossProfileStore]";

export const crossProfileStore = createCrossProfileStore();

function emptySlot<T>(): SlotState<T> {
    return { data: null, fetchedAt: null, pending: null, error: null };
}

function createCrossProfileStore() {
    const store = writable<StoreState>({
        ids: new Map(),
        diagrams: new Map(),
    });

    const { subscribe, update } = store;

    // =========================================================================
    // HELPERS
    // =========================================================================

    function getIdState(
        state: StoreState,
        datasetName: string,
    ): SlotState<string> {
        return state.ids.get(datasetName) ?? emptySlot();
    }

    function getDiagramState(
        state: StoreState,
        datasetName: string,
    ): SlotState<CrossProfileDiagramDto> {
        return state.diagrams.get(datasetName) ?? emptySlot();
    }

    function setIdState(
        state: StoreState,
        datasetName: string,
        next: SlotState<string>,
    ): StoreState {
        const ids = new Map(state.ids);
        ids.set(datasetName, next);
        return { ...state, ids };
    }

    function setDiagramState(
        state: StoreState,
        datasetName: string,
        next: SlotState<CrossProfileDiagramDto>,
    ): StoreState {
        const diagrams = new Map(state.diagrams);
        diagrams.set(datasetName, next);
        return { ...state, diagrams };
    }

    // =========================================================================
    // LOADERS (cached)
    // =========================================================================

    async function loadId(datasetName: string, force = false) {
        if (!datasetName) return;

        const current = getIdState(get(store), datasetName);
        if (!force && current.data !== null) return;
        if (current.pending) return current.pending;

        console.log(
            `${LOG_PREFIX} Loading cross-profile ID for dataset="${datasetName}"`,
        );

        const pending = (async () => {
            try {
                const { data, error } = await getCrossProfileDiagramId({
                    path: { datasetName },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load cross-profile ID for dataset="${datasetName}"`,
                        await describeError(error),
                    );
                    update(s =>
                        setIdState(s, datasetName, {
                            ...getIdState(s, datasetName),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setIdState(s, datasetName, {
                        data: data ?? null,
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded cross-profile ID="${data}" for dataset="${datasetName}"`,
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading cross-profile ID for dataset="${datasetName}"`,
                    err,
                );
                update(s =>
                    setIdState(s, datasetName, {
                        ...getIdState(s, datasetName),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setIdState(s, datasetName, {
                ...getIdState(s, datasetName),
                pending,
            }),
        );

        return pending;
    }

    async function loadDiagram(datasetName: string, force = false) {
        if (!datasetName) return;

        const current = getDiagramState(get(store), datasetName);
        if (!force && current.data !== null) return;
        if (current.pending) return current.pending;

        console.log(
            `${LOG_PREFIX} Loading cross-profile diagram for dataset="${datasetName}"`,
        );

        const pending = (async () => {
            try {
                const { data, error } = await getCrossProfileDiagram({
                    path: { datasetName },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load cross-profile diagram for dataset="${datasetName}"`,
                        await describeError(error),
                    );
                    update(s =>
                        setDiagramState(s, datasetName, {
                            ...getDiagramState(s, datasetName),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setDiagramState(s, datasetName, {
                        data: data ?? null,
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded cross-profile diagram for dataset="${datasetName}"`,
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading cross-profile diagram for dataset="${datasetName}"`,
                    err,
                );
                update(s =>
                    setDiagramState(s, datasetName, {
                        ...getDiagramState(s, datasetName),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setDiagramState(s, datasetName, {
                ...getDiagramState(s, datasetName),
                pending,
            }),
        );

        return pending;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    function getId(datasetName: string): string | null {
        return getIdState(get(store), datasetName).data;
    }

    function getDiagram(datasetName: string): CrossProfileDiagramDto | null {
        return getDiagramState(get(store), datasetName).data;
    }

    // =========================================================================
    // INVALIDATION
    // =========================================================================

    function invalidateDataset(datasetName: string) {
        console.log(
            `${LOG_PREFIX} Invalidating cross-profile cache for dataset="${datasetName}"`,
        );
        update(s => {
            const ids = new Map(s.ids);
            const diagrams = new Map(s.diagrams);
            ids.delete(datasetName);
            diagrams.delete(datasetName);
            return { ids, diagrams };
        });
    }

    function invalidateAll() {
        console.log(`${LOG_PREFIX} Invalidating all cross-profile caches`);
        update(() => ({ ids: new Map(), diagrams: new Map() }));
    }

    return {
        subscribe,

        // loaders (cached)
        loadId,
        loadDiagram,

        // getters
        getId,
        getDiagram,

        // pass-through
        fetchRenderingData,
        fetchColors,
        saveColors,

        // invalidation
        invalidateDataset,
        invalidateAll,
    };
}

// =========================================================================
// PASS-THROUGH (not cached)
// =========================================================================

async function fetchRenderingData(
    datasetName: string,
): Promise<Result<RenderingDataDto>> {
    if (!datasetName) return { error: null };

    console.log(
        `${LOG_PREFIX} Fetching cross-profile rendering data for dataset="${datasetName}"`,
    );

    const { data, error } = await getCrossProfileRenderingData({
        path: { datasetName },
    });

    if (error) {
        console.error(
            `${LOG_PREFIX} Failed to fetch cross-profile rendering data for dataset="${datasetName}"`,
            await describeError(error),
        );
        return { error };
    }

    return { error: null, data: data ?? undefined };
}

async function fetchColors(
    datasetName: string,
): Promise<Result<CrossProfileDiagramColorDataDto>> {
    if (!datasetName) return { error: null };

    console.log(
        `${LOG_PREFIX} Fetching cross-profile colors for dataset="${datasetName}"`,
    );

    const { data, error } = await getCrossProfileColors({
        path: { datasetName },
    });

    if (error) {
        console.error(
            `${LOG_PREFIX} Failed to fetch cross-profile colors for dataset="${datasetName}"`,
            await describeError(error),
        );
        toastStore.error("Load failed", "Could not load color data.");
        return { error };
    }

    return { error: null, data: data ?? undefined };
}

async function saveColors(
    datasetName: string,
    colorData: CrossProfileDiagramColorDataDto,
): Promise<Result> {
    if (!datasetName) return { error: null };

    console.log(
        `${LOG_PREFIX} Saving cross-profile colors for dataset="${datasetName}"`,
    );

    const { error } = await updateCrossProfileColors({
        path: { datasetName },
        body: colorData,
    });

    if (error) {
        console.error(
            `${LOG_PREFIX} Failed to save cross-profile colors for dataset="${datasetName}"`,
            await describeError(error),
        );
        toastStore.error("Save failed", "Could not save color data.");
        return { error };
    }

    console.log(
        `${LOG_PREFIX} Saved cross-profile colors for dataset="${datasetName}"`,
    );
    toastStore.success("Colors saved", "Color data was saved successfully.");
    return { error: null };
}
