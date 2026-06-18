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

import { classStore } from "./ClassStore";
import { packageStore } from "./PackageStore";
import {
    undo as sdkUndo,
    redo as sdkRedo,
    canUndo as sdkCanUndo,
    canRedo as sdkCanRedo,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type Flags = {
    canUndo: boolean;
    canRedo: boolean;
    pending: Promise<void> | null;
};
type State = { byGraph: Map<string, Flags> };

const LOG = "[versionControlStore]";

export const versionControlStore = createStore();
const key = (dataset: string, graph: string) => `${dataset}::${graph}`;

function emptyFlags(): Flags {
    return { canUndo: false, canRedo: false, pending: null };
}

function createStore() {
    const store = writable<State>({ byGraph: new Map() });
    const { subscribe, update } = store;

    function patch(dataset: string, graph: string, next: Partial<Flags>) {
        update(s => {
            const m = new Map(s.byGraph);
            const cur = m.get(key(dataset, graph)) ?? emptyFlags();
            m.set(key(dataset, graph), { ...cur, ...next });
            return { byGraph: m };
        });
    }

    async function refresh(dataset: string, graph: string) {
        if (!dataset || !graph) return;
        const [u, r] = await Promise.all([
            sdkCanUndo({ path: { datasetName: dataset, graphURI: graph } }),
            sdkCanRedo({ path: { datasetName: dataset, graphURI: graph } }),
        ]);
        patch(dataset, graph, {
            canUndo: !u.error && u.data === true,
            canRedo: !r.error && r.data === true,
        });
    }

    function canUndo(dataset: string, graph: string): boolean {
        return get(store).byGraph.get(key(dataset, graph))?.canUndo ?? false;
    }

    function canRedo(dataset: string, graph: string): boolean {
        return get(store).byGraph.get(key(dataset, graph))?.canRedo ?? false;
    }

    async function doUndo(dataset: string, graph: string) {
        const { error } = await sdkUndo({
            path: { datasetName: dataset, graphURI: graph },
        });
        if (error) {
            console.error(`${LOG} undo failed`, error);
            toastStore.error("Undo failed", "Could not undo the last change.");
            return { error };
        }
        toastStore.info("Undone");
        classStore.invalidateGraph(dataset, graph);
        packageStore.invalidateGraph(dataset, graph);
        await refresh(dataset, graph);
        return { error: null };
    }

    async function doRedo(dataset: string, graph: string) {
        const { error } = await sdkRedo({
            path: { datasetName: dataset, graphURI: graph },
        });
        if (error) {
            console.error(`${LOG} redo failed`, error);
            toastStore.error("Redo failed", "Could not redo the change.");
            return { error };
        }
        toastStore.info("Redone");
        classStore.invalidateGraph(dataset, graph);
        packageStore.invalidateGraph(dataset, graph);
        await refresh(dataset, graph);
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
