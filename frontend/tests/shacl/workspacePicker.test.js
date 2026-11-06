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

import WorkspacePicker from "../../src/routes/shacl/workbench/WorkspacePicker.svelte";

const state = vi.hoisted(() => ({ workspaces: [], select: vi.fn() }));

let mounted;
let target;

async function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(WorkspacePicker, { target });
    for (let tick = 0; tick < 5; tick += 1) {
        await Promise.resolve();
        flushSync();
    }
    return target;
}

function openButton(view) {
    return [...view.querySelectorAll("button")].find(button =>
        button.textContent.includes("Open its constraints"),
    );
}

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        getWorkspaces: () => Promise.resolve(state.workspaces),
    },
}));
vi.mock("$lib/sharedState.svelte.js", () => ({
    editorState: { selectWorkspace: state.select },
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    state.select.mockClear();
});

describe("WorkspacePicker", () => {
    test("picks the only workspace there is", async () => {
        state.workspaces = [{ label: "cgmes" }];
        const view = await render();

        expect(view.querySelector("select").value).toBe("cgmes");
        expect(openButton(view).disabled).toBe(false);

        openButton(view).click();

        expect(state.select).toHaveBeenCalledWith("cgmes");
    });

    test("leaves the choice open when there are several", async () => {
        state.workspaces = [{ label: "cgmes" }, { label: "nc" }];
        const view = await render();

        expect(view.querySelector("select").value).toBe("");
        expect(openButton(view).disabled).toBe(true);
    });
});
