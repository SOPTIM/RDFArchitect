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
import { afterEach, describe, expect, test } from "vitest";

import NestedDialogs from "./NestedDialogs.svelte";

let mounted = null;
let target = null;

function dialogs() {
    return [...document.querySelectorAll("[role='dialog']")];
}

function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(NestedDialogs, { target });
    flushSync();
    return mounted;
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("DialogBase", () => {
    test("marks its content as a layer of its own", () => {
        // What keeps the dialog underneath from taking an interaction with the one above it for
        // one that happened outside, closing itself and the other with it.
        render();

        expect(dialogs()[0].hasAttribute("data-dialog-layer")).toBe(true);
    });

    test("opens one within another without closing the one it opened from", () => {
        const host = render();

        host.openInner();
        flushSync();

        expect(dialogs()).toHaveLength(2);
        expect(host.isOuterOpen()).toBe(true);
        for (const dialog of dialogs()) {
            expect(dialog.closest("[data-dialog-layer]")).not.toBeNull();
        }
    });
});
