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
import { undoConfirmStore } from "../../src/lib/eventhandling/undoConfirmStore.svelte.js";
import { editorState } from "../../src/lib/sharedState.svelte.js";
import { classStore } from "../../src/lib/stores/classStore";
import { datatypesStore } from "../../src/lib/stores/datatypesStore";
import { customDiagramStore } from "../../src/lib/stores/diagramStore";
import { graphStore } from "../../src/lib/stores/graphStore";
import { ontologyStore } from "../../src/lib/stores/ontologyStore";
import { packageStore } from "../../src/lib/stores/packageStore";
import { createVersionControlStore } from "../../src/lib/stores/versionControlStore";
import { workspaceStore } from "../../src/lib/stores/workspaceStore";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    undo: vi.fn(),
    redo: vi.fn(),
    canUndo: vi.fn(),
    canRedo: vi.fn(),
    getPendingUndo: vi.fn(),
}));

vi.mock("$lib/sharedState.svelte.js", () => ({
    editorState: {
        selectedWorkspace: { getValue: vi.fn(), updateValue: vi.fn() },
        selectedGraph: { getValue: vi.fn(), updateValue: vi.fn() },
    },
}));

vi.mock("$lib/stores/classStore", () => ({
    classStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/datatypesStore", () => ({
    datatypesStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/diagramStore", () => ({
    customDiagramStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/graphStore", () => ({
    graphStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/ontologyStore", () => ({
    ontologyStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/packageStore", () => ({
    packageStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/workspaceStore", () => ({
    workspaceStore: { invalidate: vi.fn() },
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { info: vi.fn(), error: vi.fn() },
}));

vi.mock("$lib/eventhandling/undoConfirmStore.svelte.js", () => ({
    undoConfirmStore: { confirm: vi.fn() },
}));

// Suppress console outputs during error tests
vi.spyOn(console, "error").mockImplementation(() => {});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("versionControlStore", () => {
    let store: ReturnType<typeof createVersionControlStore>;
    const WORKSPACE = "workspaceA";

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

        // Nothing disappears unless a test says so, so undo goes through unasked.
        vi.mocked(api.getPendingUndo).mockResolvedValue({
            data: { message: "a change", removedOnUndo: [] },
            error: undefined,
        });
    });

    // -------------------------------------------------------------------------
    describe("refresh", () => {
        test("updates state based on the explicit workspace", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });

            await store.refresh(WORKSPACE);

            expect(await store.canUndo(WORKSPACE)).toBe(true);
            expect(await store.canRedo(WORKSPACE)).toBe(false);
            expect(api.canUndo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
            });
        });

        test("falls back to editorState if the argument is omitted", async () => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
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

            expect(await store.canUndo(WORKSPACE)).toBe(true);
            expect(await store.canRedo(WORKSPACE)).toBe(true);
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

            await store.refresh(WORKSPACE);

            expect(await store.canUndo(WORKSPACE)).toBe(false);
            expect(await store.canRedo(WORKSPACE)).toBe(false);
        });

        test("does nothing if no workspace resolves", async () => {
            await store.refresh(); // no args, no editorState
            expect(api.canUndo).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("workspace scope", () => {
        test("asks the backend once per workspace, not once per graph", async () => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
            );
            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });

            await store.canUndo();
            vi.mocked(editorState.selectedGraph.getValue).mockReturnValue(
                "http://example.org/other",
            );
            await store.canUndo();

            expect(api.canUndo).toHaveBeenCalledTimes(1);
        });

        test("works without a selected graph", async () => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
            );
            vi.mocked(api.undo).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });

            const result = await store.undo();

            expect(result.error).toBeNull();
            expect(api.undo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
            });
        });
    });

    // -------------------------------------------------------------------------
    describe("canUndo / canRedo (Getters)", () => {
        test("returns correct flags from state", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            await store.refresh(WORKSPACE);

            expect(await store.canUndo(WORKSPACE)).toBe(true);
            expect(await store.canRedo(WORKSPACE)).toBe(false);
        });

        test("returns false for an unknown workspace", async () => {
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            expect(await store.canUndo("unknown")).toBe(false);
        });
    });

    // -------------------------------------------------------------------------
    describe("undo", () => {
        test("calls SDK, invalidates the whole workspace, toasts, and refreshes", async () => {
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

            const result = await store.undo(WORKSPACE);

            expect(result.error).toBeNull();
            expect(api.undo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
            });

            // An undone change can sit in any graph, so every graph-keyed cache
            // of the workspace has to go, not just the selected one.
            expect(classStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(ontologyStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(packageStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(datatypesStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(customDiagramStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            // The navigation names a schema from the graph list.
            expect(graphStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            // The namespaces live in the cached workspace list.
            expect(workspaceStore.invalidate).toHaveBeenCalled();

            expect(toastStore.info).toHaveBeenCalledWith(
                "Undone",
                expect.any(String),
            );
            expect(api.canUndo).toHaveBeenCalled(); // Proves refresh was called
        });

        test("returns error and prevents invalidation if SDK fails", async () => {
            const error = new Error("Conflict");
            vi.mocked(api.undo).mockResolvedValue({ data: undefined, error });

            const result = await store.undo(WORKSPACE);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Undo failed",
                "Could not undo the last change.",
            );

            // Stores should NOT be invalidated if undo failed
            expect(classStore.invalidateWorkspace).not.toHaveBeenCalled();
        });

        test("fails early if no workspace resolves", async () => {
            const result = await store.undo(); // no args, no global state

            expect(result.error).toBe("No undo target selected.");
            expect(api.undo).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("what the toast says", () => {
        const GRAPH = "http://example.org/schemas/Core";
        const OTHER = "http://example.org/schemas/Operation";

        beforeEach(() => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
            );
            vi.mocked(editorState.selectedGraph.getValue).mockReturnValue(
                GRAPH,
            );
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });
        });

        test("names the change and where it landed", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: {
                    message: 'Renamed class "Terminal" to "Node"',
                    affectedGraphUris: [OTHER],
                },
                error: undefined,
            });

            await store.undo(WORKSPACE);

            const [title, message] = vi.mocked(toastStore.info).mock.calls[0];
            expect(title).toBe("Undone");
            expect(message).toContain('Renamed class "Terminal" to "Node"');
            expect(message).toContain("Operation");
        });

        test("says nothing about a place when the change is in the open graph", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: {
                    message: "a change",
                    affectedGraphUris: [GRAPH],
                },
                error: undefined,
            });

            await store.undo(WORKSPACE);

            expect(vi.mocked(toastStore.info).mock.calls[0][1]).toBe(
                "a change",
            );
        });

        test("counts the graphs when a change spanned several", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: {
                    message: "copied a class",
                    affectedGraphUris: [
                        OTHER,
                        "http://example.org/schemas/Third",
                    ],
                },
                error: undefined,
            });

            await store.undo(WORKSPACE);

            expect(vi.mocked(toastStore.info).mock.calls[0][1]).toContain(
                "in 2 graphs",
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("undo that removes something", () => {
        beforeEach(() => {
            vi.mocked(editorState.selectedWorkspace.getValue).mockReturnValue(
                WORKSPACE,
            );
            vi.mocked(api.undo).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.canUndo).mockResolvedValue({
                data: false,
                error: undefined,
            });
            vi.mocked(api.canRedo).mockResolvedValue({
                data: true,
                error: undefined,
            });
            vi.mocked(api.getPendingUndo).mockResolvedValue({
                data: {
                    message: "imported graph http://example.org/a",
                    removedOnUndo: ["http://example.org/a"],
                },
                error: undefined,
            });
        });

        test("asks before undoing and goes ahead when confirmed", async () => {
            vi.mocked(undoConfirmStore.confirm).mockResolvedValue(true);

            const result = await store.undo(WORKSPACE);

            expect(undoConfirmStore.confirm).toHaveBeenCalledWith(
                "imported graph http://example.org/a",
                ["http://example.org/a"],
            );
            expect(api.undo).toHaveBeenCalled();
            expect(result.error).toBeNull();
        });

        test("leaves everything alone when the user declines", async () => {
            vi.mocked(undoConfirmStore.confirm).mockResolvedValue(false);

            const result = await store.undo(WORKSPACE);

            expect(api.undo).not.toHaveBeenCalled();
            expect(classStore.invalidateWorkspace).not.toHaveBeenCalled();
            expect(toastStore.info).not.toHaveBeenCalled();
            expect(result.cancelled).toBe(true);
            expect(result.error).toBeNull();
        });

        test("does not ask when nothing would disappear", async () => {
            vi.mocked(api.getPendingUndo).mockResolvedValue({
                data: { message: "renamed a class", removedOnUndo: [] },
                error: undefined,
            });

            await store.undo(WORKSPACE);

            expect(undoConfirmStore.confirm).not.toHaveBeenCalled();
            expect(api.undo).toHaveBeenCalled();
        });

        test("undoes rather than blocks when the peek fails", async () => {
            // A failed peek must not stand between the user and their undo.
            vi.mocked(api.getPendingUndo).mockResolvedValue({
                data: undefined,
                error: new Error("Server down"),
            });

            await store.undo(WORKSPACE);

            expect(undoConfirmStore.confirm).not.toHaveBeenCalled();
            expect(api.undo).toHaveBeenCalled();
        });

        test("redo never asks", async () => {
            vi.mocked(api.redo).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            await store.redo(WORKSPACE);

            expect(api.getPendingUndo).not.toHaveBeenCalled();
            expect(undoConfirmStore.confirm).not.toHaveBeenCalled();
        });
    });

    // -------------------------------------------------------------------------
    describe("redo", () => {
        test("calls SDK, invalidates the whole workspace, toasts, and refreshes", async () => {
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

            const result = await store.redo(WORKSPACE);

            expect(result.error).toBeNull();
            expect(api.redo).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
            });

            expect(classStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(ontologyStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(packageStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(datatypesStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(customDiagramStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            // The navigation names a schema from the graph list.
            expect(graphStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(workspaceStore.invalidate).toHaveBeenCalled();

            expect(toastStore.info).toHaveBeenCalledWith(
                "Redone",
                expect.any(String),
            );
        });

        test("returns error and prevents invalidation if SDK fails", async () => {
            const error = new Error("Cannot redo");
            vi.mocked(api.redo).mockResolvedValue({ data: undefined, error });

            const result = await store.redo(WORKSPACE);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Redo failed",
                "Could not redo the change.",
            );
            expect(classStore.invalidateWorkspace).not.toHaveBeenCalled();
        });

        test("fails early if no workspace resolves", async () => {
            const result = await store.redo();

            expect(result.error).toBe("No redo target selected.");
            expect(api.redo).not.toHaveBeenCalled();
        });
    });
});
