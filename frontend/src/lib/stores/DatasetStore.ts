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

import {
    listDatasets,
    deleteDataset,
    CimPrefixPair,
    replaceNamespaces,
} from "../api/generated";

export type DatasetInfo = {
    label: string;
    readonly: boolean | null;
    prefixes: CimPrefixPair[];
};

type DatasetsState = {
    data: DatasetInfo[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

export const datasetStore = createDatasetStore();

function createDatasetStore() {
    const store = writable<DatasetsState>({
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    }); // ← kein Start-Callback

    const { subscribe: baseSubscribe, update } = store;

    let initialLoadTriggered = false;

    function subscribe(
        run: (value: DatasetsState) => void,
        invalidate?: () => void,
    ) {
        if (!initialLoadTriggered) {
            initialLoadTriggered = true;
            void load();
        }
        return baseSubscribe(run, invalidate);
    }

    async function load(force = false) {
        const state = get(store);

        if (!force && state.data !== null) return;
        if (state.pending !== null) return state.pending;

        const promise = (async () => {
            try {
                const { data, error } = await listDatasets();
                if (error) {
                    update(s => ({
                        ...s,
                        pending: null,
                        error,
                    }));
                    return;
                }

                const datasets = data ?? [];

                const nextData: DatasetInfo[] = datasets.map(dataset => ({
                    label: dataset.name ?? "",
                    readonly: dataset.readonly ?? null,
                    prefixes: dataset.prefixes ?? [],
                }));

                update(s => ({
                    ...s,
                    pending: null,
                    error: null,
                    data: nextData,
                    fetchedAt: Date.now(),
                }));
            } catch (err) {
                update(s => ({
                    ...s,
                    pending: null,
                    error: err,
                }));
            }
        })();

        update(s => ({ ...s, pending: promise }));
        return promise;
    }

    async function remove(datasetName: string) {
        const { error } = await deleteDataset({ path: { datasetName } });
        if (error) return { error };

        update(s => ({
            ...s,
            data: s.data?.filter(d => d.label !== datasetName) ?? null,
        }));
        return { error: null };
    }

    function invalidate() {
        void load(true);
    }

    function isReadOnly(datasetName: string): boolean | null {
        const dataset = get(store).data?.find(d => d.label === datasetName);
        if (!dataset) {
            console.warn(
                `isReadOnly called before dataset "${datasetName}" was loaded`,
            );
            return null;
        }
        return dataset.readonly;
    }

    function getNamespaces(datasetName: string): CimPrefixPair[] {
        const dataset = get(store).data?.find(d => d.label === datasetName);
        if (!dataset) {
            console.warn(`getNamespaces called before dataset "${datasetName}" was loaded`);
            return [];
        }
        return dataset.prefixes;
    }

    async function saveNamespaces(
        datasetName: string,
        namespaces: CimPrefixPair[],
    ) {
        const { error } = await replaceNamespaces({
            path: { datasetName },
            body: namespaces,
        });

        if (error) return { error };

        // Update prefixes in store so getNamespaces() returns fresh data
        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === datasetName
                        ? { ...d, prefixes: namespaces }
                        : d,
                ) ?? null,
        }));

        return { error: null };
    }

    function updateReadonly(datasetName: string, readonly: boolean) {
        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === datasetName ? { ...d, readonly } : d,
                ) ?? null,
        }));
    }

    return {
        subscribe,
        load,
        remove,
        isReadOnly,
        updateReadonly,
        invalidate,
        getNamespaces,
        saveNamespaces,
    };
}
