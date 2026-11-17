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

/** Just enough of Monaco to size the box: it measures the text once, while being created. */
const fake = vi.hoisted(() => {
    const state = { text: "", contentHeight: 0, sizeListeners: [] };
    const model = {
        getValue: () => state.text,
        setValue: text => (state.text = text),
        getFullModelRange: () => ({}),
        pushEditOperations: () => {},
        pushStackElement: () => {},
        getLineCount: () => 1,
        getWordAtPosition: () => null,
        getLineMaxColumn: () => 1,
        dispose: () => {},
    };
    const editor = {
        getModel: () => model,
        getValue: () => state.text,
        onDidChangeModelContent: () => {},
        onDidContentSizeChange: listener => state.sizeListeners.push(listener),
        getContentHeight: () => state.contentHeight,
        addAction: () => {},
        updateOptions: () => {},
        setScrollPosition: () => {},
        setPosition: () => {},
        saveViewState: () => null,
        restoreViewState: () => {},
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
            setModelMarkers: () => {},
        },
    };
    return { state, api };
});

let target;
let component;

async function showEditor(contentHeight) {
    fake.state.contentHeight = contentHeight;
    target = document.createElement("div");
    document.body.append(target);
    component = mount(TurtleEditor, {
        target,
        props: { value: "ex:a ex:b ex:c .", autoGrow: true },
    });
    for (let tick = 0; tick < 5; tick += 1) {
        await Promise.resolve();
        flushSync();
    }
    return target.querySelector("div");
}

vi.mock("$app/environment", () => ({ browser: true }));
vi.mock("$lib/monaco/monaco.js", () => ({
    loadMonaco: () => Promise.resolve(fake.api),
    TURTLE_LANGUAGE_ID: "turtle",
}));

afterEach(() => {
    if (component) {
        unmount(component);
    }
    target?.remove();
    fake.state.sizeListeners.length = 0;
});

test("an auto-growing editor fits the text it opens with", async () => {
    const box = await showEditor(300);

    expect(box.style.height).toBe("300px");
});

test("an auto-growing editor stays within its limits", async () => {
    const box = await showEditor(5000);

    expect(box.style.height).toBe("480px");
});

test("an auto-growing editor follows the text as it changes", async () => {
    const box = await showEditor(300);

    fake.state.contentHeight = 200;
    fake.state.sizeListeners.forEach(listener => listener());
    flushSync();

    expect(box.style.height).toBe("200px");
});
