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

import { reactiveProps } from "./reactiveProps.svelte.js";
import TurtleEditor from "../../src/lib/monaco/TurtleEditor.svelte";

/** Just enough of Monaco to see which markers the editor sets, and against which text. */
const fake = vi.hoisted(() => {
    const state = { text: "", markers: [], listeners: [] };
    const model = {
        getValue: () => state.text,
        setValue: text => {
            state.text = text;
            // Monaco drops markers whose range the new text no longer has; an emptied model has
            // none of them left.
            state.markers = [];
        },
        getLineCount: () => state.text.split("\n").length,
        getWordAtPosition: () => null,
        getLineMaxColumn: () => 20,
        getFullModelRange: () => ({}),
        pushEditOperations: () => {},
        pushStackElement: () => {},
        dispose: () => {},
    };
    const editor = {
        getModel: () => model,
        getValue: () => state.text,
        onDidChangeModelContent: listener => state.listeners.push(listener),
        onDidContentSizeChange: () => {},
        getContentHeight: () => 100,
        addAction: () => {},
        updateOptions: () => {},
        setScrollPosition: () => {},
        setPosition: () => {},
        saveViewState: () => null,
        restoreViewState: () => {},
        focus: () => {},
        layout: () => {},
        revealPositionInCenterIfOutsideViewport: () => {},
        dispose: () => {},
    };
    const api = {
        KeyMod: { CtrlCmd: 0 },
        KeyCode: { KeyS: 0 },
        MarkerSeverity: { Error: 8, Warning: 4, Info: 2 },
        editor: {
            create: (_container, options) => {
                state.text = options.value;
                return editor;
            },
            setTheme: () => {},
            setModelMarkers: (_model, _owner, markers) => {
                // Monaco keeps a marker only if its line exists in the text at the time.
                const lines = state.text.split("\n").length;
                state.markers = markers.filter(
                    marker =>
                        marker.startLineNumber <= lines && state.text !== "",
                );
            },
        },
    };
    return { state, api };
});

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
vi.mock("$lib/monaco/turtleLanguageFeatures.js", () => ({
    attachTermSource: () => {},
    detachTermSource: () => {},
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
