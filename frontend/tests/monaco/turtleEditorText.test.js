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
import { afterEach, describe, expect, test, vi } from "vitest";

import TurtleEditor from "../../src/lib/monaco/TurtleEditor.svelte";
import { reactiveProps } from "../shacl/reactiveProps.svelte.js";
import { fake } from "./fakeMonaco.js";

let mounted;
let target;

async function settle() {
    for (let tick = 0; tick < 5; tick += 1) {
        await Promise.resolve();
        flushSync();
    }
}

async function showEditor(initial) {
    const props = reactiveProps({ onchange: vi.fn(), ...initial });
    target = document.createElement("div");
    document.body.append(target);
    mounted = mount(TurtleEditor, { target, props });
    await settle();
    return props;
}

async function type(text) {
    fake.state.editor.executeEdits("test", [
        {
            range: {
                startLineNumber: 1,
                startColumn: 1,
                endLineNumber: 1,
                endColumn: 1,
            },
            text,
        },
    ]);
    await settle();
}

async function undo() {
    fake.state.editor.trigger("test", "undo");
    await settle();
}

vi.mock("$app/environment", () => ({ browser: true }));
vi.mock("$lib/monaco/monaco.js", () => ({
    loadMonaco: () => Promise.resolve(fake.api),
    TURTLE_LANGUAGE_ID: "turtle",
}));

afterEach(() => {
    if (mounted) {
        unmount(mounted);
        mounted = undefined;
    }
    target?.remove();
});

describe("a text the editor holds differently from how it was given", () => {
    const MIXED = "a\r\nb\nc\n";
    const MARKED = "\uFEFFa\nb\n";

    test.each([
        ["mixed line endings", MIXED],
        ["a byte order mark", MARKED],
    ])("with %s stays as given when it is opened", async (_, text) => {
        const props = await showEditor({ value: "", documentKey: 1 });

        props.value = text;
        props.documentKey = 2;
        await settle();

        expect(fake.state.model.getValue()).not.toBe(text);
        expect(props.value).toBe(text);
        expect(props.onchange).not.toHaveBeenCalled();
    });

    test("stays as given when the editor opens with it", async () => {
        const props = await showEditor({ value: MIXED });

        expect(props.value).toBe(MIXED);
    });

    test("stays as given when it arrives from outside within a document", async () => {
        const props = await showEditor({ value: "", documentKey: 1 });

        props.value = MIXED;
        await settle();

        expect(props.value).toBe(MIXED);
        expect(props.onchange).not.toHaveBeenCalled();
    });

    test("is written as the editor holds it once the user edits it", async () => {
        const props = await showEditor({ value: "", documentKey: 1 });
        props.value = MIXED;
        props.documentKey = 2;
        await settle();

        await type("x");

        expect(props.value).toBe("xa\nb\nc\n");
        expect(props.onchange).toHaveBeenCalledTimes(1);
    });

    test("is as given again once the edits are undone", async () => {
        const props = await showEditor({ value: "", documentKey: 1 });
        props.value = MARKED;
        props.documentKey = 2;
        await settle();

        await type("x");
        await undo();

        expect(props.value).toBe(MARKED);
        expect(fake.state.model.getValue()).toBe("a\nb\n");
    });
});

describe("text from outside", () => {
    test("is an edit undo takes back within one document", async () => {
        const props = await showEditor({ value: "first\n", documentKey: 1 });

        props.value = "second\n";
        await settle();
        await undo();

        expect(props.value).toBe("first\n");
    });

    test("with another key opens a document undo cannot leave", async () => {
        const props = await showEditor({ value: "one\n", documentKey: 1 });

        props.value = "two\n";
        props.documentKey = 2;
        await settle();
        await undo();

        expect(props.value).toBe("two\n");
    });

    test("arriving after its key still opens the document", async () => {
        const props = await showEditor({ value: "one\n", documentKey: 1 });

        props.documentKey = 2;
        await settle();
        props.value = "two\n";
        await settle();
        await undo();

        expect(props.value).toBe("two\n");
        expect(props.onchange).not.toHaveBeenCalled();
    });

    test("arriving after the user typed under the new key is an edit again", async () => {
        const props = await showEditor({ value: "one\n", documentKey: 1 });

        props.documentKey = 2;
        await settle();
        await type("x");
        props.value = "two\n";
        await settle();
        await undo();

        expect(props.value).toBe("xone\n");
    });
});
