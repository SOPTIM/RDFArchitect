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
import { CustomDiagramDto } from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createCustomDiagramStore } from "../../src/lib/stores/diagramStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = "datasetA";
const DATASET_B = "datasetB";
const GRAPH_URI_1 = "http://example.org/graph1";
const GRAPH_URI_2 = "http://example.org/graph2";
const DIAGRAM_ID = "diag-456";

const MOCK_DATASET_DIAGRAMS: CustomDiagramDto[] = [
    { diagramId: "diag-1", name: "Dataset Diagram 1" } as CustomDiagramDto,
    { diagramId: "diag-2", name: "Dataset Diagram 2" } as CustomDiagramDto,
];

const MOCK_GRAPH_DIAGRAMS: CustomDiagramDto[] = [
    { diagramId: "diag-3", name: "Graph Diagram 1" } as CustomDiagramDto,
];

const MOCK_DIAGRAM_BODY = { diagramId: "diag-4", name: "New Diagram" };

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    getCustomDatasetDiagramList: vi.fn(),
    getCustomGraphDiagramList: vi.fn(),
    replaceCustomDatasetDiagram: vi.fn(),
    replaceCustomGraphDiagram: vi.fn(),
    deleteCustomDatasetDiagram: vi.fn(),
    deleteCustomGraphDiagram: vi.fn(),
    addToCustomDatasetDiagram: vi.fn(),
    addToCustomGraphDiagram: vi.fn(),
    removeFromCustomDatasetDiagram: vi.fn(),
    removeFromDiagram: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("customDiagramStore", () => {
    let store: ReturnType<typeof createCustomDiagramStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createCustomDiagramStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with empty maps", () => {
            const state = get(store);
            expect(state.datasetLists.size).toBe(0);
            expect(state.graphLists.size).toBe(0);
        });
    });

    // -------------------------------------------------------------------------
    describe("getDatasetDiagrams", () => {
        test("returns null for empty datasetName without calling API", async () => {
            const result = await store.getDatasetDiagrams("");
            expect(result).toBeNull();
            expect(api.getCustomDatasetDiagramList).not.toHaveBeenCalled();
        });

        test("fetches and caches diagrams for a dataset", async () => {
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });

            const result = await store.getDatasetDiagrams(DATASET_A);
            await store.getDatasetDiagrams(DATASET_A);

            expect(result).toEqual(MOCK_DATASET_DIAGRAMS);
            expect(api.getCustomDatasetDiagramList).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses the cache and re-fetches", async () => {
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });

            await store.getDatasetDiagrams(DATASET_A);
            await store.getDatasetDiagrams(DATASET_A, true);

            expect(api.getCustomDatasetDiagramList).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: undefined,
                error: new Error("Network error"),
            });

            const result = await store.getDatasetDiagrams(DATASET_A);
            expect(result).toBeNull();
        });

        test("caches are independent for different datasets", async () => {
            vi.mocked(api.getCustomDatasetDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_DATASET_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                });

            const resultA = await store.getDatasetDiagrams(DATASET_A);
            const resultB = await store.getDatasetDiagrams(DATASET_B);

            expect(resultA).toEqual(MOCK_DATASET_DIAGRAMS);
            expect(resultB).toEqual(MOCK_GRAPH_DIAGRAMS);
            expect(api.getCustomDatasetDiagramList).toHaveBeenCalledTimes(2);
        });
    });

    // -------------------------------------------------------------------------
    describe("getGraphDiagrams", () => {
        test("returns null if datasetName is empty", async () => {
            const result = await store.getGraphDiagrams("", GRAPH_URI_1);
            expect(result).toBeNull();
            expect(api.getCustomGraphDiagramList).not.toHaveBeenCalled();
        });

        test("returns null if graphURI is empty", async () => {
            const result = await store.getGraphDiagrams(DATASET_A, "");
            expect(result).toBeNull();
            expect(api.getCustomGraphDiagramList).not.toHaveBeenCalled();
        });

        test("returns null if both datasetName and graphURI are empty", async () => {
            const result = await store.getGraphDiagrams("", "");
            expect(result).toBeNull();
            expect(api.getCustomGraphDiagramList).not.toHaveBeenCalled();
        });

        test("fetches and caches diagrams for a dataset+graph pair", async () => {
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });

            const result = await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            expect(result).toEqual(MOCK_GRAPH_DIAGRAMS);
            expect(api.getCustomGraphDiagramList).toHaveBeenCalledTimes(1);
        });

        test("treats different graphURIs under the same dataset as separate cache entries", async () => {
            vi.mocked(api.getCustomGraphDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_DATASET_DIAGRAMS,
                    error: undefined,
                });

            const result1 = await store.getGraphDiagrams(
                DATASET_A,
                GRAPH_URI_1,
            );
            const result2 = await store.getGraphDiagrams(
                DATASET_A,
                GRAPH_URI_2,
            );

            expect(result1).toEqual(MOCK_GRAPH_DIAGRAMS);
            expect(result2).toEqual(MOCK_DATASET_DIAGRAMS);
            expect(api.getCustomGraphDiagramList).toHaveBeenCalledTimes(2);
        });

        test("treats same graphURI under different datasets as separate cache entries", async () => {
            vi.mocked(api.getCustomGraphDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_DATASET_DIAGRAMS,
                    error: undefined,
                });

            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_B, GRAPH_URI_1);

            expect(api.getCustomGraphDiagramList).toHaveBeenCalledTimes(2);
        });

        test("force=true bypasses the cache and re-fetches", async () => {
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });

            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1, true);

            expect(api.getCustomGraphDiagramList).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: undefined,
                error: new Error("Server error"),
            });

            const result = await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("saveDatasetDiagram", () => {
        test("calls API with correct arguments updates cache on success", async () => {
            vi.mocked(api.replaceCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            // Seed the cache
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.saveDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                MOCK_DIAGRAM_BODY,
            );

            expect(result.error).toBeNull();
            expect(api.replaceCustomDatasetDiagram).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, diagramId: DIAGRAM_ID },
                body: MOCK_DIAGRAM_BODY,
            });

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);

            expect(toastStore.success).toHaveBeenCalledWith(
                "Diagram saved",
                "Dataset diagram was saved.",
            );
        });

        test("returns error and does not invalidate cache on API failure", async () => {
            const error = new Error("Save failed");
            vi.mocked(api.replaceCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            // Seed the cache
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.saveDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                MOCK_DIAGRAM_BODY,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Save failed",
                "Could not save dataset diagram.",
            );

            // Cache should remain intact
            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("saveGraphDiagram", () => {
        test("calls API with correct arguments and updates cache on success", async () => {
            vi.mocked(api.replaceCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            // Seed the cache
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.saveGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                MOCK_DIAGRAM_BODY,
            );

            expect(result.error).toBeNull();
            expect(api.replaceCustomGraphDiagram).toHaveBeenCalledWith({
                path: {
                    datasetName: DATASET_A,
                    graphURI: GRAPH_URI_1,
                    diagramId: DIAGRAM_ID,
                },
                body: MOCK_DIAGRAM_BODY,
            });

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );

            expect(toastStore.success).toHaveBeenCalledWith(
                "Diagram saved",
                "Graph diagram was saved.",
            );
        });

        test("returns error and does not invalidate cache on API failure", async () => {
            const error = new Error("Save failed");
            vi.mocked(api.replaceCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.saveGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                MOCK_DIAGRAM_BODY,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Save failed",
                "Could not save graph diagram.",
            );

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("deleteDatasetDiagram", () => {
        test("calls API with correct arguments and updates dataset cache on success", async () => {
            vi.mocked(api.deleteCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.deleteDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
            );

            expect(result.error).toBeNull();
            expect(api.deleteCustomDatasetDiagram).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, diagramId: DIAGRAM_ID },
            });

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);

            expect(toastStore.success).toHaveBeenCalledWith(
                "Diagram deleted",
                "Dataset diagram was removed.",
            );
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Delete failed");
            vi.mocked(api.deleteCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.deleteDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Delete failed",
                "Could not delete dataset diagram.",
            );

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("deleteGraphDiagram", () => {
        test("calls API with correct arguments and updates graph cache on success", async () => {
            vi.mocked(api.deleteCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.deleteGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
            );

            expect(result.error).toBeNull();
            expect(api.deleteCustomGraphDiagram).toHaveBeenCalledWith({
                path: {
                    datasetName: DATASET_A,
                    graphURI: GRAPH_URI_1,
                    diagramId: DIAGRAM_ID,
                },
            });

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );

            expect(toastStore.success).toHaveBeenCalledWith(
                "Diagram deleted",
                "Graph diagram was removed.",
            );
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Delete failed");
            vi.mocked(api.deleteCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.deleteGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Delete failed",
                "Could not delete graph diagram.",
            );

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("addClassesToDatasetDiagram", () => {
        const CLASSES = ["ClassA", "ClassB"];

        test("calls API with correct arguments and invalidates dataset cache on success", async () => {
            vi.mocked(api.addToCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.addClassesToDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBeNull();
            expect(api.addToCustomDatasetDiagram).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, diagramId: DIAGRAM_ID },
                body: CLASSES,
            });

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(false);
        });

        test("does not show a success toast on success", async () => {
            vi.mocked(api.addToCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.addClassesToDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(toastStore.success).not.toHaveBeenCalled();
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Add failed");
            vi.mocked(api.addToCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.addClassesToDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Update failed",
                "Could not add classes to diagram.",
            );

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("addClassesToGraphDiagram", () => {
        const CLASSES = ["ClassA", "ClassB"];

        test("calls API with correct arguments and invalidates graph cache on success", async () => {
            vi.mocked(api.addToCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.addClassesToGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBeNull();
            expect(api.addToCustomGraphDiagram).toHaveBeenCalledWith({
                path: {
                    datasetName: DATASET_A,
                    graphURI: GRAPH_URI_1,
                    diagramId: DIAGRAM_ID,
                },
                body: CLASSES,
            });

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                false,
            );
        });

        test("does not show a success toast on success", async () => {
            vi.mocked(api.addToCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.addClassesToGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(toastStore.success).not.toHaveBeenCalled();
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Add failed");
            vi.mocked(api.addToCustomGraphDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.addClassesToGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Update failed",
                "Could not add classes to diagram.",
            );

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("removeClassesFromDatasetDiagram", () => {
        const CLASS_IDS = ["ClassA", "ClassB"];

        test("calls API with correct arguments and invalidates dataset cache on success", async () => {
            vi.mocked(api.removeFromCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.removeClassesFromDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                CLASS_IDS,
            );

            expect(result.error).toBeNull();
            expect(api.removeFromCustomDatasetDiagram).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, diagramId: DIAGRAM_ID },
                body: CLASS_IDS,
            });

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(false);
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Remove failed");
            vi.mocked(api.removeFromCustomDatasetDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            const result = await store.removeClassesFromDatasetDiagram(
                DATASET_A,
                DIAGRAM_ID,
                CLASS_IDS,
            );

            expect(result.error).toBe(error);

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("removeClassesFromGraphDiagram", () => {
        const CLASSES = ["ClassA"];

        test("calls API with correct arguments (single classId) and invalidates graph cache on success", async () => {
            vi.mocked(api.removeFromDiagram).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.removeClassesFromGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBeNull();
            expect(api.removeFromDiagram).toHaveBeenCalledWith({
                path: {
                    datasetName: DATASET_A,
                    graphURI: GRAPH_URI_1,
                    diagramId: DIAGRAM_ID,
                },
                body: CLASSES,
            });

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                false,
            );
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Remove failed");
            vi.mocked(api.removeFromDiagram).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            const result = await store.removeClassesFromGraphDiagram(
                DATASET_A,
                GRAPH_URI_1,
                DIAGRAM_ID,
                CLASSES,
            );

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Update failed",
                "Could not remove class from diagram.",
            );

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateDataset", () => {
        test("removes the dataset list cache for the given dataset", async () => {
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            await store.getDatasetDiagrams(DATASET_A);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(false);
        });

        test("also removes all graph list cache entries belonging to that dataset", async () => {
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_2);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                false,
            );
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_2}`)).toBe(
                false,
            );
        });

        test("does not affect other datasets or their graph lists", async () => {
            vi.mocked(api.getCustomDatasetDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_DATASET_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_DATASET_DIAGRAMS,
                    error: undefined,
                });
            vi.mocked(api.getCustomGraphDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                });

            await store.getDatasetDiagrams(DATASET_A);
            await store.getDatasetDiagrams(DATASET_B);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_B, GRAPH_URI_1);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(false);
            expect(state.datasetLists.has(DATASET_B)).toBe(true);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                false,
            );
            expect(state.graphLists.has(`${DATASET_B}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });

        test("does not incorrectly match a dataset whose name is a prefix of another", async () => {
            // e.g. invalidating "data" should not clear "datasetA"
            const SHORT_DATASET = "data";
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1); // key: "datasetA::..."

            store.invalidateDataset(SHORT_DATASET);

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                true,
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateGraph", () => {
        test("removes only the specific graph list entry", async () => {
            vi.mocked(api.getCustomGraphDiagramList)
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                })
                .mockResolvedValueOnce({
                    data: MOCK_GRAPH_DIAGRAMS,
                    error: undefined,
                });

            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_2);

            store.invalidateGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_1}`)).toBe(
                false,
            );
            expect(state.graphLists.has(`${DATASET_A}::${GRAPH_URI_2}`)).toBe(
                true,
            );
        });

        test("does not affect the dataset list cache", async () => {
            vi.mocked(api.getCustomDatasetDiagramList).mockResolvedValue({
                data: MOCK_DATASET_DIAGRAMS,
                error: undefined,
            });
            vi.mocked(api.getCustomGraphDiagramList).mockResolvedValue({
                data: MOCK_GRAPH_DIAGRAMS,
                error: undefined,
            });

            await store.getDatasetDiagrams(DATASET_A);
            await store.getGraphDiagrams(DATASET_A, GRAPH_URI_1);

            store.invalidateGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.datasetLists.has(DATASET_A)).toBe(true);
        });
    });
});
