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

import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
import { classStore } from "$lib/stores/classStore.ts";

import ClassEditorButtons from "../../src/routes/mainpage/classEditor/components/ClassEditorButtons.svelte";

let mounted = null;
let target = null;
let dialog = null;

function pressCtrlSWhileTyping() {
    return shortcutStore.handleEvent(
        new KeyboardEvent("keydown", {
            key: "s",
            code: "KeyS",
            ctrlKey: true,
            cancelable: true,
        }),
        { typing: true },
    );
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));
vi.mock("$lib/models/reactive/mapper/map-reactive-object-to-dto.js", () => ({
    mapReactiveClassToClassDto: vi.fn(() => ({ uuid: "c-1" })),
}));
vi.mock("$lib/stores/classStore.ts", () => ({
    classStore: {
        replaceClass: vi.fn(async () => ({ error: null })),
        invalidateGraph: vi.fn(),
    },
}));
vi.mock("$lib/stores/datatypesStore.ts", () => ({
    datatypesStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/crossProfileStore.ts", () => ({
    crossProfileStore: { invalidateWorkspace: vi.fn() },
}));

beforeEach(() => {
    vi.mocked(classStore.replaceClass).mockClear();
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ClassEditorButtons, {
        target,
        context: new Map([
            [
                "classEditor",
                { readOnly: false, workspaceName: "Draft", schemaName: "a" },
            ],
        ]),
        props: {
            reactiveClass: {
                isValid: true,
                isModified: true,
                associations: { isModified: false },
                save: vi.fn(),
            },
            showDiscardSaveConfirmDialog: false,
            pendingAction: null,
            closeClassEditor: vi.fn(),
        },
    });
    flushSync();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    mounted = null;
    target?.remove();
    dialog?.remove();
    dialog = null;
});

describe("the class editor's Ctrl+S in a text field", () => {
    test("saveClass_typingWithChanges_savesTheClass", () => {
        expect(pressCtrlSWhileTyping()).toBe(true);

        expect(classStore.replaceClass).toHaveBeenCalledTimes(1);
    });

    test("saveClass_unsavedChangesGuard_savesWithoutOfferingToDiscard", () => {
        const guard = vi.fn();
        eventStack.registerActionGuard(guard);
        try {
            pressCtrlSWhileTyping();
        } finally {
            eventStack.unregisterActionGuard(guard);
        }

        expect(guard).not.toHaveBeenCalled();
        expect(classStore.replaceClass).toHaveBeenCalledTimes(1);
    });

    test("saveClass_typingInADialogOnTop_leavesTheClassAlone", () => {
        dialog = document.createElement("div");
        dialog.setAttribute("role", "dialog");
        document.body.appendChild(dialog);

        expect(pressCtrlSWhileTyping()).toBe(false);

        expect(classStore.replaceClass).not.toHaveBeenCalled();
    });
});
