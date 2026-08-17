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
import { createGraphStore } from "../../src/lib/stores/graphStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = "datasetA";
const DATASET_B = "datasetB";
const GRAPH_URI = "http://example.org/graph1";
const GRAPH_URI_2 = "http://example.org/graph2";

const MOCK_GRAPHS: api.GraphDto[] = [
    makeGraphDto(GRAPH_URI),
    makeGraphDto(GRAPH_URI_2),
];

const MOCK_FILE = new File(["content"], "graph.ttl", { type: "text/turtle" });

const MOCK_BULK_RESPONSE: api.GraphBulkImportResponse = {
    importedGraphUris: [GRAPH_URI],
    failedImports: [],
    warnings: [],
};

// Helper to produce a GraphDto whose URI reconstructs to the given string.
function makeGraphDto(uri: string): api.GraphDto {
    return {
        uri: { prefix: uri, suffix: "" },
    } as api.GraphDto;
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    listGraphs: vi.fn(),
    deleteGraph: vi.fn(),
    replaceGraphs: vi.fn(),
    replaceGraph: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("graphStore", () => {
    let store: ReturnType<typeof createGraphStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createGraphStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with an empty graphs map", () => {
            const state = get(store);
            expect(state.graphs.size).toBe(0);
        });
    });

    // -------------------------------------------------------------------------
    describe("getGraphs", () => {
        test("returns null for empty datasetName without calling API", async () => {
            const result = await store.getGraphs("");
            expect(result).toBeNull();
            expect(api.listGraphs).not.toHaveBeenCalled();
        });

        test("fetches and caches graphs for a dataset", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });

            const result = await store.getGraphs(DATASET_A);
            await store.getGraphs(DATASET_A);

            expect(result).toEqual(MOCK_GRAPHS);
            expect(api.listGraphs).toHaveBeenCalledTimes(1);
            expect(api.listGraphs).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A },
            });
        });

        test("force=true bypasses the cache and re-fetches", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });

            await store.getGraphs(DATASET_A);
            await store.getGraphs(DATASET_A, true);

            expect(api.listGraphs).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: undefined,
                error: new Error("Network error"),
            });

            const result = await store.getGraphs(DATASET_A);
            expect(result).toBeNull();
        });

        test("caches are independent for different datasets", async () => {
            vi.mocked(api.listGraphs)
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined })
                .mockResolvedValueOnce({ data: [makeGraphDto(GRAPH_URI_2)], error: undefined });

            const resultA = await store.getGraphs(DATASET_A);
            const resultB = await store.getGraphs(DATASET_B);

            expect(resultA).toEqual(MOCK_GRAPHS);
            expect(resultB).toEqual([makeGraphDto(GRAPH_URI_2)]);
            expect(api.listGraphs).toHaveBeenCalledTimes(2);
        });
    });

    // -------------------------------------------------------------------------
    describe("addEmptyGraph", () => {
        test("calls API with correct arguments and invalidates dataset cache on success", async () => {
            vi.mocked(api.replaceGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            const result = await store.addEmptyGraph(DATASET_A, GRAPH_URI);

            expect(result.error).toBeNull();
            expect(api.replaceGraph).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI },
            });

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(false);

            expect(toastStore.success).toHaveBeenCalledWith(
                "Schema created",
                `"${GRAPH_URI}" was added to "${DATASET_A}".`,
            );
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Create failed");
            vi.mocked(api.replaceGraph).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            const result = await store.addEmptyGraph(DATASET_A, GRAPH_URI);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Create failed",
                `Could not create schema "${GRAPH_URI}".`,
            );

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("importGraphs", () => {
        test("returns an error immediately if no files are provided", async () => {
            const result = await store.importGraphs(DATASET_A, [], [GRAPH_URI]);

            expect(result.error).toBeInstanceOf(Error);
            expect(api.replaceGraphs).not.toHaveBeenCalled();
        });

        test("calls API with correct arguments on valid input", async () => {
            vi.mocked(api.replaceGraphs).mockResolvedValue({
                data: MOCK_BULK_RESPONSE,
                error: undefined,
            });

            await store.importGraphs(DATASET_A, [MOCK_FILE], [GRAPH_URI]);

            expect(api.replaceGraphs).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A },
                body: { files: [MOCK_FILE] },
                query: { graphUris: [GRAPH_URI] },
            });
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Server error");
            vi.mocked(api.replaceGraphs).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            const result = await store.importGraphs(DATASET_A, [MOCK_FILE], [GRAPH_URI]);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Import failed",
                `Could not import into "${DATASET_A}".`,
            );

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(true);
        });

        test("invalidates dataset cache on successful import", async () => {
            vi.mocked(api.replaceGraphs).mockResolvedValue({
                data: MOCK_BULK_RESPONSE,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            await store.importGraphs(DATASET_A, [MOCK_FILE], [GRAPH_URI]);

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(false);
        });
    });

    // -------------------------------------------------------------------------
    describe("remove (removeGraph)", () => {
        test("calls API with correct arguments on success", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            const result = await store.remove(DATASET_A, GRAPH_URI);

            expect(result.error).toBeNull();
            expect(api.deleteGraph).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI },
            });
        });

        test("removes only the matching graph from the cached list", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            await store.remove(DATASET_A, GRAPH_URI);

            const state = get(store);
            const cached = state.graphs.get(DATASET_A)?.data;
            expect(cached).toBeDefined();
            expect(cached?.some(g => `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}` === GRAPH_URI)).toBe(false);
            expect(cached?.some(g => `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}` === GRAPH_URI_2)).toBe(true);
        });

        test("keeps the cache populated after removal (does not invalidate)", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            await store.remove(DATASET_A, GRAPH_URI);

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(true);
        });

        test("returns error and preserves the full cached list on API failure", async () => {
            const error = new Error("Delete failed");
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            const result = await store.remove(DATASET_A, GRAPH_URI);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Delete failed",
                `Could not delete schema "${GRAPH_URI}".`,
            );

            const state = get(store);
            expect(state.graphs.get(DATASET_A)?.data).toEqual(MOCK_GRAPHS);
        });

        test("handles removal when nothing is cached without throwing", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            // No prior getGraphs call — cache is empty
            const result = await store.remove(DATASET_A, GRAPH_URI);

            expect(result.error).toBeNull();
            // Cache entry data should be null since there was nothing to filter
            const state = get(store);
            expect(state.graphs.get(DATASET_A)?.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateDataset", () => {
        test("removes the dataset entry from the cache", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(DATASET_A);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(false);
        });

        test("does not affect other datasets", async () => {
            vi.mocked(api.listGraphs)
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined });

            await store.getGraphs(DATASET_A);
            await store.getGraphs(DATASET_B);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.graphs.has(DATASET_A)).toBe(false);
            expect(state.graphs.has(DATASET_B)).toBe(true);
        });
    });
});
