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
import { afterEach, describe, expect, test, vi } from "vitest";

import SchemaPicker from "../../src/routes/shacl/workbench/SchemaPicker.svelte";

const graphs = vi.hoisted(() => ({ list: [] }));

const EQ = {
    keyword: "EQ",
    uri: { prefix: "http://ex.org/", suffix: "EQ" },
};
const SSH = {
    keyword: "SSH",
    uri: { prefix: "http://ex.org/", suffix: "SSH" },
};

let mounted;
let target;

async function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SchemaPicker, { target });
    for (let tick = 0; tick < 5; tick += 1) {
        await Promise.resolve();
        flushSync();
    }
    return target;
}

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        getWorkspaces: () => Promise.resolve([{ label: "cgmes" }]),
    },
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: () => Promise.resolve(graphs.list) },
}));
vi.mock("$lib/sharedState.svelte.js", () => ({
    editorState: { selectGraph: vi.fn() },
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
});

describe("SchemaPicker", () => {
    test("picks the only schema there is", async () => {
        graphs.list = [EQ];
        const view = await render();

        const [, schema] = view.querySelectorAll("select");
        expect(schema.value).toBe("http://ex.org/EQ");
        const open = [...view.querySelectorAll("button")].find(button =>
            button.textContent.includes("Open its constraints"),
        );
        expect(open.disabled).toBe(false);
    });

    test("leaves the choice open when there are several", async () => {
        graphs.list = [EQ, SSH];
        const view = await render();

        const [, schema] = view.querySelectorAll("select");
        expect(schema.value).toBe("");
    });
});
