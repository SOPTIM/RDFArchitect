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
import { beforeEach, describe, expect, test, vi } from "vitest";

import { editorState } from "$lib/sharedState.svelte.js";
import { workspaceState } from "$lib/workspaceState.svelte.js";

const { getWorkspaces } = vi.hoisted(() => ({ getWorkspaces: vi.fn() }));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces },
}));

beforeEach(() => {
    editorState.reset();
    editorState.selectWorkspace(null);
});

describe("workspaceState.load", () => {
    test("activates the first workspace when none is active", async () => {
        getWorkspaces.mockResolvedValue([{ label: "a" }, { label: "b" }]);

        await workspaceState.load();

        expect(workspaceState.getActive()).toBe("a");
    });

    test("leaves nothing active in a session without workspaces", async () => {
        getWorkspaces.mockResolvedValue([]);

        await expect(workspaceState.load()).resolves.toBeUndefined();
        expect(workspaceState.getActive()).toBeFalsy();
    });
});
