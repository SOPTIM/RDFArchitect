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
import { afterEach, describe, expect, test, vi } from "vitest";

import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";

/** Graph list as the backend returns it: the URI next to its dcat:keyword. */
const GRAPHS = [
    {
        uri: { prefix: "http://iec.ch/TC57/CIM100#", suffix: "EQ" },
        keyword: null,
    },
    {
        uri: { prefix: "http://iec.ch/TC57/CIM100#", suffix: "TP" },
        keyword: "Topology",
    },
];

let mounted = null;
let target = null;

/** The second select holds the graphs, the first one the workspaces. */
function graphSelect(container) {
    return container.querySelectorAll("select")[1];
}

async function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(WorkspaceAndGraphSelection, { target, props });
    await vi.waitFor(() =>
        expect(graphSelect(target).options.length).toBe(GRAPHS.length + 1),
    );
    return target;
}

/** The options of the graph select without the leading placeholder. */
function graphOptions(container) {
    return [...graphSelect(container).options].slice(1);
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://localhost/api",
}));

vi.mock("$lib/api/apiWorkspaceUtils.js", () => ({
    isReadOnly: async () => false,
}));

vi.mock("$lib/api/backend.js", () => ({
    BackendConnection: class {
        async getWorkspaceNames() {
            return { json: async () => ["cgmes"] };
        }
        async getGraphs() {
            return { json: async () => GRAPHS };
        }
    },
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("WorkspaceAndGraphSelection", () => {
    test("uses the full graph URI as the value of an option", async () => {
        const container = await render({ workspace: "cgmes", graph: null });

        expect(graphOptions(container).map(option => option.value)).toEqual([
            "http://iec.ch/TC57/CIM100#EQ",
            "http://iec.ch/TC57/CIM100#TP",
        ]);
    });

    test("labels a graph by its keyword and falls back to the URI suffix", async () => {
        const container = await render({ workspace: "cgmes", graph: null });

        expect(
            graphOptions(container).map(option => option.textContent.trim()),
        ).toEqual(["EQ", "Topology"]);
    });

    test("keeps a preselected graph that is part of the workspace", async () => {
        const container = await render({
            workspace: "cgmes",
            graph: "http://iec.ch/TC57/CIM100#EQ",
        });

        await vi.waitFor(() =>
            expect(graphSelect(container).value).toBe(
                "http://iec.ch/TC57/CIM100#EQ",
            ),
        );
    });

    test("clears a graph that is not part of the workspace", async () => {
        const container = await render({
            workspace: "cgmes",
            graph: "http://example.org/gone#XY",
        });

        await vi.waitFor(() =>
            expect(graphSelect(container).value).toBe("__NULL__"),
        );
    });
});
