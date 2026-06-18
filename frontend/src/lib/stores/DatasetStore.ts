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
    listDatasets,
    deleteDataset,
    CimPrefixPair,
    replaceNamespaces,
    enableEditing,
    disableEditing,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

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

// Generic envelope used by all mutating store methods.
type Result<T = void> = { error: unknown; data?: T };

const LOG_PREFIX = "[datasetStore]";

export const datasetStore = createDatasetStore();

function createDatasetStore() {
    const store = writable<DatasetsState>({
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    });

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

    // ----- Load -----

    async function load(force = false) {
        const state = get(store);

        if (!force && state.data !== null) return;
        if (state.pending !== null) return state.pending;

        const promise = (async () => {
            try {
                const { data, error } = await listDatasets();
                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load datasets:`,
                        await describeError(error),
                    );
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
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading datasets:`,
                    err,
                );
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

    // ----- Getters -----

    function isReadOnly(datasetName: string): boolean | null {
        const dataset = get(store).data?.find(d => d.label === datasetName);
        if (!dataset) {
            return null;
        }
        return dataset.readonly;
    }

    function getNamespaces(datasetName: string): CimPrefixPair[] {
        const dataset = get(store).data?.find(d => d.label === datasetName);
        if (!dataset) {
            return [];
        }
        return dataset.prefixes;
    }

    // ----- Mutations -----

    async function remove(datasetName: string): Promise<Result> {
        console.log(`${LOG_PREFIX} Deleting dataset "${datasetName}"`);

        const { error } = await deleteDataset({ path: { datasetName } });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not delete dataset "${datasetName}":`,
                msg,
            );
            toastStore.error(
                "Delete failed",
                `Could not delete dataset "${datasetName}".`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data: s.data?.filter(d => d.label !== datasetName) ?? null,
        }));

        console.log(`${LOG_PREFIX} Deleted dataset "${datasetName}"`);
        toastStore.success("Dataset deleted", `"${datasetName}" was deleted.`);

        return { error: null };
    }

    async function saveNamespaces(
        datasetName: string,
        namespaces: CimPrefixPair[],
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Saving ${namespaces.length} namespace(s) for "${datasetName}"`,
        );

        const { error } = await replaceNamespaces({
            path: { datasetName },
            body: namespaces,
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not save namespaces for "${datasetName}":`,
                msg,
            );
            toastStore.error(
                "Save failed",
                `Could not save namespaces for "${datasetName}".`,
            );
            return { error };
        }

        // Keep getNamespaces() consistent with what the backend now holds.
        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === datasetName
                        ? { ...d, prefixes: namespaces }
                        : d,
                ) ?? null,
        }));

        console.log(`${LOG_PREFIX} Saved namespaces for "${datasetName}"`);
        toastStore.success(
            "Namespaces saved",
            `Namespaces for "${datasetName}" were updated.`,
        );

        return { error: null };
    }

    async function updateReadonly(
        datasetName: string,
        readonly: boolean,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Setting readonly=${readonly} for "${datasetName}"`,
        );

        const res = readonly
            ? await disableEditing({ path: { datasetName } })
            : await enableEditing({ path: { datasetName } });

        if (res?.error) {
            const msg = await describeError(res.error);
            console.error(
                `${LOG_PREFIX} Could not update readonly flag for "${datasetName}":`,
                msg,
            );
            toastStore.error(
                readonly
                    ? "Could not disable editing"
                    : "Could not enable editing",
                readonly
                    ? `Dataset "${datasetName}" remains editable.`
                    : `Dataset "${datasetName}" remains read-only.`,
            );
            return { error: res };
        }

        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === datasetName ? { ...d, readonly } : d,
                ) ?? null,
        }));

        console.log(
            `${LOG_PREFIX} Updated readonly=${readonly} for "${datasetName}"`,
        );
        toastStore.success(
            readonly ? "Editing disabled" : "Editing enabled",
            `"${datasetName}" is now ${readonly ? "read-only" : "editable"}.`,
        );

        return { error: null };
    }

    // ----- Invalidation -----

    function invalidate() {
        update(s => ({
            ...s,
            data: null,
            fetchedAt: null,
            pending: null,
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
