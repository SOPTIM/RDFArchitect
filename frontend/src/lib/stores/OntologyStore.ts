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
    getOntology,
    getOntology2,
    createOntology,
    replaceOntology,
    type OntologyDto,
    type OntologyEntry,
    type OntologyField,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type Result<T = void> = { error: unknown; data?: T };

type LoadState<T> = {
    data: T | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type GraphKey = `${string}::${string}`;

type OntologyStoreState = {
    byGraph: Map<GraphKey, LoadState<OntologyDto>>;
};

const LOG_PREFIX = "[ontologyStore]";

export const ontologyStore = createOntologyStore();

function createEmptyLoadState<T>(): LoadState<T> {
    return {
        data: null,
        fetchedAt: null,
        pending: null,
        error: null,
    };
}

function makeGraphKey(datasetName: string, graphURI: string): GraphKey {
    return `${datasetName}::${graphURI}`;
}

function createOntologyStore() {
    const store = writable<OntologyStoreState>({
        byGraph: new Map(),
    });

    const { subscribe, update } = store;

    // ---------- helpers ----------

    function getGraphState(
        state: OntologyStoreState,
        key: GraphKey,
    ): LoadState<OntologyDto> {
        return state.byGraph.get(key) ?? createEmptyLoadState<OntologyDto>();
    }

    function setGraphState(
        state: OntologyStoreState,
        key: GraphKey,
        next: LoadState<OntologyDto>,
    ): OntologyStoreState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    function patchGraphDto(
        datasetName: string,
        graphURI: string,
        patch: Partial<OntologyDto>,
    ) {
        const key = makeGraphKey(datasetName, graphURI);
        update(s => {
            const current = getGraphState(s, key);
            const merged: OntologyDto = {
                ...current.data,
                ...patch,
            };
            return setGraphState(s, key, {
                data: merged,
                fetchedAt: Date.now(),
                pending: null,
                error: null,
            });
        });
    }

    // ---------- load ontology for graph ----------

    async function loadOntology(
        datasetName: string,
        graphURI: string,
        force = false,
    ) {
        if (!datasetName || !graphURI) return;

        const key = makeGraphKey(datasetName, graphURI);
        const state = getGraphState(get(store), key);

        if (!force && state.data !== null) return;
        if (state.pending !== null) return state.pending;

        console.log(
            `${LOG_PREFIX} Loading ontology for dataset="${datasetName}", graph="${graphURI}"`,
        );

        const pending = (async () => {
            try {
                const { data, error } = await getOntology({
                    path: { datasetName, graphURI },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load ontology for dataset="${datasetName}", graph="${graphURI}":`,
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
                        data: data ?? null,
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded ontology for dataset="${datasetName}", graph="${graphURI}"`,
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading ontology for dataset="${datasetName}", graph="${graphURI}":`,
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
                pending,
            }),
        );

        return pending;
    }

    function getOntologyForGraph(
        datasetName: string,
        graphURI: string,
    ): OntologyDto | null {
        const key = makeGraphKey(datasetName, graphURI);
        return getGraphState(get(store), key).data;
    }

    // ---------- generate ontology entries ----------

    async function generateOntologyEntries(
        datasetName: string,
        graphURI: string,
    ): Promise<Result<OntologyEntry[]>> {
        console.log(
            `${LOG_PREFIX} Generating ontology entries for dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { data, error } = await getOntology2({
            path: { datasetName, graphURI },
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not generate ontology entries:`,
                await describeError(error),
            );
            toastStore.error(
                "Generate failed",
                "Could not generate ontology entries.",
            );
            return { error };
        }

        const entries = data ?? [];

        patchGraphDto(datasetName, graphURI, { entries });

        console.log(
            `${LOG_PREFIX} Generated ${entries.length} ontology entries for dataset="${datasetName}", graph="${graphURI}"`,
        );
        toastStore.success(
            "Ontology generated",
            `Generated ${entries.length} ontology entry${entries.length === 1 ? "" : "ies"}.`,
        );

        return { error: null, data: entries };
    }

    // ---------- mutations ----------

    async function createOntologyForGraph(
        datasetName: string,
        graphURI: string,
        newOntology: OntologyDto,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Creating ontology for dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { error } = await createOntology({
            path: { datasetName, graphURI },
            body: newOntology,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not create ontology:`,
                await describeError(error),
            );
            toastStore.error(
                "Save failed",
                "Could not create ontology for this schema.",
            );
            return { error };
        }

        // No DTO returned from server -> patch local cache with what we sent.
        patchGraphDto(datasetName, graphURI, newOntology);

        console.log(
            `${LOG_PREFIX} Created ontology for dataset="${datasetName}", graph="${graphURI}"`,
        );
        toastStore.success("Ontology created", "Ontology was created.");

        return { error: null };
    }

    async function replaceOntologyForGraph(
        datasetName: string,
        graphURI: string,
        newOntology: OntologyDto,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Replacing ontology for dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { error } = await replaceOntology({
            path: { datasetName, graphURI },
            body: newOntology,
        });

        if (error) {
            console.error(
                `${LOG_PREFIX} Could not replace ontology:`,
                await describeError(error),
            );
            toastStore.error(
                "Save failed",
                "Could not save ontology changes.",
            );
            return { error };
        }

        // No DTO returned from server -> patch local cache with the new state.
        patchGraphDto(datasetName, graphURI, newOntology);

        console.log(
            `${LOG_PREFIX} Replaced ontology for dataset="${datasetName}", graph="${graphURI}"`,
        );
        toastStore.success("Ontology saved", "Ontology changes were saved.");

        return { error: null };
    }

    // ---------- invalidation ----------

    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeGraphKey(datasetName, graphURI);
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
            for (const key of byGraph.keys()) {
                if (key.startsWith(prefix)) byGraph.delete(key);
            }
            return { ...s, byGraph };
        });
    }

    function invalidateKnownFields() {
        update(s => ({
            ...s,
            knownFields: createEmptyLoadState<OntologyField[]>(),
        }));
    }

    function invalidateAll() {
        update(() => ({
            knownFields: createEmptyLoadState<OntologyField[]>(),
            byGraph: new Map(),
        }));
    }

    return {
        subscribe,

        // ontology per graph
        loadOntology,
        getOntologyForGraph,
        generateOntologyEntries,

        // mutations
        createOntology: createOntologyForGraph,
        replaceOntology: replaceOntologyForGraph,

        // invalidation
        invalidateGraph,
        invalidateDataset,
        invalidateKnownFields,
        invalidateAll,
    };
}