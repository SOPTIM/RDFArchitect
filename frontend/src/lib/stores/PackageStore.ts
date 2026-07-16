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
    listPackages,
    addPackage,
    replacePackage,
    PackageDto,
    deletePackage,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type PackageListInfo = {
    internal: PackageDto[];
    external: PackageDto[];
};

type GraphPackageState = {
    data: PackageListInfo | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type PackagesState = {
    byGraph: Map<GraphKey, GraphPackageState>;
};

type GraphKey = `${string}::${string}`;

type Result<T = void> = { error: unknown; data?: T };

const LOG_PREFIX = "[packageStore]";

export const packageStore = createPackageStore();

function makeKey(datasetName: string, graphURI: string): GraphKey {
    return `${datasetName}::${graphURI}`;
}

function createEmptyGraphState(): GraphPackageState {
    return {
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    };
}

function getPackageDisplayLabel(pkg: PackageDto): string {
    return pkg.label ?? pkg.uuid ?? "(unnamed)";
}

function createPackageStore() {
    const store = writable<PackagesState>({ byGraph: new Map() });
    const { subscribe, update } = store;

    function getGraphState(
        state: PackagesState,
        key: GraphKey,
    ): GraphPackageState {
        return state.byGraph.get(key) ?? createEmptyGraphState();
    }

    function setGraphState(
        state: PackagesState,
        key: GraphKey,
        next: GraphPackageState,
    ): PackagesState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    // ----- Load -----

    async function load(datasetName: string, graphURI: string, force = false) {
        if (!datasetName || !graphURI) return;

        const key = makeKey(datasetName, graphURI);
        const graphState = getGraphState(get(store), key);

        if (!force && graphState.data !== null) return;
        if (graphState.pending !== null) return graphState.pending;

        const promise = (async () => {
            try {
                const { data, error } = await listPackages({
                    path: { datasetName, graphURI },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load packages for`,
                        { datasetName, graphURI },
                        await describeError(error),
                    );
                    update(s =>
                        setGraphState(s, key, {
                            ...getGraphState(s, key),
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setGraphState(s, key, {
                        data: {
                            internal: data?.internalPackageList ?? [],
                            external: data?.externalPackageList ?? [],
                        },
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading packages for`,
                    { datasetName, graphURI },
                    err,
                );
                update(s =>
                    setGraphState(s, key, {
                        ...getGraphState(s, key),
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setGraphState(s, key, {
                ...getGraphState(s, key),
                pending: promise,
            }),
        );

        return promise;
    }

    function getPackages(
        datasetName: string,
        graphURI: string,
    ): PackageListInfo | null {
        return getGraphState(get(store), makeKey(datasetName, graphURI)).data;
    }

    // ----- Internal: local cache patch after a successful save -----

    function patchLocalPackage(
        datasetName: string,
        graphURI: string,
        pkg: PackageDto,
    ) {
        if (!datasetName || !graphURI || !pkg) return;
        const key = makeKey(datasetName, graphURI);

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

    function removeLocalPackage(
        datasetName: string,
        graphURI: string,
        packageUUID: string,
    ) {
        if (!datasetName || !graphURI || !packageUUID) return;
        const key = makeKey(datasetName, graphURI);

        update(s => {
            const current = getGraphState(s, key);
            if (!current.data) return s;

            const internal = current.data.internal.filter(
                p => p.uuid !== packageUUID,
            );
            const external = current.data.external.filter(
                p => p.uuid !== packageUUID,
            );

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
        datasetName: string,
        graphURI: string,
        pkg: PackageDto,
    ): Promise<Result<string>> {
        const label = getPackageDisplayLabel(pkg);

        console.log(`${LOG_PREFIX} Creating package "${label}" in`, {
            datasetName,
            graphURI,
        });

        const { data, error } = await addPackage({
            path: { datasetName, graphURI },
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
        patchLocalPackage(datasetName, graphURI, stored);

        console.log(
            `${LOG_PREFIX} Created package "${label}" (uuid=${newUUID})`,
        );
        toastStore.success("Package created", `"${label}" was saved.`);

        return { error: null, data: newUUID };
    }

    async function replaceExistingPackage(
        datasetName: string,
        graphURI: string,
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
            { datasetName, graphURI },
        );

        const { error } = await replacePackage({
            path: { datasetName, graphURI, packageUUID: pkg.uuid },
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

        patchLocalPackage(datasetName, graphURI, pkg);

        console.log(`${LOG_PREFIX} Saved package "${label}"`);
        toastStore.success("Package saved", `"${label}" was saved.`);

        return { error: null };
    }

    // Single convenience method that picks add vs. replace based on uuid
    async function savePackage(
        datasetName: string,
        graphURI: string,
        pkg: PackageDto,
    ): Promise<Result<string | undefined>> {
        if (pkg.uuid) {
            const res = await replaceExistingPackage(
                datasetName,
                graphURI,
                pkg,
            );
            return res.error
                ? { error: res.error }
                : { error: null, data: pkg.uuid };
        }
        return addNewPackage(datasetName, graphURI, pkg);
    }

    async function deleteExistingPackage(
        datasetName: string,
        graphURI: string,
        pkg: PackageDto,
    ): Promise<Result> {
        const label = getPackageDisplayLabel(pkg);

        if (!pkg.uuid) {
            const error = new Error("pkg.uuid is required for deletePackage");
            console.error(`${LOG_PREFIX} ${error.message}`);
            toastStore.error(
                "Delete failed",
                `Could not delete package "${label}".`,
            );
            return { error };
        }

        console.log(
            `${LOG_PREFIX} Deleting package "${label}" (uuid=${pkg.uuid}) from`,
            { datasetName, graphURI },
        );

        const { error } = await deletePackage({
            path: { datasetName, graphURI, packageUUID: pkg.uuid },
        });

        if (error) {
            const msg = await describeError(error);
            console.error(
                `${LOG_PREFIX} Could not delete package "${label}":`,
                msg,
            );
            toastStore.error(
                "Delete failed",
                `Could not delete package "${label}".`,
            );
            return { error };
        }

        removeLocalPackage(datasetName, graphURI, pkg.uuid);

        console.log(
            `${LOG_PREFIX} Deleted package "${label}" (uuid=${pkg.uuid})`,
        );
        toastStore.success("Package deleted", `"${label}" was removed.`);

        return { error: null };
    }

    // ----- Invalidation -----

    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeKey(datasetName, graphURI);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    function invalidateDataset(datasetName: string) {
        const prefix = `${datasetName}::`;
        update(s => {
            const byGraph = new Map(s.byGraph);
            for (const k of byGraph.keys()) {
                if (k.startsWith(prefix)) byGraph.delete(k);
            }
            return { ...s, byGraph };
        });
    }

    function invalidateAll() {
        update(() => ({ byGraph: new Map() }));
    }

    return {
        subscribe,
        load,
        getPackages,
        addPackage: addNewPackage,
        replacePackage: replaceExistingPackage,
        deletePackage: deleteExistingPackage,
        savePackage,
        invalidateGraph,
        invalidateDataset,
        invalidateAll,
    };
}
