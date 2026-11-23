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

import { guardUnsavedChanges } from "$lib/eventhandling/unsavedChanges.js";
import { editorState } from "$lib/sharedState.svelte.js";

import HelpMenuHarness from "./HelpMenuHarness.svelte";

import { goto } from "$app/navigation";

const WORKSPACE = "cgmes";

let mounted = null;
let target = null;
let release = null;

/** Opens the Help menu and picks the item labelled `label`. */
async function choose(label) {
    const trigger = [...document.querySelectorAll("button")].find(
        button => button.textContent.trim() === "Help",
    );
    // bits-ui opens a menu bar menu on pointerdown; the click alone does not reach it in jsdom.
    trigger.dispatchEvent(
        new PointerEvent("pointerdown", {
            bubbles: true,
            button: 0,
            pointerType: "mouse",
        }),
    );
    trigger.click();
    flushSync();
    let item;
    await vi.waitFor(() => {
        item = [...document.querySelectorAll("[role=menuitem]")].find(
            candidate => candidate.textContent.includes(label),
        );
        expect(item).toBeDefined();
    });
    item.click();
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));
vi.mock("$app/navigation", () => ({ goto: vi.fn(async () => {}) }));

beforeEach(() => {
    editorState.reset();
    editorState.selectWorkspace(WORKSPACE);
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(HelpMenuHarness, { target });
    flushSync();
});

afterEach(() => {
    release?.();
    release = null;
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    editorState.reset();
    vi.clearAllMocks();
});

describe("Help menu", () => {
    test("About asks about unsaved work before it clears the selection", async () => {
        const ask = vi.fn().mockResolvedValue(false);
        release = guardUnsavedChanges(ask);

        await choose("About");

        await vi.waitFor(() => expect(ask).toHaveBeenCalled());
        await Promise.resolve();
        expect(editorState.selectedWorkspace.getValue()).toBe(WORKSPACE);
        expect(goto).not.toHaveBeenCalled();
    });

    test("About opens the start page once the unsaved work is settled", async () => {
        release = guardUnsavedChanges(vi.fn().mockResolvedValue(true));

        await choose("About");

        await vi.waitFor(() => expect(goto).toHaveBeenCalledWith("/"));
        expect(editorState.selectedWorkspace.getValue()).toBeNull();
    });
});
