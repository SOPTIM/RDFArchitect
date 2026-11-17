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

import { loadSlot, makeGraphKey } from "./storeHelpers";
import { describeError } from "./storeLogging";
import {
    type AsyncListSlot,
    createEmptyListSlot,
    type Result,
} from "./storeTypes";
import {
    getCustomDatasetDiagramList,
    getCustomGraphDiagramList,
    replaceCustomDatasetDiagram,
    replaceCustomGraphDiagram,
    deleteCustomDatasetDiagram,
    deleteCustomGraphDiagram,
    addToCustomDatasetDiagram,
    removeFromCustomDatasetDiagram,
    addToCustomGraphDiagram,
    type CustomDiagramDto,
    removeFromDiagram,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type StoreState = {
    // key: workspace
    workspaceLists: Map<string, AsyncListSlot<CustomDiagramDto>>;
    // key: workspace::graph
    graphLists: Map<string, AsyncListSlot<CustomDiagramDto>>;
};

const LOG_PREFIX = "[CustomDiagramStore]";

export const customDiagramStore = createCustomDiagramStore();

function createCustomDiagramStore() {
    const store = writable<StoreState>({
        workspaceLists: new Map(),
        graphLists: new Map(),
    });

    const { subscribe, update } = store;

    // ---------- helpers ----------
    function getWorkspaceListState(
        state: StoreState,
        workspaceName: string,
    ): AsyncListSlot<CustomDiagramDto> {
        return (
            state.workspaceLists.get(workspaceName) ??
            createEmptyListSlot<CustomDiagramDto>()
        );
    }

    function getGraphListState(
        state: StoreState,
        workspaceName: string,
        graphName: string,
    ): AsyncListSlot<CustomDiagramDto> {
        return (
            state.graphLists.get(makeGraphKey(workspaceName, graphName)) ??
            createEmptyListSlot<CustomDiagramDto>()
        );
    }

    // ---------- getters ----------
    async function getWorkspaceDiagrams(
        workspaceName: string,
        force = false,
    ): Promise<CustomDiagramDto[] | null> {
        if (!workspaceName) return null;
        return loadSlot<StoreState, CustomDiagramDto[]>(
            store,
            s =>
                s.workspaceLists.get(workspaceName) ??
                createEmptyListSlot<CustomDiagramDto>(),
            (s, patch) => {
                const map = new Map(s.workspaceLists);
                map.set(workspaceName, {
                    ...getWorkspaceListState(s, workspaceName),
                    ...patch,
                });
                return { ...s, workspaceLists: map };
            },
            () =>
                getCustomDatasetDiagramList({
                    path: { workspaceName: workspaceName },
                }),
            LOG_PREFIX,
            `workspace diagrams for "${workspaceName}"`,
            force,
        );
    }

    async function getGraphDiagrams(
        workspaceName: string,
        graphName: string,
        force = false,
    ): Promise<CustomDiagramDto[] | null> {
        if (!workspaceName || !graphName) return null;
        const key = makeGraphKey(workspaceName, graphName);
        return loadSlot<StoreState, CustomDiagramDto[]>(
            store,
            s =>
                s.graphLists.get(key) ??
                createEmptyListSlot<CustomDiagramDto>(),
            (s, patch) => {
                const map = new Map(s.graphLists);
                map.set(key, {
                    ...getGraphListState(s, workspaceName, graphName),
                    ...patch,
                });
                return { ...s, graphLists: map };
            },
            () =>
                getCustomGraphDiagramList({
                    path: { workspaceName: workspaceName, graphName },
                }),
            LOG_PREFIX,
            `graph diagrams for workspace="${workspaceName}" graph="${graphName}"`,
            force,
        );
    }

    // ---------- mutations ----------
    async function saveWorkspaceDiagram(
        workspaceName: string,
        diagramId: string,
        diagram: CustomDiagramDto,
    ): Promise<Result> {
        const { error } = await replaceCustomDatasetDiagram({
            path: { workspaceName: workspaceName, diagramId },
            body: diagram,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not save workspace diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Save failed",
                "Could not save workspace diagram.",
            );
            return { error };
        }

        update(s => {
            const existing = s.workspaceLists.get(workspaceName);
            if (!existing?.data) return s;

            const workspaceLists = new Map(s.workspaceLists);
            const isNew = !existing.data.some(d => d.diagramId === diagramId);
            workspaceLists.set(workspaceName, {
                ...existing,
                data: isNew
                    ? [...existing.data, diagram]
                    : existing.data.map(d =>
                          d.diagramId === diagramId ? diagram : d,
                      ),
            });
            return { ...s, workspaceLists };
        });

        toastStore.success("Diagram saved", "Workspace diagram was saved.");
        return { error: null };
    }

    async function saveGraphDiagram(
        workspaceName: string,
        graphName: string,
        diagramId: string,
        diagram: CustomDiagramDto,
    ): Promise<Result> {
        const { error } = await replaceCustomGraphDiagram({
            path: { workspaceName: workspaceName, graphName, diagramId },
            body: diagram,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not save graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Could not save graph diagram.");
            return { error };
        }

        const key = makeGraphKey(workspaceName, graphName);
        update(s => {
            const existing = s.graphLists.get(key);
            if (!existing?.data) return s;

            const graphLists = new Map(s.graphLists);
            const isNew = !existing.data.some(d => d.diagramId === diagramId);
            graphLists.set(key, {
                ...existing,
                data: isNew
                    ? [...existing.data, diagram]
                    : existing.data.map(d =>
                          d.diagramId === diagramId ? diagram : d,
                      ),
            });
            return { ...s, graphLists };
        });

        toastStore.success("Diagram saved", "Graph diagram was saved.");
        return { error: null };
    }

    async function deleteWorkspaceDiagram(
        workspaceName: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteCustomDatasetDiagram({
            path: { workspaceName: workspaceName, diagramId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete workspace diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Could not delete workspace diagram.",
            );
            return { error };
        }

        update(s => {
            const existing = s.workspaceLists.get(workspaceName);
            if (!existing?.data) return s;
            const workspaceLists = new Map(s.workspaceLists);
            workspaceLists.set(workspaceName, {
                ...existing,
                data: existing.data.filter(d => d.diagramId !== diagramId),
            });
            return { ...s, workspaceLists };
        });

        toastStore.success("Diagram deleted", "Workspace diagram was removed.");
        return { error: null };
    }

    async function deleteGraphDiagram(
        workspaceName: string,
        graphName: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteCustomGraphDiagram({
            path: { workspaceName: workspaceName, graphName, diagramId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Could not delete graph diagram.",
            );
            return { error };
        }

        const key = makeGraphKey(workspaceName, graphName);
        update(s => {
            const existing = s.graphLists.get(key);
            if (!existing?.data) return s;
            const graphLists = new Map(s.graphLists);
            graphLists.set(key, {
                ...existing,
                data: existing.data.filter(d => d.diagramId !== diagramId),
            });
            return { ...s, graphLists };
        });

        toastStore.success("Diagram deleted", "Graph diagram was removed.");
        return { error: null };
    }

    async function addClassesToWorkspaceDiagram(
        workspaceName: string,
        diagramId: string,
        classes: string[],
    ): Promise<Result> {
        const { error } = await addToCustomDatasetDiagram({
            path: { workspaceName: workspaceName, diagramId },
            body: classes as never,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add classes to workspace diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                "Could not add classes to diagram.",
            );
            return { error };
        }

        invalidateWorkspace(workspaceName);
        return { error: null };
    }

    async function addClassesToGraphDiagram(
        workspaceName: string,
        graphName: string,
        diagramId: string,
        classes: string[],
    ): Promise<Result> {
        const { error } = await addToCustomGraphDiagram({
            path: { workspaceName: workspaceName, graphName, diagramId },
            body: classes as never,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add classes to graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                "Could not add classes to diagram.",
            );
            return { error };
        }

        invalidateGraph(workspaceName, graphName);
        return { error: null };
    }

    async function removeClassesFromWorkspaceDiagram(
        workspaceName: string,
        diagramId: string,
        classIDs: string[],
    ): Promise<Result> {
        const { error } = await removeFromCustomDatasetDiagram({
            path: { workspaceName: workspaceName, diagramId },
            body: classIDs,
        });

        const classCount = classIDs.length;
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not remove class${classCount === 1 ? "" : "es"} from workspace diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                `Could not remove class${classCount === 1 ? "" : "es"} from diagram.`,
            );
            return { error };
        }

        invalidateWorkspace(workspaceName);
        return { error: null };
    }

    async function removeClassesFromGraphDiagram(
        workspaceName: string,
        graphName: string,
        diagramId: string,
        classIds: string[],
    ): Promise<Result> {
        const { error } = await removeFromDiagram({
            path: { workspaceName: workspaceName, graphName, diagramId },
            body: classIds,
        });

        const classCount = classIds.length;
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not remove class${classCount === 1 ? "" : "es"} from graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                `Could not remove class${classCount === 1 ? "" : "es"} from diagram.`,
            );
            return { error };
        }

        invalidateGraph(workspaceName, graphName);
        return { error: null };
    }

    // ---------- invalidation ----------
    function invalidateWorkspace(workspaceName: string) {
        update(s => {
            const workspaceLists = new Map(s.workspaceLists);
            const graphLists = new Map(s.graphLists);

            workspaceLists.delete(workspaceName);

            const workspacePrefix = `${workspaceName}::`;
            for (const key of graphLists.keys()) {
                if (key.startsWith(workspacePrefix)) graphLists.delete(key);
            }

            return {
                workspaceLists,
                graphLists,
            };
        });
    }

    function invalidateGraph(workspaceName: string, graphName: string) {
        update(s => {
            const graphLists = new Map(s.graphLists);

            graphLists.delete(makeGraphKey(workspaceName, graphName));

            return { ...s, graphLists };
        });
    }

    return {
        subscribe,

        //getters
        getWorkspaceDiagrams,
        getGraphDiagrams,

        // mutations
        saveWorkspaceDiagram,
        saveGraphDiagram,
        deleteWorkspaceDiagram,
        deleteGraphDiagram,
        addClassesToWorkspaceDiagram,
        addClassesToGraphDiagram,
        removeClassesFromWorkspaceDiagram,
        removeClassesFromGraphDiagram,

        // invalidation
        invalidateWorkspace,
        invalidateGraph,
    };
}
export { createCustomDiagramStore };
