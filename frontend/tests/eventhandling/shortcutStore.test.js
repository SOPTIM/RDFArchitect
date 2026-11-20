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

import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";

function press(key, { ctrl = false, shift = false } = {}) {
    const event = new KeyboardEvent("keydown", {
        key,
        code: `Key${key.toUpperCase()}`,
        ctrlKey: ctrl,
        shiftKey: shift,
        cancelable: true,
    });
    return { handled: shortcutStore.handleEvent(event), event };
}

describe("shortcutStore", () => {
    const registered = [];

    function register(...args) {
        registered.push(shortcutStore.register(...args));
    }

    afterEach(() => {
        registered.splice(0).forEach(unregister => unregister());
    });

    test("runs the shortcut registered for the keys", () => {
        const save = vi.fn();
        register("save", ["ctrl", "s"], save);

        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(true);
        expect(event.defaultPrevented).toBe(true);
        expect(save).toHaveBeenCalledTimes(1);
    });

    test("prefers the shortcut registered last for the same keys", () => {
        const global = vi.fn();
        const editor = vi.fn();
        register("saveWorkspace", ["ctrl", "s"], global);
        register("saveClass", ["ctrl", "s"], editor);

        press("s", { ctrl: true });

        expect(editor).toHaveBeenCalledTimes(1);
        expect(global).not.toHaveBeenCalled();
    });

    test("leaves the keys to the next shortcut while one does not apply", () => {
        const global = vi.fn();
        const editor = vi.fn();
        let classChanged = false;
        register("saveWorkspace", ["ctrl", "s"], global);
        register("saveClass", ["ctrl", "s"], editor, false, () => classChanged);

        press("s", { ctrl: true });
        expect(global).toHaveBeenCalledTimes(1);
        expect(editor).not.toHaveBeenCalled();

        classChanged = true;
        press("s", { ctrl: true });
        expect(editor).toHaveBeenCalledTimes(1);
        expect(global).toHaveBeenCalledTimes(1);
    });

    test("keeps the browser's own action from running for claimed keys nobody takes", () => {
        register("saveClass", ["ctrl", "s"], vi.fn(), false, () => false);

        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(true);
    });

    test("leaves keys nobody claimed to the browser", () => {
        const { handled, event } = press("s", { ctrl: true });

        expect(handled).toBe(false);
        expect(event.defaultPrevented).toBe(false);
    });

    test("counts a registration made again as the latest", () => {
        const first = vi.fn();
        const second = vi.fn();
        register("a", ["ctrl", "s"], first);
        register("b", ["ctrl", "s"], second);
        register("a", ["ctrl", "s"], first);

        press("s", { ctrl: true });

        expect(first).toHaveBeenCalledTimes(1);
        expect(second).not.toHaveBeenCalled();
    });
});
