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

import { describe, expect, test } from "vitest";

import { ownsKeyboardInput } from "../../src/lib/eventhandling/keyboardTargets.js";

/** Builds `<parent class="..."><child></child></parent>` and returns the child. */
function inside(parentClass, childTag = "div") {
    const parent = document.createElement("div");
    parent.className = parentClass;
    const child = document.createElement(childTag);
    parent.appendChild(child);
    return child;
}

describe("ownsKeyboardInput", () => {
    test("form controls type for themselves", () => {
        for (const tag of ["input", "textarea", "select"]) {
            expect(ownsKeyboardInput(document.createElement(tag))).toBe(true);
        }
    });

    test("so does anything contenteditable", () => {
        const element = document.createElement("div");
        // jsdom never computes isContentEditable, so the browser's answer is stood in for.
        Object.defineProperty(element, "isContentEditable", { value: true });

        expect(ownsKeyboardInput(element)).toBe(true);
    });

    test("a plain element does not", () => {
        expect(ownsKeyboardInput(document.createElement("div"))).toBe(false);
        expect(ownsKeyboardInput(document.createElement("button"))).toBe(false);
    });

    test("nothing at all does not", () => {
        expect(ownsKeyboardInput(null)).toBe(false);
        expect(ownsKeyboardInput(window)).toBe(false);
    });

    /*
     * The editor's focus host is a bare div when `editContext` is on, so only its place in the
     * editor says that a key press there is someone typing rather than a shortcut. Getting this
     * wrong meant Ctrl+C copied a class instead of the selected text — and because the browser,
     * not Monaco, implements copy, the preventDefault that went with it lost the copy entirely.
     */
    test("Monaco's edit context does, wherever the browser puts the focus", () => {
        expect(ownsKeyboardInput(inside("monaco-editor vs"))).toBe(true);
        expect(ownsKeyboardInput(inside("monaco-editor", "textarea"))).toBe(
            true,
        );
    });

    test("and so does its context menu, which is portalled out of the editor", () => {
        expect(ownsKeyboardInput(inside("monaco-menu"))).toBe(true);
    });
});
