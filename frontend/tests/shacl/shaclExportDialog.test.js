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

import SHACLExportDialog from "../../src/routes/shacl/SHACLExportDialog.svelte";

const WORKSPACE = "cgmes";
const GRAPH = "http://example.org/EQ";

const listShapesDocuments = vi.hoisted(() => vi.fn());

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
            lockedGraphUri: GRAPH,
        },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    await vi.waitFor(() =>
        expect(listShapesDocuments).toHaveBeenCalledWith({
            path: { datasetName: WORKSPACE, graphURI: GRAPH },
        }),
    );
}

vi.mock("$lib/api/generated/index.ts", () => ({ listShapesDocuments }));

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
                uri: { prefix: "http://example.org/", suffix: "EQ" },
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

    test("ticks the documents that are switched on, and only those", async () => {
        documents(
            { id: "on", name: "on.ttl", order: 0, enabled: true },
            { id: "off", name: "off.ttl", order: 1, enabled: false },
        );

        await render();

        await vi.waitFor(() => expect(checkbox("on.ttl")).toBeTruthy());
        expect(checkbox("on.ttl").checked).toBe(true);
        expect(checkbox("off.ttl").checked).toBe(false);
        expect(dialog().textContent).toContain("Switched off");
    });

    test("leaves the generated shapes out when the graph has documents", async () => {
        documents({ id: "on", name: "on.ttl", order: 0, enabled: true });

        await render();

        await vi.waitFor(() => expect(checkbox("on.ttl")).toBeTruthy());
        expect(checkbox("Generated shapes").checked).toBe(false);
    });

    test("ticks the generated shapes when there is nothing else to export", async () => {
        await render();

        await vi.waitFor(() =>
            expect(dialog().textContent).toContain(
                "This schema has no constraints documents",
            ),
        );
        expect(checkbox("Generated shapes").checked).toBe(true);
    });
});
