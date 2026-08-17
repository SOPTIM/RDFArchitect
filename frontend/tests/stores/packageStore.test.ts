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
import { createPackageStore } from "../../src/lib/stores/packageStore";
import { makeGraphKey } from "../../src/lib/stores/storeHelpers";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const DATASET_A = "datasetA";
const DATASET_B = "datasetB";
const GRAPH_URI_1 = "http://example.org/graph1";
const GRAPH_URI_2 = "http://example.org/graph2";

const PKG_UUID_1 = "uuid-001";
const PKG_UUID_2 = "uuid-002";

const MOCK_PKG_INTERNAL: api.PackageDto = { uuid: PKG_UUID_1, label: "Internal Pkg" };
const MOCK_PKG_EXTERNAL: api.PackageDto = { uuid: PKG_UUID_2, label: "External Pkg" };

const MOCK_PACKAGE_LIST_RESPONSE = {
    internalPackageList: [MOCK_PKG_INTERNAL],
    externalPackageList: [MOCK_PKG_EXTERNAL],
};

const MOCK_PACKAGE_LIST_INFO = {
    internal: [MOCK_PKG_INTERNAL],
    external: [MOCK_PKG_EXTERNAL],
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function seedCache(store: ReturnType<typeof createPackageStore>) {
    vi.mocked(api.listPackages).mockResolvedValueOnce({
        data: MOCK_PACKAGE_LIST_RESPONSE,
        error: undefined,
    });
    await store.getPackages(DATASET_A, GRAPH_URI_1);
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    listPackages: vi.fn(),
    addPackage: vi.fn(),
    replacePackage: vi.fn(),
    deletePackage: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("packageStore", () => {
    let store: ReturnType<typeof createPackageStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createPackageStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("initializes with an empty byGraph map", () => {
            const state = get(store);
            expect(state.byGraph.size).toBe(0);
        });
    });

    // -------------------------------------------------------------------------
    describe("getPackages", () => {
        test("returns null if datasetName is empty", async () => {
            const result = await store.getPackages("", GRAPH_URI_1);
            expect(result).toBeNull();
            expect(api.listPackages).not.toHaveBeenCalled();
        });

        test("returns null if graphURI is empty", async () => {
            const result = await store.getPackages(DATASET_A, "");
            expect(result).toBeNull();
            expect(api.listPackages).not.toHaveBeenCalled();
        });

        test("returns null if both args are empty", async () => {
            const result = await store.getPackages("", "");
            expect(result).toBeNull();
            expect(api.listPackages).not.toHaveBeenCalled();
        });

        test("fetches and caches packages, mapping to internal/external shape", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });

            const result = await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_A, GRAPH_URI_1);

            expect(result).toEqual(MOCK_PACKAGE_LIST_INFO);
            expect(api.listPackages).toHaveBeenCalledTimes(1);
            expect(api.listPackages).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
            });
        });

        test("treats missing internalPackageList or externalPackageList as empty arrays", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: { internalPackageList: undefined, externalPackageList: undefined } as never,
                error: undefined,
            });

            const result = await store.getPackages(DATASET_A, GRAPH_URI_1);
            expect(result?.internal).toEqual([]);
            expect(result?.external).toEqual([]);
        });

        test("force=true bypasses the cache", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });

            await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_A, GRAPH_URI_1, true);

            expect(api.listPackages).toHaveBeenCalledTimes(2);
        });

        test("different dataset+graph combinations are cached independently", async () => {
            vi.mocked(api.listPackages)
                .mockResolvedValueOnce({ data: MOCK_PACKAGE_LIST_RESPONSE, error: undefined })
                .mockResolvedValueOnce({ data: { internalPackageList: [], externalPackageList: [] }, error: undefined });

            await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_A, GRAPH_URI_2);

            expect(api.listPackages).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: undefined,
                error: new Error("Network error"),
            });

            const result = await store.getPackages(DATASET_A, GRAPH_URI_1);
            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("addPackage", () => {
        test("calls API with correct arguments", async () => {
            const newPkg: api.PackageDto = { label: "New Package" };
            vi.mocked(api.addPackage).mockResolvedValue({ data: PKG_UUID_1, error: undefined });

            await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            expect(api.addPackage).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1 },
                body: newPkg,
            });
        });

        test("returns the server-issued UUID on success", async () => {
            const newPkg: api.PackageDto = { label: "New Package" };
            vi.mocked(api.addPackage).mockResolvedValue({ data: PKG_UUID_1, error: undefined });

            const result = await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            expect(result.error).toBeNull();
            expect(result.data).toBe(PKG_UUID_1);
        });

        test("prefers server-issued UUID over the one sent in the DTO", async () => {
            const newPkg: api.PackageDto = { uuid: "client-uuid", label: "New Package" };
            const serverUUID = "server-uuid";
            vi.mocked(api.addPackage).mockResolvedValue({ data: serverUUID, error: undefined });
            await seedCache(store);

            await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            const added = cached?.internal.find(p => p.uuid === serverUUID);
            expect(added).toBeDefined();
            expect(cached?.internal.some(p => p.uuid === "client-uuid")).toBe(false);
        });

        test("falls back to the DTO uuid if server returns no UUID", async () => {
            const newPkg: api.PackageDto = { uuid: PKG_UUID_1, label: "New Package" };
            vi.mocked(api.addPackage).mockResolvedValue({ data: undefined, error: undefined });
            await seedCache(store);

            await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            // PKG_UUID_1 was already in the cache; the find-and-replace path should fire
            const found = cached?.internal.find(p => p.uuid === PKG_UUID_1);
            expect(found).toBeDefined();
        });

        test("appends new package to the internal list when cache is populated", async () => {
            const newPkg: api.PackageDto = { label: "Brand New" };
            const serverUUID = "new-server-uuid";
            vi.mocked(api.addPackage).mockResolvedValue({ data: serverUUID, error: undefined });
            await seedCache(store);

            await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.internal.some(p => p.uuid === serverUUID)).toBe(true);
        });

        test("does not patch the cache when nothing has been loaded yet", async () => {
            const newPkg: api.PackageDto = { label: "New Package" };
            vi.mocked(api.addPackage).mockResolvedValue({ data: PKG_UUID_1, error: undefined });

            await store.addPackage(DATASET_A, GRAPH_URI_1, newPkg);

            const state = get(store);
            // No cache entry should have been created
            expect(state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data).toBeUndefined();
        });

        test("returns error and does not modify cache on API failure", async () => {
            const error = new Error("Add failed");
            vi.mocked(api.addPackage).mockResolvedValue({ data: undefined, error });
            await seedCache(store);

            const result = await store.addPackage(DATASET_A, GRAPH_URI_1, { label: "New" });

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_PACKAGE_LIST_INFO);
        });
    });

    // -------------------------------------------------------------------------
    describe("replacePackage", () => {
        test("calls API with correct arguments", async () => {
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error: undefined });
            const updatedPkg: api.PackageDto = { uuid: PKG_UUID_1, label: "Updated" };

            await store.replacePackage(DATASET_A, GRAPH_URI_1, updatedPkg);

            expect(api.replacePackage).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1, packageUUID: PKG_UUID_1 },
                body: updatedPkg,
            });
        });

        test("early-returns with error if pkg.uuid is missing, without calling API", async () => {
            const pkgWithoutUUID: api.PackageDto = { label: "No UUID" };

            const result = await store.replacePackage(DATASET_A, GRAPH_URI_1, pkgWithoutUUID);

            expect(result.error).toBeInstanceOf(Error);
            expect(api.replacePackage).not.toHaveBeenCalled();
        });

        test("updates the matching package in the internal list on success", async () => {
            await seedCache(store);
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error: undefined });

            const updatedPkg: api.PackageDto = { uuid: PKG_UUID_1, label: "Updated Label" };
            await store.replacePackage(DATASET_A, GRAPH_URI_1, updatedPkg);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            const found = cached?.internal.find(p => p.uuid === PKG_UUID_1);
            expect(found?.label).toBe("Updated Label");
        });

        test("removes the package from the external list if it was there", async () => {
            await seedCache(store);
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error: undefined });

            // Replace a package whose UUID matches one in the external list
            const pkg: api.PackageDto = { uuid: PKG_UUID_2, label: "Now Internal" };
            await store.replacePackage(DATASET_A, GRAPH_URI_1, pkg);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.external.some(p => p.uuid === PKG_UUID_2)).toBe(false);
        });

        test("does not patch cache when nothing is loaded yet", async () => {
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error: undefined });

            await store.replacePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            const state = get(store);
            expect(state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data).toBeUndefined();
        });

        test("returns error and preserves cache on API failure", async () => {
            await seedCache(store);
            const error = new Error("Replace failed");
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error });

            const result = await store.replacePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_PACKAGE_LIST_INFO);
        });
    });

    // -------------------------------------------------------------------------
    describe("savePackage", () => {
        test("dispatches to replacePackage when pkg.uuid is present", async () => {
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error: undefined });

            const result = await store.savePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            expect(api.replacePackage).toHaveBeenCalled();
            expect(api.addPackage).not.toHaveBeenCalled();
            expect(result.error).toBeNull();
            expect(result.data).toBe(PKG_UUID_1);
        });

        test("dispatches to addPackage when pkg.uuid is absent", async () => {
            const newPkg: api.PackageDto = { label: "No UUID yet" };
            vi.mocked(api.addPackage).mockResolvedValue({ data: "new-uuid", error: undefined });

            const result = await store.savePackage(DATASET_A, GRAPH_URI_1, newPkg);

            expect(api.addPackage).toHaveBeenCalled();
            expect(api.replacePackage).not.toHaveBeenCalled();
            expect(result.error).toBeNull();
            expect(result.data).toBe("new-uuid");
        });

        test("passes through error from replacePackage", async () => {
            const error = new Error("Replace failed");
            vi.mocked(api.replacePackage).mockResolvedValue({ data: undefined, error });

            const result = await store.savePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            expect(result.error).toBe(error);
        });

        test("passes through error from addPackage", async () => {
            const error = new Error("Add failed");
            vi.mocked(api.addPackage).mockResolvedValue({ data: undefined, error });

            const result = await store.savePackage(DATASET_A, GRAPH_URI_1, { label: "No UUID" });

            expect(result.error).toBe(error);
        });
    });

    // -------------------------------------------------------------------------
    describe("deletePackage", () => {
        test("early-returns with error if pkg.uuid is missing, without calling API", async () => {
            const pkgWithoutUUID: api.PackageDto = { label: "No UUID" };

            const result = await store.deletePackage(DATASET_A, GRAPH_URI_1, pkgWithoutUUID);

            expect(result.error).toBeInstanceOf(Error);
            expect(api.deletePackage).not.toHaveBeenCalled();
        });

        test("calls API with correct arguments", async () => {
            vi.mocked(api.deletePackage).mockResolvedValue({ data: undefined, error: undefined });

            await store.deletePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            expect(api.deletePackage).toHaveBeenCalledWith({
                path: { datasetName: DATASET_A, graphURI: GRAPH_URI_1, packageUUID: PKG_UUID_1 },
            });
        });

        test("removes the package from the internal list on success", async () => {
            await seedCache(store);
            vi.mocked(api.deletePackage).mockResolvedValue({ data: undefined, error: undefined });

            await store.deletePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.internal.some(p => p.uuid === PKG_UUID_1)).toBe(false);
            // External list should be untouched
            expect(cached?.external).toEqual([MOCK_PKG_EXTERNAL]);
        });

        test("removes the package from the external list on success", async () => {
            await seedCache(store);
            vi.mocked(api.deletePackage).mockResolvedValue({ data: undefined, error: undefined });

            await store.deletePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_EXTERNAL);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached?.external.some(p => p.uuid === PKG_UUID_2)).toBe(false);
            // Internal list should be untouched
            expect(cached?.internal).toEqual([MOCK_PKG_INTERNAL]);
        });

        test("does not modify cache when nothing is loaded yet", async () => {
            vi.mocked(api.deletePackage).mockResolvedValue({ data: undefined, error: undefined });

            await store.deletePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            const state = get(store);
            expect(state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data).toBeUndefined();
        });

        test("returns error and preserves cache on API failure", async () => {
            await seedCache(store);
            const error = new Error("Delete failed");
            vi.mocked(api.deletePackage).mockResolvedValue({ data: undefined, error });

            const result = await store.deletePackage(DATASET_A, GRAPH_URI_1, MOCK_PKG_INTERNAL);

            expect(result.error).toBe(error);

            const state = get(store);
            const cached = state.byGraph.get(makeGraphKey(DATASET_A, GRAPH_URI_1))?.data;
            expect(cached).toEqual(MOCK_PACKAGE_LIST_INFO);
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateGraph", () => {
        test("removes only the specified graph entry", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });
            await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_A, GRAPH_URI_2);

            store.invalidateGraph(DATASET_A, GRAPH_URI_1);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_2))).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateDataset", () => {
        test("removes all graph entries for the dataset", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });
            await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_A, GRAPH_URI_2);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_2))).toBe(false);
        });

        test("does not affect entries from other datasets", async () => {
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });
            await store.getPackages(DATASET_A, GRAPH_URI_1);
            await store.getPackages(DATASET_B, GRAPH_URI_1);

            store.invalidateDataset(DATASET_A);

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(false);
            expect(state.byGraph.has(makeGraphKey(DATASET_B, GRAPH_URI_1))).toBe(true);
        });

        test("does not incorrectly match a dataset whose name is a prefix of another", async () => {
            const SHORT_DATASET = "data";
            vi.mocked(api.listPackages).mockResolvedValue({
                data: MOCK_PACKAGE_LIST_RESPONSE,
                error: undefined,
            });
            await store.getPackages(DATASET_A, GRAPH_URI_1); // key: "datasetA::..."

            store.invalidateDataset(SHORT_DATASET); // "data::" should not match "datasetA::"

            const state = get(store);
            expect(state.byGraph.has(makeGraphKey(DATASET_A, GRAPH_URI_1))).toBe(true);
        });
    });
});
