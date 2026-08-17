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
import { createOntologyStore } from "../../src/lib/stores/ontologyStore";
import { makeGraphKey } from "../../src/lib/stores/storeHelpers";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = "datasetA";
const DATASET_B = "datasetB";
const GRAPH_URI_1 = "http://example.org/graph1";
const GRAPH_URI_2 = "http://example.org/graph2";

const MOCK_ONTOLOGY: api.OntologyDto = {
    uuid: "123e4567-e89b-12d3-a456-426614174000",
    namespace: "http://example.org/ontology",
    entries: [],
};

const MOCK_ONTOLOGY_WITH_ENTRIES: api.OntologyDto = {
    ...MOCK_ONTOLOGY,
    entries: [{ id: "entry-1" } as api.OntologyEntry],
};

const MOCK_KNOWN_FIELDS: api.OntologyField[] = [
    { name: "field1" } as api.OntologyField,
    { name: "field2" } as api.OntologyField,
];

const MOCK_ENTRIES: api.OntologyEntry[] = [
    { id: "entry-1" } as api.OntologyEntry,
    { id: "entry-2" } as api.OntologyEntry,
];

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    getOntology: vi.fn(),
    createOntology: vi.fn(),
    replaceOntology: vi.fn(),
    getKnownOntologyFields: vi.fn(),
    getOntologyEntries: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ontologyStore", () => {
    let store: ReturnType<typeof createOntologyStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createOntologyStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with empty byGraph map and empty knownFields slot", () => {
            const state = get(store);
            expect(state.byGraph.size).toBe(0);
            expect(state.knownFields.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("getOntologyForGraph", () => {
        test("returns null if datasetName is empty", async () => {
            const result = await store.getOntologyForGraph("", GRAPH_URI_1);
            expect(result).toBeNull();
            expect(api.getOntology).not.toHaveBeenCalled();
        });

        test("returns null if graphURI is empty", async () => {
            const result = await store.getOntologyForGraph(DATASET_A, "");
            expect(result).toBeNull();
            expect(api.getOntology).not.toHaveBeenCalled();
        });

        test("returns null if both args are empty", async () => {
            const result = await store.getOntologyForGraph("", "");
            expect(result).toBeNull();
            expect(api.getOntology).not.toHaveBeenCalled();
        });

        test("fetches and caches ontology for a given dataset+graph", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({ data: MOCK_ONTOLOGY, error: undefined });

            const result = await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            expect(result).toEqual(MOCK_ONTOLOGY);
            expect(api.getOntology).toHaveBeenCalledTimes(1);
            expect(api.getOntology).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
            });
        });

        test("force=true bypasses the cache", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({ data: MOCK_ONTOLOGY, error: undefined });

            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1, true);

            expect(api.getOntology).toHaveBeenCalledTimes(2);
        });

        test("treats different graph URIs as separate cache entries", async () => {
            vi.mocked(api.getOntology)
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY_WITH_ENTRIES, error: undefined });

            const r1 = await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            const r2 = await store.getOntologyForGraph(DATASET_A, GRAPH_URI_2);

            expect(r1).toEqual(MOCK_ONTOLOGY);
            expect(r2).toEqual(MOCK_ONTOLOGY_WITH_ENTRIES);
            expect(api.getOntology).toHaveBeenCalledTimes(2);
        });

        test("treats same graph URI under different datasets as separate cache entries", async () => {
            vi.mocked(api.getOntology)
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY_WITH_ENTRIES, error: undefined });

            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_B, GRAPH_URI_1);

            expect(api.getOntology).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: undefined,
                error: new Error("Network error"),
            });

            const result = await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("getKnownFields", () => {
        test("fetches and caches known fields", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: MOCK_KNOWN_FIELDS,
                error: undefined,
            });

            const result = await store.getKnownFields();
            await store.getKnownFields();

            expect(result).toEqual(MOCK_KNOWN_FIELDS);
            expect(api.getKnownOntologyFields).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses the cache", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: MOCK_KNOWN_FIELDS,
                error: undefined,
            });

            await store.getKnownFields();
            await store.getKnownFields(true);

            expect(api.getKnownOntologyFields).toHaveBeenCalledTimes(2);
        });

        test("known fields cache is independent of graph-level cache", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: MOCK_KNOWN_FIELDS,
                error: undefined,
            });
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });

            await store.getKnownFields();
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.knownFields.data).toEqual(MOCK_KNOWN_FIELDS);
            expect(state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data).toEqual(MOCK_ONTOLOGY);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: undefined,
                error: new Error("Fields fetch failed"),
            });

            const result = await store.getKnownFields();
            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("generateOntologyEntries", () => {
        test("calls API with correct arguments", async () => {
            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: MOCK_ENTRIES,
                error: undefined,
            });

            await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            expect(api.getOntologyEntries).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
            });
        });

        test("patches only the entries field in the cached DTO, preserving other fields", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: MOCK_ENTRIES,
                error: undefined,
            });
            await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.entries).toEqual(MOCK_ENTRIES);
            // Other fields from the original fetch should still be present
            expect(cached?.uuid).toBe(MOCK_ONTOLOGY.uuid);
            expect(cached?.namespace).toBe(MOCK_ONTOLOGY.namespace);
        });

        test("writes entries into cache even if there was no prior fetch", async () => {
            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: MOCK_ENTRIES,
                error: undefined,
            });

            await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.entries).toEqual(MOCK_ENTRIES);
        });

        test("treats an empty entries array from the API as a valid result", async () => {
            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: [],
                error: undefined,
            });

            const result = await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            expect(result.error).toBeNull();
            expect(result.data).toEqual([]);
        });

        test("treats a null/undefined data response as an empty array", async () => {
            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            expect(result.error).toBeNull();
            expect(result.data).toEqual([]);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.entries).toEqual([]);
        });

        test("returns error and does not patch cache on API failure", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const error = new Error("Generate failed");
            vi.mocked(api.getOntologyEntries).mockResolvedValue({
                data: undefined,
                error,
            });

            const result = await store.generateOntologyEntries(DATASET_A, GRAPH_URI_1);

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_ONTOLOGY);
        });
    });

    // -------------------------------------------------------------------------
    describe("createOntology", () => {
        test("calls API with correct arguments", async () => {
            vi.mocked(api.createOntology).mockResolvedValue({ data: undefined, error: undefined });

            await store.createOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY);

            expect(api.createOntology).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
                body: MOCK_ONTOLOGY,
            });
        });

        test("patches the cache with the sent DTO on success", async () => {
            vi.mocked(api.createOntology).mockResolvedValue({ data: undefined, error: undefined });

            await store.createOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toMatchObject(MOCK_ONTOLOGY);
        });

        test("merges patch into existing cached data rather than replacing it", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY_WITH_ENTRIES,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const partialUpdate: api.OntologyDto = { namespace: "http://example.org/newOntology" };
            vi.mocked(api.createOntology).mockResolvedValue({ data: undefined, error: undefined });
            await store.createOntology(DATASET_A, GRAPH_URI_1, partialUpdate);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.namespace).toBe("http://example.org/newOntology");
            // Fields not present in the patch should survive from the prior fetch
            expect(cached?.entries).toEqual(MOCK_ONTOLOGY_WITH_ENTRIES.entries);
        });

        test("returns error and does not patch cache on API failure", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const error = new Error("Create failed");
            vi.mocked(api.createOntology).mockResolvedValue({ data: undefined, error });

            const result = await store.createOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY_WITH_ENTRIES);

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_ONTOLOGY);
        });
    });

    // -------------------------------------------------------------------------
    describe("replaceOntology", () => {
        test("calls API with correct arguments", async () => {
            vi.mocked(api.replaceOntology).mockResolvedValue({ data: undefined, error: undefined });

            await store.replaceOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY);

            expect(api.replaceOntology).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
                body: MOCK_ONTOLOGY,
            });
        });

        test("patches the cache with the sent DTO on success", async () => {
            vi.mocked(api.replaceOntology).mockResolvedValue({ data: undefined, error: undefined });

            await store.replaceOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toMatchObject(MOCK_ONTOLOGY);
        });

        test("merges patch into existing cached data rather than replacing it", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY_WITH_ENTRIES,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const partialUpdate: api.OntologyDto = { namespace: "http://example.org/newOntology" };
            vi.mocked(api.replaceOntology).mockResolvedValue({ data: undefined, error: undefined });
            await store.replaceOntology(DATASET_A, GRAPH_URI_1, partialUpdate);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.namespace).toBe("http://example.org/newOntology");
            expect(cached?.entries).toEqual(MOCK_ONTOLOGY_WITH_ENTRIES.entries);
        });

        test("returns error and does not patch cache on API failure", async () => {
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            const error = new Error("Replace failed");
            vi.mocked(api.replaceOntology).mockResolvedValue({ data: undefined, error });

            const result = await store.replaceOntology(DATASET_A, GRAPH_URI_1, MOCK_ONTOLOGY_WITH_ENTRIES);

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_ONTOLOGY);
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateGraph", () => {
        test("removes only the specific graph entry from the cache", async () => {
            vi.mocked(api.getOntology)
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY_WITH_ENTRIES, error: undefined });

            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_2);

            store.invalidateGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_2))).toBe(true);
        });

        test("does not affect the knownFields cache", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: MOCK_KNOWN_FIELDS,
                error: undefined,
            });
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });

            await store.getKnownFields();
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);

            store.invalidateGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.knownFields.data).toEqual(MOCK_KNOWN_FIELDS);
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateDataset", () => {
        test("removes all graph entries belonging to the dataset", async () => {
            vi.mocked(api.getOntology)
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY_WITH_ENTRIES, error: undefined });

            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_2);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_2))).toBe(false);
        });

        test("does not affect entries from other datasets", async () => {
            vi.mocked(api.getOntology)
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_ONTOLOGY, error: undefined });

            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1);
            await store.getOntologyForGraph(DATASET_B, GRAPH_URI_1);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_B, GRAPH_URI_1))).toBe(true);
        });

        test("does not affect the knownFields cache", async () => {
            vi.mocked(api.getKnownOntologyFields).mockResolvedValue({
                data: MOCK_KNOWN_FIELDS,
                error: undefined,
            });

            await store.getKnownFields();
            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.knownFields.data).toEqual(MOCK_KNOWN_FIELDS);
        });

        test("does not incorrectly match a dataset whose name is a prefix of another", async () => {
            const SHORT_DATASET = "data";
            vi.mocked(api.getOntology).mockResolvedValue({
                data: MOCK_ONTOLOGY,
                error: undefined,
            });
            await store.getOntologyForGraph(DATASET_A, GRAPH_URI_1); // key: "datasetA::..."

            store.invalidateDataset(SHORT_DATASET); // prefix "data::" should not match "datasetA::"

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(true);
        });
    });
});