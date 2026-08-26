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
import { type CimPrefixPair } from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createWorkspaceStore } from "../../src/lib/stores/workspaceStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const WORKSPACE_A = {
    label: "workspaceA",
    readOnly: false,
    prefixes: [{ prefix: "ex", substitutedPrefix: "http://example.org/" }],
};

const WORKSPACE_B = {
    label: "workspaceB",
    readOnly: true,
    prefixes: [],
};

/** Raw shape returned by the backend */
function makeApiWorkspace(
    label: string,
    readOnly = false,
    prefixes: CimPrefixPair[] = [],
) {
    return { name: label, readOnly, prefixes };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockListWorkspacesSuccess(
    ...workspaces: ReturnType<typeof makeApiWorkspace>[]
) {
    vi.mocked(api.listDatasets).mockResolvedValue({
        data: workspaces,
        error: undefined,
    });
}

function mockListWorkspacesError(error = new Error("network error")) {
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

describe("WorkspaceStore", () => {
    let store: ReturnType<typeof createWorkspaceStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createWorkspaceStore();
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
    describe("getWorkspaces", () => {
        test("maps API response to WorkspaceInfo shape", async () => {
            mockListWorkspacesSuccess(
                makeApiWorkspace("workspaceA", false, [
                    { prefix: "ex", substitutedPrefix: "http://example.org/" },
                ]),
                makeApiWorkspace("workspaceB", true),
            );

            const result = await store.getWorkspaces();

            expect(result).toEqual([WORKSPACE_A, WORKSPACE_B]);
        });

        test("returns null when the API returns an error", async () => {
            mockListWorkspacesError();

            const result = await store.getWorkspaces();

            expect(result).toBeNull();
        });

        test("returns cached data on second call without re-fetching", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));

            await store.getWorkspaces();
            await store.getWorkspaces();

            expect(api.listDatasets).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses cache and re-fetches", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));

            await store.getWorkspaces();
            await store.getWorkspaces(true);

            expect(api.listDatasets).toHaveBeenCalledTimes(2);
        });

        test("treats missing name/prefixes as empty defaults", async () => {
            vi.mocked(api.listDatasets).mockResolvedValue({
                data: [
                    { name: undefined, readOnly: false, prefixes: undefined },
                ],
                error: undefined,
            });

            const result = await store.getWorkspaces();

            expect(result?.[0]).toEqual({
                label: "",
                readOnly: false,
                prefixes: [],
            });
        });
    });

    // -------------------------------------------------------------------------
    describe("isReadOnly", () => {
        test("returns readOnly flag for a known workspace", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA", true));

            expect(await store.isReadOnly("workspaceA")).toBe(true);
        });

        test("returns null for an unknown workspace", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));

            expect(await store.isReadOnly("doesNotExist")).toBeNull();
        });
        test("returns null if gets fails to fetch data", async () => {
            mockListWorkspacesError(new Error("Network offline"));

            const result = await store.isReadOnly("workspaceA");

            expect(result).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("getNamespaces", () => {
        test("returns prefixes for a known workspace", async () => {
            mockListWorkspacesSuccess(
                makeApiWorkspace("workspaceA", false, [
                    { prefix: "ex", substitutedPrefix: "http://example.org/" },
                ]),
            );

            const prefixes = await store.getNamespaces("workspaceA");

            expect(prefixes).toEqual([
                { prefix: "ex", substitutedPrefix: "http://example.org/" },
            ]);
        });

        test("returns empty array for unknown workspace", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));

            expect(await store.getNamespaces("unknown")).toEqual([]);
        });
        test("returns an empty array if getWorkspaces fails to fetch data", async () => {
            mockListWorkspacesError(new Error("Network offline"));

            const result = await store.getNamespaces("workspaceA");

            expect(result).toEqual([]);
        });
    });

    // -------------------------------------------------------------------------
    describe("remove", () => {
        test("removes the workspace from the store on success", async () => {
            mockListWorkspacesSuccess(
                makeApiWorkspace("workspaceA"),
                makeApiWorkspace("workspaceB"),
            );
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getWorkspaces();
            await store.remove("workspaceA");

            const state = get(store);
            expect(state.data?.map(d => d.label)).toEqual(["workspaceB"]);
        });

        test("returns error and shows error toast when API fails", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));
            const err = new Error("server error");
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getWorkspaces();
            const result = await store.remove("workspaceA");

            expect(result.error).toBe(err);
            expect(toastStore.error).toHaveBeenCalledOnce();
        });

        test("shows success toast on successful deletion", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getWorkspaces();
            await store.remove("workspaceA");

            expect(toastStore.success).toHaveBeenCalledOnce();
        });
        test("does not crash if called before workspaces are fetched", async () => {
            vi.mocked(api.deleteDataset).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            // Notice we are NOT calling await store.getWorkspaces() first
            const result = await store.remove("workspaceA");

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
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getWorkspaces();
            await store.saveNamespaces("workspaceA", newPrefixes);

            const state = get(store);
            expect(
                state.data?.find(d => d.label === "workspaceA")?.prefixes,
            ).toEqual(newPrefixes);
        });

        test("returns error and does not update store when API fails", async () => {
            mockListWorkspacesSuccess(
                makeApiWorkspace("workspaceA", false, [
                    { prefix: "old", substitutedPrefix: "http://old.org/" },
                ]),
            );
            const err = new Error("save failed");
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getWorkspaces();
            const result = await store.saveNamespaces(
                "workspaceA",
                newPrefixes,
            );

            expect(result.error).toBe(err);
            // original prefixes unchanged
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "workspaceA")?.prefixes,
            ).toEqual([
                { prefix: "old", substitutedPrefix: "http://old.org/" },
            ]);
        });
        test("does not crash if called before workspaces are fetched", async () => {
            vi.mocked(api.replaceNamespaces).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.saveNamespaces(
                "workspaceA",
                newPrefixes,
            );

            expect(result.error).toBeNull();
            const state = get(store);
            expect(state.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("updateReadonly", () => {
        test("calls disableEditing when readOnly=true and updates store", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA", false));
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getWorkspaces();
            await store.updateReadonly("workspaceA", true);

            expect(api.disableEditing).toHaveBeenCalledOnce();
            expect(api.enableEditing).not.toHaveBeenCalled();
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "workspaceA")?.readOnly,
            ).toBe(true);
        });

        test("calls enableEditing when readOnly=false and updates store", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA", true));
            vi.mocked(api.enableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.getWorkspaces();
            await store.updateReadonly("workspaceA", false);

            expect(api.enableEditing).toHaveBeenCalledOnce();
            const state = get(store);
            expect(
                state.data?.find(d => d.label === "workspaceA")?.readOnly,
            ).toBe(false);
        });

        test("returns error and shows error toast when API fails", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA", false));
            const err = new Error("forbidden");
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: err,
            });

            await store.getWorkspaces();
            const result = await store.updateReadonly("workspaceA", true);

            expect(result.error).toBe(err);
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
        test("does not crash if called before workspaces are fetched", async () => {
            vi.mocked(api.disableEditing).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.updateReadonly("workspaceA", true);

            expect(result.error).toBeNull();
            const state = get(store);
            expect(state.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidate", () => {
        test("clears cached data so the next getWorkspaces re-fetches", async () => {
            mockListWorkspacesSuccess(makeApiWorkspace("workspaceA"));

            await store.getWorkspaces();
            store.invalidate();
            await store.getWorkspaces();

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
