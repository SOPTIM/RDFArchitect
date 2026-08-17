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
import { CimPrefixPair } from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createDatasetStore } from "../../src/lib/stores/datasetStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = {
    label: "datasetA",
    readOnly: false,
    prefixes: [{ prefix: "ex", substitutedPrefix: "http://example.org/" }],
};

const DATASET_B = {
    label: "datasetB",
    readOnly: true,
    prefixes: [],
};

/** Raw shape returned by the backend */
function makeApiDataset(
    label: string,
    readOnly = false,
    prefixes: CimPrefixPair[] = [],
) {
    return { name: label, readOnly, prefixes };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockListDatasetsSuccess(
    ...datasets: ReturnType<typeof makeApiDataset>[]
) {
    vi.mocked(api.listDatasets).mockResolvedValue({
        data: datasets,
        error: undefined,
    });
}

function mockListDatasetsError(error = new Error("network error")) {
    vi.mocked(api.listDatasets).mockResolvedValue({ data: undefined, error });
}

// Mock must be declared before the import of the module under test
vi.mock("$lib/api/generated", () => ({
    listDatasets: vi.fn(),
    deleteDataset: vi.fn(),
    replaceNamespaces: vi.fn(),
    enableEditing: vi.fn(),
    disableEditing: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("DatasetStore", () => {
    let store: ReturnType<typeof createDatasetStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createDatasetStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with empty slot values", () => {
            const state = get(store);
            expect(state.data).toBeNull();
            expect(state.pending).toBeNull();
            expect(state.fetchedAt).toBeNull();
            expect(state.error).toBeNull();
        });
    });
    // -------------------------------------------------------------------------
    describe("getDatasets", () => {
        test("maps API response to DatasetInfo shape", async () => {
            mockListDatasetsSuccess(
                makeApiDataset("datasetA", false, [
                    { prefix: "ex", substitutedPrefix: "http://example.org/" },
                ]),
                makeApiDataset("datasetB", true),
            );

            const result = await store.getDatasets();

            expect(result).toEqual([DATASET_A, DATASET_B]);
        });

        test("returns null when the API returns an error", async () => {
            mockListDatasetsError();

            const result = await store.getDatasets();

            expect(result).toBeNull();
        });

        test("returns cached data on second call without re-fetching", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));

            await store.getDatasets();
            await store.getDatasets();

            expect(api.listDatasets).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses cache and re-fetches", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));

            await store.getDatasets();
            await store.getDatasets(true);

            expect(api.listDatasets).toHaveBeenCalledTimes(2);
        });

        test("treats missing name/prefixes as empty defaults", async () => {
            vi.mocked(api.listDatasets).mockResolvedValue({
                data: [
                    { name: undefined, readOnly: false, prefixes: undefined },
                ],
                error: undefined,
            });

            const result = await store.getDatasets();

            expect(result?.[0]).toEqual({
                label: "",
                readOnly: false,
                prefixes: [],
            });
        });
    });

    // -------------------------------------------------------------------------
    describe("isReadOnly", () => {
        test("returns readOnly flag for a known dataset", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA", true));

            expect(await store.isReadOnly("datasetA")).toBe(true);
        });

        test("returns null for an unknown dataset", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));

            expect(await store.isReadOnly("doesNotExist")).toBeNull();
        });
        test("returns null if getDatasets fails to fetch data", async () => {
            mockListDatasetsError(new Error("Network offline"));

            const result = await store.isReadOnly("datasetA");

            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("getNamespaces", () => {
        test("returns prefixes for a known dataset", async () => {
            mockListDatasetsSuccess(
                makeApiDataset("datasetA", false, [
                    { prefix: "ex", substitutedPrefix: "http://example.org/" },
                ]),
            );

            const prefixes = await store.getNamespaces("datasetA");

            expect(prefixes).toEqual([
                { prefix: "ex", substitutedPrefix: "http://example.org/" },
            ]);
        });

        test("returns empty array for unknown dataset", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));

            expect(await store.getNamespaces("unknown")).toEqual([]);
        });
        test("returns an empty array if getDatasets fails to fetch data", async () => {
            mockListDatasetsError(new Error("Network offline"));

            const result = await store.getNamespaces("datasetA");

            expect(result).toEqual([]);
        });
    });

    // -------------------------------------------------------------------------
    describe("remove", () => {
        test("removes the dataset from the store on success", async () => {
            mockListDatasetsSuccess(
                makeApiDataset("datasetA"),
                makeApiDataset("datasetB"),
            );
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getDatasets();
            await store.remove("datasetA");

            const state = get(store);
            expect(state.data?.map(d => d.label)).toEqual(["datasetB"]);
        });

        test("returns error and shows error toast when API fails", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));
            const err = new Error("server error");
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getDatasets();
            const result = await store.remove("datasetA");

            expect(result.error).toBe(err);
            expect(toastStore.error).toHaveBeenCalledOnce();
        });

        test("shows success toast on successful deletion", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getDatasets();
            await store.remove("datasetA");

            expect(toastStore.success).toHaveBeenCalledOnce();
        });
        test("does not crash if called before datasets are fetched", async () => {
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            // Notice we are NOT calling await store.getDatasets() first
            const result = await store.remove("datasetA");

            expect(result.error).toBeNull();
            const state = get(store);
            expect(state.data).toBeNull(); // Data remains null, safely bypassed
        });
    });

    // -------------------------------------------------------------------------
    describe("saveNamespaces", () => {
        const newPrefixes = [
            { prefix: "new", substitutedPrefix: "http://new.org/" },
        ];

        test("updates prefixes in the store on success", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getDatasets();
            await store.saveNamespaces("datasetA", newPrefixes);

            const state = get(store);
            expect(
                state.data?.find(d => d.label === "datasetA")?.prefixes,
            ).toEqual(newPrefixes);
        });

        test("returns error and does not update store when API fails", async () => {
            mockListDatasetsSuccess(
                makeApiDataset("datasetA", false, [
                    { prefix: "old", substitutedPrefix: "http://old.org/" },
                ]),
            );
            const err = new Error("save failed");
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getDatasets();
            const result = await store.saveNamespaces("datasetA", newPrefixes);

            expect(result.error).toBe(err);
            // original prefixes unchanged
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "datasetA")?.prefixes,
            ).toEqual([
                { prefix: "old", substitutedPrefix: "http://old.org/" },
            ]);
        });
        test("does not crash if called before datasets are fetched", async () => {
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.saveNamespaces("datasetA", newPrefixes);

            expect(result.error).toBeNull();
            const state = get(store);
            expect(state.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("updateReadonly", () => {
        test("calls disableEditing when readOnly=true and updates store", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA", false));
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getDatasets();
            await store.updateReadonly("datasetA", true);

            expect(api.disableEditing).toHaveBeenCalledOnce();
            expect(api.enableEditing).not.toHaveBeenCalled();
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "datasetA")?.readOnly,
            ).toBe(true);
        });

        test("calls enableEditing when readOnly=false and updates store", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA", true));
            vi.mocked(api.enableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getDatasets();
            await store.updateReadonly("datasetA", false);

            expect(api.enableEditing).toHaveBeenCalledOnce();
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "datasetA")?.readOnly,
            ).toBe(false);
        });

        test("returns error and shows error toast when API fails", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA", false));
            const err = new Error("forbidden");
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getDatasets();
            const result = await store.updateReadonly("datasetA", true);

            expect(result.error).toBe(err);
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
        test("does not crash if called before datasets are fetched", async () => {
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.updateReadonly("datasetA", true);

            expect(result.error).toBeNull();
            const state = get(store);
            expect(state.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidate", () => {
        test("clears cached data so the next getDatasets re-fetches", async () => {
            mockListDatasetsSuccess(makeApiDataset("datasetA"));

            await store.getDatasets();
            store.invalidate();
            await store.getDatasets();

            expect(api.listDatasets).toHaveBeenCalledTimes(2);
        });

        test("store data is null after invalidation", () => {
            store.invalidate();

            const state = get(store);
            expect(state.data).toBeNull();
            expect(state.fetchedAt).toBeNull();
        });
    });
});
