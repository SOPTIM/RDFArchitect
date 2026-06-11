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
const key = (d: string, g: string) => `${d}::${g}`;

function emptyFlags(): Flags {
    return { canUndo: false, canRedo: false, pending: null };
}

function createStore() {
    const store = writable<State>({ byGraph: new Map() });
    const { subscribe, update } = store;

    function patch(d: string, g: string, next: Partial<Flags>) {
        update(s => {
            const m = new Map(s.byGraph);
            const cur = m.get(key(d, g)) ?? emptyFlags();
            m.set(key(d, g), { ...cur, ...next });
            return { byGraph: m };
        });
    }

    async function refresh(d: string, g: string) {
        if (!d || !g) return;
        const [u, r] = await Promise.all([
            sdkCanUndo({ path: { datasetName: d, graphURI: g } }),
            sdkCanRedo({ path: { datasetName: d, graphURI: g } }),
        ]);
        patch(d, g, {
            canUndo: !u.error && u.data === true,
            canRedo: !r.error && r.data === true,
        });
    }

    function canUndo(d: string, g: string): boolean {
        return get(store).byGraph.get(key(d, g))?.canUndo ?? false;
    }

    function canRedo(d: string, g: string): boolean {
        return get(store).byGraph.get(key(d, g))?.canRedo ?? false;
    }

    async function doUndo(d: string, g: string) {
        const { error } = await sdkUndo({
            path: { datasetName: d, graphURI: g },
        });
        if (error) {
            console.error(`${LOG} undo failed`, error);
            toastStore.error("Undo failed", "Could not undo the last change.");
            return { error };
        }
        toastStore.info("Undone");
        classStore.invalidateGraph(d, g);
        packageStore.invalidateGraph(d, g);
        await refresh(d, g);
        return { error: null };
    }

    async function doRedo(d: string, g: string) {
        const { error } = await sdkRedo({
            path: { datasetName: d, graphURI: g },
        });
        if (error) {
            console.error(`${LOG} redo failed`, error);
            toastStore.error("Redo failed", "Could not redo the change.");
            return { error };
        }
        toastStore.info("Redone");
        classStore.invalidateGraph(d, g);
        packageStore.invalidateGraph(d, g);
        await refresh(d, g);
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
