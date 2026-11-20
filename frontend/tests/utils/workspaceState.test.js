/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { beforeEach, describe, expect, test, vi } from "vitest";

import { editorState } from "$lib/sharedState.svelte.js";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";
import { workspaceState } from "$lib/workspaceState.svelte.js";

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces: vi.fn(), remove: vi.fn() },
}));

describe("workspaceState", () => {
    beforeEach(() => {
        vi.mocked(workspaceStore.getWorkspaces).mockReset();
        vi.mocked(workspaceStore.remove).mockReset();
        workspaceState.activate(null);
    });

    test("activates the first workspace when none is active", async () => {
        vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue([
            { label: "Draft" },
            { label: "Other" },
        ]);

        await workspaceState.load();

        expect(editorState.selectedWorkspace.getValue()).toBe("Draft");
    });

    test("copes with a session that holds no workspace", async () => {
        vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue([]);

        await workspaceState.load();

        expect(editorState.selectedWorkspace.getValue()).toBeNull();
    });

    test("moves on to the next workspace by name after removing the active one", async () => {
        workspaceState.activate("Draft");
        vi.mocked(workspaceStore.remove).mockResolvedValue({ error: null });
        vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue([
            { label: "Other" },
        ]);

        await workspaceState.remove("Draft");

        expect(editorState.selectedWorkspace.getValue()).toBe("Other");
    });
});
