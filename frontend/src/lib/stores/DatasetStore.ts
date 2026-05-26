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

import { writable, get, derived } from "svelte/store";

import { listDatasets, deleteDataset, isReadOnly as fetchIsReadOnly } from "../api/generated";

export type DatasetInfo = {
    label: string;
    readonly: boolean | null;
    prefixes: Set<string>;
};

type DatasetsState = {
    data: DatasetInfo[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

export const datasetStore = createDatasetStore();

function createDatasetStore() {
    const store = writable<DatasetsState>(
        {
            data: null,
            fetchedAt: null,
            pending: null,
            error: null,
        },
        () => {
            const state = get(store);

            if (state.data === null && !state.pending) {
                void load();
            }
        },
    );

    const { subscribe, update } = store;

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

                const datasetNames = data ?? [];
                const previous = get(store).data ?? [];
                const byName = new Map(previous.map(d => [d.label, d]));

                const baseData: DatasetInfo[] = datasetNames.map(name => {
                    const prev = byName.get(name);
                    return {
                        label: name,
                        readonly: null,
                        prefixes: prev?.prefixes ?? new Set<string>(),
                    };
                });

                const readonlyPairs = await Promise.all(
                    baseData.map(async d => {
                        const { data: roData, error: roError } =
                            await fetchIsReadOnly({
                                path: { datasetName: d.label },
                            });
                        return {
                            name: d.label,
                            readonly: roError ? null : (roData ?? null),
                        };
                    }),
                );

                const readonlyByName = new Map(
                    readonlyPairs.map(x => [x.name, x.readonly]),
                );

                const nextData: DatasetInfo[] = baseData.map(d => ({
                    ...d,
                    readonly: readonlyByName.get(d.label) ?? null,
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

    async function isReadOnly(datasetName: string, force = false) {
        if (force) {
            const { data, error } = await fetchIsReadOnly({
                path: { datasetName },
            });
            if (error) {
                return;
            }
            const readonly = data ?? null;
            update(s => ({
                ...s,
                data:
                    s.data?.map(d =>
                        d.label === datasetName ? { ...d, readonly: readonly } : d,
                    ) ?? null,
            }));
        }

        return derived(datasetStore, $store => {
            return $store.data?.find(d => d.label === datasetName)?.readonly ?? null;
        });
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
    };
}
