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
    // key: dataset
    datasetLists: Map<string, AsyncListSlot<CustomDiagramDto>>;
    // key: dataset::graph
    graphLists: Map<string, AsyncListSlot<CustomDiagramDto>>;
};

const LOG_PREFIX = "[CustomDiagramStore]";

export const customDiagramStore = createCustomDiagramStore();

function createCustomDiagramStore() {
    const store = writable<StoreState>({
        datasetLists: new Map(),
        graphLists: new Map(),
    });

    const { subscribe, update } = store;

    // ---------- helpers ----------
    function getDatasetListState(
        state: StoreState,
        datasetName: string,
    ): AsyncListSlot<CustomDiagramDto> {
        return (
            state.datasetLists.get(datasetName) ??
            createEmptyListSlot<CustomDiagramDto>()
        );
    }

    function getGraphListState(
        state: StoreState,
        datasetName: string,
        graphURI: string,
    ): AsyncListSlot<CustomDiagramDto> {
        return (
            state.graphLists.get(makeGraphKey(datasetName, graphURI)) ??
            createEmptyListSlot<CustomDiagramDto>()
        );
    }

    // ---------- getters ----------
    async function getDatasetDiagrams(
        datasetName: string,
        force = false,
    ): Promise<CustomDiagramDto[] | null> {
        if (!datasetName) return null;
        return loadSlot<StoreState, CustomDiagramDto[]>(
            store,
            s =>
                s.datasetLists.get(datasetName) ??
                createEmptyListSlot<CustomDiagramDto>(),
            (s, patch) => {
                const map = new Map(s.datasetLists);
                map.set(datasetName, {
                    ...getDatasetListState(s, datasetName),
                    ...patch,
                });
                return { ...s, datasetLists: map };
            },
            () => getCustomDatasetDiagramList({ path: { datasetName } }),
            LOG_PREFIX,
            `dataset diagrams for "${datasetName}"`,
            force,
        );
    }

    async function getGraphDiagrams(
        datasetName: string,
        graphURI: string,
        force = false,
    ): Promise<CustomDiagramDto[] | null> {
        if (!datasetName || !graphURI) return null;
        const key = makeGraphKey(datasetName, graphURI);
        return loadSlot<StoreState, CustomDiagramDto[]>(
            store,
            s =>
                s.graphLists.get(key) ??
                createEmptyListSlot<CustomDiagramDto>(),
            (s, patch) => {
                const map = new Map(s.graphLists);
                map.set(key, {
                    ...getGraphListState(s, datasetName, graphURI),
                    ...patch,
                });
                return { ...s, graphLists: map };
            },
            () =>
                getCustomGraphDiagramList({ path: { datasetName, graphURI } }),
            LOG_PREFIX,
            `graph diagrams for dataset="${datasetName}" graph="${graphURI}"`,
            force,
        );
    }

    // ---------- mutations ----------
    async function saveDatasetDiagram(
        datasetName: string,
        diagramId: string,
        diagram: CustomDiagramDto,
    ): Promise<Result> {
        const { error } = await replaceCustomDatasetDiagram({
            path: { datasetName, diagramId },
            body: diagram,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not save dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Could not save dataset diagram.");
            return { error };
        }

        update(s => {
            const existing = s.datasetLists.get(datasetName);
            if (!existing?.data) return s;

            const datasetLists = new Map(s.datasetLists);
            const isNew = !existing.data.some(d => d.diagramId === diagramId);
            datasetLists.set(datasetName, {
                ...existing,
                data: isNew
                    ? [...existing.data, diagram]
                    : existing.data.map(d =>
                          d.diagramId === diagramId ? diagram : d,
                      ),
            });
            return { ...s, datasetLists };
        });

        toastStore.success("Diagram saved", "Dataset diagram was saved.");
        return { error: null };
    }

    async function saveGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        diagram: CustomDiagramDto,
    ): Promise<Result> {
        const { error } = await replaceCustomGraphDiagram({
            path: { datasetName, graphURI, diagramId },
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

        const key = makeGraphKey(datasetName, graphURI);
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

    async function deleteDatasetDiagram(
        datasetName: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteCustomDatasetDiagram({
            path: { datasetName, diagramId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Could not delete dataset diagram.",
            );
            return { error };
        }

        update(s => {
            const existing = s.datasetLists.get(datasetName);
            if (!existing?.data) return s;
            const datasetLists = new Map(s.datasetLists);
            datasetLists.set(datasetName, {
                ...existing,
                data: existing.data.filter(d => d.diagramId !== diagramId),
            });
            return { ...s, datasetLists };
        });

        toastStore.success("Diagram deleted", "Dataset diagram was removed.");
        return { error: null };
    }

    async function deleteGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteCustomGraphDiagram({
            path: { datasetName, graphURI, diagramId },
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

        const key = makeGraphKey(datasetName, graphURI);
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

    async function addClassesToDatasetDiagram(
        datasetName: string,
        diagramId: string,
        classes: string[],
    ): Promise<Result> {
        const { error } = await addToCustomDatasetDiagram({
            path: { datasetName, diagramId },
            body: classes as never,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add classes to dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                "Could not add classes to diagram.",
            );
            return { error };
        }

        invalidateDataset(datasetName);
        return { error: null };
    }

    async function addClassesToGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        classes: string[],
    ): Promise<Result> {
        const { error } = await addToCustomGraphDiagram({
            path: { datasetName, graphURI, diagramId },
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

        invalidateGraph(datasetName, graphURI);
        return { error: null };
    }

    async function removeClassesFromDatasetDiagram(
        datasetName: string,
        diagramId: string,
        classIDs: string[],
    ): Promise<Result> {
        const { error } = await removeFromCustomDatasetDiagram({
            path: { datasetName, diagramId },
            body: classIDs,
        });

        const classCount = classIDs.length;
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not remove class${classCount === 1 ? "" : "es"} from dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                `Could not remove class${classCount === 1 ? "" : "es"} from diagram.`,
            );
            return { error };
        }

        invalidateDataset(datasetName);
        return { error: null };
    }

    async function removeClassesFromGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        classIds: string[],
    ): Promise<Result> {
        const { error } = await removeFromDiagram({
            path: { datasetName, graphURI, diagramId },
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

        invalidateGraph(datasetName, graphURI);
        return { error: null };
    }

    // ---------- invalidation ----------
    function invalidateDataset(datasetName: string) {
        update(s => {
            const datasetLists = new Map(s.datasetLists);
            const graphLists = new Map(s.graphLists);

            datasetLists.delete(datasetName);

            const datasetPrefix = `${datasetName}::`;
            for (const key of graphLists.keys()) {
                if (key.startsWith(datasetPrefix)) graphLists.delete(key);
            }

            return {
                datasetLists,
                graphLists,
            };
        });
    }

    function invalidateGraph(datasetName: string, graphURI: string) {
        update(s => {
            const graphLists = new Map(s.graphLists);

            graphLists.delete(makeGraphKey(datasetName, graphURI));

            return { ...s, graphLists };
        });
    }

    return {
        subscribe,

        //getters
        getDatasetDiagrams,
        getGraphDiagrams,

        // mutations
        saveDatasetDiagram,
        saveGraphDiagram,
        deleteDatasetDiagram,
        deleteGraphDiagram,
        addClassesToDatasetDiagram,
        addClassesToGraphDiagram,
        removeClassesFromDatasetDiagram,
        removeClassesFromGraphDiagram,

        // invalidation
        invalidateDataset,
        invalidateGraph,
    };
}
export { createCustomDiagramStore };
