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

import { afterEach, describe, expect, test, vi } from "vitest";

import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";

function press(key, { ctrl = false, shift = false, typing = false } = {}) {
    const event = new KeyboardEvent("keydown", {
        key,
        code: `Key${key.toUpperCase()}`,
        ctrlKey: ctrl,
        shiftKey: shift,
        cancelable: true,
    });
    return { handled: shortcutStore.handleEvent(event, { typing }), event };
}

describe("shortcutStore", () => {
    const registered = [];

    function register(...args) {
        registered.push(shortcutStore.register(...args));
    }

    afterEach(() => {
        registered.splice(0).forEach(unregister => unregister());
    });

    test("handleEvent_registeredKeys_runsTheShortcut", () => {
        const save = vi.fn();
        register("save", ["ctrl", "s"], save);

        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(true);
        expect(event.defaultPrevented).toBe(true);
        expect(save).toHaveBeenCalledTimes(1);
    });

    test("handleEvent_twoShortcutsForTheKeys_runsTheOneRegisteredLast", () => {
        const global = vi.fn();
        const editor = vi.fn();
        register("saveWorkspace", ["ctrl", "s"], global);
        register("saveClass", ["ctrl", "s"], editor);

        press("s", { ctrl: true });

        expect(editor).toHaveBeenCalledTimes(1);
        expect(global).not.toHaveBeenCalled();
    });

    test("handleEvent_shortcutNotActive_leavesTheKeysToTheNextOne", () => {
        const global = vi.fn();
        const editor = vi.fn();
        let classChanged = false;
        register("saveWorkspace", ["ctrl", "s"], global);
        register("saveClass", ["ctrl", "s"], editor, false, {
            isActive: () => classChanged,
        });

        press("s", { ctrl: true });
        expect(global).toHaveBeenCalledTimes(1);
        expect(editor).not.toHaveBeenCalled();

        classChanged = true;
        press("s", { ctrl: true });
        expect(editor).toHaveBeenCalledTimes(1);
        expect(global).toHaveBeenCalledTimes(1);
    });

    test("handleEvent_claimedKeysNobodyTakes_keepsTheBrowserActionFromRunning", () => {
        register("saveClass", ["ctrl", "s"], vi.fn(), false, {
            isActive: () => false,
        });

        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(true);
    });

    test("handleEvent_unclaimedKeys_leavesThemToTheBrowser", () => {
        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(false);
    });

    test("register_again_countsAsTheLatest", () => {
        const first = vi.fn();
        const second = vi.fn();
        register("a", ["ctrl", "s"], first);
        register("b", ["ctrl", "s"], second);
        register("a", ["ctrl", "s"], first);

        press("s", { ctrl: true });

        expect(first).toHaveBeenCalledTimes(1);
        expect(second).not.toHaveBeenCalled();
    });

    test("handleEvent_typingAndShortcutWorksWhileTyping_runsItInsteadOfTheBrowserAction", () => {
        const save = vi.fn();
        register("saveWorkspace", ["ctrl", "s"], save, false, {
            whileTyping: true,
        });

        const { handled, event } = press("s", { ctrl: true, typing: true });

        expect(handled).toBe(true);
        expect(event.defaultPrevented).toBe(true);
        expect(save).toHaveBeenCalledTimes(1);
    });

    test("handleEvent_typingAndNoShortcutWorksWhileTyping_leavesTheKeysToTheField", () => {
        const copy = vi.fn();
        register("copy", ["ctrl", "c"], copy);

        const { handled, event } = press("c", { ctrl: true, typing: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(false);
        expect(copy).not.toHaveBeenCalled();
    });

    test("handleEvent_typingAndTheWinnerDoesNotWorkWhileTyping_runsNothing", () => {
        const saveWorkspace = vi.fn();
        const saveClass = vi.fn();
        register("saveWorkspace", ["ctrl", "s"], saveWorkspace, false, {
            whileTyping: true,
        });
        register("saveClass", ["ctrl", "s"], saveClass);

        const { handled, event } = press("s", { ctrl: true, typing: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(true);
        expect(saveWorkspace).not.toHaveBeenCalled();
        expect(saveClass).not.toHaveBeenCalled();
    });

    test("handleEvent_typingAndWhileTypingDependsOnTheState_followsIt", () => {
        const saveClass = vi.fn();
        let dialogOpen = true;
        register("saveClass", ["ctrl", "s"], saveClass, false, {
            whileTyping: () => !dialogOpen,
        });

        const behindDialog = press("s", { ctrl: true, typing: true });
        expect(behindDialog.handled).toBe(false);
        expect(behindDialog.event.defaultPrevented).toBe(false);

        dialogOpen = false;
        expect(press("s", { ctrl: true, typing: true }).handled).toBe(true);
        expect(saveClass).toHaveBeenCalledTimes(1);
    });

    test("handleEvent_unsavedEdits_asksUnlessTheShortcutKeepsThem", () => {
        const guard = vi.fn();
        eventStack.registerActionGuard(guard);
        try {
            const save = vi.fn();
            const open = vi.fn();
            register("save", ["ctrl", "s"], save, false, { keepsEdits: true });
            register("open", ["ctrl", "o"], open);

            press("s", { ctrl: true });
            expect(save).toHaveBeenCalledTimes(1);
            expect(guard).not.toHaveBeenCalled();

            press("o", { ctrl: true });
            expect(open).not.toHaveBeenCalled();
            expect(guard).toHaveBeenCalledTimes(1);
        } finally {
            eventStack.unregisterActionGuard(guard);
        }
    });
});
