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

import { describe, expect, test, vi } from "vitest";

import { pushExternalText } from "../../src/lib/monaco/externalText.js";

/**
 * Text written into the editor from outside it — a form edit, above all.
 *
 * The behaviour under test is what `setValue` took away: an undoable edit that leaves the view
 * where it was.
 */
function fakeEditor(text) {
    const calls = [];
    let value = text;
    const model = {
        getValue: () => value,
        getFullModelRange: () => "whole model",
        pushStackElement: () => calls.push("stack"),
        pushEditOperations: (before, edits) => {
            calls.push("edit");
            value = edits[0].text;
        },
    };
    return {
        calls,
        model,
        getModel: () => model,
        getValue: () => value,
        saveViewState: vi.fn(() => "view state"),
        restoreViewState: vi.fn(() => calls.push("restore")),
        setValue: vi.fn(),
    };
}

describe("pushing external text into the editor", () => {
    test("replaces the text as one undoable edit, not with setValue", () => {
        const editor = fakeEditor("before");

        expect(pushExternalText(editor, "after")).toBe(true);

        expect(editor.getValue()).toBe("after");
        expect(editor.setValue).not.toHaveBeenCalled();
        // Bracketed, so undo takes the whole change back rather than merging it into the typing
        // that came before it.
        expect(editor.calls).toEqual(["stack", "edit", "stack", "restore"]);
    });

    test("puts the scroll position and cursor back", () => {
        const editor = fakeEditor("before");

        pushExternalText(editor, "after");

        expect(editor.saveViewState).toHaveBeenCalled();
        expect(editor.restoreViewState).toHaveBeenCalledWith("view state");
    });

    test("leaves text the editor already holds alone", () => {
        // A keystroke sets the bound value from the editor itself; pushing it back would move the
        // cursor on every character typed.
        const editor = fakeEditor("same");

        expect(pushExternalText(editor, "same")).toBe(false);

        expect(editor.calls).toEqual([]);
        expect(editor.saveViewState).not.toHaveBeenCalled();
    });

    test("does nothing before the editor exists", () => {
        expect(pushExternalText(null, "anything")).toBe(false);
        expect(pushExternalText({ getModel: () => null }, "anything")).toBe(
            false,
        );
    });
});
