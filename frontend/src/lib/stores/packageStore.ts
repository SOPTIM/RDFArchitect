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

import {
    listPackages,
    addPackage,
    replacePackage,
    type PackageDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";
import { graphStore } from "./graphStore";
import { type GraphKey, loadSlot, makeGraphKey } from "./storeHelpers";
import { describeError } from "./storeLogging";
import { type AsyncSlot, createEmptySlot, type Result } from "./storeTypes";

type PackageListInfo = {
    internal: PackageDto[];
    external: PackageDto[];
};

type PackagesState = {
    byGraph: Map<GraphKey, AsyncSlot<PackageListInfo>>;
};

const LOG_PREFIX = "[packageStore]";

export const packageStore = createPackageStore();

function getPackageDisplayLabel(pkg: PackageDto): string {
    return pkg.label ?? pkg.uuid ?? "(unnamed)";
}

function createPackageStore() {
    const store = writable<PackagesState>({ byGraph: new Map() });
    const { subscribe, update } = store;

    function getGraphState(
        state: PackagesState,
        key: GraphKey,
    ): AsyncSlot<PackageListInfo> {
        return state.byGraph.get(key) ?? createEmptySlot();
    }

    function setGraphState(
        state: PackagesState,
        key: GraphKey,
        next: AsyncSlot<PackageListInfo>,
    ): PackagesState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    // ----- Getter -----
    async function getPackages(
        workspaceName: string,
        schemaName: string,
        force = false,
    ): Promise<PackageListInfo | null> {
        if (!workspaceName || !schemaName) return null;
        const key = makeGraphKey(workspaceName, schemaName);
        return loadSlot(
            store,
            s => getGraphState(s, key),
            (s, patch) => {
                const byGraph = new Map(s.byGraph);
                byGraph.set(key, { ...getGraphState(s, key), ...patch });
                return { ...s, byGraph };
            },
            async () => {
                const { data, error } = await listPackages({
                    path: { workspaceName: workspaceName, schemaName },
                });
                if (error) return { error };
                return {
                    data: {
                        internal: data?.internalPackageList ?? [],
                        external: data?.externalPackageList ?? [],
                    },
                };
            },
            LOG_PREFIX,
            `packages for workspace="${workspaceName}", graph="${schemaName}"`,
            force,
        );
    }

    // ----- Internal: local cache patch after a successful save -----
    function patchLocalPackage(
        workspaceName: string,
        schemaName: string,
        pkg: PackageDto,
    ) {
        if (!workspaceName || !schemaName || !pkg) return;
        const key = makeGraphKey(workspaceName, schemaName);

        update(s => {
            const current = getGraphState(s, key);
            // If we haven't loaded the list yet, there's nothing meaningful to patch.
            if (!current.data) return s;

            const currentData = current.data;
            const pkgId = pkg.uuid;

            const internal = (() => {
                if (!pkgId) {
                    if (currentData.internal.includes(pkg))
                        return currentData.internal;
                    return [...currentData.internal, pkg];
                }
                const idx = currentData.internal.findIndex(
                    p => p.uuid === pkgId,
                );
                if (idx >= 0) {
                    const next = [...currentData.internal];
                    next[idx] = pkg;
                    return next;
                }
                return [...currentData.internal, pkg];
            })();

            // A newly added/updated package is always internal; make sure it's
            // not lingering in the external list.
            const external = pkgId
                ? currentData.external.filter(p => p.uuid !== pkgId)
                : currentData.external.filter(p => p !== pkg);

            return setGraphState(s, key, {
                ...current,
                data: { internal, external },
                fetchedAt: Date.now(),
                error: null,
            });
        });
    }

    // ----- Mutations -----

    async function addNewPackage(
        workspaceName: string,
        schemaName: string,
        pkg: PackageDto,
    ): Promise<Result<string>> {
        const label = getPackageDisplayLabel(pkg);

        console.log(`${LOG_PREFIX} Creating package "${label}" in`, {
            workspaceName,
            schemaName,
        });

        const { data, error } = await addPackage({
            path: { workspaceName: workspaceName, schemaName },
            body: pkg,
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not create package "${label}":`,
                msg,
            );
            toastStore.error(
                "Save failed",
                `Could not save package "${label}".`,
            );
            return { error };
        }

        // Prefer server-issued uuid; fall back to whatever the caller sent.
        const newUUID = data ?? pkg.uuid;
        const stored: PackageDto = { ...pkg, uuid: newUUID };
        patchLocalPackage(workspaceName, schemaName, stored);

        console.log(
            `${LOG_PREFIX} Created package "${label}" (uuid=${newUUID})`,
        );
        toastStore.success("Package created", `"${label}" was saved.`);

        return { error: null, data: newUUID };
    }

    async function replaceExistingPackage(
        workspaceName: string,
        schemaName: string,
        pkg: PackageDto,
    ): Promise<Result> {
        const label = getPackageDisplayLabel(pkg);

        if (!pkg.uuid) {
            const error = new Error("pkg.uuid is required for replacePackage");
            console.error(`${LOG_PREFIX} ${error.message}`);
            toastStore.error(
                "Save failed",
                `Could not save package "${label}".`,
            );
            return { error };
        }

        console.log(
            `${LOG_PREFIX} Replacing package "${label}" (uuid=${pkg.uuid}) in`,
            { workspaceName, schemaName },
        );

        const { error } = await replacePackage({
            path: {
                workspaceName: workspaceName,
                schemaName,
                packageUUID: pkg.uuid,
            },
            body: pkg,
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not save package "${label}":`,
                msg,
            );
            toastStore.error(
                "Save failed",
                `Could not save package "${label}".`,
            );
            return { error };
        }

        patchLocalPackage(workspaceName, schemaName, pkg);
        // A CGMES 2.4.15 profile may take its name from its profile package.
        graphStore.invalidateWorkspace(workspaceName);

        console.log(`${LOG_PREFIX} Saved package "${label}"`);
        toastStore.success("Package saved", `"${label}" was saved.`);

        return { error: null };
    }

    // Single convenience method that picks add vs. replace based on uuid
    async function savePackage(
        workspaceName: string,
        schemaName: string,
        pkg: PackageDto,
    ): Promise<Result<string | undefined>> {
        if (pkg.uuid) {
            const res = await replaceExistingPackage(
                workspaceName,
                schemaName,
                pkg,
            );
            return res.error
                ? { error: res.error }
                : { error: null, data: pkg.uuid };
        }
        return addNewPackage(workspaceName, schemaName, pkg);
    }

    // ----- Invalidation -----

    function invalidateGraph(workspaceName: string, schemaName: string) {
        const key = makeGraphKey(workspaceName, schemaName);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    function invalidateWorkspace(workspaceName: string) {
        const prefix = `${workspaceName}::`;
        update(s => {
            const byGraph = new Map(s.byGraph);
            for (const k of byGraph.keys()) {
                if (k.startsWith(prefix)) byGraph.delete(k);
            }
            return { ...s, byGraph };
        });
    }

    return {
        subscribe,
        getPackages,
        addPackage: addNewPackage,
        replacePackage: replaceExistingPackage,
        savePackage,
        invalidateGraph,
        invalidateWorkspace,
    };
}
export { createPackageStore };
