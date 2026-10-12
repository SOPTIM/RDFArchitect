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
import { crossProfileStore } from "../../src/lib/stores/crossProfileStore";
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
    restoreVersion: vi.fn(),
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
vi.mock("$lib/stores/graphStore", () => ({
    graphStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/crossProfileStore", () => ({
    crossProfileStore: { invalidateWorkspace: vi.fn() },
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
        test("calls SDK, invalidates the whole workspace, and toasts", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: {
                    change: { message: "a change" },
                    canUndo: false,
                    canRedo: true,
                },
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
            expect(crossProfileStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            // The namespaces live in the cached workspace list.
            expect(workspaceStore.invalidate).toHaveBeenCalled();

            expect(toastStore.info).toHaveBeenCalledWith(
                "Undone",
                expect.any(String),
            );
            // The step reports where it left the history, so nothing is asked
            // again afterwards.
            expect(api.canUndo).not.toHaveBeenCalled();
            expect(await store.canRedo(WORKSPACE)).toBe(true);
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
                    change: {
                        message: 'Renamed class "Terminal" to "Node"',
                        affectedGraphUris: [OTHER],
                    },
                    canUndo: false,
                    canRedo: true,
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
                    change: {
                        message: "a change",
                        affectedGraphUris: [GRAPH],
                    },
                    canUndo: false,
                    canRedo: true,
                },
                error: undefined,
            });

            await store.undo(WORKSPACE);

            expect(vi.mocked(toastStore.info).mock.calls[0][1]).toBe(
                "a change",
            );
        });

        test("an unnamed redo does not say the change was reverted", async () => {
            vi.mocked(api.redo).mockResolvedValue({
                data: { change: {}, canUndo: true, canRedo: false },
                error: undefined,
            });

            await store.redo(WORKSPACE);

            expect(vi.mocked(toastStore.info).mock.calls[0][1]).not.toContain(
                "reverted",
            );
        });

        test("counts the graphs when a change spanned several", async () => {
            vi.mocked(api.undo).mockResolvedValue({
                data: {
                    change: {
                        message: "copied a class",
                        affectedGraphUris: [
                            OTHER,
                            "http://example.org/schemas/Third",
                        ],
                    },
                    canUndo: false,
                    canRedo: true,
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
    describe("one step at a time", () => {
        beforeEach(() => {
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
        });

        test("a held shortcut does not put several undos on the wire at once", async () => {
            // Every request takes the workspace lock, so overlapping steps queue
            // up on it and hold a request thread each until they time out.
            let running = 0;
            let highWaterMark = 0;
            vi.mocked(api.undo).mockImplementation(async () => {
                running++;
                highWaterMark = Math.max(highWaterMark, running);
                await new Promise(resolve => setTimeout(resolve, 0));
                running--;
                return {
                    data: { change: { message: "a change" } },
                    error: undefined,
                };
            });

            await Promise.all([
                store.undo(WORKSPACE),
                store.undo(WORKSPACE),
                store.undo(WORKSPACE),
            ]);

            expect(highWaterMark).toBe(1);
        });

        test("a press that was ignored says so, so nothing reloads for it", async () => {
            vi.mocked(api.undo).mockImplementation(async () => {
                await new Promise(resolve => setTimeout(resolve, 0));
                return {
                    data: { change: { message: "a change" } },
                    error: undefined,
                };
            });

            const [first, second] = await Promise.all([
                store.undo(WORKSPACE),
                store.undo(WORKSPACE),
            ]);

            expect(first.skipped).toBeUndefined();
            expect(second.skipped).toBe(true);
        });

        test("presses made while one runs are ignored, not collected", async () => {
            // Key repeat fires far faster than a step completes. Remembering
            // the presses would keep the editor busy long after the key was
            // released.
            vi.mocked(api.undo).mockImplementation(async () => {
                await new Promise(resolve => setTimeout(resolve, 0));
                return {
                    data: { change: { message: "a change" } },
                    error: undefined,
                };
            });

            await Promise.all(
                Array.from({ length: 20 }, () => store.undo(WORKSPACE)),
            );

            expect(api.undo).toHaveBeenCalledTimes(1);
        });

        test("a press right after a finished step is still ignored", async () => {
            // The reload a step sets off outlasts the step itself, so the next
            // press has to wait for more than the response.
            vi.mocked(api.undo).mockResolvedValue({
                data: { change: { message: "a change" } },
                error: undefined,
            });

            await store.undo(WORKSPACE);
            const second = await store.undo(WORKSPACE);

            expect(second.skipped).toBe(true);
            expect(api.undo).toHaveBeenCalledTimes(1);
        });

        test("the next press goes through once the interval has passed", async () => {
            vi.useFakeTimers();
            try {
                vi.mocked(api.undo).mockResolvedValue({
                    data: { change: { message: "a change" } },
                    error: undefined,
                });

                await store.undo(WORKSPACE);
                vi.advanceTimersByTime(1000);
                await store.undo(WORKSPACE);

                expect(api.undo).toHaveBeenCalledTimes(2);
            } finally {
                vi.useRealTimers();
            }
        });

        test("a redo pressed during an undo is ignored too", async () => {
            vi.mocked(api.undo).mockImplementation(async () => {
                await new Promise(resolve => setTimeout(resolve, 0));
                return {
                    data: { change: { message: "a change" } },
                    error: undefined,
                };
            });
            vi.mocked(api.redo).mockResolvedValue({
                data: { change: { message: "a change" } },
                error: undefined,
            });

            await Promise.all([store.undo(WORKSPACE), store.redo(WORKSPACE)]);

            expect(api.redo).not.toHaveBeenCalled();
        });

        test("a failed step does not block the next one", async () => {
            vi.mocked(api.undo).mockRejectedValueOnce(new Error("boom"));

            await store.undo(WORKSPACE).catch(() => {});
            vi.mocked(api.undo).mockResolvedValue({
                data: { change: { message: "a change" } },
                error: undefined,
            });
            const result = await store.undo(WORKSPACE);

            expect(result.error).toBeNull();
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

        test("a declined undo reports itself skipped, so nothing reloads", async () => {
            vi.mocked(undoConfirmStore.confirm).mockResolvedValue(false);

            const result = await store.undo(WORKSPACE);

            expect(result.skipped).toBe(true);
        });

        test("declining does not block the next press", async () => {
            vi.mocked(undoConfirmStore.confirm).mockResolvedValueOnce(false);
            await store.undo(WORKSPACE);

            vi.mocked(undoConfirmStore.confirm).mockResolvedValue(true);
            const second = await store.undo(WORKSPACE);

            expect(second.skipped).toBeUndefined();
            expect(api.undo).toHaveBeenCalledTimes(1);
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
            expect(crossProfileStore.invalidateWorkspace).toHaveBeenCalledWith(
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
    // -------------------------------------------------------------------------
    describe("restore", () => {
        const CHANGE_ID = "11111111-1111-1111-1111-111111111111";

        test("restores the whole workspace and says what came back", async () => {
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: {
                    change: {
                        changeId: CHANGE_ID,
                        message: "added a class",
                        affectedGraphUris: [],
                    },
                    canUndo: true,
                    canRedo: false,
                },
                error: undefined,
            });

            const result = await store.restore(CHANGE_ID, [], WORKSPACE);

            expect(result.error).toBeNull();
            expect(api.restoreVersion).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
                body: { versionId: CHANGE_ID, graphUris: [] },
            });
            expect(toastStore.info).toHaveBeenCalledWith(
                "Version restored",
                expect.stringContaining("added a class"),
            );
        });

        test("passes the graphs a restricted restore is held to", async () => {
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: { change: { changeId: CHANGE_ID }, canUndo: true },
                error: undefined,
            });

            await store.restore(CHANGE_ID, ["http://example.org/a"], WORKSPACE);

            expect(api.restoreVersion).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE },
                body: {
                    versionId: CHANGE_ID,
                    graphUris: ["http://example.org/a"],
                },
            });
        });

        test("reloads the whole workspace, because a restore can reach all of it", async () => {
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: { change: { changeId: CHANGE_ID }, canUndo: true },
                error: undefined,
            });

            await store.restore(CHANGE_ID, [], WORKSPACE);

            expect(classStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(graphStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(crossProfileStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            );
            expect(workspaceStore.invalidate).toHaveBeenCalled();
        });

        test("takes over the flags the restore reports", async () => {
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: {
                    change: { changeId: CHANGE_ID },
                    canUndo: true,
                    canRedo: false,
                },
                error: undefined,
            });

            await store.restore(CHANGE_ID, [], WORKSPACE);

            expect(await store.canUndo(WORKSPACE)).toBe(true);
            expect(await store.canRedo(WORKSPACE)).toBe(false);
            expect(api.canUndo).not.toHaveBeenCalled();
        });

        test("says nothing was restored when the scope covered nothing", async () => {
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: { change: null, canUndo: true, canRedo: false },
                error: undefined,
            });

            const result = await store.restore(
                CHANGE_ID,
                ["http://example.org/b"],
                WORKSPACE,
            );

            expect(result.skipped).toBe(true);
            expect(toastStore.info).toHaveBeenCalledWith(
                "Nothing to restore",
                expect.any(String),
            );
            expect(classStore.invalidateWorkspace).not.toHaveBeenCalled();
        });

        test("returns error and prevents invalidation if SDK fails", async () => {
            const error = new Error("Cannot restore");
            vi.mocked(api.restoreVersion).mockResolvedValue({
                data: undefined,
                error,
            });

            const result = await store.restore(CHANGE_ID, [], WORKSPACE);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Restore failed",
                "Could not restore the selected version.",
            );
            expect(classStore.invalidateWorkspace).not.toHaveBeenCalled();
        });

        test("fails early if no workspace resolves", async () => {
            const result = await store.restore(CHANGE_ID);

            expect(result.error).toBe("No workspace selected.");
            expect(api.restoreVersion).not.toHaveBeenCalled();
        });
    });
});
