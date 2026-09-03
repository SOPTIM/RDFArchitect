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
import PropertyShapeCard from "../../src/routes/shacl/workbench/form/PropertyShapeCard.svelte";
import FormEditor from "../../src/routes/shacl/workbench/FormEditor.svelte";

/**
 * Rules the document writes as shapes of their own, which several shapes then share.
 *
 * The tab could read a `-Con-Simple-` profile and change nothing in it, because every constraint in
 * one is a named rule and a named rule was shown read-only. It is editable now, so what these tests
 * pin is the part that has to come with that: the card says how many shapes a change would reach,
 * and a change to a rule more than one shape uses is not sent until the user has answered for it.
 */

const CIM = "http://iec.ch/TC57/CIM100#";
const SHACL = "http://www.w3.org/ns/shacl#";
const EX = "http://example.org/shapes#";

const PREFIXES = { cim: CIM, sh: SHACL, ex: EX };

const TERMS = [
    {
        kind: "PROPERTY",
        iri: `${CIM}IdentifiedObject.name`,
        namespace: CIM,
        localName: "IdentifiedObject.name",
    },
];

let mounted = null;
let target = null;

function render(component, props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(component, { target, props });
    return target;
}

/** A rule the document writes under a name of its own, used by however many shapes. */
function sharedRule(overrides = {}) {
    return {
        iri: `${EX}NameCardinality`,
        sourceIndex: 0,
        path: `${CIM}IdentifiedObject.name`,
        minCount: 1,
        retained: [],
        usedBy: [`${EX}ACLineSegmentShape`, `${EX}TerminalShape`],
        editable: true,
        ...overrides,
    };
}

function shape(overrides = {}) {
    return {
        iri: `${EX}ACLineSegmentShape`,
        targetClasses: [`${CIM}ACLineSegment`],
        properties: [],
        retained: [],
        editable: true,
        ...overrides,
    };
}

function fakeForm(overrides = {}) {
    return {
        shapes: [shape()],
        propertyShapes: [],
        parseError: null,
        loading: false,
        applying: false,
        error: null,
        read: vi.fn(),
        reload: vi.fn(),
        applyShape: vi
            .fn()
            .mockResolvedValue({ turtle: "new turtle", warnings: [] }),
        applyRule: vi
            .fn()
            .mockResolvedValue({ turtle: "new turtle", warnings: [] }),
        scheduleRule: vi.fn(),
        schedule: vi.fn(),
        removeShape: vi
            .fn()
            .mockResolvedValue({ turtle: "new turtle", warnings: [] }),
        describes: () => true,
        ...overrides,
    };
}

function number(view, label) {
    const labels = [...view.querySelectorAll("label")];
    const found = labels.find(element => element.textContent.trim() === label);
    return view.querySelector(`#${CSS.escape(found.getAttribute("for"))}`);
}

function commit(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("input", { bubbles: true }));
    element.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

function choose(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

/** Opens the shared-rules section, which is closed until asked for. */
function openSharedRules(view) {
    [...view.querySelectorAll("button")]
        .find(button => button.textContent.includes("Shared rules"))
        .click();
    flushSync();
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

describe("a rule several shapes share", () => {
    test("says how many shapes a change to it would reach", () => {
        const view = render(PropertyShapeCard, {
            property: sharedRule(),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(view.textContent).toContain("shared rule · used by 2 shapes");
        expect(view.textContent).toContain("ex:NameCardinality");
    });

    test("is editable, where it used to say to edit it in Turtle", () => {
        const property = sharedRule();
        const onchange = vi.fn();
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
            onchange,
        });

        commit(number(view, "Minimum values"), "0");

        expect(property.minCount).toBe(0);
        expect(onchange).toHaveBeenCalled();
        expect(view.textContent).not.toContain("edit in Turtle");
    });

    test("has no remove button on a card it is not written under", () => {
        // Its own card is the rule itself; there is nothing there to remove it from.
        const view = render(PropertyShapeCard, {
            property: sharedRule(),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(
            view.querySelector(
                '[aria-label="Remove this rule from the shape"]',
            ),
        ).toBeNull();
        expect(
            view.querySelector('[aria-label="Remove this rule"]'),
        ).toBeNull();
    });

    test("is removed from the shape, not from the document, under a shape", () => {
        const view = render(PropertyShapeCard, {
            property: sharedRule(),
            terms: TERMS,
            prefixes: PREFIXES,
            onremove: vi.fn(),
        });

        expect(
            view.querySelector(
                '[aria-label="Remove this rule from the shape"]',
            ),
        ).not.toBeNull();
    });
});

describe("a rule the form cannot place", () => {
    const locked = sharedRule({
        iri: null,
        usedBy: [],
        editable: false,
        readOnlyReason:
            "This shape writes two rules the form cannot tell apart",
    });

    test("says why, and does not offer its fields", () => {
        const view = render(PropertyShapeCard, {
            property: locked,
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(view.textContent).toContain("Turtle only");
        expect(view.textContent).toContain("cannot tell apart");
        expect(number(view, "Minimum values").readOnly).toBe(true);
    });

    test("cannot be removed either", () => {
        const view = render(PropertyShapeCard, {
            property: locked,
            terms: TERMS,
            prefixes: PREFIXES,
            onremove: vi.fn(),
        });

        expect(
            view.querySelector('[aria-label="Remove this rule"]'),
        ).toBeNull();
    });
});

describe("using a rule the document already writes", () => {
    test("adds a reference to the shape rather than a rule of its own", () => {
        const subject = shape();
        const onchange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: subject,
            terms: TERMS,
            prefixes: PREFIXES,
            sharedRules: [sharedRule()],
            expanded: true,
            onchange,
        });

        const picker = [...view.querySelectorAll("select")].at(-1);
        choose(picker, `${EX}NameCardinality`);

        expect(subject.properties).toEqual([{ iri: `${EX}NameCardinality` }]);
        expect(onchange).toHaveBeenCalled();
    });

    test("does not offer a rule the shape already uses", () => {
        const view = render(NodeShapeCard, {
            shape: shape({ properties: [sharedRule()] }),
            terms: TERMS,
            prefixes: PREFIXES,
            sharedRules: [sharedRule()],
            expanded: true,
        });

        expect(view.textContent).not.toContain("pick a shared rule");
    });
});

describe("where a change to a rule is sent", () => {
    test("a shared rule is written back through itself, not through the shape", () => {
        const rule = sharedRule();
        const onchange = vi.fn();
        const onrulechange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: shape({ properties: [rule] }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
            onrulechange,
        });

        commit(number(view, "Minimum values"), "0");

        expect(onrulechange).toHaveBeenCalledWith(rule);
        expect(onchange).not.toHaveBeenCalled();
    });

    test("a rule written inside the shape still goes with the shape", () => {
        const onchange = vi.fn();
        const onrulechange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: shape({
                properties: [
                    {
                        sourceIndex: 0,
                        path: `${CIM}IdentifiedObject.name`,
                        retained: [],
                        usedBy: [],
                        editable: true,
                    },
                ],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
            onrulechange,
        });

        commit(number(view, "Minimum values"), "0");

        expect(onchange).toHaveBeenCalled();
        expect(onrulechange).not.toHaveBeenCalled();
    });
});

describe("the form editor's shared rules", () => {
    test("counts the document's own rules without rendering them", () => {
        // A -Con-Simple- profile holds some five hundred, each a dozen controls.
        const view = render(FormEditor, {
            form: fakeForm({ propertyShapes: [sharedRule()] }),
            turtle: "",
            terms: TERMS,
        });

        expect(view.textContent).toContain("Shared rules (1)");
        expect(view.textContent).not.toContain("used by 2 shapes");
    });

    test("lists them once the section is opened", () => {
        const view = render(FormEditor, {
            form: fakeForm({ propertyShapes: [sharedRule()] }),
            turtle: "",
            terms: TERMS,
        });

        openSharedRules(view);

        expect(view.textContent).toContain("used by 2 shapes");
    });

    test("sends a change to a rule only one shape uses straight away", async () => {
        const rule = sharedRule({ usedBy: [`${EX}TerminalShape`] });
        const form = fakeForm({ propertyShapes: [rule] });
        const view = render(FormEditor, { form, turtle: "ttl", terms: TERMS });

        openSharedRules(view);
        commit(number(view, "Minimum values"), "0");
        await Promise.resolve();

        expect(form.applyRule).toHaveBeenCalledWith("ttl", rule);
    });

    test("asks before sending a change to a rule several shapes use", async () => {
        const form = fakeForm({ propertyShapes: [sharedRule()] });
        const view = render(FormEditor, { form, turtle: "ttl", terms: TERMS });

        openSharedRules(view);
        commit(number(view, "Minimum values"), "0");
        await Promise.resolve();

        // Nothing is written until the question of how far the change should reach is answered.
        expect(form.applyRule).not.toHaveBeenCalled();
    });

    test("does not send a shared rule's typed fields on every keystroke", () => {
        const form = fakeForm({ propertyShapes: [sharedRule()] });
        const view = render(FormEditor, { form, turtle: "ttl", terms: TERMS });

        openSharedRules(view);
        const field = number(view, "Minimum values");
        field.value = "0";
        field.dispatchEvent(new Event("input", { bubbles: true }));
        flushSync();

        // A dialog per keystroke would be unusable; the change waits for the field to be left.
        expect(form.scheduleRule).not.toHaveBeenCalled();
        expect(form.applyRule).not.toHaveBeenCalled();
    });
});
