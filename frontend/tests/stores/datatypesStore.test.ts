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
import { createDatatypesStore } from "../../src/lib/stores/datatypesStore";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    listPrimitives: vi.fn(),
    listDatatypes: vi.fn(),
    listStereotypes: vi.fn(),
}));

vi.mock("$lib/stores/storeHelpers", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../src/lib/stores/storeHelpers")>();
    return {
        ...actual,
        makeGraphKey: vi.fn((dataset, graph) => `${dataset}::${graph}`),
    };
});

// Suppress console output for expected errors
vi.spyOn(console, "log").mockImplementation(() => {});
vi.spyOn(console, "error").mockImplementation(() => {});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("datatypesStore", () => {
    let store: ReturnType<typeof createDatatypesStore>;
    const DATASET = "datasetA";
    const GRAPH = "http://example.org/graph";
    const KEY = `${DATASET}::${GRAPH}`;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createDatatypesStore();
    });

    describe("Custom loadSlot behaviors (via getPrimitives)", () => {
        test("fetches data and caches it on subsequent calls", async () => {
            const mockData = ["uri:1", "uri:2"];
            vi.mocked(api.listPrimitives).mockResolvedValue({ data: mockData as never, error: undefined });

            const res1 = await store.getPrimitives(DATASET, GRAPH);
            const res2 = await store.getPrimitives(DATASET, GRAPH);

            expect(res1).toEqual(mockData);
            expect(res2).toEqual(mockData);
            expect(api.listPrimitives).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses the cache", async () => {
            const mockData = ["uri:1"];
            vi.mocked(api.listPrimitives).mockResolvedValue({ data: mockData as never, error: undefined });

            await store.getPrimitives(DATASET, GRAPH);
            await store.getPrimitives(DATASET, GRAPH, true);

            expect(api.listPrimitives).toHaveBeenCalledTimes(2);
        });

        test("coalesces concurrent requests to a single API call", async () => {
            const mockData = ["uri:1"];
            // Artificial delay to ensure promises overlap
            vi.mocked(api.listPrimitives).mockImplementation(async () => {
                await new Promise(resolve => setTimeout(resolve, 50));
                return { data: mockData as never, error: undefined };
            });

            // Fire multiple requests simultaneously
            const [res1, res2, res3] = await Promise.all([
                store.getPrimitives(DATASET, GRAPH),
                store.getPrimitives(DATASET, GRAPH),
                store.getPrimitives(DATASET, GRAPH),
            ]);

            expect(res1).toEqual(mockData);
            expect(res2).toEqual(mockData);
            expect(res3).toEqual(mockData);
            expect(api.listPrimitives).toHaveBeenCalledTimes(1);
        });

        test("returns null if datasetName or graphURI is missing", async () => {
            expect(await store.getPrimitives("", GRAPH)).toBeNull();
            expect(await store.getPrimitives(DATASET, "")).toBeNull();
            expect(api.listPrimitives).not.toHaveBeenCalled();
        });

        test("handles standard API errors gracefully", async () => {
            const error = new Error("API Failure");
            vi.mocked(api.listPrimitives).mockResolvedValue({ data: undefined, error });

            const result = await store.getPrimitives(DATASET, GRAPH);

            expect(result).toBeNull();
            const state = get(store).byGraph.get(KEY);
            expect(state?.primitives.error).toBe(error);
        });

        test("catches unexpected exceptions from the fetcher", async () => {
            const exception = new Error("Unexpected crash");
            vi.mocked(api.listPrimitives).mockRejectedValue(exception);

            const result = await store.getPrimitives(DATASET, GRAPH);

            expect(result).toBeNull();
            const state = get(store).byGraph.get(KEY);
            expect(state?.primitives.error).toBe(exception);
        });

        test("caches for primitives, datatypes, and stereotypes are independent", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: ["uri:1"] as never,
                error: undefined,
            });
            vi.mocked(api.listDatatypes).mockResolvedValue({
                data: [{ uuid: "dt-1" }] as never,
                error: undefined,
            });

            await store.getPrimitives(DATASET, GRAPH);
            store.invalidateGraph(DATASET, GRAPH); // löscht alle drei

            await store.getPrimitives(DATASET, GRAPH);
            expect(api.listPrimitives).toHaveBeenCalledTimes(2); // refetched
            expect(api.listDatatypes).toHaveBeenCalledTimes(0); // war nie gefetcht
        });
    });

    // -------------------------------------------------------------------------
    describe("Specific API Getters", () => {
        test("getDatatypes calls listDatatypes", async () => {
            const mockData = [{ id: "type1" }];
            vi.mocked(api.listDatatypes).mockResolvedValue({ data: mockData as never, error: undefined });

            const result = await store.getDatatypes(DATASET, GRAPH);

            expect(result).toEqual(mockData);
            expect(api.listDatatypes).toHaveBeenCalledWith({ path: { datasetName: DATASET, graphURI: GRAPH } });
        });

        test("getStereotypes calls listStereotypes", async () => {
            const mockData = ["stereo1"];
            vi.mocked(api.listStereotypes).mockResolvedValue({ data: mockData, error: undefined });

            const result = await store.getStereotypes(DATASET, GRAPH);

            expect(result).toEqual(mockData);
            expect(api.listStereotypes).toHaveBeenCalledWith({ path: { datasetName: DATASET, graphURI: GRAPH } });
        });
    });

    // -------------------------------------------------------------------------
    describe("Invalidation", () => {
        test("invalidateGraph removes specific graph from cache", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: ["uri1"] as never,
                error: undefined,
            });

            await store.getPrimitives(DATASET, GRAPH);
            await store.getPrimitives(DATASET, "other-graph");

            store.invalidateGraph(DATASET, GRAPH);

            const state = get(store).byGraph;
            expect(state.has(KEY)).toBe(false);
            expect(state.has(`${DATASET}::other-graph`)).toBe(true);
        });

        test("after invalidateGraph, the next call re-fetches", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: [] as never,
                error: undefined,
            });
            await store.getPrimitives(DATASET, GRAPH);
            store.invalidateGraph(DATASET, GRAPH);
            await store.getPrimitives(DATASET, GRAPH);
            expect(api.listPrimitives).toHaveBeenCalledTimes(2);
        });
        
        test("invalidateDataset removes all graphs for a given dataset", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({ data: ["uri1"] as never, error: undefined });

            await store.getPrimitives(DATASET, GRAPH);
            await store.getPrimitives(DATASET, "other-graph");
            await store.getPrimitives("datasetB", GRAPH);

            store.invalidateDataset(DATASET);

            const state = get(store).byGraph;
            expect(state.has(KEY)).toBe(false);
            expect(state.has(`${DATASET}::other-graph`)).toBe(false);
            expect(state.has(`datasetB::${GRAPH}`)).toBe(true);
        });
    });
});