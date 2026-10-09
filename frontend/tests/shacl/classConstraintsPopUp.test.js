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

import { editorState } from "$lib/sharedState.svelte.js";

import SHACLClassSpecificPopUp from "../../src/routes/shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

const DOCUMENT_ID = "11111111-2222-3333-4444-555555555555";
const WORKSPACE = "other";
const GRAPH = "http://example.org/Other";

const api = vi.hoisted(() => ({
    getShaclRelatedToClass: vi.fn(),
    getClassesReferencingThisClass: vi.fn(),
}));

const goto = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function empty() {
    return {
        namespaces: "",
        nodeShapes: [],
        propertyShapes: [],
        derivedPropertyShapes: [],
    };
}

function dialog() {
    return document.querySelector("[role='dialog']");
}

function button(label) {
    return [...dialog().querySelectorAll("button")].find(element =>
        element.textContent.trim().startsWith(label),
    );
}

async function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SHACLClassSpecificPopUp, {
        target,
        props: {
            showDialog: true,
            workspaceName: WORKSPACE,
            graphUri: GRAPH,
            reactiveClass: {
                uuid: { value: "class-uuid" },
                label: { value: "Thing" },
            },
        },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    await vi.waitFor(() =>
        expect(api.getShaclRelatedToClass).toHaveBeenCalled(),
    );
    flushSync();
}

vi.mock("$lib/api/generated/index.ts", () => api);

vi.mock("$app/navigation", () => ({ goto }));

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
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
    api.getClassesReferencingThisClass.mockResolvedValue({
        data: { classesReferencingThisClass: {} },
    });
    api.getShaclRelatedToClass.mockResolvedValue({
        data: {
            custom: {
                ...empty(),
                propertyShapes: [
                    {
                        label: "Thing.value",
                        propertyType: "attribute",
                        summary: "1..1",
                        propertyShapes: [
                            {
                                id: "http://example.org/value-rule",
                                triples: "ex:value-rule a sh:PropertyShape .",
                                origins: [
                                    {
                                        documentId: DOCUMENT_ID,
                                        documentName: "mine.ttl",
                                        line: 7,
                                    },
                                ],
                            },
                        ],
                    },
                ],
            },
            generated: empty(),
        },
    });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SHACLClassSpecificPopUp", () => {
    test("a document chip selects the class's schema before opening the workbench", async () => {
        await render();
        await vi.waitFor(() => expect(button("mine.ttl")).toBeTruthy());

        button("mine.ttl").click();

        expect(editorState.selectedWorkspace.getValue()).toBe(WORKSPACE);
        expect(editorState.selectedGraph.getValue()).toBe(GRAPH);
        expect(goto).toHaveBeenCalledWith(
            `/shacl?document=${DOCUMENT_ID}&line=7`,
        );
    });

    test("the header button opens the document the class's rules are in", async () => {
        await render();
        await vi.waitFor(() => expect(button("mine.ttl")).toBeTruthy());

        button("Edit in workbench").click();

        expect(goto).toHaveBeenCalledWith(
            `/shacl?document=${DOCUMENT_ID}&line=7`,
        );
        expect(editorState.selectedGraph.getValue()).toBe(GRAPH);
    });

    test("asks for references in the dialog's own schema", async () => {
        await render();

        await vi.waitFor(() =>
            expect(api.getClassesReferencingThisClass).toHaveBeenCalledWith({
                path: {
                    datasetName: WORKSPACE,
                    graphURI: GRAPH,
                    classUUID: "class-uuid",
                },
            }),
        );
    });

    test("says the references could not be read rather than that there are none", async () => {
        api.getClassesReferencingThisClass.mockResolvedValue({
            error: { detail: "boom" },
        });

        await render();

        await vi.waitFor(() =>
            expect(dialog().textContent).toContain("could not be read"),
        );
        expect(dialog().textContent).not.toContain(
            "Nothing references this class",
        );
    });
});
