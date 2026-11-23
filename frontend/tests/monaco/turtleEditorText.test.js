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
});
