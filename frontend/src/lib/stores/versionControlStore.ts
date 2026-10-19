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

import { writable } from "svelte/store";

import { editorState } from "../sharedState.svelte.js";
import { classStore } from "./classStore";
import { crossProfileStore } from "./crossProfileStore";
import { datatypesStore } from "./datatypesStore";
import { customDiagramStore } from "./diagramStore";
import { graphStore } from "./graphStore";
import { ontologyStore } from "./ontologyStore";
import { packageStore } from "./packageStore";
import { loadSlot } from "./storeHelpers";
import { type AsyncSlot, createEmptySlot } from "./storeTypes";
import { workspaceStore } from "./workspaceStore";
import {
    undo as sdkUndo,
    redo as sdkRedo,
    canUndo as sdkCanUndo,
    canRedo as sdkCanRedo,
    getPendingUndo as sdkPendingUndo,
    restoreVersion as sdkRestore,
} from "../api/generated";
import { type ChangeLogEntryDTO } from "../api/generated/types.gen";
import { toastStore } from "../eventhandling/toastStore.svelte.js";
import { undoConfirmStore } from "../eventhandling/undoConfirmStore.svelte.js";

type WorkspaceFlags = {
    canUndo: AsyncSlot<boolean>;
    canRedo: AsyncSlot<boolean>;
};

type State = { byWorkspace: Map<string, WorkspaceFlags> };

type Direction = "undo" | "redo";

/** A move through the history, including the one that is not a single step. */
type Move = Direction | "restore";

/** What the backend answers a move with. */
type MoveData = {
    change?: ChangeLogEntryDTO;
    canUndo?: boolean;
    canRedo?: boolean;
};

/**
 * What a move leaves the caller with: whether it failed, and whether it moved
 * anything at all — a move that did not is not something to reload for.
 */
type MoveResult = {
    error: unknown;
    skipped?: boolean;
    cancelled?: boolean;
};

const LOG = "[versionControlStore]";

export const versionControlStore = createVersionControlStore();

const WORDING: Record<
    Move,
    {
        successTitle: string;
        failureTitle: string;
        failureText: string;
        unnamedChange: string;
    }
> = {
    undo: {
        successTitle: "Undone",
        failureTitle: "Undo failed",
        failureText: "Could not undo the last change.",
        unnamedChange: "The last change was reverted.",
    },
    redo: {
        successTitle: "Redone",
        failureTitle: "Redo failed",
        failureText: "Could not redo the change.",
        unnamedChange: "The last change was restored.",
    },
    restore: {
        successTitle: "Version restored",
        failureTitle: "Restore failed",
        failureText: "Could not restore the selected version.",
        unnamedChange: "The selected version was restored.",
    },
};

/**
 * How long after a step the next press is ignored.
 *
 * A step is two requests, but what follows it is a reload of the whole editor
 * — a burst of further requests. Without a pause between them those bursts
 * overlap, and pressing faster than they settle buries the workspace in work
 * nobody is waiting for. Long enough to let one settle, short enough to walk
 * back through the history at a deliberate pace.
 */
const MIN_STEP_INTERVAL_MS = 300;

function getFlags(s: State, workspace: string): WorkspaceFlags {
    return (
        s.byWorkspace.get(workspace) ?? {
            canUndo: createEmptySlot(),
            canRedo: createEmptySlot(),
        }
    );
}

function setFlags(
    s: State,
    workspace: string,
    patch: Partial<WorkspaceFlags>,
): State {
    const m = new Map(s.byWorkspace);
    m.set(workspace, { ...getFlags(s, workspace), ...patch });
    return { byWorkspace: m };
}

function createVersionControlStore() {
    const store = writable<State>({ byWorkspace: new Map() });
    const { subscribe } = store;

    /**
     * Whether a step is running.
     *
     * One step is four requests plus a reload of the editor, and each of them
     * takes the workspace lock. Key repeat fires far faster than that
     * completes, so a press that arrives while one is running is ignored
     * rather than remembered: holding Ctrl+Z steps through the history as
     * fast as the backend manages, and letting go stops at once.
     */
    let running = false;
    let lastStepEndedAt = 0;

    /**
     * Whether the workspace has a change to step over in this direction.
     *
     * Cached per workspace and direction; `force` reloads rather than
     * answering from the cache.
     */
    async function canStep(
        workspace: string | undefined,
        direction: Direction,
        force: boolean,
    ): Promise<boolean> {
        const target = resolveWorkspace(workspace);
        if (!target) return false;
        const slot = direction === "undo" ? "canUndo" : "canRedo";
        const call = direction === "undo" ? sdkCanUndo : sdkCanRedo;
        return (
            (await loadSlot(
                store,
                s => getFlags(s, target)[slot],
                (s, patch) =>
                    setFlags(s, target, {
                        [slot]: { ...getFlags(s, target)[slot], ...patch },
                    }),
                () => call({ path: { datasetName: target } }),
                LOG,
                `${slot} for workspace="${target}"`,
                force,
            )) ?? false
        );
    }

    async function canUndo(workspace?: string, force = false) {
        return canStep(workspace, "undo", force);
    }

    async function canRedo(workspace?: string, force = false) {
        return canStep(workspace, "redo", force);
    }

    async function refresh(workspace?: string) {
        const target = resolveWorkspace(workspace);
        if (!target) return;
        await Promise.all([canUndo(target, true), canRedo(target, true)]);
    }

    async function doUndo(workspace?: string) {
        return runMove(() => step(workspace, "undo"));
    }

    async function doRedo(workspace?: string) {
        return runMove(() => step(workspace, "redo"));
    }

    /**
     * Runs a move through the history, with the pause that keeps the next one
     * from starting before this one has settled.
     *
     * Every kind of move goes through here, so that a restore and the undo
     * after it are held apart the same way two undos are.
     */
    async function runMove(perform: () => Promise<MoveResult>) {
        // Told apart from a move that ran, so that a press which did nothing
        // does not reload the editor for nothing.
        if (running || Date.now() - lastStepEndedAt < MIN_STEP_INTERVAL_MS) {
            return { error: null, skipped: true };
        }
        running = true;
        let cancelled = false;
        try {
            const result = await perform();
            cancelled = !!result.cancelled;
            return result;
        } finally {
            running = false;
            // Declining is not a move; the next press is a fresh decision.
            if (!cancelled) lastStepEndedAt = Date.now();
        }
    }

    /** Reports a move that could not be attempted at all. */
    function cannotStart(move: Move, message: string): MoveResult {
        console.error(`${LOG} ${move} failed`, message);
        toastStore.error(WORDING[move].failureTitle, message);
        return { error: message };
    }

    /** Reports a move the backend refused. */
    function refused(move: Move, error: unknown): MoveResult {
        console.error(`${LOG} ${move} failed`, error);
        toastStore.error(WORDING[move].failureTitle, WORDING[move].failureText);
        return { error };
    }

    /**
     * Records where a move left the workspace and says what it did.
     *
     * A move reaches the whole workspace, so what it changed may sit in a
     * graph the user is not looking at, which is why the whole workspace is
     * invalidated rather than the graph in view.
     */
    function landed(
        move: Move,
        target: string,
        data: MoveData | undefined,
    ): MoveResult {
        announce(move, data?.change);
        applyMoveResult(target, data);
        return { error: null };
    }

    /**
     * A step through the workspace history. Undo and redo differ only in which
     * endpoint they call, what they are called in a message, and whether
     * anything is asked first.
     */
    async function step(
        workspace: string | undefined,
        direction: Direction,
    ): Promise<MoveResult> {
        const target = resolveWorkspace(workspace);
        if (!target) {
            return cannotStart(direction, `No ${direction} target selected.`);
        }
        if (direction === "undo" && !(await confirmedIfDestructive(target))) {
            // Skipped too: nothing moved, so there is nothing to reload for.
            return { error: null, cancelled: true, skipped: true };
        }
        const call = direction === "undo" ? sdkUndo : sdkRedo;
        const { data, error } = await call({ path: { datasetName: target } });
        return error
            ? refused(direction, error)
            : landed(direction, target, data);
    }

    /**
     * Puts the workspace back the way a recorded version left it.
     *
     * Not a step through the history but a change of its own, so it ends up
     * here all the same: it can move anything in the workspace, which is what
     * decides what has to be reloaded and what the user has to be told.
     *
     * @param changeId the version to restore to
     * @param graphUris the graphs to put back, or empty for the whole workspace
     * @param workspace the workspace to act on, or the selected one
     */
    async function restore(
        changeId: string,
        graphUris: string[] = [],
        workspace?: string,
    ) {
        return runMove(async () => {
            const target = resolveWorkspace(workspace);
            if (!target) {
                return cannotStart("restore", "No workspace selected.");
            }
            const { data, error } = await sdkRestore({
                path: { datasetName: target },
                body: { versionId: changeId, graphUris },
            });
            if (error) return refused("restore", error);
            // A restore that covers nothing which has changed since writes no
            // change at all. Saying it restored something would be a lie, and
            // reloading for it would be work for nothing.
            if (!data?.change) {
                toastStore.info(
                    "Nothing to restore",
                    "The workspace is already at that version.",
                );
                return { error: null, skipped: true };
            }
            return landed("restore", target, data);
        });
    }

    /**
     * Reloads what a move may have changed and records where it left the
     * history.
     *
     * The move says that itself, so there is nothing left to ask: two fewer
     * requests, and no window in which another request could move the workspace
     * on between the move and the question.
     */
    function applyMoveResult(
        target: string,
        data: { canUndo?: boolean; canRedo?: boolean } | undefined,
    ) {
        invalidateWorkspace(target);
        store.update(s =>
            setFlags(s, target, {
                canUndo: {
                    ...getFlags(s, target).canUndo,
                    data: !!data?.canUndo,
                },
                canRedo: {
                    ...getFlags(s, target).canRedo,
                    data: !!data?.canRedo,
                },
            }),
        );
    }

    /**
     * Says what the move did, and where.
     *
     * A move reaches the whole workspace, so what it changed may sit in a graph the user is not
     * looking at — or in its SHACL shapes rather than its schema. Without saying so, the editor
     * would appear unchanged and the change would look lost.
     */
    function announce(move: Move, entry: ChangeLogEntryDTO | undefined) {
        const elsewhere = (entry?.affectedGraphUris ?? []).filter(
            graph => graph !== editorState.selectedGraph.getValue(),
        );
        toastStore.info(
            WORDING[move].successTitle,
            describe(move, entry, elsewhere),
        );
    }

    /**
     * Asks before an undo that makes something disappear — an imported or newly created graph, a
     * new diagram. A change that only alters what already exists goes through unasked, which is
     * the common case and must not become a click-through habit.
     */
    async function confirmedIfDestructive(workspace: string): Promise<boolean> {
        const { data, error } = await sdkPendingUndo({
            path: { datasetName: workspace },
        });
        if (error) {
            console.error(`${LOG} could not read the pending undo`, error);
            return true;
        }
        const removed = data?.removedOnUndo ?? [];
        if (removed.length === 0) return true;
        return undoConfirmStore.confirm(data?.message ?? "", removed);
    }

    function invalidateWorkspace(workspace: string) {
        classStore.invalidateWorkspace(workspace);
        ontologyStore.invalidateWorkspace(workspace);
        packageStore.invalidateWorkspace(workspace);
        datatypesStore.invalidateWorkspace(workspace);
        customDiagramStore.invalidateWorkspace(workspace);
        // Creating, deleting and renaming a graph are undoable, so the graph
        // list the navigation shows can be stale too.
        graphStore.invalidateWorkspace(workspace);
        // The merged view is derived from the schemas: undoing a rename moves
        // a class between merged entries, and the class editor resolves the
        // open class through this diagram.
        crossProfileStore.invalidateWorkspace(workspace);
        // The namespaces are part of the cached workspace list, and they are
        // undoable like everything else in a workspace.
        workspaceStore.invalidate();
    }

    return {
        subscribe,
        refresh,
        canUndo,
        canRedo,
        undo: doUndo,
        redo: doRedo,
        restore,
    };
}

function resolveWorkspace(workspace?: string) {
    return workspace ?? editorState.selectedWorkspace.getValue() ?? null;
}

/**
 * What the change did, and where it landed if that is somewhere the user is not looking.
 */
function describe(
    move: Move,
    entry: ChangeLogEntryDTO | undefined,
    elsewhere: string[],
): string {
    const what = entry?.message ?? WORDING[move].unnamedChange;
    if (elsewhere.length === 0) return what;
    return elsewhere.length === 1
        ? `${what} in ${shortName(elsewhere[0])}`
        : `${what} in ${elsewhere.length} graphs`;
}

/** The readable tail of a graph URI, for a label that has to fit in a toast. */
function shortName(graphUri: string): string {
    const tail = graphUri.split(/[#/]/).filter(Boolean).pop();
    return tail || graphUri;
}

export { createVersionControlStore };
