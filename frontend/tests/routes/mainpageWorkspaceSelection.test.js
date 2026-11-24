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

import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { editorState } from "$lib/sharedState.svelte.js";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";
import { workspaceState } from "$lib/workspaceState.svelte.js";

import MainPage from "../../src/routes/mainpage/+page.svelte";

const WORKSPACES = [{ label: "Draft" }, { label: "Review" }];

let mounted = null;
let target = null;

async function settle() {
    for (let i = 0; i < 5; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
        flushSync();
    }
}

function activeTabs() {
    return [...target.querySelectorAll("[role=tab][aria-selected=true]")].map(
        tab => tab.getAttribute("aria-label"),
    );
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "",
    PUBLIC_DESKTOP: false,
    PUBLIC_SHARE_SNAPSHOT_ENABLED: false,
}));

vi.mock("$lib/stores/workspaceStore.ts", async () => {
    const { writable } = await import("svelte/store");
    const state = writable({ data: null, loading: false, error: null });
    return {
        workspaceStore: {
            subscribe: state.subscribe,
            set: state.set,
            getWorkspaces: vi.fn(),
            isReadOnly: vi.fn().mockResolvedValue(false),
        },
    };
});

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn().mockResolvedValue([]) },
}));

beforeEach(() => {
    workspaceState.activate(null);
    workspaceStore.set({ data: WORKSPACES, loading: false, error: null });
    vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue(WORKSPACES);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    mounted = null;
    target?.remove();
    workspaceStore.set({ data: null, loading: false, error: null });
});

describe("main page with workspaces but none selected", () => {
    test("render_noWorkspaceSelected_activatesOneInsteadOfWaiting", async () => {
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(MainPage, { target });
        await settle();
        expect(editorState.selectedWorkspace.getValue()).toBe("Draft");

        // The logo clears the selection without reloading the page.
        workspaceState.activate("Review");
        editorState.reset();
        await settle();

        expect(editorState.selectedWorkspace.getValue()).toBe("Draft");
        expect(activeTabs()).toEqual(["Draft"]);
        expect(target.querySelector(".mm-spinner")).toBeNull();
    });

    test("render_sessionWithoutWorkspaces_leavesSelectionEmpty", async () => {
        workspaceStore.set({ data: [], loading: false, error: null });
        vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue([]);
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(MainPage, { target });
        await settle();

        expect(editorState.selectedWorkspace.getValue()).toBeNull();
        expect(target.querySelector(".mm-spinner")).toBeNull();
    });
});
