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
import { afterEach, expect, test, vi } from "vitest";

import TurtleEditor from "../../src/lib/monaco/TurtleEditor.svelte";
import { fake } from "../monaco/fakeMonaco.js";
import { reactiveProps } from "./reactiveProps.svelte.js";

const FINDINGS = [
    {
        severity: "ERROR",
        line: 3,
        column: 5,
        message: "Shape sh:targetClass: class does not exist",
        source: "SHAPE",
    },
];

let mounted;
let target;

async function settle() {
    for (let tick = 0; tick < 5; tick += 1) {
        await Promise.resolve();
        flushSync();
    }
}

vi.mock("$app/environment", () => ({ browser: true }));
vi.mock("$lib/monaco/monaco.js", () => ({
    loadMonaco: () => Promise.resolve(fake.api),
    TURTLE_LANGUAGE_ID: "turtle",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
});

test("a document opened with findings shows them once its text has arrived", async () => {
    // How the workbench opens a document: the buffer is emptied, the text is read, and the
    // findings — from the stored report — are the same before and after the text arrives.
    const props = reactiveProps({
        value: "",
        documentKey: 1,
        findings: FINDINGS,
    });
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(TurtleEditor, { target, props });
    await settle();

    props.value = "@prefix sh: <x#> .\n\nex:A sh:targetClass cim:Nope .\n";
    props.documentKey = 2;
    await settle();

    expect(fake.state.markers).toHaveLength(1);
    expect(fake.state.markers[0].startLineNumber).toBe(3);
});

test("markers move with the text being typed until new findings arrive", async () => {
    const props = reactiveProps({
        value: "@prefix sh: <x#> .\n\nex:A sh:targetClass cim:Nope .\n",
        documentKey: 1,
        findings: FINDINGS,
    });
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(TurtleEditor, { target, props });
    await settle();

    fake.state.editor.executeEdits("test", [
        {
            range: {
                startLineNumber: 1,
                startColumn: 1,
                endLineNumber: 1,
                endColumn: 1,
            },
            text: "# shapes\n",
        },
    ]);
    await settle();

    expect(fake.state.markers[0].startLineNumber).toBe(4);

    props.findings = [{ ...FINDINGS[0], line: 2 }];
    await settle();

    expect(fake.state.markers[0].startLineNumber).toBe(2);
});
