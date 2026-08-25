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

vi.mock("$lib/stores/storeHelpers", async importOriginal => {
    const actual =
        await importOriginal<
            typeof import("../../src/lib/stores/storeHelpers")
        >();
    return {
        ...actual,
        makeGraphKey: vi.fn((workspace, graph) => `${workspace}::${graph}`),
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
    const WORKSPACE = "workspaceA";
    const GRAPH = "http://example.org/graph";
    const KEY = `${WORKSPACE}::${GRAPH}`;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createDatatypesStore();
    });

    describe("Custom loadSlot behaviors (via getPrimitives)", () => {
        test("fetches data and caches it on subsequent calls", async () => {
            const mockData = ["uri:1", "uri:2"];
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: mockData as never,
                error: undefined,
            });

            const res1 = await store.getPrimitives(WORKSPACE, GRAPH);
            const res2 = await store.getPrimitives(WORKSPACE, GRAPH);

            expect(res1).toEqual(mockData);
            expect(res2).toEqual(mockData);
            expect(api.listPrimitives).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses the cache", async () => {
            const mockData = ["uri:1"];
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: mockData as never,
                error: undefined,
            });

            await store.getPrimitives(WORKSPACE, GRAPH);
            await store.getPrimitives(WORKSPACE, GRAPH, true);

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
                store.getPrimitives(WORKSPACE, GRAPH),
                store.getPrimitives(WORKSPACE, GRAPH),
                store.getPrimitives(WORKSPACE, GRAPH),
            ]);

            expect(res1).toEqual(mockData);
            expect(res2).toEqual(mockData);
            expect(res3).toEqual(mockData);
            expect(api.listPrimitives).toHaveBeenCalledTimes(1);
        });

        test("returns null if workspaceName or graphURI is missing", async () => {
            expect(await store.getPrimitives("", GRAPH)).toBeNull();
            expect(await store.getPrimitives(WORKSPACE, "")).toBeNull();
            expect(api.listPrimitives).not.toHaveBeenCalled();
        });

        test("handles standard API errors gracefully", async () => {
            const error = new Error("API Failure");
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: undefined,
                error,
            });

            const result = await store.getPrimitives(WORKSPACE, GRAPH);

            expect(result).toBeNull();
            const state = get(store).byGraph.get(KEY);
            expect(state?.primitives.error).toBe(error);
        });

        test("catches unexpected exceptions from the fetcher", async () => {
            const exception = new Error("Unexpected crash");
            vi.mocked(api.listPrimitives).mockRejectedValue(exception);

            const result = await store.getPrimitives(WORKSPACE, GRAPH);

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

            await store.getPrimitives(WORKSPACE, GRAPH);
            store.invalidateGraph(WORKSPACE, GRAPH); // löscht alle drei

            await store.getPrimitives(WORKSPACE, GRAPH);
            expect(api.listPrimitives).toHaveBeenCalledTimes(2); // refetched
            expect(api.listDatatypes).toHaveBeenCalledTimes(0); // war nie gefetcht
        });
    });

    // -------------------------------------------------------------------------
    describe("Specific API Getters", () => {
        test("getDatatypes calls listDatatypes", async () => {
            const mockData = [{ id: "type1" }];
            vi.mocked(api.listDatatypes).mockResolvedValue({
                data: mockData as never,
                error: undefined,
            });

            const result = await store.getDatatypes(WORKSPACE, GRAPH);

            expect(result).toEqual(mockData);
            expect(api.listDatatypes).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE, graphURI: GRAPH },
            });
        });

        test("getStereotypes calls listStereotypes", async () => {
            const mockData = ["stereo1"];
            vi.mocked(api.listStereotypes).mockResolvedValue({
                data: mockData,
                error: undefined,
            });

            const result = await store.getStereotypes(WORKSPACE, GRAPH);

            expect(result).toEqual(mockData);
            expect(api.listStereotypes).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE, graphURI: GRAPH },
            });
        });
    });

    // -------------------------------------------------------------------------
    describe("Invalidation", () => {
        test("invalidateGraph removes specific graph from cache", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: ["uri1"] as never,
                error: undefined,
            });

            await store.getPrimitives(WORKSPACE, GRAPH);
            await store.getPrimitives(WORKSPACE, "other-graph");

            store.invalidateGraph(WORKSPACE, GRAPH);

            const state = get(store).byGraph;
            expect(state.has(KEY)).toBe(false);
            expect(state.has(`${WORKSPACE}::other-graph`)).toBe(true);
        });

        test("after invalidateGraph, the next call re-fetches", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: [] as never,
                error: undefined,
            });
            await store.getPrimitives(WORKSPACE, GRAPH);
            store.invalidateGraph(WORKSPACE, GRAPH);
            await store.getPrimitives(WORKSPACE, GRAPH);
            expect(api.listPrimitives).toHaveBeenCalledTimes(2);
        });

        test("invalidateWorkspace removes all graphs for a given workspace", async () => {
            vi.mocked(api.listPrimitives).mockResolvedValue({
                data: ["uri1"] as never,
                error: undefined,
            });

            await store.getPrimitives(WORKSPACE, GRAPH);
            await store.getPrimitives(WORKSPACE, "other-graph");
            await store.getPrimitives("workspaceB", GRAPH);

            store.invalidateWorkspace(WORKSPACE);

            const state = get(store).byGraph;
            expect(state.has(KEY)).toBe(false);
            expect(state.has(`${WORKSPACE}::other-graph`)).toBe(false);
            expect(state.has(`workspaceB::${GRAPH}`)).toBe(true);
        });
    });
});
