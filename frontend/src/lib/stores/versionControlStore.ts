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
    getUndoPreview as sdkUndoPreview,
    restoreVersion as sdkRestore,
} from "../api/generated";
import {
    type ChangeLogEntryDto,
    type HistoryStepDto,
} from "../api/generated/types.gen";
import { toastStore } from "../eventhandling/toastStore.svelte.js";
import { undoConfirmStore } from "../eventhandling/undoConfirmStore.svelte.js";

type Direction = "undo" | "redo";

/** A move through the history, including the one that is not a single step. */
type Move = Direction | "restore";

/** Whether a workspace can be stepped, one cached answer per workspace and direction. */
type State = { steppable: Map<string, AsyncSlot<boolean>> };

/**
 * What a move leaves the caller with: whether it failed, and whether it moved
 * anything at all — a move that did not is not something to reload for.
 */
type MoveResult = {
    error: unknown;
    skipped?: boolean;
};

const LOG = "[versionControlStore]";

export const versionControlStore = createVersionControlStore();

/**
 * Everything the moves through the history do differently: which endpoints a
 * step uses and what each move is called on screen. Keeping it in one table is
 * what lets them share the rest. A restore has no endpoint of its own here
 * because it carries a body rather than only a workspace.
 */
const MOVES = {
    undo: {
        check: sdkCanUndo,
        apply: sdkUndo,
        successTitle: "Undone",
        failureTitle: "Undo failed",
        failureText: "Could not undo the last change.",
        unnamedChange: "The last change was reverted.",
    },
    redo: {
        check: sdkCanRedo,
        apply: sdkRedo,
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
} as const;

/** Keyed like the graph-scoped caches of the other stores. */
function stepKey(workspace: string, direction: Direction) {
    return `${workspace}::${direction}`;
}

function createVersionControlStore() {
    const store = writable<State>({ steppable: new Map() });
    const { subscribe } = store;

    /**
     * Whether the workspace has a change to step over in this direction.
     *
     * Cached per workspace and direction; `force` asks the backend rather than
     * answering from the cache. A workspace nobody has asked about yet has no
     * entry at all, and no history either, so it cannot be stepped.
     */
    async function canStep(
        workspace: string | undefined,
        direction: Direction,
        force: boolean,
    ): Promise<boolean> {
        const target = resolveWorkspace(workspace);
        if (!target) return false;
        const key = stepKey(target, direction);

        const read = (s: State) =>
            s.steppable.get(key) ?? createEmptySlot<boolean>();
        const write = (s: State, patch: Partial<AsyncSlot<boolean>>) => ({
            steppable: new Map(s.steppable).set(key, { ...read(s), ...patch }),
        });
        const ask = () =>
            MOVES[direction].check({ path: { datasetName: target } });

        const cached = await loadSlot(
            store,
            read,
            write,
            ask,
            LOG,
            `can ${direction} in workspace="${target}"`,
            force,
        );
        return cached ?? false;
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

    /**
     * The moves asked for, run one after another.
     *
     * Every press counts, so one that arrives while a move is running waits for
     * it instead of being dropped. Running them one at a time keeps a burst of
     * presses from working the workspace twice over: each move takes the
     * workspace's write lock and is followed by a reload of the editor, and two
     * of those at once only make each other slower. The caller is handed the
     * promise of its own move, so it reloads when its move has run rather than
     * when somebody else's has. A restore queues with the steps, so it and the
     * undo after it are held apart the same way two undos are.
     */
    let queue: Promise<unknown> = Promise.resolve();

    function enqueue(perform: () => Promise<MoveResult>) {
        const result = queue.then(perform);
        // A move that throws must not take the ones behind it down with it.
        queue = result.catch(() => {});
        return result;
    }

    /** Reports a move that could not be attempted at all. */
    function cannotStart(move: Move, message: string): MoveResult {
        console.error(`${LOG} ${move} failed`, message);
        toastStore.error(MOVES[move].failureTitle, message);
        return { error: message };
    }

    /** Reports a move the backend refused. */
    function refused(move: Move, error: unknown): MoveResult {
        console.error(`${LOG} ${move} failed`, error);
        toastStore.error(MOVES[move].failureTitle, MOVES[move].failureText);
        return { error };
    }

    /**
     * Says what a move did, reloads what it may have changed and records where
     * it left the history.
     *
     * A move reaches the whole workspace, so what it changed may sit in a graph
     * the user is not looking at, which is why the whole workspace is
     * invalidated rather than the graph in view.
     */
    function landed(
        move: Move,
        target: string,
        step: HistoryStepDto | undefined,
    ): MoveResult {
        toastStore.info(MOVES[move].successTitle, describe(move, step?.change));
        invalidateWorkspace(target);
        rememberHistoryEnd(target, step);
        return { error: null };
    }

    /**
     * A step through the workspace history. A held Ctrl+Z is filtered out as
     * auto-repeat where the key is read, so every step here stands for a press
     * somebody made. Undo and redo differ only in which endpoint they call,
     * what they are called in a message, and whether anything is asked first.
     */
    async function takeStep(
        workspace: string | undefined,
        direction: Direction,
    ): Promise<MoveResult> {
        const target = resolveWorkspace(workspace);
        if (!target) {
            return cannotStart(direction, `No ${direction} target selected.`);
        }
        if (direction === "undo" && !(await confirmedIfDestructive(target))) {
            // Skipped: nothing moved, so there is nothing to reload for.
            return { error: null, skipped: true };
        }
        const { data, error } = await MOVES[direction].apply({
            path: { datasetName: target },
        });
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
        return enqueue(async () => {
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
     * Takes the ends of the history from the move's own answer. Asking for them
     * would be two more requests, and would leave a window in which another
     * request could move the workspace on in between.
     */
    function rememberHistoryEnd(
        workspace: string,
        step: HistoryStepDto | undefined,
    ) {
        store.update(s => ({
            steppable: new Map(s.steppable)
                .set(stepKey(workspace, "undo"), settled(!!step?.canUndo))
                .set(stepKey(workspace, "redo"), settled(!!step?.canRedo)),
        }));
    }

    /**
     * Asks before an undo that makes something disappear — an imported or newly created graph, a
     * new diagram. A change that only alters what already exists goes through unasked, which is
     * the common case and must not become a click-through habit.
     */
    async function confirmedIfDestructive(workspace: string): Promise<boolean> {
        const { data, error } = await sdkUndoPreview({
            path: { datasetName: workspace },
        });
        if (error) {
            console.error(`${LOG} could not read the undo preview`, error);
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
        undo: (workspace?: string) =>
            enqueue(() => takeStep(workspace, "undo")),
        redo: (workspace?: string) =>
            enqueue(() => takeStep(workspace, "redo")),
        restore,
    };
}

function resolveWorkspace(workspace?: string) {
    return workspace ?? editorState.selectedWorkspace.getValue() ?? null;
}

/** A slot holding a value that came with something else rather than being loaded. */
function settled<T>(data: T): AsyncSlot<T> {
    return { data, fetchedAt: Date.now(), pending: null, error: null };
}

/**
 * What the change did, and where it landed if that is somewhere the user is not looking.
 *
 * A move reaches the whole workspace, so what it changed may sit in a graph the user is not
 * looking at, or in its SHACL shapes rather than its schema. Without saying so, the editor would
 * appear unchanged and the change would look lost.
 */
function describe(move: Move, change: ChangeLogEntryDto | undefined): string {
    const what = change?.message ?? MOVES[move].unnamedChange;
    const elsewhere = (change?.affectedGraphUris ?? []).filter(
        graph => graph !== editorState.selectedGraph.getValue(),
    );
    if (elsewhere.length === 0) return what;
    if (elsewhere.length === 1) return `${what} in ${shortName(elsewhere[0])}`;
    return `${what} in ${elsewhere.length} graphs`;
}

/** The readable tail of a graph URI, for a label that has to fit in a toast. */
function shortName(graphUri: string): string {
    const tail = graphUri.split(/[#/]/).filter(Boolean).pop();
    return tail || graphUri;
}

export { createVersionControlStore };
