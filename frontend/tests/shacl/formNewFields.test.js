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

/**
 * The fields the model carried and the card never showed, and the ones neither had.
 *
 * A shape's own name and message, the three targets that are not a class, value ranges, lengths,
 * patterns, `sh:in`. Sixteen fields on a rule is more than a flat card can carry, so what is also
 * pinned here is the grouping: the two groups nothing uses stay shut, and the fields that have
 * been on the card since it shipped stay where they were.
 */

const CIM = "http://iec.ch/TC57/CIM100#";
const SHACL = "http://www.w3.org/ns/shacl#";
const EX = "http://example.org/shapes#";

const PREFIXES = { cim: CIM, sh: SHACL, ex: EX };

const TERMS = [
    {
        kind: "CLASS",
        iri: `${CIM}ACLineSegment`,
        namespace: CIM,
        localName: "ACLineSegment",
    },
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
    // The controls copy their value in from a prop in an effect, so nothing is on screen the way
    // it will be until effects have run.
    flushSync();
    return target;
}

function rule(overrides = {}) {
    return {
        sourceIndex: 0,
        path: `${CIM}ACLineSegment.r`,
        retained: [],
        usedBy: [],
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

function field(view, label) {
    const found = [...view.querySelectorAll("label")].find(
        element => element.textContent.trim() === label,
    );
    return found
        ? view.querySelector(`#${CSS.escape(found.getAttribute("for"))}`)
        : null;
}

/** Makes sure a group is on screen. A group that holds something is open already. */
function open(view, title) {
    const toggle = [...view.querySelectorAll("button")].find(
        button => button.getAttribute("aria-label") === title,
    );
    if (toggle.getAttribute("aria-expanded") !== "true") {
        toggle.click();
        flushSync();
    }
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

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("the groups a rule's fields are in", () => {
    test("the ones a rule usually says nothing about start shut", () => {
        const view = render(PropertyShapeCard, {
            property: rule(),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(view.textContent).toContain("Between which values");
        expect(field(view, "At least")).toBeNull();
        expect(field(view, "Matching")).toBeNull();
    });

    test("a group that holds something opens itself", () => {
        const view = render(PropertyShapeCard, {
            property: rule({ minInclusive: "0.0" }),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(field(view, "At least").value).toBe("0.0");
    });

    test("the fields that were always on the card still are", () => {
        // Grouping is meant to make sixteen fields legible, not to hide the eight that shipped.
        const view = render(PropertyShapeCard, {
            property: rule(),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(field(view, "Minimum values")).not.toBeNull();
        expect(field(view, "Maximum values")).not.toBeNull();
        expect(
            field(view, "Message shown when the rule is broken"),
        ).not.toBeNull();
    });
});

describe("a value range", () => {
    test("is written as the text it was typed as, not as a number", () => {
        // "0.0" and "0" are the same number and different documents; the writer puts these digits
        // back inside the literal the document already holds.
        const property = rule({ minInclusive: "0.0" });
        const onchange = vi.fn();
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
            onchange,
        });

        commit(field(view, "At least"), "1.5");

        expect(property.minInclusive).toBe("1.5");
        expect(onchange).toHaveBeenCalled();
    });

    test("is cleared rather than set to zero when emptied", () => {
        const property = rule({ maxExclusive: "100.0" });
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
        });

        commit(field(view, "Less than"), "");

        expect(property.maxExclusive).toBeNull();
    });

    test("one the form cannot write is shown as the document writes it", () => {
        const view = render(PropertyShapeCard, {
            property: rule({
                retained: [
                    {
                        predicate: `${SHACL}minInclusive`,
                        value: '"2020-01-01"^^xsd:date',
                        field: "minInclusive",
                        reason: "The form cannot write this value back unchanged.",
                    },
                ],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        expect(field(view, "At least")).toBeNull();
        expect(view.textContent).toContain('"2020-01-01"^^xsd:date');
    });
});

describe("the text rules on a rule", () => {
    test("lengths, a pattern and its flags are written", () => {
        const property = rule({ minLength: 2 });
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
        });

        commit(field(view, "Longest"), "64");
        commit(field(view, "Matching"), "^[A-Z]");
        commit(field(view, "Match flags"), "i");

        expect(property.maxLength).toBe(64);
        expect(property.pattern).toBe("^[A-Z]");
        expect(property.flags).toBe("i");
    });
});

describe("the list of values a rule allows", () => {
    test("shows each one through the document's prefixes", () => {
        const view = render(PropertyShapeCard, {
            property: rule({ allowedValues: [`${CIM}Kind.a`, "kV"] }),
            terms: TERMS,
            prefixes: PREFIXES,
        });

        const rows = [...view.querySelectorAll("input")].map(
            input => input.value,
        );
        expect(rows).toContain("cim:Kind.a");
        expect(rows).toContain("kV");
    });

    test("a prefixed name typed into it goes back as the term it names", () => {
        // Without this, leaving the box would turn the IRI into the plain string "cim:Kind.a".
        const property = rule({ allowedValues: [`${CIM}Kind.a`] });
        const onchange = vi.fn();
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
            onchange,
        });

        const row = [...view.querySelectorAll("input")].find(
            input => input.value === "cim:Kind.a",
        );
        commit(row, "cim:Kind.b");

        expect(property.allowedValues).toEqual([`${CIM}Kind.b`]);
        expect(onchange).toHaveBeenCalled();
    });

    test("an empty row is not sent until something is typed into it", () => {
        const property = rule({ allowedValues: [] });
        const onchange = vi.fn();
        const view = render(PropertyShapeCard, {
            property,
            terms: TERMS,
            prefixes: PREFIXES,
            onchange,
        });

        [...view.querySelectorAll("button")]
            .find(button => button.textContent.includes("add a value"))
            .click();
        flushSync();

        expect(onchange).not.toHaveBeenCalled();
        expect(property.allowedValues).toEqual([]);
    });
});

describe("what a shape applies to", () => {
    test("every kind of target is a row saying which kind it is", () => {
        const view = render(NodeShapeCard, {
            shape: shape({
                targetSubjectsOf: [`${CIM}IdentifiedObject.name`],
                targetNodes: [`${EX}TheOne`],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        const kinds = [...view.querySelectorAll("select")]
            .filter(select => select.value.startsWith("target"))
            .map(select => select.value);
        expect(kinds).toEqual([
            "targetClasses",
            "targetSubjectsOf",
            "targetNodes",
        ]);
    });

    test("changing a target's kind moves it rather than adding one", () => {
        const model = shape();
        const onchange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        const kind = [...view.querySelectorAll("select")].find(select =>
            select.value.startsWith("target"),
        );
        choose(kind, "targetObjectsOf");

        expect(model.targetClasses).toEqual([]);
        expect(model.targetObjectsOf).toEqual([`${CIM}ACLineSegment`]);
        expect(onchange).toHaveBeenCalled();
    });

    test("a target the form keeps as written is shown in its own right", () => {
        const view = render(NodeShapeCard, {
            shape: shape({
                targetClasses: [],
                retained: [
                    {
                        predicate: `${SHACL}targetNode`,
                        value: '"a literal"',
                        field: "targetNodes",
                        reason: "The form cannot write this value back unchanged.",
                    },
                ],
            }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        expect(view.textContent).toContain('"a literal"');
        expect(view.textContent).toContain("the resource");
    });
});

describe("a shape's own words", () => {
    test("are written and applied", () => {
        const model = shape();
        const onchange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        open(view, "This shape's own words");
        commit(field(view, "Name"), "Line segment");

        expect(model.name).toBe("Line segment");
        expect(onchange).toHaveBeenCalled();
    });

    test("the properties a closed shape ignores are asked for only once it is closed", () => {
        // "Except these properties" means nothing on a shape that allows everything, and an empty
        // list editor above the rules would be one more thing to read past.
        const open2 = render(NodeShapeCard, {
            shape: shape({ closed: true }),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });
        open(open2, "This shape's own words");
        expect(open2.textContent).toContain("Except these properties");
    });

    test("a shape that allows everything is not asked what it ignores", () => {
        const view = render(NodeShapeCard, {
            shape: shape(),
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
        });

        open(view, "This shape's own words");

        expect(view.textContent).not.toContain("Except these properties");
    });

    test("closing a shape is written to it", () => {
        const model = shape();
        const onchange = vi.fn();
        const view = render(NodeShapeCard, {
            shape: model,
            terms: TERMS,
            prefixes: PREFIXES,
            expanded: true,
            onchange,
        });

        open(view, "This shape's own words");
        const closed = [...view.querySelectorAll("input[type=checkbox]")].at(
            -1,
        );
        closed.checked = true;
        closed.dispatchEvent(new Event("change", { bubbles: true }));
        flushSync();

        expect(model.closed).toBe(true);
        expect(onchange).toHaveBeenCalled();
    });
});
