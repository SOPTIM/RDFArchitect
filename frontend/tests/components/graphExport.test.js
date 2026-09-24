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

import { mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "$lib/api/generated";
import GraphExport from "$lib/GraphExport.svelte";
import { graphStore } from "$lib/stores/graphStore.ts";
import { ontologyStore } from "$lib/stores/ontologyStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

const WORKSPACE = "cgmes";

/** A schema that states a profile header. */
const ONTOLOGY = {
    uuid: "3f2b1c44-8a1e-4f2b-9c77-2a5d6e7f8091",
    namespace: "http://graph#",
    entries: [{ iri: "http://purl.org/dc/terms/title", value: "Equipment" }],
};

/** What the backend offers to add, which the schema does not state yet. */
const GENERATED = [{ iri: "http://www.w3.org/ns/dcat#keyword", value: "EQ" }];

let mounted = null;
let target = null;

/**
 * A graph of its own per test: the ontology store caches by graph, and what one test leaves
 * behind is exactly what this file is about.
 */
function graphOf(name) {
    return `http://graph#${name}`;
}

async function open(graphUri) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(GraphExport, {
        target,
        props: {
            showDialog: true,
            lockedWorkspaceName: WORKSPACE,
            lockedGraphUri: graphUri,
            generateOntologyEntries: true,
        },
    });
    // Settled: the namespaces effect runs for any schema, with a header or without one.
    await vi.waitFor(() =>
        expect(workspaceStore.getNamespaces).toHaveBeenCalled(),
    );
    return mounted;
}

const route = (workspace, graph) =>
    `http://backend/api/datasets/${workspace}/graphs/${graph}`;

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("$lib/utils/fileUtils.ts", async importOriginal => ({
    ...(await importOriginal()),
    saveFile: vi.fn(),
}));

vi.mock("$lib/api/generated", () => ({
    getOntology: vi.fn(),
    createOntology: vi.fn(),
    replaceOntology: vi.fn(),
    getKnownOntologyFields: vi.fn(),
    getOntologyEntries: vi.fn(),
}));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn(), invalidateWorkspace: vi.fn() },
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces: vi.fn(), getNamespaces: vi.fn() },
}));

beforeEach(() => {
    vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue({
            ok: true,
            blob: async () => new Blob(["<rdf/>"]),
            headers: new Headers(),
        }),
    );
    graphStore.getGraphs.mockResolvedValue([]);
    workspaceStore.getWorkspaces.mockResolvedValue([
        { label: WORKSPACE, readOnly: false },
    ]);
    workspaceStore.getNamespaces.mockResolvedValue([]);
    vi.mocked(api.getOntology).mockResolvedValue({
        data: ONTOLOGY,
        error: undefined,
    });
    vi.mocked(api.getOntologyEntries).mockResolvedValue({
        data: GENERATED,
        error: undefined,
    });
    vi.mocked(api.replaceOntology).mockResolvedValue({
        data: undefined,
        error: undefined,
    });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.unstubAllGlobals();
    vi.clearAllMocks();
});

describe("GraphExport", () => {
    test("saves the generated entries into the header the schema has", async () => {
        const graphUri = graphOf("Equipment");
        const component = await open(graphUri);
        await vi.waitFor(() =>
            expect(api.getOntologyEntries).toHaveBeenCalled(),
        );

        await component.handleExport(route);

        expect(api.replaceOntology).toHaveBeenCalledWith(
            expect.objectContaining({
                body: expect.objectContaining({ namespace: "http://graph#" }),
            }),
        );
        expect(fetch).toHaveBeenCalled();
    });

    /**
     * A schema with no profile header has no namespace to compose an ontology IRI from, so the
     * export must not try to save one: it reached the backend as a null and came back as a 500.
     * Generating entries elsewhere - "Add known fields" does it whether or not there is a header
     * - used to leave exactly that in the cache, an ontology of entries alone.
     */
    test("writes no header into a schema that has none", async () => {
        const graphUri = graphOf("Notes");
        vi.mocked(api.getOntology).mockResolvedValue({
            data: null,
            error: undefined,
        });

        await ontologyStore.generateOntologyEntries(WORKSPACE, graphUri);
        const component = await open(graphUri);

        await component.handleExport(route);

        expect(api.replaceOntology).not.toHaveBeenCalled();
        expect(fetch).toHaveBeenCalled();
    });
});
