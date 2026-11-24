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

import {
    holdsText,
    openText,
    pushExternalText,
} from "../../src/lib/monaco/externalText.js";
import { fake } from "./fakeMonaco.js";

/**
 * Text written into the editor from outside it.
 *
 * The behaviour under test is what `setValue` took away: an undoable edit that leaves the view
 * where it was.
 */
function editorWith(text) {
    return fake.api.editor.create(null, { value: text });
}

/** Typing, as far as undo is concerned: an edit that the next one may still join. */
function type(editor, text) {
    const model = editor.getModel();
    const end = model.getFullModelRange();
    model.pushEditOperations(
        [],
        [
            {
                range: {
                    startLineNumber: end.endLineNumber,
                    startColumn: end.endColumn,
                    endLineNumber: end.endLineNumber,
                    endColumn: end.endColumn,
                },
                text,
            },
        ],
        () => null,
    );
}

describe("pushing external text into the editor", () => {
    test("replaces the text as one step that undo takes back", () => {
        const editor = editorWith("before");

        expect(pushExternalText(editor, "after")).toBe(true);
        expect(editor.getValue()).toBe("after");

        editor.trigger("test", "undo");
        expect(editor.getValue()).toBe("before");
    });

    test("keeps what was typed before it out of that step", () => {
        const editor = editorWith("typed");
        type(editor, " more");

        pushExternalText(editor, "replaced");
        editor.trigger("test", "undo");

        expect(editor.getValue()).toBe("typed more");
    });

    test("puts the scroll position and cursor back", () => {
        const editor = editorWith("one\ntwo\nthree");
        editor.setPosition({ lineNumber: 3, column: 2 });
        editor.setScrollPosition({ scrollTop: 40 });

        pushExternalText(editor, "one\ntwo\nthree, changed");

        expect(editor.getPosition()).toEqual({ lineNumber: 3, column: 2 });
        expect(editor.getScrollTop()).toBe(40);
    });

    test("leaves text the editor already holds alone", () => {
        // A keystroke sets the bound value from the editor itself; pushing it back would move the
        // cursor on every character typed.
        const editor = editorWith("same");
        const version = editor.getModel().getAlternativeVersionId();

        expect(pushExternalText(editor, "same")).toBe(false);

        expect(editor.getModel().getAlternativeVersionId()).toBe(version);
    });

    test("leaves text alone that the editor holds with other line endings", () => {
        const editor = editorWith("a\r\nb\nc\n");
        const version = editor.getModel().getAlternativeVersionId();

        expect(pushExternalText(editor, "a\r\nb\nc\n")).toBe(false);

        expect(editor.getModel().getAlternativeVersionId()).toBe(version);
    });

    test("does nothing before the editor exists", () => {
        expect(pushExternalText(null, "anything")).toBe(false);
        expect(pushExternalText({ getModel: () => null }, "anything")).toBe(
            false,
        );
    });
});

describe("opening another document in the editor", () => {
    test("starts a history of its own instead of an undoable edit", () => {
        const editor = editorWith("the previous document");
        type(editor, ", edited");

        expect(openText(editor, "the next document")).toBe(true);
        expect(editor.getValue()).toBe("the next document");

        // Undo after opening must not bring the previous document's text back.
        editor.trigger("test", "undo");
        expect(editor.getValue()).toBe("the next document");
    });

    test("starts at the top rather than where the previous one was scrolled to", () => {
        const editor = editorWith("one\ntwo\nthree");
        editor.setPosition({ lineNumber: 3, column: 2 });
        editor.setScrollPosition({ scrollTop: 40 });

        openText(editor, "after");

        expect(editor.getPosition()).toEqual({ lineNumber: 1, column: 1 });
        expect(editor.getScrollTop()).toBe(0);
    });

    test("does nothing before the editor exists", () => {
        expect(openText(null, "anything")).toBe(false);
    });
});

describe("whether the editor holds a text", () => {
    test("compares with the text as the editor would hold it", () => {
        const editor = editorWith("\uFEFFa\r\nb\nc\n");

        expect(editor.getValue()).toBe("a\nb\nc\n");
        expect(holdsText(editor, "\uFEFFa\r\nb\nc\n")).toBe(true);
        expect(holdsText(editor, "a\nb\nc\n")).toBe(true);
        expect(holdsText(editor, "a\nb\nc")).toBe(false);
    });

    test("is false before the editor exists", () => {
        expect(holdsText(null, "")).toBe(false);
    });
});
