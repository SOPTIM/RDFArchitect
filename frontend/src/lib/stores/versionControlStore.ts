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
import { loadSlot, makeGraphKey } from "./storeHelpers";
import { type AsyncSlot, createEmptySlot } from "./storeTypes";
import {
    undo as sdkUndo,
    redo as sdkRedo,
    canUndo as sdkCanUndo,
    canRedo as sdkCanRedo,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type GraphFlags = {
    canUndo: AsyncSlot<boolean>;
    canRedo: AsyncSlot<boolean>;
};

type State = { byGraph: Map<string, GraphFlags> };

const LOG = "[versionControlStore]";

export const versionControlStore = createVersionControlStore();

function getGraphFlags(s: State, workspace: string, graph: string): GraphFlags {
    return (
        s.byGraph.get(makeGraphKey(workspace, graph)) ?? {
            canUndo: createEmptySlot(),
            canRedo: createEmptySlot(),
        }
    );
}

function setGraphFlags(
    s: State,
    workspace: string,
    graph: string,
    patch: Partial<GraphFlags>,
): State {
    const m = new Map(s.byGraph);
    m.set(makeGraphKey(workspace, graph), {
        ...getGraphFlags(s, workspace, graph),
        ...patch,
    });
    return { byGraph: m };
}

function createVersionControlStore() {
    const store = writable<State>({ byGraph: new Map() });
    const { subscribe } = store;

    async function canUndo(
        workspace?: string,
        graph?: string,
        force = false,
    ): Promise<boolean> {
        const targets = resolveTargets(workspace, graph);
        if (!targets) return false;
        return (
            (await loadSlot(
                store,
                s => getGraphFlags(s, targets.workspace, targets.graph).canUndo,
                (s, patch) =>
                    setGraphFlags(s, targets.workspace, targets.graph, {
                        canUndo: {
                            ...getGraphFlags(
                                s,
                                targets.workspace,
                                targets.graph,
                            ).canUndo,
                            ...patch,
                        },
                    }),
                () =>
                    sdkCanUndo({
                        path: {
                            datasetName: targets.workspace,
                            graphURI: targets.graph,
                        },
                    }),
                LOG,
                `canUndo for workspace="${targets.workspace}" graph="${targets.graph}"`,
                force,
            )) ?? false
        );
    }

    async function canRedo(
        workspace?: string,
        graph?: string,
        force = false,
    ): Promise<boolean> {
        const targets = resolveTargets(workspace, graph);
        if (!targets) return false;
        return (
            (await loadSlot(
                store,
                s => getGraphFlags(s, targets.workspace, targets.graph).canRedo,
                (s, patch) =>
                    setGraphFlags(s, targets.workspace, targets.graph, {
                        canRedo: {
                            ...getGraphFlags(
                                s,
                                targets.workspace,
                                targets.graph,
                            ).canRedo,
                            ...patch,
                        },
                    }),
                () =>
                    sdkCanRedo({
                        path: {
                            datasetName: targets.workspace,
                            graphURI: targets.graph,
                        },
                    }),
                LOG,
                `canRedo for workspace="${targets.workspace}" graph="${targets.graph}"`,
                force,
            )) ?? false
        );
    }

    async function refresh(workspace?: string, graph?: string) {
        const targets = resolveTargets(workspace, graph);
        if (!targets) return;
        await Promise.all([
            canUndo(targets.workspace, targets.graph, true),
            canRedo(targets.workspace, targets.graph, true),
        ]);
    }

    async function doUndo(workspace?: string, graph?: string) {
        const targets = resolveTargets(workspace, graph);
        if (!targets) {
            console.error(`${LOG} undo failed`, "No undo target selected.");
            toastStore.error("Undo failed", "No undo target selected.");
            return { error: "No undo target selected." };
        }
        const { error } = await sdkUndo({
            path: { datasetName: targets.workspace, graphURI: targets.graph },
        });
        if (error) {
            console.error(`${LOG} undo failed`, error);
            toastStore.error("Undo failed", "Could not undo the last change.");
            return { error };
        }
        toastStore.info("Undone");

        classStore.invalidateGraph(targets.workspace, targets.graph);
        ontologyStore.invalidateGraph(targets.workspace, targets.graph);
        customDiagramStore.invalidateWorkspace(targets.workspace);
        packageStore.invalidateGraph(targets.workspace, targets.graph);
        datatypesStore.invalidateGraph(targets.workspace, targets.graph);
        await refresh(targets.workspace, targets.graph);
        return { error: null };
    }

    async function doRedo(workspace?: string, graph?: string) {
        const targets = resolveTargets(workspace, graph);
        if (!targets) {
            console.error(`${LOG} redo failed`, "No redo target selected.");
            toastStore.error("Redo failed", "No redo target selected.");
            return { error: "No redo target selected." };
        }
        const { error } = await sdkRedo({
            path: { datasetName: targets.workspace, graphURI: targets.graph },
        });
        if (error) {
            console.error(`${LOG} redo failed`, error);
            toastStore.error("Redo failed", "Could not redo the change.");
            return { error };
        }
        toastStore.info("Redone");

        classStore.invalidateGraph(targets.workspace, targets.graph);
        packageStore.invalidateGraph(targets.workspace, targets.graph);
        ontologyStore.invalidateGraph(targets.workspace, targets.graph);
        customDiagramStore.invalidateWorkspace(targets.workspace);
        await refresh(targets.workspace, targets.graph);
        return { error: null };
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

function resolveTargets(workspace?: string, graph?: string) {
    const d = workspace ?? editorState.selectedWorkspace.getValue();
    const g = graph ?? editorState.selectedGraph.getValue();
    return d && g ? { workspace: d, graph: g } : null;
}
export { createVersionControlStore };
