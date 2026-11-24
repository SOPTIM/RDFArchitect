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

import { saveWorkspace } from "$lib/desktop/saveWorkspace.js";
import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
import { editorState } from "$lib/sharedState.svelte.js";

import Layout from "../../src/routes/+layout.svelte";

let mounted = null;
let target = null;
let field = null;

function pressCtrlS({ repeat = false } = {}) {
    const event = new KeyboardEvent("keydown", {
        key: "s",
        code: "KeyS",
        ctrlKey: true,
        repeat,
        bubbles: true,
        cancelable: true,
    });
    field.dispatchEvent(event);
    flushSync();
    return event;
}

async function settle() {
    for (let i = 0; i < 5; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
        flushSync();
    }
}

vi.mock("$app/state", () => ({
    page: { url: new URL("http://localhost/changelog"), state: {} },
}));
vi.mock("$app/navigation", () => ({
    afterNavigate: vi.fn(),
    beforeNavigate: vi.fn(),
    goto: vi.fn(),
    replaceState: vi.fn(),
}));
vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "",
    PUBLIC_DESKTOP: true,
    PUBLIC_DESKTOP_HEARTBEAT_SECONDS: 10,
    PUBLIC_EMBED_SESSION_HANDSHAKE: false,
    PUBLIC_SHARE_SNAPSHOT_ENABLED: false,
}));
vi.mock(
    "$lib/api/backendConnectionMonitor.svelte.js",
    async importOriginal => ({
        ...(await importOriginal()),
        installBackendFetchInterceptor: vi.fn(),
        probeBackendConnection: vi.fn(),
    }),
);
vi.mock("$lib/desktop/desktopApi.js", () => ({
    listRecoveredWorkspaces: vi.fn(async () => []),
    sendGoodbye: vi.fn(),
    sendHeartbeat: vi.fn(async () => ({ unsavedWorkspaces: [] })),
}));
vi.mock("$lib/desktop/saveWorkspace.js", async importOriginal => ({
    ...(await importOriginal()),
    saveWorkspace: vi.fn(async () => "saved"),
}));
vi.mock("$lib/stores/workspaceStore.ts", async () => {
    const { writable } = await import("svelte/store");
    const state = writable({ data: [], loading: false, error: null });
    return {
        workspaceStore: {
            subscribe: state.subscribe,
            getNamespaces: vi.fn(async () => []),
            getWorkspaces: vi.fn(async () => []),
            isReadOnly: vi.fn(async () => false),
            updateReadonly: vi.fn(),
        },
    };
});
vi.mock("$lib/stores/versionControlStore.ts", () => ({
    versionControlStore: {
        canRedo: vi.fn(async () => false),
        canUndo: vi.fn(async () => false),
        refreshAfterReload: vi.fn(async () => {}),
    },
}));

beforeEach(async () => {
    vi.mocked(saveWorkspace).mockClear();
    editorState.reset();
    editorState.selectedWorkspace.updateValue("Draft");
    target = document.createElement("div");
    document.body.appendChild(target);
    field = document.createElement("input");
    document.body.appendChild(field);
    mounted = mount(Layout, { target });
    await settle();
    field.focus();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    mounted = null;
    target?.remove();
    field?.remove();
    editorState.reset();
});

describe("Ctrl+S in a text field of the desktop app", () => {
    test("handleKeydown_textFieldFocused_savesInsteadOfTheBrowserDialog", () => {
        const event = pressCtrlS();

        expect(event.defaultPrevented).toBe(true);
        expect(saveWorkspace).toHaveBeenCalledExactlyOnceWith("Draft", {
            saveAs: false,
        });
    });

    test("handleKeydown_heldKey_savesOnce", () => {
        pressCtrlS();
        const repeated = [1, 2, 3].map(() => pressCtrlS({ repeat: true }));

        expect(saveWorkspace).toHaveBeenCalledTimes(1);
        expect(repeated.every(event => event.defaultPrevented)).toBe(true);
    });

    test("handleKeydown_unsavedClassChanges_savesWithoutOfferingToDiscard", () => {
        const guard = vi.fn();
        eventStack.registerActionGuard(guard);
        try {
            pressCtrlS();
        } finally {
            eventStack.unregisterActionGuard(guard);
        }

        expect(guard).not.toHaveBeenCalled();
        expect(saveWorkspace).toHaveBeenCalledTimes(1);
    });
});
