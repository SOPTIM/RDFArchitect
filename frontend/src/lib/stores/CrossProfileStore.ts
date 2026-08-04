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

import { loadSlot } from "./storeHelpers";
import { describeError } from "./StoreLogging";
import { AsyncSlot, createEmptySlot, Result } from "./storeTypes";
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

type StoreState = {
    ids: Map<string, AsyncSlot<string>>;
    diagrams: Map<string, AsyncSlot<CrossProfileDiagramDto>>;
};

const LOG_PREFIX = "[crossProfileStore]";

export const crossProfileStore = createCrossProfileStore();

function createCrossProfileStore() {
    const store = writable<StoreState>({
        ids: new Map(),
        diagrams: new Map(),
    });

    const { subscribe, update } = store;

    // =========================================================================
    // HELPERS
    // =========================================================================

    function getIdSlot(s: StoreState, datasetName: string): AsyncSlot<string> {
        return s.ids.get(datasetName) ?? createEmptySlot();
    }

    function setIdSlot(
        s: StoreState,
        datasetName: string,
        patch: Partial<AsyncSlot<string>>,
    ): StoreState {
        const ids = new Map(s.ids);
        ids.set(datasetName, { ...getIdSlot(s, datasetName), ...patch });
        return { ...s, ids };
    }

    function getDiagramSlot(
        s: StoreState,
        datasetName: string,
    ): AsyncSlot<CrossProfileDiagramDto> {
        return s.diagrams.get(datasetName) ?? createEmptySlot();
    }

    function setDiagramSlot(
        s: StoreState,
        datasetName: string,
        patch: Partial<AsyncSlot<CrossProfileDiagramDto>>,
    ): StoreState {
        const diagrams = new Map(s.diagrams);
        diagrams.set(datasetName, {
            ...getDiagramSlot(s, datasetName),
            ...patch,
        });
        return { ...s, diagrams };
    }

    // =========================================================================
    // LOADERS (cached)
    // =========================================================================

    async function loadId(datasetName: string, force = false) {
        if (!datasetName) return;
        return loadSlot(
            store,
            s => getIdSlot(s, datasetName),
            (s, patch) => setIdSlot(s, datasetName, patch),
            () => getCrossProfileDiagramId({ path: { datasetName } }),
            LOG_PREFIX,
            `cross-profile ID for dataset="${datasetName}"`,
            force,
        );
    }

    async function loadDiagram(datasetName: string, force = false) {
        if (!datasetName) return;
        return loadSlot(
            store,
            s => getDiagramSlot(s, datasetName),
            (s, patch) => setDiagramSlot(s, datasetName, patch),
            () => getCrossProfileDiagram({ path: { datasetName } }),
            LOG_PREFIX,
            `cross-profile diagram for dataset="${datasetName}"`,
            force,
        );
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    function getId(datasetName: string): string | null {
        return getIdSlot(get(store), datasetName).data;
    }

    function getDiagram(datasetName: string): CrossProfileDiagramDto | null {
        return getDiagramSlot(get(store), datasetName).data;
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
