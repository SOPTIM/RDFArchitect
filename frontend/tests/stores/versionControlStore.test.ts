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

import { beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { editorState } from "../../src/lib/sharedState.svelte.js";
import { classStore } from "../../src/lib/stores/classStore";
import { customDiagramStore } from "../../src/lib/stores/diagramStore";
import { ontologyStore } from "../../src/lib/stores/ontologyStore";
import { packageStore } from "../../src/lib/stores/packageStore";
import { createVersionControlStore } from "../../src/lib/stores/versionControlStore";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    undo: vi.fn(),
    redo: vi.fn(),
    canUndo: vi.fn(),
    canRedo: vi.fn(),
}));

vi.mock("$lib/sharedState.svelte.js", () => ({
    editorState: {
        selectedWorkspace: { getValue: vi.fn() },
        selectedGraph: { getValue: vi.fn() },
    },
}));

vi.mock("$lib/stores/classStore", () => ({
    classStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/diagramStore", () => ({
    customDiagramStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/ontologyStore", () => ({
    ontologyStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/packageStore", () => ({
    packageStore: { invalidateGraph: vi.fn() },
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { info: vi.fn(), error: vi.fn() },
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

// Suppress console outputs during error tests
vi.spyOn(console, "error").mockImplementation(() => {});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("versionControlStore", () => {
    let store: ReturnType<typeof createVersionControlStore>;
    const WORKSPACE = "workspaceA";
    const GRAPH = "http://example.org/graph";

    beforeEach(() => {
        vi.clearAllMocks();
        store = createVersionControlStore();

        // Default global state mocks
        vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
            undefined,
        );
        vi.mocked(editorState.selectedGraph.getValue).mockReturnValue(
            undefined,
        );
    });

    // -------------------------------------------------------------------------
    describe("refresh", () => {
        test("updates state based on explicit arguments", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });

            await store.refresh(WORKSPACE, GRAPH);

            expect(await store.canUndo(WORKSPACE, GRAPH)).toBe(true);
            expect(await store.canRedo(WORKSPACE, GRAPH)).toBe(false);
            expect(api.canUndo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE, graphURI: GRAPH },
            });
        });

        test("falls back to editorState if arguments are omitted", async () => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
            );
            vi.mocked(editorState.selectedGraph.getValue).mockReturnValue(
                GRAPH,
            );

            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });

            await store.refresh();

            expect(await store.canUndo(WORKSPACE, GRAPH)).toBe(true);
            expect(await store.canRedo(WORKSPACE, GRAPH)).toBe(true);
        });

        test("sets flags to false if API returns an error", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: undefined,
                error: new Error("Server down"),
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: undefined,
                error: new Error("Server down"),
            });

            await store.refresh(WORKSPACE, GRAPH);

            expect(await store.canUndo(WORKSPACE, GRAPH)).toBe(false);
            expect(await store.canRedo(WORKSPACE, GRAPH)).toBe(false);
        });

        test("does nothing if no targets resolve", async () => {
            await store.refresh(); // no args, no editorState
            expect(api.canUndo).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("canUndo / canRedo (Getters)", () => {
        test("returns correct flags from state based on explicit args", async () => {
            // Seed state via refresh
            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            await store.refresh(WORKSPACE, GRAPH);

            expect(await store.canUndo(WORKSPACE, GRAPH)).toBe(true);
            expect(await store.canRedo(WORKSPACE, GRAPH)).toBe(false);
        });

        test("returns false for unknown graph", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            expect(await store.canUndo("unknown", "unknown")).toBe(false);
        });
    });

    // -------------------------------------------------------------------------
    describe("undo", () => {
        test("calls SDK, invalidates stores, toasts, and refreshes on success", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            // Mock refresh endpoints so it doesn't fail when called at the end
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });

            const result = await store.undo(WORKSPACE, GRAPH);

            expect(result.error).toBeNull();
            expect(api.undo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE, graphURI: GRAPH },
            });

            // Ensure invalidations were broadcast
            expect(classStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(ontologyStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(packageStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(customDiagramStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );

            expect(toastStore.info).toHaveBeenCalledWith("Undone");
            expect(api.canUndo).toHaveBeenCalled(); // Proves refresh was called
        });

        test("returns error and prevents invalidation if SDK fails", async () => {
            const error = new Error("Conflict");
            vi.mocked(api.undo).mockResolvedValue({ data: undefined, error });

            const result = await store.undo(WORKSPACE, GRAPH);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Undo failed",
                "Could not undo the last change.",
            );

            // Stores should NOT be invalidated if undo failed
            expect(classStore.invalidateGraph).not.toHaveBeenCalled();
        });

        test("fails early if targets cannot be resolved", async () => {
            const result = await store.undo(); // no args, no global state

            expect(result.error).toBe("No undo target selected.");
            expect(api.undo).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("redo", () => {
        test("calls SDK, invalidates stores, toasts, and refreshes on success", async () => {
            vi.mocked(api.redo).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });

            const result = await store.redo(WORKSPACE, GRAPH);

            expect(result.error).toBeNull();
            expect(api.redo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE, graphURI: GRAPH },
            });

            expect(classStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(ontologyStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(packageStore.invalidateGraph).toHaveBeenCalledWith(
                WORKSPACE,
                GRAPH,
            );
            expect(customDiagramStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );

            expect(toastStore.info).toHaveBeenCalledWith("Redone");
        });

        test("returns error and prevents invalidation if SDK fails", async () => {
            const error = new Error("Cannot redo");
            vi.mocked(api.redo).mockResolvedValue({ data: undefined, error });

            const result = await store.redo(WORKSPACE, GRAPH);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Redo failed",
                "Could not redo the change.",
            );
            expect(classStore.invalidateGraph).not.toHaveBeenCalled();
        });
    });
});
