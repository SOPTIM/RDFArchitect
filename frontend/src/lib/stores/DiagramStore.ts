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

import { writable, get } from "svelte/store";

import { describeError } from "./StoreLogging";
import {
    getCustomDiagramList,
    getCustomDiagramList1,
    getDiagramRenderingData,
    getDiagramRenderingData1,
    replaceDiagram,
    replaceDiagram1,
    deleteDiagram,
    deleteDiagram1,
    addToDiagram,
    addToDiagram1,
    removeFromDiagram,
    removeFromDiagram1,
    type CustomDiagram,
    type RenderingDataDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type Result<T = void> = { error: unknown; data?: T };

type ListState<T> = {
    data: T[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type ItemState<T> = {
    data: T | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type StoreState = {
    // key: dataset
    datasetLists: Map<string, ListState<CustomDiagram>>;
    // key: dataset::graph
    graphLists: Map<string, ListState<CustomDiagram>>;
    // key: dataset::diagramId
    datasetRenderings: Map<string, ItemState<RenderingDataDto>>;
    // key: dataset::graph::diagramId
    graphRenderings: Map<string, ItemState<RenderingDataDto>>;
};

const LOG_PREFIX = "[customDiagramStore]";

export const customDiagramStore = createCustomDiagramStore();

const emptyListState = (): ListState<CustomDiagram> => ({
    data: null,
    fetchedAt: null,
    pending: null,
    error: null,
});

const emptyItemState = <T>(): ItemState<T> => ({
    data: null,
    fetchedAt: null,
    pending: null,
    error: null,
});

const graphListKey = (datasetName: string, graphURI: string) =>
    `${datasetName}::${graphURI}`;
const datasetRenderingKey = (datasetName: string, diagramId: string) =>
    `${datasetName}::${diagramId}`;
const graphRenderingKey = (
    datasetName: string,
    graphURI: string,
    diagramId: string,
) => `${datasetName}::${graphURI}::${diagramId}`;

function createCustomDiagramStore() {
    const store = writable<StoreState>({
        datasetLists: new Map(),
        graphLists: new Map(),
        datasetRenderings: new Map(),
        graphRenderings: new Map(),
    });

    const { subscribe, update } = store;

    // ---------- helpers ----------
    function getDatasetListState(
        state: StoreState,
        datasetName: string,
    ): ListState<CustomDiagram> {
        return state.datasetLists.get(datasetName) ?? emptyListState();
    }

    function getGraphListState(
        state: StoreState,
        datasetName: string,
        graphURI: string,
    ): ListState<CustomDiagram> {
        return (
            state.graphLists.get(graphListKey(datasetName, graphURI)) ??
            emptyListState()
        );
    }

    function setDatasetListState(
        state: StoreState,
        datasetName: string,
        next: ListState<CustomDiagram>,
    ): StoreState {
        const map = new Map(state.datasetLists);
        map.set(datasetName, next);
        return { ...state, datasetLists: map };
    }

    function setGraphListState(
        state: StoreState,
        datasetName: string,
        graphURI: string,
        next: ListState<CustomDiagram>,
    ): StoreState {
        const key = graphListKey(datasetName, graphURI);
        const map = new Map(state.graphLists);
        map.set(key, next);
        return { ...state, graphLists: map };
    }

    function getDatasetRenderingState(
        state: StoreState,
        datasetName: string,
        diagramId: string,
    ): ItemState<RenderingDataDto> {
        return (
            state.datasetRenderings.get(
                datasetRenderingKey(datasetName, diagramId),
            ) ?? emptyItemState<RenderingDataDto>()
        );
    }

    function getGraphRenderingState(
        state: StoreState,
        datasetName: string,
        graphURI: string,
        diagramId: string,
    ): ItemState<RenderingDataDto> {
        return (
            state.graphRenderings.get(
                graphRenderingKey(datasetName, graphURI, diagramId),
            ) ?? emptyItemState<RenderingDataDto>()
        );
    }

    function setDatasetRenderingState(
        state: StoreState,
        datasetName: string,
        diagramId: string,
        next: ItemState<RenderingDataDto>,
    ): StoreState {
        const key = datasetRenderingKey(datasetName, diagramId);
        const map = new Map(state.datasetRenderings);
        map.set(key, next);
        return { ...state, datasetRenderings: map };
    }

    function setGraphRenderingState(
        state: StoreState,
        datasetName: string,
        graphURI: string,
        diagramId: string,
        next: ItemState<RenderingDataDto>,
    ): StoreState {
        const key = graphRenderingKey(datasetName, graphURI, diagramId);
        const map = new Map(state.graphRenderings);
        map.set(key, next);
        return { ...state, graphRenderings: map };
    }

    // ---------- loaders ----------
    async function loadDatasetDiagrams(datasetName: string, force = false) {
        if (!datasetName) return;

        const current = getDatasetListState(get(store), datasetName);
        if (!force && current.data !== null) return;
        if (current.pending) return current.pending;

        console.log(
            `${LOG_PREFIX} Loading dataset diagrams for "${datasetName}"`,
        );

        const pending = (async () => {
            try {
                const { data, error } = await getCustomDiagramList1({
                    path: { datasetName },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load dataset diagrams`,
                        await describeError(error),
                    );
                    update(s =>
                        setDatasetListState(s, datasetName, {
                            ...getDatasetListState(s, datasetName),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setDatasetListState(s, datasetName, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading dataset diagrams`,
                    err,
                );
                update(s =>
                    setDatasetListState(s, datasetName, {
                        ...getDatasetListState(s, datasetName),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setDatasetListState(s, datasetName, {
                ...getDatasetListState(s, datasetName),
                pending,
            }),
        );

        return pending;
    }

    async function loadGraphDiagrams(
        datasetName: string,
        graphURI: string,
        force = false,
    ) {
        if (!datasetName || !graphURI) return;

        const current = getGraphListState(get(store), datasetName, graphURI);
        if (!force && current.data !== null) return;
        if (current.pending) return current.pending;

        console.log(
            `${LOG_PREFIX} Loading graph diagrams for dataset="${datasetName}" graph="${graphURI}"`,
        );

        const pending = (async () => {
            try {
                const { data, error } = await getCustomDiagramList({
                    path: { datasetName, graphURI },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load graph diagrams`,
                        await describeError(error),
                    );
                    update(s =>
                        setGraphListState(s, datasetName, graphURI, {
                            ...getGraphListState(s, datasetName, graphURI),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setGraphListState(s, datasetName, graphURI, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading graph diagrams`,
                    err,
                );
                update(s =>
                    setGraphListState(s, datasetName, graphURI, {
                        ...getGraphListState(s, datasetName, graphURI),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setGraphListState(s, datasetName, graphURI, {
                ...getGraphListState(s, datasetName, graphURI),
                pending,
            }),
        );

        return pending;
    }

    async function loadDatasetDiagramRendering(
        datasetName: string,
        diagramId: string,
        force = false,
    ): Promise<Result<RenderingDataDto>> {
        if (!datasetName || !diagramId) return { error: null };

        const current = getDatasetRenderingState(
            get(store),
            datasetName,
            diagramId,
        );
        if (!force && current.data) return { error: null, data: current.data };
        if (current.pending) {
            await current.pending;
            const next = getDatasetRenderingState(
                get(store),
                datasetName,
                diagramId,
            );
            return { error: next.error, data: next.data ?? undefined };
        }

        const pending = (async () => {
            try {
                const { data, error } = await getDiagramRenderingData1({
                    path: { datasetName, diagramId },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load dataset diagram rendering`,
                        await describeError(error),
                    );
                    update(s =>
                        setDatasetRenderingState(s, datasetName, diagramId, {
                            ...getDatasetRenderingState(
                                s,
                                datasetName,
                                diagramId,
                            ),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setDatasetRenderingState(s, datasetName, diagramId, {
                        data: data ?? null,
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading dataset diagram rendering`,
                    err,
                );
                update(s =>
                    setDatasetRenderingState(s, datasetName, diagramId, {
                        ...getDatasetRenderingState(s, datasetName, diagramId),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setDatasetRenderingState(s, datasetName, diagramId, {
                ...getDatasetRenderingState(s, datasetName, diagramId),
                pending,
            }),
        );

        await pending;
        const next = getDatasetRenderingState(
            get(store),
            datasetName,
            diagramId,
        );
        return { error: next.error, data: next.data ?? undefined };
    }

    async function loadGraphDiagramRendering(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        force = false,
    ): Promise<Result<RenderingDataDto>> {
        if (!datasetName || !graphURI || !diagramId) return { error: null };

        const current = getGraphRenderingState(
            get(store),
            datasetName,
            graphURI,
            diagramId,
        );
        if (!force && current.data) return { error: null, data: current.data };
        if (current.pending) {
            await current.pending;
            const next = getGraphRenderingState(
                get(store),
                datasetName,
                graphURI,
                diagramId,
            );
            return { error: next.error, data: next.data ?? undefined };
        }

        const pending = (async () => {
            try {
                const { data, error } = await getDiagramRenderingData({
                    path: { datasetName, graphURI, diagramId },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load graph diagram rendering`,
                        await describeError(error),
                    );
                    update(s =>
                        setGraphRenderingState(
                            s,
                            datasetName,
                            graphURI,
                            diagramId,
                            {
                                ...getGraphRenderingState(
                                    s,
                                    datasetName,
                                    graphURI,
                                    diagramId,
                                ),
                                pending: null,
                                error,
                            },
                        ),
                    );
                    return;
                }

                update(s =>
                    setGraphRenderingState(
                        s,
                        datasetName,
                        graphURI,
                        diagramId,
                        {
                            data: data ?? null,
                            fetchedAt: Date.now(),
                            pending: null,
                            error: null,
                        },
                    ),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error loading graph diagram rendering`,
                    err,
                );
                update(s =>
                    setGraphRenderingState(
                        s,
                        datasetName,
                        graphURI,
                        diagramId,
                        {
                            ...getGraphRenderingState(
                                s,
                                datasetName,
                                graphURI,
                                diagramId,
                            ),
                            pending: null,
                            error: err,
                        },
                    ),
                );
            }
        })();

        update(s =>
            setGraphRenderingState(s, datasetName, graphURI, diagramId, {
                ...getGraphRenderingState(s, datasetName, graphURI, diagramId),
                pending,
            }),
        );

        await pending;
        const next = getGraphRenderingState(
            get(store),
            datasetName,
            graphURI,
            diagramId,
        );
        return { error: next.error, data: next.data ?? undefined };
    }

    // ---------- mutations ----------
    async function saveDatasetDiagram(
        datasetName: string,
        diagramId: string,
        diagram: unknown,
    ): Promise<Result> {
        const { error } = await replaceDiagram1({
            path: { datasetName, diagramId },
            body: diagram as never,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not save dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Could not save dataset diagram.");
            return { error };
        }

        invalidateDataset(datasetName);
        toastStore.success("Diagram saved", "Dataset diagram was saved.");
        return { error: null };
    }

    async function saveGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        diagram: unknown,
    ): Promise<Result> {
        const { error } = await replaceDiagram({
            path: { datasetName, graphURI, diagramId },
            body: diagram as never,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not save graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Could not save graph diagram.");
            return { error };
        }

        invalidateGraph(datasetName, graphURI);
        toastStore.success("Diagram saved", "Graph diagram was saved.");
        return { error: null };
    }

    async function deleteDatasetDiagram(
        datasetName: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteDiagram1({
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

        invalidateDataset(datasetName);
        toastStore.success("Diagram deleted", "Dataset diagram was removed.");
        return { error: null };
    }

    async function deleteGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
    ): Promise<Result> {
        const { error } = await deleteDiagram({
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

        invalidateGraph(datasetName, graphURI);
        toastStore.success("Diagram deleted", "Graph diagram was removed.");
        return { error: null };
    }

    async function addClassesToDatasetDiagram(
        datasetName: string,
        diagramId: string,
        classes: string[],
    ): Promise<Result> {
        const { error } = await addToDiagram1({
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
        const { error } = await addToDiagram({
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

    async function removeClassFromDatasetDiagram(
        datasetName: string,
        diagramId: string,
        classId: string,
    ): Promise<Result> {
        const { error } = await removeFromDiagram1({
            path: { datasetName, diagramId, classId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not remove class from dataset diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                "Could not remove class from diagram.",
            );
            return { error };
        }

        invalidateDataset(datasetName);
        return { error: null };
    }

    async function removeClassFromGraphDiagram(
        datasetName: string,
        graphURI: string,
        diagramId: string,
        classId: string,
    ): Promise<Result> {
        const { error } = await removeFromDiagram({
            path: { datasetName, graphURI, diagramId, classId },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not remove class from graph diagram "${diagramId}"`,
                await describeError(error),
            );
            toastStore.error(
                "Update failed",
                "Could not remove class from diagram.",
            );
            return { error };
        }

        invalidateGraph(datasetName, graphURI);
        return { error: null };
    }

    // ---------- getters ----------
    function getDatasetDiagrams(datasetName: string): CustomDiagram[] | null {
        return getDatasetListState(get(store), datasetName).data;
    }

    function getGraphDiagrams(
        datasetName: string,
        graphURI: string,
    ): CustomDiagram[] | null {
        return getGraphListState(get(store), datasetName, graphURI).data;
    }

    function getDatasetDiagramRendering(
        datasetName: string,
        diagramId: string,
    ): RenderingDataDto | null {
        return getDatasetRenderingState(get(store), datasetName, diagramId)
            .data;
    }

    function getGraphDiagramRendering(
        datasetName: string,
        graphURI: string,
        diagramId: string,
    ): RenderingDataDto | null {
        return getGraphRenderingState(
            get(store),
            datasetName,
            graphURI,
            diagramId,
        ).data;
    }

    // ---------- invalidation ----------
    function invalidateDataset(datasetName: string) {
        update(s => {
            const datasetLists = new Map(s.datasetLists);
            const graphLists = new Map(s.graphLists);
            const datasetRenderings = new Map(s.datasetRenderings);
            const graphRenderings = new Map(s.graphRenderings);

            datasetLists.delete(datasetName);

            const datasetPrefix = `${datasetName}::`;
            for (const key of graphLists.keys()) {
                if (key.startsWith(datasetPrefix)) graphLists.delete(key);
            }
            for (const key of datasetRenderings.keys()) {
                if (key.startsWith(datasetPrefix))
                    datasetRenderings.delete(key);
            }
            for (const key of graphRenderings.keys()) {
                if (key.startsWith(datasetPrefix)) graphRenderings.delete(key);
            }

            return {
                datasetLists,
                graphLists,
                datasetRenderings,
                graphRenderings,
            };
        });
    }

    function invalidateGraph(datasetName: string, graphURI: string) {
        update(s => {
            const graphLists = new Map(s.graphLists);
            const graphRenderings = new Map(s.graphRenderings);

            graphLists.delete(graphListKey(datasetName, graphURI));

            const prefix = `${datasetName}::${graphURI}::`;
            for (const key of graphRenderings.keys()) {
                if (key.startsWith(prefix)) graphRenderings.delete(key);
            }

            return { ...s, graphLists, graphRenderings };
        });
    }

    function invalidateAll() {
        update(() => ({
            datasetLists: new Map(),
            graphLists: new Map(),
            datasetRenderings: new Map(),
            graphRenderings: new Map(),
        }));
    }

    return {
        subscribe,

        // loaders
        loadDatasetDiagrams,
        loadGraphDiagrams,
        loadDatasetDiagramRendering,
        loadGraphDiagramRendering,

        // getters
        getDatasetDiagrams,
        getGraphDiagrams,
        getDatasetDiagramRendering,
        getGraphDiagramRendering,

        // mutations
        saveDatasetDiagram,
        saveGraphDiagram,
        deleteDatasetDiagram,
        deleteGraphDiagram,
        addClassesToDatasetDiagram,
        addClassesToGraphDiagram,
        removeClassFromDatasetDiagram,
        removeClassFromGraphDiagram,

        // invalidation
        invalidateDataset,
        invalidateGraph,
        invalidateAll,
    };
}
