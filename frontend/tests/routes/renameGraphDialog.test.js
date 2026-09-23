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

/** A profile that names itself: the tree shows it by its dcterms:title. */
const PROFILE = {
    uri: { prefix: "http://graph#", suffix: "Equipment" },
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
};

/** A schema made in the app: no profile header, so the tail of its URI is its name. */
const PLAIN = {
    uri: { prefix: "http://graph#", suffix: "MyNotes" },
};

let mounted = null;
let target = null;

/** Opens the dialog on one schema and waits for it to have read the workspace. */
async function open(graphUri) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(RenameGraphDialog, {
        target,
        props: { showDialog: true, workspaceName: "cgmes", graphUri },
    });
    await vi.waitFor(() => expect(nameInput().value).not.toBe(""));
    return target;
}

function nameInput() {
    return document.querySelector('input[placeholder="Schema name"]');
}

function primaryButton() {
    return [...document.querySelectorAll("button")].find(
        button => button.textContent.trim() === "Rename Schema",
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

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getNamespaces: vi.fn().mockResolvedValue([]) },
}));

beforeEach(() => {
    graphStore.getGraphs.mockResolvedValue([PROFILE, PLAIN]);
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
    test("opens on the name the schema is listed under, not on its URI", async () => {
        await open("http://graph#Equipment");

        expect(nameInput().value).toBe("Core Equipment Vocabulary");
    });

    test("names a profile in its header and leaves the graph URI alone", async () => {
        await open("http://graph#Equipment");

        await type(nameInput(), "Our Equipment");
        primaryButton().click();

        await vi.waitFor(() =>
            expect(graphStore.renameGraph).toHaveBeenCalledWith(
                "cgmes",
                "http://graph#Equipment",
                "http://graph#Equipment",
                "Our Equipment",
            ),
        );
    });

    test("moves the graph URI for a schema whose URI is its only name", async () => {
        await open("http://graph#MyNotes");

        expect(nameInput().value).toBe("MyNotes");
        await type(nameInput(), "Our Notes");
        primaryButton().click();

        await vi.waitFor(() =>
            expect(graphStore.renameGraph).toHaveBeenCalledWith(
                "cgmes",
                "http://graph#MyNotes",
                "http://graph#OurNotes",
                "Our Notes",
            ),
        );
    });

    test("refuses a name that another schema's URI already occupies", async () => {
        await open("http://graph#MyNotes");

        await type(nameInput(), "Equipment");

        expect(primaryButton().disabled).toBe(true);
        expect(document.body.textContent).toContain("Schema already exists");
    });

    test("keeps the rename disabled until something actually changes", async () => {
        await open("http://graph#Equipment");

        expect(primaryButton().disabled).toBe(true);
    });
});
