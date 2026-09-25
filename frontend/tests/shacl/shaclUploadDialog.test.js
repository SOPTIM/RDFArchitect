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

import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import SHACLUploadDialog from "../../src/routes/shacl/SHACLUploadDialog.svelte";

const GRAPH = "http://example.org/EQ";

const WORKSPACES = vi.hoisted(() => [
    { label: "editable", readOnly: false },
    { label: "locked", readOnly: true },
]);

const api = vi.hoisted(() => ({
    listShapesDocuments: vi.fn(),
    createShapesDocumentFromFile: vi.fn(),
}));

const toasts = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='dialog']");
}

function importButton() {
    return [...dialog().querySelectorAll("button")].find(
        button => button.textContent.trim() === "Import",
    );
}

async function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SHACLUploadDialog, {
        target,
        props: { showDialog: true, lockedGraphUri: GRAPH, ...props },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
}

function chooseFile() {
    const input = dialog().querySelector("input[type='file']");
    const file = new File(
        ["@prefix sh: <http://www.w3.org/ns/shacl#> ."],
        "mine.ttl",
    );
    Object.defineProperty(input, "files", { value: [file] });
    input.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

vi.mock("$lib/api/generated/index.ts", () => api);

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: toasts,
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        getWorkspaces: vi.fn().mockResolvedValue(WORKSPACES),
        isReadOnly: vi.fn(
            async name =>
                WORKSPACES.find(workspace => workspace.label === name)
                    ?.readOnly ?? null,
        ),
    },
}));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: {
        getGraphs: vi.fn().mockResolvedValue([
            {
                uri: { prefix: "http://example.org/", suffix: "EQ" },
                keyword: "Equipment",
            },
        ]),
    },
}));

beforeEach(() => {
    api.listShapesDocuments.mockResolvedValue({ data: [] });
    api.createShapesDocumentFromFile.mockResolvedValue({ data: {} });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SHACLUploadDialog", () => {
    test("refuses to import into a read-only workspace", async () => {
        await render({ lockedWorkspaceName: "locked" });

        chooseFile();
        importButton().click();

        await vi.waitFor(() => expect(toasts.error).toHaveBeenCalled());
        expect(api.createShapesDocumentFromFile).not.toHaveBeenCalled();
    });

    test("does not offer read-only workspaces in the picker", async () => {
        await render({ lockedWorkspaceName: undefined });

        const options = await vi.waitFor(() => {
            const found = [...dialog().querySelectorAll("select")[0].options];
            expect(found.length).toBeGreaterThan(WORKSPACES.length);
            return found;
        });
        const byValue = Object.fromEntries(
            options.map(option => [option.value, option.disabled]),
        );
        expect(byValue.locked).toBe(true);
        expect(byValue.editable).toBe(false);
    });

    test("names the schema by its label once the file is added", async () => {
        await render({ lockedWorkspaceName: "editable" });

        chooseFile();
        importButton().click();

        await vi.waitFor(() => expect(toasts.success).toHaveBeenCalled());
        expect(api.createShapesDocumentFromFile).toHaveBeenCalledTimes(1);
        expect(toasts.success.mock.calls[0][1]).toContain('"Equipment"');
    });
});
