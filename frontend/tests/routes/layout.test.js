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
import { versionControlStore } from "$lib/stores/versionControlStore.ts";

import Layout from "../../src/routes/+layout.svelte";

import { goto } from "$app/navigation";

const WORKSPACE = "cgmes";

let mounted = null;
let target = null;
let release = null;

/** Registers what a page with unsaved work would answer. */
function unsavedWorkAnswers(answer) {
    const ask = vi.fn().mockResolvedValue(answer);
    release = guardUnsavedChanges(ask);
    return ask;
}

function logo() {
    return target.querySelector("button[aria-label='RDFArchitect']");
}

function press(key, options = {}) {
    window.dispatchEvent(
        new KeyboardEvent("keydown", {
            key,
            code: `Key${key.toUpperCase()}`,
            ctrlKey: true,
            bubbles: true,
            cancelable: true,
            ...options,
        }),
    );
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
    PUBLIC_EMBED_SESSION_HANDSHAKE: false,
}));
vi.mock("$app/navigation", () => ({
    goto: vi.fn(async () => {}),
    beforeNavigate: vi.fn(),
    afterNavigate: vi.fn(),
}));
vi.mock("$app/state", () => ({
    page: { url: new URL("http://localhost/shacl") },
}));
vi.mock("$lib/api/backendConnectionMonitor.svelte.js", () => ({
    installBackendFetchInterceptor: vi.fn(),
    probeBackendConnection: vi.fn(),
}));
vi.mock("$lib/stores/versionControlStore.ts", () => ({
    versionControlStore: {
        refreshAfterReload: vi.fn(async () => {}),
        canUndo: vi.fn(async () => true),
        canRedo: vi.fn(async () => true),
        undo: vi.fn(async () => ({})),
        redo: vi.fn(async () => ({})),
    },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        isReadOnly: vi.fn(async () => false),
        getWorkspaces: vi.fn(async () => []),
        getNamespaces: vi.fn(async () => []),
        subscribe: vi.fn(() => () => {}),
    },
}));

beforeEach(() => {
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => new Response("{}", { status: 200 })),
    );
    editorState.reset();
    editorState.selectWorkspace(WORKSPACE);
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(Layout, { target, props: {} });
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
    vi.unstubAllGlobals();
    vi.clearAllMocks();
});

describe("the toolbar", () => {
    test("the logo asks about unsaved work before it clears the selection", async () => {
        const ask = unsavedWorkAnswers(false);

        logo().click();

        await vi.waitFor(() => expect(ask).toHaveBeenCalled());
        await Promise.resolve();
        expect(editorState.selectedWorkspace.getValue()).toBe(WORKSPACE);
        expect(goto).not.toHaveBeenCalled();
    });

    test("the logo goes to the main page once the unsaved work is settled", async () => {
        unsavedWorkAnswers(true);

        logo().click();

        await vi.waitFor(() => expect(goto).toHaveBeenCalledWith("/mainpage"));
        expect(editorState.selectedWorkspace.getValue()).toBeNull();
    });
});

describe("undo and redo by keyboard", () => {
    test.each([
        ["z", {}, "undo"],
        ["y", {}, "redo"],
    ])(
        "Ctrl+%s asks about unsaved work without offering to save it",
        async (key, options, step) => {
            await vi.waitFor(() =>
                expect(versionControlStore.canRedo).toHaveBeenCalled(),
            );
            const ask = unsavedWorkAnswers(false);

            press(key, options);

            await vi.waitFor(() =>
                expect(ask).toHaveBeenCalledWith({ allowSave: false }),
            );
            await Promise.resolve();
            expect(versionControlStore[step]).not.toHaveBeenCalled();
        },
    );

    test("Ctrl+Z undoes once the unsaved work is settled", async () => {
        await vi.waitFor(() =>
            expect(versionControlStore.canUndo).toHaveBeenCalled(),
        );
        unsavedWorkAnswers(true);

        press("z");

        await vi.waitFor(() =>
            expect(versionControlStore.undo).toHaveBeenCalled(),
        );
    });
});
