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
import { datatypesStore } from "./datatypesStore";
import { customDiagramStore } from "./diagramStore";
import { ontologyStore } from "./ontologyStore";
import { packageStore } from "./packageStore";
import { loadSlot } from "./storeHelpers";
import { type AsyncSlot, createEmptySlot } from "./storeTypes";
import {
    undo as sdkUndo,
    redo as sdkRedo,
    canUndo as sdkCanUndo,
    canRedo as sdkCanRedo,
    getPendingUndo as sdkPendingUndo,
} from "../api/generated";
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

const FAILURE_TITLE: Record<Direction, string> = {
    undo: "Undo failed",
    redo: "Redo failed",
};

const FAILURE_TEXT: Record<Direction, string> = {
    undo: "Could not undo the last change.",
    redo: "Could not redo the change.",
};

const SUCCESS_TITLE: Record<Direction, string> = {
    undo: "Undone",
    redo: "Redone",
};

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

    async function canUndo(
        workspace?: string,
        force = false,
    ): Promise<boolean> {
        const target = resolveWorkspace(workspace);
        if (!target) return false;
        return (
            (await loadSlot(
                store,
                s => getFlags(s, target).canUndo,
                (s, patch) =>
                    setFlags(s, target, {
                        canUndo: { ...getFlags(s, target).canUndo, ...patch },
                    }),
                () => sdkCanUndo({ path: { datasetName: target } }),
                LOG,
                `canUndo for workspace="${target}"`,
                force,
            )) ?? false
        );
    }

    async function canRedo(
        workspace?: string,
        force = false,
    ): Promise<boolean> {
        const target = resolveWorkspace(workspace);
        if (!target) return false;
        return (
            (await loadSlot(
                store,
                s => getFlags(s, target).canRedo,
                (s, patch) =>
                    setFlags(s, target, {
                        canRedo: { ...getFlags(s, target).canRedo, ...patch },
                    }),
                () => sdkCanRedo({ path: { datasetName: target } }),
                LOG,
                `canRedo for workspace="${target}"`,
                force,
            )) ?? false
        );
    }

    async function refresh(workspace?: string) {
        const target = resolveWorkspace(workspace);
        if (!target) return;
        await Promise.all([canUndo(target, true), canRedo(target, true)]);
    }

    async function doUndo(workspace?: string) {
        return step(workspace, "undo");
    }

    async function doRedo(workspace?: string) {
        return step(workspace, "redo");
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
            toastStore.error(FAILURE_TITLE[direction], message);
            return { error: message };
        }
        if (direction === "undo" && !(await confirmedIfDestructive(target))) {
            return { error: null, cancelled: true };
        }
        const call = direction === "undo" ? sdkUndo : sdkRedo;
        const { error } = await call({ path: { datasetName: target } });
        if (error) {
            console.error(`${LOG} ${direction} failed`, error);
            toastStore.error(FAILURE_TITLE[direction], FAILURE_TEXT[direction]);
            return { error };
        }
        toastStore.info(SUCCESS_TITLE[direction]);

        invalidateWorkspace(target);
        await refresh(target);
        return { error: null };
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

export { createVersionControlStore };
