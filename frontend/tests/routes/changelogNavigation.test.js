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

import Navigation from "../../src/routes/changelog/Navigation.svelte";

const getWorkspaces = vi.hoisted(() => vi.fn());
const getGraphs = vi.hoisted(() => vi.fn());
const activate = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function render(props = {}) {
    const given = { onSelectGraph: vi.fn(), ...props };
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(Navigation, { target, props: given });
    return given;
}

function entries() {
    return [...target.querySelectorAll("button[title]")];
}

function entry(title) {
    return entries().find(each => each.title.split("\n").includes(title));
}

/** A single mouse click, which the entry tells apart from a double click by its detail. */
function click(element) {
    element.dispatchEvent(
        new MouseEvent("click", { bubbles: true, detail: 1 }),
    );
}

function graph(uri, fields = {}) {
    const cut = uri.lastIndexOf("/") + 1;
    return {
        uri: { prefix: uri.slice(0, cut), suffix: uri.slice(cut) },
        ...fields,
    };
}

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces },
}));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs },
}));

vi.mock("$lib/workspaceState.svelte.js", () => ({
    workspaceState: { activate },
}));

vi.mock("$lib/sharedState.svelte.js", () => ({
    editorState: { selectedWorkspace: { getValue: () => "cgmes" } },
    forceReloadTrigger: { subscribe: () => {} },
}));

beforeEach(() => {
    getWorkspaces.mockResolvedValue([{ label: "cgmes" }, { label: "nc" }]);
    getGraphs.mockImplementation(async workspace =>
        workspace === "cgmes"
            ? [
                  graph("http://example.org/eq1", { keyword: "EQ" }),
                  graph("http://example.org/eq2", { keyword: "EQ" }),
                  graph("http://example.org/ssh", {
                      keyword: "SSH",
                      label: "Steady State Hypothesis",
                  }),
              ]
            : [],
    );
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("changelog Navigation", () => {
    test("names schemas like the editor: title first, and tells apart those that read alike", async () => {
        render();

        await vi.waitFor(() =>
            expect(entry("http://example.org/ssh")).toBeDefined(),
        );

        expect(entry("http://example.org/ssh").textContent).toContain(
            "Steady State Hypothesis",
        );
        expect(entry("http://example.org/eq1").textContent).toContain(
            "EQ (eq1)",
        );
        expect(entry("http://example.org/eq2").textContent).toContain(
            "EQ (eq2)",
        );
    });

    test("switches workspace through the workspace state, which keeps the editor's selection", async () => {
        const props = render();
        await vi.waitFor(() =>
            expect(entry("nc — every change in the workspace")).toBeDefined(),
        );

        click(entry("nc — every change in the workspace"));

        expect(activate).toHaveBeenCalledWith("nc");
        expect(props.onSelectGraph).toHaveBeenCalledWith(null);
    });

    test("holds the log to a schema without moving the editor's own selection", async () => {
        const props = render();
        await vi.waitFor(() =>
            expect(entry("http://example.org/ssh")).toBeDefined(),
        );

        click(entry("http://example.org/ssh"));

        expect(activate).toHaveBeenCalledWith("cgmes");
        expect(props.onSelectGraph).toHaveBeenCalledWith(
            "http://example.org/ssh",
        );
    });
});
