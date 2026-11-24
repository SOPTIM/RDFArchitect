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
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";
import { editorState } from "$lib/sharedState.svelte.js";

import SHACLPropertySpecificDialog from "../../src/routes/shacl/SHACLPropertySpecificDialog.svelte";

const DOCUMENT_ID = "11111111-2222-3333-4444-555555555555";
const OTHER_DOCUMENT_ID = "66666666-7777-8888-9999-000000000000";

const api = vi.hoisted(() => ({
    getAttributeShacl: vi.fn(),
    getAssociationShacl: vi.fn(),
    getCustomShaclNamespacesAsString: vi.fn(),
    getGeneratedShaclNamespacesAsString: vi.fn(),
}));

const goto = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='dialog']");
}

function button(label) {
    return [...dialog().querySelectorAll("button")].find(element =>
        element.textContent.trim().startsWith(label),
    );
}

function answer(custom) {
    api.getAttributeShacl.mockResolvedValue({
        data: { custom, generated: [] },
    });
}

async function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SHACLPropertySpecificDialog, {
        target,
        props: {
            showDialog: true,
            property: new ReactiveAttribute("attribute-uuid", "Thing.value"),
            classUuidOverride: "class-uuid",
        },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    await vi.waitFor(() => expect(api.getAttributeShacl).toHaveBeenCalled());
    flushSync();
}

vi.mock("$lib/api/generated/index.ts", () => api);

vi.mock("$app/navigation", () => ({ goto }));

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

vi.mock("$lib/models/reactive/models/reactive-attribute.svelte.js", () => ({
    ReactiveAttribute: class {
        constructor(uuid, label) {
            this.uuid = { value: uuid };
            this.label = { value: label };
        }
    },
}));

vi.mock("$lib/models/reactive/models/reactive-association.svelte.js", () => ({
    ReactiveAssociation: class {},
}));

vi.mock("$lib/monaco/TurtleEditor.svelte", async () => {
    const { createRawSnippet } = await import("svelte");
    return {
        default: function TurtleEditorStub(anchor) {
            return createRawSnippet(() => ({
                render: () => `<div class="turtle-editor-stub"></div>`,
            }))(anchor);
        },
    };
});

beforeEach(() => {
    editorState.selectedWorkspace.updateValue("selected");
    editorState.selectedGraph.updateValue("http://example.org/Selected");
    editorState.selectedClassWorkspace.updateValue("other");
    editorState.selectedClassGraph.updateValue("http://example.org/Other");
    api.getCustomShaclNamespacesAsString.mockResolvedValue({ data: "" });
    api.getGeneratedShaclNamespacesAsString.mockResolvedValue({ data: "" });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SHACLPropertySpecificDialog", () => {
    test("names the document of each custom rule", async () => {
        answer([
            {
                id: "http://example.org/rule-a",
                triples: "ex:rule-a a sh:PropertyShape .",
                origins: [
                    {
                        documentId: DOCUMENT_ID,
                        documentName: "mine.ttl",
                        line: 12,
                    },
                ],
            },
            {
                id: "http://example.org/rule-b",
                triples: "ex:rule-b a sh:PropertyShape .",
                origins: [
                    {
                        documentId: OTHER_DOCUMENT_ID,
                        documentName: "theirs.ttl",
                        line: null,
                    },
                ],
            },
        ]);

        await render();

        await vi.waitFor(() => expect(button("mine.ttl")).toBeTruthy());
        expect(button("mine.ttl").textContent).toContain("line 12");
        expect(button("theirs.ttl")).toBeTruthy();
    });

    test("a chip opens that document at that line, on the property's schema", async () => {
        answer([
            {
                id: "http://example.org/rule-a",
                triples: "ex:rule-a a sh:PropertyShape .",
                origins: [
                    {
                        documentId: DOCUMENT_ID,
                        documentName: "mine.ttl",
                        line: 12,
                    },
                ],
            },
        ]);
        await render();
        await vi.waitFor(() => expect(button("mine.ttl")).toBeTruthy());

        button("mine.ttl").click();

        expect(goto).toHaveBeenCalledWith(
            `/shacl?document=${DOCUMENT_ID}&line=12`,
        );
        // The workbench opens the selected schema, so it has to be this property's.
        expect(editorState.selectedWorkspace.getValue()).toBe("other");
        expect(editorState.selectedGraph.getValue()).toBe(
            "http://example.org/Other",
        );
    });

    test("the header button follows the first document a rule names", async () => {
        answer([
            {
                id: "http://example.org/rule-a",
                triples: "ex:rule-a a sh:PropertyShape .",
                origins: [
                    {
                        documentId: DOCUMENT_ID,
                        documentName: "mine.ttl",
                        line: 3,
                    },
                ],
            },
        ]);
        await render();
        await vi.waitFor(() => expect(button("mine.ttl")).toBeTruthy());

        button("Edit in workbench").click();

        expect(goto).toHaveBeenCalledWith(
            `/shacl?document=${DOCUMENT_ID}&line=3`,
        );
    });

    test("shows rules without chips when no document is named", async () => {
        answer([
            {
                id: "http://example.org/rule-a",
                triples: "ex:rule-a a sh:PropertyShape .",
            },
        ]);
        await render();
        await vi.waitFor(() =>
            expect(
                dialog().querySelector(".turtle-editor-stub"),
            ).not.toBeNull(),
        );

        button("Edit in workbench").click();

        expect(goto).toHaveBeenCalledWith("/shacl");
    });
});
