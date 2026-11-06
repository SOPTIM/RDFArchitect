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

import { graphStore } from "$lib/stores/graphStore.ts";

import RenameGraphDialog from "../../src/routes/RenameGraphDialog.svelte";

/** A CGMES 3.0 profile: it names itself on an ontology object the ontology editor owns. */
const CURRENT = {
    uri: { prefix: "http://graph#", suffix: "Equipment" },
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
    ontologyHeader: true,
};

/** A CGMES 2.4.15 profile: no ontology object, its metadata is fixed on a class. */
const LEGACY = {
    uri: { prefix: "http://graph#", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
    profileClassIri:
        "http://entsoe.eu/CIM/SchemaExtension/3/1#EquipmentVersion",
    profileClassUuid: "uuid-equipment-version",
};

/**
 * The same legacy profile again, reading exactly alike: only the graph they were imported into
 * tells the two apart, which is how the official equipment profiles come.
 */
const LEGACY_TWIN = {
    ...LEGACY,
    uri: { prefix: "http://graph#", suffix: "EquipmentCoreOperation" },
};

/**
 * A legacy profile whose version class has no uuid yet. It states a name and has nowhere to
 * edit it: no ontology object, and no class the class editor could open.
 */
const LEGACY_WITHOUT_TARGET = {
    uri: { prefix: "http://graph#", suffix: "EquipmentShortCircuit" },
    keyword: "SC",
    label: "ShortCircuitProfile",
    profileClassIri: "http://entsoe.eu/CIM/SchemaExtension/3/1#SCVersion",
};

/** A schema made in the app: no profile header, so its graph URI is its only name. */
const PLAIN = {
    uri: { prefix: "http://graph#", suffix: "MyNotes" },
};

let mounted = null;
let target = null;
let editHeaderCalls = [];

async function open(graphUri) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(RenameGraphDialog, {
        target,
        props: {
            showDialog: true,
            workspaceName: "cgmes",
            graphUri,
            onEditHeader: profileClass => editHeaderCalls.push(profileClass),
        },
    });
    await vi.waitFor(() => expect(nameInput().value).not.toBe(""));
}

function nameInput() {
    return document.querySelector('input[placeholder="Schema name"]');
}

function button(label) {
    return [...document.querySelectorAll("button")].find(
        candidate => candidate.textContent.trim() === label,
    );
}

function headerHintButton() {
    return [...document.querySelectorAll("button")].find(candidate =>
        candidate.textContent.trim().startsWith("Edit"),
    );
}

async function type(input, value) {
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await vi.waitFor(() => expect(input.value).toBe(value));
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn(), renameGraph: vi.fn() },
}));

beforeEach(() => {
    editHeaderCalls = [];
    graphStore.getGraphs.mockResolvedValue([
        CURRENT,
        LEGACY,
        LEGACY_WITHOUT_TARGET,
        PLAIN,
    ]);
    graphStore.renameGraph.mockResolvedValue({ error: null });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("RenameGraphDialog", () => {
    test("edits the graph name, not the name the profile gives itself", async () => {
        await open("http://graph#Equipment");

        expect(nameInput().value).toBe("Equipment");
    });

    test("offers no namespace to choose, since a graph URI is not one", async () => {
        await open("http://graph#Equipment");

        expect(document.querySelector("datalist")).toBeNull();
        expect(document.body.textContent).not.toContain("Namespace");
    });

    test("renames the graph and leaves the profile header alone", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "Our Notes");
        button("Rename Schema").click();

        await vi.waitFor(() =>
            expect(graphStore.renameGraph).toHaveBeenCalledWith(
                "cgmes",
                "http://graph#MyNotes",
                "http://graph#OurNotes",
            ),
        );
    });

    test("says where the name on screen comes from for a profile", async () => {
        await open("http://graph#Equipment");

        // A line break before the comma would render as "Vocabulary , from".
        expect(document.body.textContent.replace(/\s+/g, " ")).toContain(
            "Shown as Core Equipment Vocabulary, from the profile header.",
        );
        expect(headerHintButton().textContent.trim()).toBe(
            "Edit profile header",
        );
    });

    test("shows the name alone, without the graph URI it resolves to", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "Our Notes");

        expect(document.body.textContent).not.toContain(
            "http://graph#OurNotes",
        );
    });

    test("sends a CGMES 3.0 profile to the ontology editor", async () => {
        await open("http://graph#Equipment");

        headerHintButton().click();

        expect(editHeaderCalls).toEqual([{ kind: "ontology" }]);
    });

    /**
     * Writing an ontology object into a CGMES 2.4.15 graph adds a header that profile never
     * reads back, so a profile with no place to edit its name offers no way in.
     */
    test("offers nowhere to edit a legacy profile whose class it cannot open", async () => {
        await open("http://graph#EquipmentShortCircuit");

        expect(document.body.textContent.replace(/\s+/g, " ")).toContain(
            "Shown as ShortCircuitProfile, from the profile header.",
        );
        expect(headerHintButton()).toBeUndefined();
    });

    test("reads as something to click", async () => {
        await open("http://graph#Equipment");

        // Tailwind 4 leaves a button at cursor:default, so it has to be asked for.
        expect(headerHintButton().className).toContain("cursor-pointer");
    });

    test("sends a CGMES 2.4.15 profile to the class that states its metadata", async () => {
        await open("http://graph#EquipmentCore");

        expect(headerHintButton().textContent.trim()).toBe(
            "Edit on EquipmentVersion",
        );
        headerHintButton().click();

        expect(editHeaderCalls).toEqual([
            {
                kind: "class",
                uuid: "uuid-equipment-version",
                label: "EquipmentVersion",
            },
        ]);
    });

    /**
     * The schemas this dialog has to tell apart are exactly the ones whose profiles name
     * themselves alike, so it names them the way the tree does rather than on their own.
     */
    test("tells apart the schemas that read alike", async () => {
        graphStore.getGraphs.mockResolvedValue([LEGACY, LEGACY_TWIN]);

        await open("http://graph#EquipmentCore");

        expect(document.body.textContent.replace(/\s+/g, " ")).toContain(
            "Shown as EquipmentProfile (EquipmentCore), from the profile header.",
        );
    });

    test("says nothing about a header for a schema that has none", async () => {
        await open("http://graph#MyNotes");

        expect(headerHintButton()).toBeUndefined();
    });

    test("refuses a name another schema's graph already occupies", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "Equipment");

        expect(button("Rename Schema").disabled).toBe(true);
        expect(document.body.textContent).toContain("Schema already exists");
    });

    test("stays disabled until the name changes", async () => {
        await open("http://graph#MyNotes");

        expect(button("Rename Schema").disabled).toBe(true);
    });

    /**
     * The graph URI is built from the name rather than being it, so a change the URI does not
     * survive is no rename: the backend would do nothing and the dialog would report success.
     */
    test("refuses a name that resolves to the graph it already has", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "My Notes");

        expect(button("Rename Schema").disabled).toBe(true);
        expect(graphStore.renameGraph).not.toHaveBeenCalled();
    });

    test("says how a name that an IRI cannot carry is spelled instead", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "C# Notes");

        expect(document.body.textContent).toContain("Stored as CNotes");
    });

    /** A second "#" makes the whole graph URI unparseable, not just the name odd. */
    test("keeps a typed name from making a second URI fragment", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "C#Notes");
        button("Rename Schema").click();

        await vi.waitFor(() =>
            expect(graphStore.renameGraph).toHaveBeenCalledWith(
                "cgmes",
                "http://graph#MyNotes",
                "http://graph#CNotes",
            ),
        );
    });
});
