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

const LOG = "[versionControlStore]";

export const versionControlStore = createVersionControlStore();

const WORDING: Record<
    Direction,
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
        return runStep(workspace, "undo");
    }

    async function doRedo(workspace?: string) {
        return runStep(workspace, "redo");
    }

    async function runStep(
        workspace: string | undefined,
        direction: Direction,
    ) {
        // Told apart from a step that ran, so that a press which did nothing
        // does not reload the editor for nothing.
        if (running || Date.now() - lastStepEndedAt < MIN_STEP_INTERVAL_MS) {
            return { error: null, skipped: true };
        }
        running = true;
        let cancelled = false;
        try {
            const result = await step(workspace, direction);
            cancelled = !!result.cancelled;
            return result;
        } finally {
            running = false;
            // Declining is not a step; the next press is a fresh decision.
            if (!cancelled) lastStepEndedAt = Date.now();
        }
    }

    /**
     * A step through the workspace history. Undo and redo differ only in which
     * endpoint they call and what they are called in a message, so they share
     * everything that matters: an undone change can sit in any graph of the
     * workspace, which is why the whole workspace is invalidated rather than
     * the graph the user happens to be looking at.
     */
    async function step(workspace: string | undefined, direction: Direction) {
        const target = resolveWorkspace(workspace);
        if (!target) {
            const message = `No ${direction} target selected.`;
            console.error(`${LOG} ${direction} failed`, message);
            toastStore.error(WORDING[direction].failureTitle, message);
            return { error: message };
        }
        if (direction === "undo" && !(await confirmedIfDestructive(target))) {
            // Skipped too: nothing moved, so there is nothing to reload for.
            return { error: null, cancelled: true, skipped: true };
        }
        const call = direction === "undo" ? sdkUndo : sdkRedo;
        const { data, error } = await call({ path: { datasetName: target } });
        if (error) {
            console.error(`${LOG} ${direction} failed`, error);
            toastStore.error(
                WORDING[direction].failureTitle,
                WORDING[direction].failureText,
            );
            return { error };
        }
        announce(direction, data?.change);

        invalidateWorkspace(target);
        // The step says where it left the history, so there is nothing left to
        // ask: two fewer requests, and no window in which another request could
        // move the workspace on between the step and the question.
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
        return { error: null };
    }

    /**
     * Says what was undone or redone, and where.
     *
     * Undo reaches the whole workspace, so what it took back may sit in a graph the user is not
     * looking at — or in its SHACL shapes rather than its schema. Without saying so, the editor
     * would appear unchanged and the change would look lost.
     */
    function announce(
        direction: Direction,
        entry: ChangeLogEntryDTO | undefined,
    ) {
        const elsewhere = (entry?.affectedGraphUris ?? []).filter(
            graph => graph !== editorState.selectedGraph.getValue(),
        );
        toastStore.info(
            WORDING[direction].successTitle,
            describe(direction, entry, elsewhere),
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
    };
}

function resolveWorkspace(workspace?: string) {
    return workspace ?? editorState.selectedWorkspace.getValue() ?? null;
}

/**
 * What the change did, and where it landed if that is somewhere the user is not looking.
 */
function describe(
    direction: Direction,
    entry: ChangeLogEntryDTO | undefined,
    elsewhere: string[],
): string {
    const what = entry?.message ?? WORDING[direction].unnamedChange;
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
