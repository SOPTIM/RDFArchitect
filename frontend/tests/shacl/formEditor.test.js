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

import NodeShapeCard from "../../src/routes/shacl/workbench/form/NodeShapeCard.svelte";
import FormEditor from "../../src/routes/shacl/workbench/FormEditor.svelte";

const CIM = "http://iec.ch/TC57/CIM100#";
const PREFIXES = {
    cim: CIM,
    sh: "http://www.w3.org/ns/shacl#",
    ex: "http://example.org/shapes#",
};

/** The buffer the form reads its prefixes from — abbreviation follows the document, not a list. */
const TURTLE = `@prefix sh:  <http://www.w3.org/ns/shacl#> .
@prefix cim: <${CIM}> .
@prefix ex:  <http://example.org/shapes#> .

ex:ACLineSegmentShape a sh:NodeShape .
`;

const TERMS = [
    {
        kind: "CLASS",
        iri: `${CIM}ACLineSegment`,
        namespace: CIM,
        localName: "ACLineSegment",
    },
    {
        kind: "PROPERTY",
        iri: `${CIM}ACLineSegment.r`,
        namespace: CIM,
        localName: "ACLineSegment.r",
        domain: `${CIM}ACLineSegment`,
    },
    {
        kind: "PROPERTY",
        iri: `${CIM}Terminal.sequenceNumber`,
        namespace: CIM,
        localName: "Terminal.sequenceNumber",
        domain: `${CIM}Terminal`,
    },
];

let mounted = null;
let target = null;

function shape(overrides = {}) {
    return {
        iri: "http://example.org/shapes#ACLineSegmentShape",
        targetClasses: [`${CIM}ACLineSegment`],
        properties: [
            { path: `${CIM}ACLineSegment.r`, minCount: 1, sourceIndex: 0 },
        ],
        retained: [],
        editable: true,
        ...overrides,
    };
}

/** Only what the components read; no requests are made. */
function fakeForm(overrides = {}) {
    return {
        shapes: [shape()],
        parseError: null,
        loading: false,
        applying: false,
        error: null,
        read: vi.fn(),
        applyShape: vi
            .fn()
            .mockResolvedValue({ turtle: "new turtle", warnings: [] }),
        removeShape: vi
            .fn()
            .mockResolvedValue({ turtle: "new turtle", warnings: [] }),
        describes: () => true,
        ...overrides,
    };
}

/** Presses the card's "Add a rule" button. */
function addRule(view) {
    [...view.querySelectorAll("button")]
        .find(button => button.textContent.includes("Add a rule"))
        .click();
    flushSync();
}

function render(component, props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(component, { target, props });
    return target;
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("FormEditor", () => {
    test("lists the document's shapes with the class each applies to", () => {
        const view = render(FormEditor, {
            form: fakeForm(),
            turtle: TURTLE,
            terms: TERMS,
        });

        expect(view.textContent).toContain("ex:ACLineSegmentShape");
        expect(view.textContent.replace(/\s+/g, " ")).toContain(
            "applies to cim:ACLineSegment",
        );
        expect(view.textContent).toContain("1 rule");
    });

    test("falls back to full IRIs when the document binds no prefix for them", () => {
        // The form abbreviates using the open document's own @prefix lines, nothing else.
        const view = render(FormEditor, {
            form: fakeForm(),
            turtle: "",
            terms: TERMS,
        });

        expect(view.textContent).toContain(
            "<http://example.org/shapes#ACLineSegmentShape>",
        );
    });

    test("explains a syntax error instead of showing an empty form", () => {
        const view = render(FormEditor, {
            form: fakeForm({
                shapes: [],
                parseError: {
                    message: "Undefined prefix: ex",
                    line: 3,
                    column: 1,
                },
            }),
            turtle: "",
            terms: TERMS,
        });

        expect(view.textContent).toContain("Undefined prefix: ex");
        expect(view.textContent).toContain("line 3, column 1");
        expect(view.textContent).toContain("Fix it in the Turtle view");
    });

    test("offers to add the first shape when there are none", () => {
        const view = render(FormEditor, {
            form: fakeForm({ shapes: [] }),
            turtle: "",
            terms: TERMS,
        });

        expect(view.textContent).toContain("No shapes yet");
    });

    test("puts the edited document back into the buffer", async () => {
        const form = fakeForm();
        const onturtle = vi.fn();
        const view = render(FormEditor, {
            form,
            turtle: "original",
            terms: TERMS,
            onturtle,
        });

        [...view.querySelectorAll("button")]
            .find(button => button.textContent.includes("Add shape"))
            .click();
        await Promise.resolve();
        await Promise.resolve();

        expect(form.applyShape).toHaveBeenCalled();
        expect(onturtle).toHaveBeenCalledWith("new turtle");
    });
});

describe("NodeShapeCard", () => {
    test("shows a shape the form cannot edit as read-only", () => {
        const view = render(NodeShapeCard, {
            shape: shape({
                editable: false,
                readOnlyReason:
                    "This shape is written as 2 separate statements.",
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        expect(view.textContent).toContain("Turtle only");
        expect(view.textContent).toContain("2 separate statements");
        // Nothing that would write the shape back may be offered.
        expect(
            [...view.querySelectorAll("button")].some(button =>
                button.textContent.includes("Add a rule"),
            ),
        ).toBe(false);
        expect(
            view.querySelector('[aria-label="Delete this shape"]'),
        ).toBeNull();
    });

    test("every class the shape targets gets a picker of its own", () => {
        // 462 shapes in the official library target two classes in one clause, and holding one of
        // them is what made all 462 read-only.
        const view = render(NodeShapeCard, {
            // No rules, so the only class pickers on screen are the shape's own targets: a
            // rule's "Value class" picker carries the same placeholder.
            shape: shape({
                properties: [],
                targetClasses: [`${CIM}ACLineSegment`, `${CIM}Conductor`],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        expect(view.textContent).toContain(
            "applies to cim:ACLineSegment, cim:Conductor",
        );
        const pickers = [...view.querySelectorAll("input[list]")].filter(
            input => input.placeholder === "pick a class",
        );
        expect(pickers.map(input => input.value)).toEqual([
            "cim:ACLineSegment",
            "cim:Conductor",
        ]);
    });

    test("another class can be added to the ones already there", () => {
        const model = shape({ properties: [] });
        const onchange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        [...view.querySelectorAll("button")]
            .find(button => button.textContent.includes("another class"))
            .click();
        flushSync();

        const added = [...view.querySelectorAll("input[list]")].findLast(
            input => input.placeholder === "pick a class",
        );
        added.value = "cim:Terminal";
        added.dispatchEvent(new Event("input", { bubbles: true }));
        added.dispatchEvent(new Event("change", { bubbles: true }));
        flushSync();

        expect(model.targetClasses).toEqual([
            `${CIM}ACLineSegment`,
            `${CIM}Terminal`,
        ]);
        expect(onchange).toHaveBeenCalled();
    });

    test("clearing a class picker drops that class and keeps the others", () => {
        const model = shape({
            properties: [],
            targetClasses: [`${CIM}ACLineSegment`, `${CIM}Terminal`],
        });
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        const first = [...view.querySelectorAll("input[list]")].find(
            input => input.placeholder === "pick a class",
        );
        first.value = "";
        first.dispatchEvent(new Event("input", { bubbles: true }));
        first.dispatchEvent(new Event("change", { bubbles: true }));
        flushSync();

        expect(model.targetClasses).toEqual([`${CIM}Terminal`]);
    });

    test("a target the form keeps as written is shown instead of a picker", () => {
        const view = render(NodeShapeCard, {
            shape: shape({
                properties: [],
                retained: [
                    {
                        predicate: "http://www.w3.org/ns/shacl#targetClass",
                        value: "cim:ACLineSegment",
                        field: "targetClasses",
                        reason: "The document states this 2 times.",
                    },
                ],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        expect(
            [...view.querySelectorAll("input[list]")].some(
                input => input.placeholder === "pick a class",
            ),
        ).toBe(false);
        expect(view.textContent).toContain("cim:ACLineSegment");
    });

    test("an editable shape offers its rules and a way to add one", () => {
        const view = render(NodeShapeCard, {
            shape: shape(),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        expect(view.textContent).toContain("cim:ACLineSegment.r");
        expect(
            [...view.querySelectorAll("button")].some(button =>
                button.textContent.includes("Add a rule"),
            ),
        ).toBe(true);
    });

    test("adding a rule shows an empty one without writing anything yet", () => {
        // A rule with no property says nothing, so there is nothing to write: it used to be sent
        // at once, dropped by the writer, and gone from the card on the next read.
        const onchange = vi.fn();
        const model = shape();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        addRule(view);

        expect(view.querySelectorAll("select")).toHaveLength(6);
        expect(model.properties).toHaveLength(1);
        expect(onchange).not.toHaveBeenCalled();
    });

    test("the rule joins the shape once it names a property", () => {
        const onchange = vi.fn();
        const model = shape();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        addRule(view);
        // The draft's own property picker, which is the last of them on the card.
        const picker = [
            ...view.querySelectorAll('input[placeholder="pick a property"]'),
        ].at(-1);
        picker.value = "cim:Terminal.sequenceNumber";
        // `input` is what the binding listens to; `change` is what commits the pick, and both is
        // what a browser sends whether the term was typed or taken from the suggestions.
        picker.dispatchEvent(new Event("input", { bubbles: true }));
        picker.dispatchEvent(new Event("change", { bubbles: true }));
        flushSync();

        expect(model.properties).toHaveLength(2);
        expect(model.properties.at(-1).path).toBe(
            `${CIM}Terminal.sequenceNumber`,
        );
        expect(onchange).toHaveBeenCalled();
        // The draft is gone from the card, rather than standing next to the rule it became.
        expect(
            view.querySelectorAll('input[placeholder="pick a property"]'),
        ).toHaveLength(1);
    });

    test("an empty rule can be dropped again without writing anything", () => {
        const onchange = vi.fn();
        const model = shape();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        addRule(view);
        [...view.querySelectorAll("button")]
            .filter(
                button =>
                    button.getAttribute("aria-label") === "Remove this rule",
            )
            .at(-1)
            .click();
        flushSync();

        expect(view.querySelectorAll("select")).toHaveLength(3);
        expect(model.properties).toHaveLength(1);
        expect(onchange).not.toHaveBeenCalled();
    });

    test("collapsed, it summarises without showing the fields", () => {
        const view = render(NodeShapeCard, {
            shape: shape(),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: false,
        });

        expect(view.textContent).toContain("ACLineSegmentShape");
        expect(view.textContent).not.toContain("Applies to class");
    });
});
