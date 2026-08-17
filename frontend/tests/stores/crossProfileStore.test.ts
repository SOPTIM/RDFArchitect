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

import { get } from "svelte/store";
import { beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createCrossProfileStore } from "../../src/lib/stores/crossProfileStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = "datasetA";
const DATASET_B = "datasetB";

const MOCK_DIAGRAM_ID = "diag-123";
const MOCK_DIAGRAM = {
    diagramId: "diag-123",
    classes: [],
};
const MOCK_COLORS = {
    graphColors: {
        graph1: "#ffffff",
        graph2: "#000000",
    },
};
const MOCK_RENDERING_DATA = { format: "SVELTEFLOW" as const };

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    getCrossProfileDiagramId: vi.fn(),
    getCrossProfileDiagram: vi.fn(),
    getCrossProfileColors: vi.fn(),
    updateCrossProfileColors: vi.fn(),
    getCrossProfileRenderingData: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("crossProfileStore", () => {
    let store: ReturnType<typeof createCrossProfileStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createCrossProfileStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with empty maps", () => {
            const state = get(store);
            expect(state.ids.size).toBe(0);
            expect(state.diagrams.size).toBe(0);
            expect(state.colors.size).toBe(0);
        });
    });

    // -------------------------------------------------------------------------
    describe("getId", () => {
        test("fetches and caches the diagram ID for a dataset", async () => {
            vi.mocked(api.getCrossProfileDiagramId).mockResolvedValue({ data: MOCK_DIAGRAM_ID, error: undefined });

            const result1 = await store.getId(DATASET_A);
            const result2 = await store.getId(DATASET_A);

            expect(result1).toBe(MOCK_DIAGRAM_ID);
            expect(result2).toBe(MOCK_DIAGRAM_ID);
            expect(api.getCrossProfileDiagramId).toHaveBeenCalledTimes(1); // Cached

            const state = get(store);
            expect(state.ids.get(DATASET_A)?.data).toBe(MOCK_DIAGRAM_ID);
        });

        test("returns null if datasetName is empty", async () => {
            const result = await store.getId("");
            expect(result).toBeNull();
            expect(api.getCrossProfileDiagramId).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("getDiagram", () => {
        test("getDiagram returns null if datasetName is empty", async () => {
            const result = await store.getDiagram("");
            expect(result).toBeNull();
            expect(api.getCrossProfileDiagram).not.toHaveBeenCalled();
        });

        test("fetches and caches the diagram for a dataset", async () => {
            vi.mocked(api.getCrossProfileDiagram).mockResolvedValue({ data: MOCK_DIAGRAM, error: undefined });

            const result = await store.getDiagram(DATASET_A);
            await store.getDiagram(DATASET_A);

            expect(result).toEqual(MOCK_DIAGRAM);
            expect(api.getCrossProfileDiagram).toHaveBeenCalledTimes(1);

            const state = get(store);
            expect(state.diagrams.get(DATASET_A)?.data).toEqual(MOCK_DIAGRAM);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getCrossProfileDiagram).mockResolvedValue({ data: undefined, error: new Error("Network error") });

            const result = await store.getDiagram(DATASET_A);
            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("getColors", () => {
        test("fetches and caches color data", async () => {
            vi.mocked(api.getCrossProfileColors).mockResolvedValue({ data: MOCK_COLORS, error: undefined });

            const result = await store.getColors(DATASET_A);
            expect(result).toEqual(MOCK_COLORS);
        });

        test("force=true bypasses the cache", async () => {
            vi.mocked(api.getCrossProfileColors).mockResolvedValue({ data: MOCK_COLORS, error: undefined });

            await store.getColors(DATASET_A);
            await store.getColors(DATASET_A, true);

            expect(api.getCrossProfileColors).toHaveBeenCalledTimes(2);
        });
    });

    // -------------------------------------------------------------------------
    describe("saveColors", () => {
        const NEW_COLORS = {
            graphColors: {
                graph1: "#ffffff",
                graph2: "#000001",
            },
        };

        test("updates the API and the local cache on success", async () => {
            vi.mocked(api.updateCrossProfileColors).mockResolvedValue({ data: undefined, error: undefined });

            const result = await store.saveColors(DATASET_A, NEW_COLORS);

            expect(result.error).toBeNull();
            expect(api.updateCrossProfileColors).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A },
                body: NEW_COLORS,
            });

            // Ensure store was updated directly
            const state = get(store);
            expect(state.colors.get(DATASET_A)?.data).toEqual(NEW_COLORS);
            expect(toastStore.success).toHaveBeenCalledWith("Colors saved", "Color data was saved successfully.");
        });

        test("returns error and does not update store when API fails", async () => {
            const error = new Error("Failed to save");
            vi.mocked(api.updateCrossProfileColors).mockResolvedValue({ data: undefined, error });

            // Seed store with initial data
            vi.mocked(api.getCrossProfileColors).mockResolvedValue({ data: MOCK_COLORS, error: undefined });
            await store.getColors(DATASET_A);

            const result = await store.saveColors(DATASET_A, NEW_COLORS);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith("Save failed", "Could not save color data.");

            // Store should still have the old data
            const state = get(store);
            expect(state.colors.get(DATASET_A)?.data).toEqual(MOCK_COLORS);
        });

        test("early returns if datasetName is empty", async () => {
            const result = await store.saveColors("", NEW_COLORS);
            expect(result.error).toBeNull();
            expect(api.updateCrossProfileColors).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateDataset", () => {
        test("clears cache for a specific dataset without affecting others", async () => {
            vi.mocked(api.getCrossProfileColors)
                .mockResolvedValueOnce({ data: MOCK_COLORS, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_COLORS, error: undefined });

            await store.getColors(DATASET_A);
            await store.getColors(DATASET_B);

            let state = get(store);
            expect(state.colors.has(DATASET_A)).toBe(true);
            expect(state.colors.has(DATASET_B)).toBe(true);

            store.invalidateDataset(DATASET_A);

            state = get(store);
            expect(state.colors.has(DATASET_A)).toBe(false);
            expect(state.colors.has(DATASET_B)).toBe(true);
        });
    });

    test("invalidateDataset removes the dataset from all three caches (ids, diagrams, colors)", async () => {
        vi.mocked(api.getCrossProfileDiagramId).mockResolvedValue({
            data: "id-1",
            error: undefined,
        });
        vi.mocked(api.getCrossProfileDiagram).mockResolvedValue({
            data: MOCK_DIAGRAM,
            error: undefined,
        });
        vi.mocked(api.getCrossProfileColors).mockResolvedValue({
            data: MOCK_COLORS,
            error: undefined,
        });

        await store.getId(DATASET_A);
        await store.getDiagram(DATASET_A);
        await store.getColors(DATASET_A);

        store.invalidateDataset(DATASET_A);

        const state = get(store);
        expect(state.ids.has(DATASET_A)).toBe(false);
        expect(state.diagrams.has(DATASET_A)).toBe(false);
        expect(state.colors.has(DATASET_A)).toBe(false);
    });

    // -------------------------------------------------------------------------
    describe("fetchRenderingData", () => {
        test("fetchRenderingData returns error:null without calling API if datasetName is empty", async () => {
            const result = await store.fetchRenderingData("");
            expect(result.error).toBeNull();
            expect(result.data).toBeUndefined();
            expect(api.getCrossProfileRenderingData).not.toHaveBeenCalled();
        });
        test("returns API data directly without caching", async () => {
            vi.mocked(api.getCrossProfileRenderingData).mockResolvedValue({ data: MOCK_RENDERING_DATA, error: undefined });

            const result = await store.fetchRenderingData(DATASET_A);

            expect(result.data).toEqual(MOCK_RENDERING_DATA);
            expect(result.error).toBeNull();

            // Prove it is un-cached by verifying state is untouched
            const state = get(store);
            expect(state.ids.size).toBe(0);
            expect(state.diagrams.size).toBe(0);
            expect(state.colors.size).toBe(0);
        });

        test("returns error on failure", async () => {
            const error = new Error("Rendering data failed");
            vi.mocked(api.getCrossProfileRenderingData).mockResolvedValue({ data: undefined, error });

            const result = await store.fetchRenderingData(DATASET_A);

            expect(result.error).toBe(error);
            expect(result.data).toBeUndefined();
        });
    });
});