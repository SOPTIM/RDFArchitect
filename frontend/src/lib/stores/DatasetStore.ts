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

import { loadSlot } from "./storeHelpers";
import { describeError } from "./StoreLogging";
import { AsyncSlot, Result, createEmptySlot } from "./storeTypes";
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
    readOnly: boolean | null;
    prefixes: CimPrefixPair[];
};

type DatasetsState = AsyncSlot<DatasetInfo[]>;

const LOG_PREFIX = "[datasetStore]";

export const datasetStore = createDatasetStore();

function createDatasetStore() {
    const store = writable<DatasetsState>(createEmptySlot<DatasetInfo[]>());
    const { subscribe, update } = store; // ← update hier verfügbar machen

    // ----- Load -----

    async function getDatasets(force = false): Promise<DatasetInfo[] | null> {
        return loadSlot(
            store,
            s => s,
            (s, patch) => ({ ...s, ...patch }),
            async () => {
                const { data, error } = await listDatasets();
                if (error) return { error };
                const mapped: DatasetInfo[] = (data ?? []).map(dataset => ({
                    label: dataset.name ?? "",
                    readOnly: dataset.readOnly ?? null,
                    prefixes: dataset.prefixes ?? [],
                }));
                return { data: mapped };
            },
            LOG_PREFIX,
            "datasets",
            force,
        );
    }

    // ----- Getters -----

    async function isReadOnly(datasetName: string): Promise<boolean | null> {
        const datasets = await getDatasets();
        const dataset = (datasets ?? []).find(d => d.label === datasetName);
        if (!dataset) return null;
        return dataset.readOnly;
    }

    async function getNamespaces(
        datasetName: string,
    ): Promise<CimPrefixPair[]> {
        const datasets = await getDatasets();
        const dataset = (datasets ?? []).find(d => d.label === datasetName);
        if (!dataset) return [];
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
        readOnly: boolean,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Setting readOnly=${readOnly} for "${datasetName}"`,
        );

        const res = readOnly
            ? await disableEditing({ path: { datasetName } })
            : await enableEditing({ path: { datasetName } });

        if (res?.error) {
            const msg = await describeError(res.error);
            console.error(
                `${LOG_PREFIX} Could not update readOnly flag for "${datasetName}":`,
                msg,
            );
            toastStore.error(
                readOnly
                    ? "Could not disable editing"
                    : "Could not enable editing",
                readOnly
                    ? `Dataset "${datasetName}" remains editable.`
                    : `Dataset "${datasetName}" remains read-only.`,
            );
            return { error: res };
        }

        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === datasetName ? { ...d, readOnly } : d,
                ) ?? null,
        }));

        console.log(
            `${LOG_PREFIX} Updated readOnly=${readOnly} for "${datasetName}"`,
        );
        toastStore.success(
            readOnly ? "Editing disabled" : "Editing enabled",
            `"${datasetName}" is now ${readOnly ? "read-only" : "editable"}.`,
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
        getDatasets,
        remove,
        isReadOnly,
        updateReadonly,
        invalidate,
        getNamespaces,
        saveNamespaces,
    };
}
