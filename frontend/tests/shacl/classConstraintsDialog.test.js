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

import ClassConstraintsView from "../../src/routes/shacl/shaclclassspecific/ClassConstraintsView.svelte";

const DOCUMENT_ID = "11111111-2222-3333-4444-555555555555";

const CUSTOM = {
    namespaces: "@prefix sh: <http://www.w3.org/ns/shacl#> .\n",
    nodeShapes: [
        {
            id: "http://official.example/DiagramShape",
            triples: "off:DiagramShape a sh:NodeShape .",
            origins: [
                {
                    documentId: DOCUMENT_ID,
                    documentName: "simple.ttl",
                    line: 40,
                },
            ],
        },
    ],
    propertyShapes: [
        {
            label: "Diagram.x1InitialView",
            propertyType: "attribute",
            summary: "0..1, xsd:float",
            propertyShapes: [
                {
                    id: "http://official.example/x1-datatype",
                    order: 1,
                    triples: "off:x1-datatype a sh:PropertyShape .",
                    origins: [
                        {
                            documentId: DOCUMENT_ID,
                            documentName: "simple.ttl",
                            line: 214,
                        },
                    ],
                },
            ],
        },
    ],
    derivedPropertyShapes: [
        {
            label: "IdentifiedObject.name",
            propertyType: "attribute",
            summary: "1..1, xsd:string",
            propertyShapes: [
                {
                    id: "http://official.example/name",
                    order: 1,
                    triples: "off:name a sh:PropertyShape .",
                    origins: [
                        {
                            documentId: DOCUMENT_ID,
                            documentName: "ido.ttl",
                            line: 12,
                        },
                    ],
                },
            ],
        },
    ],
};

const GENERATED = {
    namespaces: "",
    nodeShapes: [],
    propertyShapes: [
        {
            label: "Diagram.x1InitialView",
            propertyType: "attribute",
            summary: "0..1, xsd:float",
            propertyShapes: [
                {
                    id: "http://gen.example/x1",
                    order: 1,
                    triples: "gen:x1 a sh:PropertyShape .",
                    origins: [],
                },
            ],
        },
        {
            label: "Diagram.orientation",
            propertyType: "attribute",
            summary: "1..1, sh:IRI",
            propertyShapes: [
                {
                    id: "http://gen.example/orientation",
                    order: 1,
                    triples: "gen:orientation a sh:PropertyShape .",
                    origins: [],
                },
            ],
        },
    ],
    derivedPropertyShapes: [],
};

/** Monaco cannot be created in jsdom; this suite is about the view, not the editor. */
const editors = [];

let mounted = null;
let target = null;

function render(props = {}) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ClassConstraintsView, {
        target,
        props: { custom: CUSTOM, generated: GENERATED, ...props },
    });
    flushSync();
    return target;
}

/** A row's toggle, by the accessible name it carries when collapsed. */
function expand(view, label) {
    [...view.querySelectorAll("button")]
        .find(button => button.getAttribute("aria-label") === `Expand ${label}`)
        .click();
    flushSync();
}

function scopeButton(view, label) {
    return [...view.querySelectorAll("button")].find(
        button => button.textContent.trim() === label,
    );
}

vi.mock("$lib/monaco/TurtleEditor.svelte", async () => {
    const { createRawSnippet } = await import("svelte");
    return {
        default: function TurtleEditorStub(anchor, props) {
            editors.push(props);
            return createRawSnippet(() => ({
                render: () => `<div class="turtle-editor-stub"></div>`,
            }))(anchor);
        },
    };
});

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    editors.length = 0;
});

describe("ClassConstraintsView", () => {
    test("shows one row per property, with the rule in words", () => {
        const view = render();
        const text = view.textContent.replace(/\s+/g, " ");

        // The old dialog made you expand Turtle to learn a cardinality.
        expect(text).toContain("Diagram.x1InitialView");
        expect(text).toContain("0..1, xsd:float");
        expect(text).toContain("Diagram.orientation");
        expect(text).toContain("1..1, sh:IRI");
    });

    test("merges the two halves rather than putting them behind a choice", () => {
        const view = render();

        // x1InitialView is stated by both sides; it is one property, so it is one row.
        const rows = [...view.querySelectorAll("li")].map(row =>
            row.textContent.replace(/\s+/g, " "),
        );
        expect(
            rows.filter(row => row.includes("Diagram.x1InitialView")),
        ).toHaveLength(1);
    });

    test("names where each rule comes from", () => {
        const view = render();
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("generated");
        expect(text).toContain("simple.ttl");
    });

    test("a document chip asks to open that document at that line", () => {
        const onopen = vi.fn();
        const view = render({ onopen });

        [...view.querySelectorAll("button")]
            .find(button => button.textContent.trim() === "simple.ttl")
            .click();
        flushSync();

        // Without the document the workbench opens on whatever it opened last.
        expect(onopen).toHaveBeenCalledWith(DOCUMENT_ID, 214);
    });

    test("summarises the whole answer in one line", () => {
        const view = render();

        expect(view.textContent.replace(/\s+/g, " ")).toContain(
            "4 rules on 3 properties · generated + 2 documents",
        );
    });

    test("mounts no editor until a row is opened", () => {
        const view = render();
        expect(editors).toHaveLength(0);

        expand(view, "Diagram.x1InitialView");

        // One per shape of the opened row only — the old view mounted one per shape, always.
        expect(editors).toHaveLength(2);
        expect(editors.every(props => props.readOnly === true)).toBe(true);
    });

    test("the scope buttons narrow the answer to one half", () => {
        const view = render();

        scopeButton(view, "Custom").click();
        flushSync();
        const custom = view.textContent.replace(/\s+/g, " ");

        expect(custom).toContain("Diagram.x1InitialView");
        expect(custom).not.toContain("Diagram.orientation");
    });

    test("the filter matches the property and the rule", () => {
        const view = render();
        const input = view.querySelector(
            "input[aria-label='Filter constraints']",
        );

        input.value = "orientation";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        flushSync();
        expect(view.textContent).not.toContain("x1InitialView");

        input.value = "xsd:float";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        flushSync();
        expect(view.textContent).toContain("x1InitialView");
        expect(view.textContent).not.toContain("Diagram.orientation");
    });

    test("says so once when nothing targets the class", () => {
        const empty = {
            namespaces: "",
            nodeShapes: [],
            propertyShapes: [],
            derivedPropertyShapes: [],
        };
        const view = render({ custom: empty, generated: empty });

        // Four stacked "No … found" lines was the old answer to an empty class.
        expect(view.textContent).toContain("No constraints target this class");
    });

    test("class-level shapes are kept apart from the property rows", () => {
        const view = render();

        expect(view.textContent.replace(/\s+/g, " ")).toContain(
            "On the class (1)",
        );
    });
});
