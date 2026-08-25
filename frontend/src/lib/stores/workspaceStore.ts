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
import { describeError } from "./storeLogging";
import { type AsyncSlot, type Result, createEmptySlot } from "./storeTypes";
import {
    listDatasets,
    deleteDataset,
    type CimPrefixPair,
    replaceNamespaces,
    enableEditing,
    disableEditing,
    createDataset,
    renameDataset,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

export type WorkspaceInfo = {
    label: string;
    readOnly: boolean | null;
    prefixes: CimPrefixPair[];
};

type WorkspacesState = AsyncSlot<WorkspaceInfo[]>;

const LOG_PREFIX = "[workspaceStore]";
export const workspaceStore = createWorkspaceStore();

function createWorkspaceStore() {
    const store = writable<WorkspacesState>(createEmptySlot<WorkspaceInfo[]>());
    const { subscribe, update } = store;

    // ----- Load -----

    async function getWorkspaces(
        force = false,
    ): Promise<WorkspaceInfo[] | null> {
        return loadSlot(
            store,
            s => s,
            (s, patch) => ({ ...s, ...patch }),
            async () => {
                const { data, error } = await listDatasets();
                if (error) return { error };
                const mapped: WorkspaceInfo[] = (data ?? []).map(workspace => ({
                    label: workspace.name ?? "",
                    readOnly: workspace.readOnly ?? null,
                    prefixes: workspace.prefixes ?? [],
                }));
                return { data: mapped };
            },
            LOG_PREFIX,
            "workspaces",
            force,
        );
    }

    // ----- Getters -----

    async function isReadOnly(workspaceName: string): Promise<boolean | null> {
        const workspaces = await getWorkspaces();
        const workspace = (workspaces ?? []).find(
            d => d.label === workspaceName,
        );
        if (!workspace) return null;
        return workspace.readOnly;
    }

    async function getNamespaces(
        workspaceName: string,
    ): Promise<CimPrefixPair[]> {
        const workspaces = await getWorkspaces();
        const workspace = (workspaces ?? []).find(
            d => d.label === workspaceName,
        );
        if (!workspace) return [];
        return workspace.prefixes;
    }

    // ----- Mutations -----

    async function create(workspaceName: string): Promise<Result> {
        console.log(`${LOG_PREFIX} Creating workspace "${workspaceName}"`);

        const { error } = await createDataset({
            path: { datasetName: workspaceName },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not create workspace "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                "Create failed",
                `Could not create workspace "${workspaceName}".`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data: [
                ...(s.data ?? []),
                { label: workspaceName, readOnly: false, prefixes: [] },
            ],
        }));

        console.log(`${LOG_PREFIX} Created workspace "${workspaceName}"`);
        toastStore.success(
            "Workspace created",
            `"${workspaceName}" was created.`,
        );

        return { error: null };
    }

    async function rename(workspaceName: string, newWorkspaceName: string): Promise<Result> {
        console.log(`${LOG_PREFIX} Renaming workspace "${workspaceName}"`);

        const { error } = await renameDataset({
            path: { datasetName: workspaceName },
            query: { newDatasetName: newWorkspaceName },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not rename workspace "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                "Rename failed",
                `Could not rename workspace "${workspaceName}".`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === workspaceName
                        ? { ...d, label: newWorkspaceName }
                        : d,
                ) ?? null,
        }));

        console.log(`${LOG_PREFIX} Renamed workspace "${workspaceName}"`);
        toastStore.success(
            "Workspace renamed",
            `"${workspaceName}" was renamed.`,
        );

        return { error: null };
    }


    async function remove(workspaceName: string): Promise<Result> {
        console.log(`${LOG_PREFIX} Deleting workspace "${workspaceName}"`);

        const { error } = await deleteDataset({
            path: { datasetName: workspaceName },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not delete workspace "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                "Delete failed",
                `Could not delete workspace "${workspaceName}".`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data: s.data?.filter(d => d.label !== workspaceName) ?? null,
        }));

        console.log(`${LOG_PREFIX} Deleted workspace "${workspaceName}"`);
        toastStore.success(
            "Workspace deleted",
            `"${workspaceName}" was deleted.`,
        );

        return { error: null };
    }

    async function saveNamespaces(
        workspaceName: string,
        namespaces: CimPrefixPair[],
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Saving ${namespaces.length} namespace(s) for "${workspaceName}"`,
        );

        const { error } = await replaceNamespaces({
            path: { datasetName: workspaceName },
            body: namespaces,
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not save namespaces for "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                "Save failed",
                `Could not save namespaces for "${workspaceName}".`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === workspaceName
                        ? { ...d, prefixes: namespaces }
                        : d,
                ) ?? null,
        }));

        console.log(`${LOG_PREFIX} Saved namespaces for "${workspaceName}"`);
        toastStore.success(
            "Namespaces saved",
            `Namespaces for "${workspaceName}" were updated.`,
        );

        return { error: null };
    }

    async function updateReadonly(
        workspaceName: string,
        readOnly: boolean,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Setting readOnly=${readOnly} for "${workspaceName}"`,
        );

        const { error } = readOnly
            ? await disableEditing({ path: { datasetName: workspaceName } })
            : await enableEditing({ path: { datasetName: workspaceName } });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not update readOnly flag for "${workspaceName}":`,
                msg,
            );
            toastStore.error(
                readOnly
                    ? "Could not disable editing"
                    : "Could not enable editing",
                readOnly
                    ? `Workspace "${workspaceName}" remains editable.`
                    : `Workspace "${workspaceName}" remains read-only.`,
            );
            return { error };
        }

        update(s => ({
            ...s,
            data:
                s.data?.map(d =>
                    d.label === workspaceName ? { ...d, readOnly } : d,
                ) ?? null,
        }));

        console.log(
            `${LOG_PREFIX} Updated readOnly=${readOnly} for "${workspaceName}"`,
        );
        toastStore.success(
            readOnly ? "Editing disabled" : "Editing enabled",
            `"${workspaceName}" is now ${readOnly ? "read-only" : "editable"}.`,
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
        getWorkspaces,
        create,
        rename,
        remove,
        isReadOnly,
        updateReadonly,
        invalidate,
        getNamespaces,
        saveNamespaces,
    };
}

export { createWorkspaceStore };
