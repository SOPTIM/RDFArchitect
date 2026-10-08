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
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

import NewGraphDialog from "../../src/routes/NewGraphDialog.svelte";

const EQUIPMENT = {
    uri: { prefix: "http://graph#", suffix: "Equipment" },
    keyword: "EQ",
};

let mounted = null;
let target = null;

async function open() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(NewGraphDialog, {
        target,
        props: { showDialog: true, lockedWorkspaceName: "cgmes" },
    });
    await vi.waitFor(() => expect(graphStore.getGraphs).toHaveBeenCalled());
}

function nameInput() {
    return document.querySelector('input[placeholder="http://graph#"]');
}

function button(label) {
    return [...document.querySelectorAll("button")].find(
        candidate => candidate.textContent.trim() === label,
    );
}

async function type(input, value) {
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    await vi.waitFor(() => expect(input.value).toBe(value));
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: {
        getGraphs: vi.fn(),
        addEmptyGraph: vi.fn(),
        invalidateWorkspace: vi.fn(),
    },
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces: vi.fn(), invalidate: vi.fn() },
}));

beforeEach(() => {
    graphStore.getGraphs.mockResolvedValue([EQUIPMENT]);
    graphStore.addEmptyGraph.mockResolvedValue({ error: null });
    workspaceStore.getWorkspaces.mockResolvedValue([
        { label: "cgmes", readOnly: false },
    ]);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("NewGraphDialog", () => {
    /**
     * The workspace lists its schemas as GraphDtos, and reading one as if it were a URI made
     * every comparison match the empty string — so no name was ever reported as taken.
     */
    test("refuses a name a schema of the workspace already has", async () => {
        await open();

        await type(nameInput(), "Equipment");

        expect(document.body.textContent).toContain("Schema already exists");
        expect(button("Create Schema").disabled).toBe(true);
    });

    test("accepts a name no schema has yet", async () => {
        await open();

        await type(nameInput(), "Topology");

        expect(document.body.textContent).not.toContain(
            "Schema already exists",
        );
        expect(button("Create Schema").disabled).toBe(false);
    });
});
