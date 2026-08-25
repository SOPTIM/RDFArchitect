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
import { type AsyncSlot, createEmptySlot, type Result } from "./storeTypes";
import {
    getCrossProfileRenderingData,
    getCrossProfileDiagram,
    getCrossProfileDiagramId,
    type CrossProfileDiagramDto,
    type RenderingDataDto,
} from "../api/generated";

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

    function getIdSlot(
        s: StoreState,
        workspaceName: string,
    ): AsyncSlot<string> {
        return s.ids.get(workspaceName) ?? createEmptySlot();
    }

    function setIdSlot(
        s: StoreState,
        workspaceName: string,
        patch: Partial<AsyncSlot<string>>,
    ): StoreState {
        const ids = new Map(s.ids);
        ids.set(workspaceName, { ...getIdSlot(s, workspaceName), ...patch });
        return { ...s, ids };
    }

    function getDiagramSlot(
        s: StoreState,
        workspaceName: string,
    ): AsyncSlot<CrossProfileDiagramDto> {
        return s.diagrams.get(workspaceName) ?? createEmptySlot();
    }

    function setDiagramSlot(
        s: StoreState,
        workspaceName: string,
        patch: Partial<AsyncSlot<CrossProfileDiagramDto>>,
    ): StoreState {
        const diagrams = new Map(s.diagrams);
        diagrams.set(workspaceName, {
            ...getDiagramSlot(s, workspaceName),
            ...patch,
        });
        return { ...s, diagrams };
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    async function getId(
        workspaceName: string,
        force = false,
    ): Promise<string | null> {
        if (!workspaceName) return null;
        return loadSlot(
            store,
            s => getIdSlot(s, workspaceName),
            (s, patch) => setIdSlot(s, workspaceName, patch),
            () =>
                getCrossProfileDiagramId({
                    path: { datasetName: workspaceName },
                }),
            LOG_PREFIX,
            `cross-profile ID for workspace="${workspaceName}"`,
            force,
        );
    }

    async function getDiagram(
        workspaceName: string,
        force = false,
    ): Promise<CrossProfileDiagramDto | null> {
        if (!workspaceName) return null;
        return loadSlot(
            store,
            s => getDiagramSlot(s, workspaceName),
            (s, patch) => setDiagramSlot(s, workspaceName, patch),
            () =>
                getCrossProfileDiagram({
                    path: { datasetName: workspaceName },
                }),
            LOG_PREFIX,
            `cross-profile diagram for workspace="${workspaceName}"`,
            force,
        );
    }

    // =========================================================================
    // INVALIDATION
    // =========================================================================

    function invalidateWorkspace(workspaceName: string) {
        console.log(
            `${LOG_PREFIX} Invalidating cross-profile cache for workspace="${workspaceName}"`,
        );
        update(s => {
            const ids = new Map(s.ids);
            const diagrams = new Map(s.diagrams);
            ids.delete(workspaceName);
            diagrams.delete(workspaceName);
            return { ids, diagrams };
        });
    }

    return {
        subscribe,

        // getters
        getId,
        getDiagram,

        // pass-through
        fetchRenderingData,

        // invalidation
        invalidateWorkspace,
    };
}

// =========================================================================
// PASS-THROUGH (not cached)
// =========================================================================

async function fetchRenderingData(
    workspaceName: string,
): Promise<Result<RenderingDataDto>> {
    if (!workspaceName) return { error: null };

    console.log(
        `${LOG_PREFIX} Fetching cross-profile rendering data for workspace="${workspaceName}"`,
    );

    const { data, error } = await getCrossProfileRenderingData({
        path: { datasetName: workspaceName },
    });

    if (error) {
        console.error(
            `${LOG_PREFIX} Failed to fetch cross-profile rendering data for workspace="${workspaceName}"`,
            await describeError(error),
        );
        return { error };
    }

    return { error: null, data: data ?? undefined };
}
export { createCrossProfileStore };
