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

import { type GraphKey, loadSlot, makeGraphKey } from "./storeHelpers";
import { describeError } from "./StoreLogging";
import { type AsyncSlot, createEmptySlot, type Result } from "./storeTypes";
import {
    getOntology,
    createOntology,
    replaceOntology,
    getKnownOntologyFields,
    getOntologyEntries,
    type OntologyDto,
    type OntologyEntry,
    type OntologyField,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

type OntologyStoreState = {
    byGraph: Map<GraphKey, AsyncSlot<OntologyDto>>;
    knownFields: AsyncSlot<OntologyField[]>;
};

const LOG_PREFIX = "[ontologyStore]";

export const ontologyStore = createOntologyStore();

function createOntologyStore() {
    const store = writable<OntologyStoreState>({
        byGraph: new Map(),
        knownFields: createEmptySlot<OntologyField[]>(),
    });

    const { subscribe, update } = store;

    // ---------- helpers ----------

    function getGraphState(
        state: OntologyStoreState,
        key: GraphKey,
    ): AsyncSlot<OntologyDto> {
        return state.byGraph.get(key) ?? createEmptySlot<OntologyDto>();
    }

    function setGraphState(
        state: OntologyStoreState,
        key: GraphKey,
        next: AsyncSlot<OntologyDto>,
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

    async function getOntologyForGraph(
        datasetName: string,
        graphURI: string,
        force = false,
    ): Promise<OntologyDto | null> {
        if (!datasetName || !graphURI) return null;
        const key = makeGraphKey(datasetName, graphURI);
        return loadSlot(
            store,
            s => getGraphState(s, key),
            (s, patch) =>
                setGraphState(s, key, { ...getGraphState(s, key), ...patch }),
            () => getOntology({ path: { datasetName, graphURI } }),
            LOG_PREFIX,
            `ontology for dataset="${datasetName}", graph="${graphURI}"`,
            force,
        );
    }

    // ---------- known ontology fields (global) ----------

    async function getKnownFields(
        force = false,
    ): Promise<OntologyField[] | null> {
        return loadSlot(
            store,
            s => s.knownFields,
            (s, patch) => ({
                ...s,
                knownFields: { ...s.knownFields, ...patch },
            }),
            () => getKnownOntologyFields(),
            LOG_PREFIX,
            "known ontology fields",
            force,
        );
    }

    // ---------- generated ontology entries ----------

    async function generateOntologyEntries(
        datasetName: string,
        graphURI: string,
    ): Promise<Result<OntologyEntry[]>> {
        console.log(
            `${LOG_PREFIX} Generating ontology entries for dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { data, error } = await getOntologyEntries({
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
            toastStore.error("Save failed", "Could not save ontology changes.");
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
        console.log(
            `${LOG_PREFIX} Invalidating ontology cache for dataset="${datasetName}", graph="${graphURI}"`,
        );
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
        });
    }

    function invalidateDataset(datasetName: string) {
        const prefix = `${datasetName}::`;
        console.log(
            `${LOG_PREFIX} Invalidating ontology cache for dataset="${datasetName}"`,
        );
        update(s => {
            const byGraph = new Map(s.byGraph);
            for (const key of byGraph.keys()) {
                if (key.startsWith(prefix)) byGraph.delete(key);
            }
            return { ...s, byGraph };
        });
    }

    return {
        subscribe,

        // ontology per graph
        getOntologyForGraph,
        generateOntologyEntries,

        // known fields (global)
        getKnownFields,

        // mutations
        createOntology: createOntologyForGraph,
        replaceOntology: replaceOntologyForGraph,

        // invalidation
        invalidateGraph,
        invalidateDataset,
    };
}
export { createOntologyStore };