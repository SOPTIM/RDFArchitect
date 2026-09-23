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
    graphStore.getGraphs.mockResolvedValue([CURRENT, LEGACY, PLAIN]);
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

        expect(document.body.textContent).toContain(
            "Core Equipment Vocabulary",
        );
        expect(headerHintButton().textContent.trim()).toBe(
            "Edit profile header",
        );
    });

    test("sends a CGMES 3.0 profile to the ontology editor", async () => {
        await open("http://graph#Equipment");

        headerHintButton().click();

        expect(editHeaderCalls).toEqual([null]);
    });

    test("sends a CGMES 2.4.15 profile to the class that states its metadata", async () => {
        await open("http://graph#EquipmentCore");

        expect(headerHintButton().textContent.trim()).toBe(
            "Edit on EquipmentVersion",
        );
        headerHintButton().click();

        expect(editHeaderCalls).toEqual([
            { uuid: "uuid-equipment-version", label: "EquipmentVersion" },
        ]);
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
});
