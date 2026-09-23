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

import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
import { ontologyStore } from "$lib/stores/ontologyStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

import OntologyDialog from "../../src/routes/mainpage/packageNavigation/ontology-editor-dialog/OntologyDialog.svelte";

/** The namespace the header is written in, as the workspace knows it. */
const NAMESPACES = [
    {
        prefix: "http://example.org/graphs/Equipment#",
        substitutedPrefix: "eq:",
    },
];

/** A profile header as the backend hands it over. */
const ONTOLOGY = {
    uuid: "3f2b1c44-8a1e-4f2b-9c77-2a5d6e7f8091",
    namespace: "http://example.org/graphs/Equipment#",
    entries: [
        {
            iri: "http://purl.org/dc/terms/title",
            value: "Core Equipment Vocabulary",
            datatypeIri: "http://www.w3.org/2001/XMLSchema#string",
        },
    ],
};

let mounted = null;
let target = null;
let onSubmit = null;
let triggered = null;

async function open() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(OntologyDialog, {
        target,
        props: {
            showDialog: true,
            workspace: "cgmes",
            graphUri: "http://example.org/graphs/Equipment",
            namespaces: NAMESPACES,
            ontology: ONTOLOGY,
            readonly: false,
            onSubmit,
        },
    });
    await vi.waitFor(() => expect(valueInput()).not.toBeNull());
}

function valueInput() {
    return document.querySelector('input[placeholder="*literal value"]');
}

function button(label) {
    return [...document.querySelectorAll("button")].find(
        candidate => candidate.textContent.trim() === label,
    );
}

async function rename(value) {
    const input = valueInput();
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await vi.waitFor(() => expect(button("Save")).toBeDefined());
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/stores/ontologyStore.ts", () => ({
    ontologyStore: {
        getOntologyForGraph: vi.fn(),
        replaceOntology: vi.fn(),
        createOntology: vi.fn(),
    },
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getNamespaces: vi.fn() },
}));

beforeEach(() => {
    onSubmit = vi.fn();
    triggered = vi.spyOn(forceReloadTrigger, "trigger");
    ontologyStore.getOntologyForGraph.mockResolvedValue(ONTOLOGY);
    ontologyStore.replaceOntology.mockResolvedValue({ error: null });
    workspaceStore.getNamespaces.mockResolvedValue([]);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.restoreAllMocks();
    vi.clearAllMocks();
});

describe("OntologyDialog", () => {
    /**
     * The header names the schema in the navigation tree, and the tree reads that name from the
     * graph list rather than from the ontology — so it has to be rebuilt after a save, whichever
     * of the two places the dialog was opened from.
     */
    test("rebuilds the navigation after a save, even with a caller of its own", async () => {
        await open();
        await rename("Renamed Vocabulary");

        button("Save").click();

        await vi.waitFor(() =>
            expect(ontologyStore.replaceOntology).toHaveBeenCalled(),
        );
        await vi.waitFor(() => expect(triggered).toHaveBeenCalled());
        expect(onSubmit).toHaveBeenCalled();
    });
});
