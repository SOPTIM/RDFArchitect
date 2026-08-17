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
import { type AsyncSlot, createEmptySlot, type Result } from "./storeTypes";
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
    colors: Map<string, AsyncSlot<CrossProfileDiagramColorDataDto>>;
};

const LOG_PREFIX = "[crossProfileStore]";

export const crossProfileStore = createCrossProfileStore();

function createCrossProfileStore() {
    const store = writable<StoreState>({
        ids: new Map(),
        diagrams: new Map(),
        colors: new Map(),
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

    function getColorSlot(
        s: StoreState,
        datasetName: string,
    ): AsyncSlot<CrossProfileDiagramColorDataDto> {
        return s.colors.get(datasetName) ?? createEmptySlot();
    }

    function setColorSlot(
        s: StoreState,
        datasetName: string,
        patch: Partial<AsyncSlot<CrossProfileDiagramColorDataDto>>,
    ): StoreState {
        const colors = new Map(s.colors);
        colors.set(datasetName, { ...getColorSlot(s, datasetName), ...patch });
        return { ...s, colors };
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    async function getId(
        datasetName: string,
        force = false,
    ): Promise<string | null> {
        if (!datasetName) return null;
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

    async function getDiagram(
        datasetName: string,
        force = false,
    ): Promise<CrossProfileDiagramDto | null> {
        if (!datasetName) return null;
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

    async function getColors(
        datasetName: string,
        force = false,
    ): Promise<CrossProfileDiagramColorDataDto | null> {
        if (!datasetName) return null;
        return loadSlot(
            store,
            s => getColorSlot(s, datasetName),
            (s, patch) => setColorSlot(s, datasetName, patch),
            () => getCrossProfileColors({ path: { datasetName } }),
            LOG_PREFIX,
            `cross-profile colors for dataset="${datasetName}"`,
            force,
        );
    }

    // =========================================================================
    // MUTATIONS
    // =========================================================================

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

        update(s =>
            setColorSlot(s, datasetName, {
                data: colorData,
                fetchedAt: Date.now(),
                pending: null,
                error: null,
            }),
        );

        console.log(
            `${LOG_PREFIX} Saved cross-profile colors for dataset="${datasetName}"`,
        );
        toastStore.success(
            "Colors saved",
            "Color data was saved successfully.",
        );
        return { error: null };
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
            const colors = new Map(s.colors);
            ids.delete(datasetName);
            diagrams.delete(datasetName);
            colors.delete(datasetName);
            return { ids, diagrams, colors };
        });
    }

    return {
        subscribe,

        // getters
        getId,
        getDiagram,

        // pass-through
        fetchRenderingData,
        getColors,
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
export { createCrossProfileStore }
