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

import SHACLExportDialog from "../../src/routes/shacl/SHACLExportDialog.svelte";

const WORKSPACE = "cgmes";
const SCHEMA = "EQ";
const OTHER = "SSH";

const listShapesDocuments = vi.hoisted(() => vi.fn());
const saveFile = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='dialog']");
}

/** The checkbox next to a label, found by the label's text. */
function checkbox(label) {
    const labelElement = [...dialog().querySelectorAll("label")].find(element =>
        element.textContent.trim().startsWith(label),
    );
    return labelElement && document.getElementById(labelElement.htmlFor);
}

function documents(...entries) {
    listShapesDocuments.mockResolvedValue({ data: entries });
}

async function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SHACLExportDialog, {
        target,
        props: {
            showDialog: true,
            lockedWorkspaceName: WORKSPACE,
            lockedSchemaName: SCHEMA,
        },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    await vi.waitFor(() =>
        expect(listShapesDocuments).toHaveBeenCalledWith({
            path: { workspaceName: WORKSPACE },
        }),
    );
}

vi.mock("$lib/api/generated/index.ts", () => ({ listShapesDocuments }));

vi.mock("$lib/utils/fileUtils.ts", async original => ({
    ...(await original()),
    saveFile,
}));

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        getWorkspaces: vi
            .fn()
            .mockResolvedValue([{ label: "cgmes", readOnly: false }]),
        getNamespaces: vi.fn().mockResolvedValue([]),
    },
}));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: {
        getGraphs: vi.fn().mockResolvedValue([
            {
                name: "EQ",
                keyword: "EQ",
            },
        ]),
    },
}));

vi.mock("$lib/stores/ontologyStore.ts", () => ({
    ontologyStore: { getOntologyForGraph: vi.fn().mockResolvedValue(null) },
}));

beforeEach(() => {
    localStorage.clear();
    documents();
});

afterEach(() => {
    vi.unstubAllGlobals();
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SHACLExportDialog", () => {
    test("offers Turtle as the format to begin with", async () => {
        await render();

        const select = dialog().querySelector("#media-types-Download");
        await vi.waitFor(() =>
            expect(select.selectedOptions[0]?.textContent.trim()).toBe(
                "TURTLE",
            ),
        );
    });

    test("ticks the chosen schema's documents that are switched on, and only those", async () => {
        documents(
            {
                id: "on",
                name: "on.ttl",
                order: 0,
                enabled: true,
                schemas: [SCHEMA],
            },
            {
                id: "off",
                name: "off.ttl",
                order: 1,
                enabled: false,
                schemas: [SCHEMA],
            },
            {
                id: "other",
                name: "other.ttl",
                order: 2,
                enabled: true,
                schemas: [OTHER],
            },
        );

        await render();

        await vi.waitFor(() => expect(checkbox("on.ttl")).toBeTruthy());
        expect(checkbox("on.ttl").checked).toBe(true);
        expect(checkbox("off.ttl").checked).toBe(false);
        expect(checkbox("other.ttl").checked).toBe(false);
        expect(dialog().textContent).toContain("Switched off");
    });

    test("lists the chosen schema's documents first, then the rest of the workspace", async () => {
        documents(
            {
                id: "other",
                name: "other.ttl",
                order: 0,
                enabled: true,
                schemas: [OTHER],
            },
            {
                id: "on",
                name: "on.ttl",
                order: 1,
                enabled: true,
                schemas: [SCHEMA],
            },
        );

        await render();

        await vi.waitFor(() => expect(checkbox("on.ttl")).toBeTruthy());
        const names = [...dialog().querySelectorAll("label")]
            .map(label => label.textContent.trim())
            .filter(name => name.endsWith(".ttl"));
        expect(names).toEqual(["on.ttl", "other.ttl"]);
    });

    test("leaves the generated shapes out when the schema has documents", async () => {
        documents({
            id: "on",
            name: "on.ttl",
            order: 0,
            enabled: true,
            schemas: [SCHEMA],
        });

        await render();

        await vi.waitFor(() => expect(checkbox("on.ttl")).toBeTruthy());
        expect(checkbox("Generated shapes").checked).toBe(false);
    });

    test("ticks the generated shapes when the schema has no document of its own", async () => {
        documents({
            id: "other",
            name: "other.ttl",
            order: 0,
            enabled: true,
            schemas: [OTHER],
        });

        await render();

        await vi.waitFor(() => expect(checkbox("other.ttl")).toBeTruthy());
        expect(checkbox("Generated shapes").checked).toBe(true);
    });

    test("ticks the generated shapes when there is nothing else to export", async () => {
        await render();

        await vi.waitFor(() =>
            expect(dialog().textContent).toContain(
                "This workspace has no constraints documents",
            ),
        );
        expect(checkbox("Generated shapes").checked).toBe(true);
    });

    test("asks for the ticked documents and the schema's generated shapes", async () => {
        const fetch = vi.fn(async () => new Response("", { status: 200 }));
        vi.stubGlobal("fetch", fetch);
        documents(
            {
                id: "on",
                name: "on.ttl",
                order: 0,
                enabled: true,
                schemas: [SCHEMA],
            },
            {
                id: "other",
                name: "other.ttl",
                order: 1,
                enabled: true,
                schemas: [OTHER],
            },
        );
        await render();
        await vi.waitFor(() => expect(checkbox("other.ttl")).toBeTruthy());

        checkbox("other.ttl").click();
        checkbox("Generated shapes").click();
        flushSync();
        [...dialog().querySelectorAll("button")]
            .find(button => button.textContent.trim() === "Export")
            .click();

        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = new URL(fetch.mock.calls[0][0]);
        expect(url.origin + url.pathname).toBe(
            "http://backend.test/api/workspaces/cgmes/shacl/export/file",
        );
        expect(url.searchParams.getAll("documentId")).toEqual(["on", "other"]);
        expect(url.searchParams.getAll("generatedFor")).toEqual([SCHEMA]);
    });
});
