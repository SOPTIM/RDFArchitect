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

import { matchingShapes } from "$lib/shacl/formNavigation.js";
import { ruleProblems } from "$lib/shacl/ruleValidation.js";

import NodeShapeCard from "../../src/routes/shacl/workbench/form/NodeShapeCard.svelte";
import PropertyShapeCard from "../../src/routes/shacl/workbench/form/PropertyShapeCard.svelte";
import SharedRuleDialog from "../../src/routes/shacl/workbench/form/SharedRuleDialog.svelte";

/**
 * What the form checks before it writes, and how it writes what it was given.
 *
 * Each of these used to reach the document looking right and meaning something else: a bound
 * typed as `2.0` became `2`, a minimum above its maximum was written as it stood, and an enum value
 * typed into "One of" became a string no data could ever equal.
 */

const CIM = "http://iec.ch/TC57/CIM100#";
const XSD = "http://www.w3.org/2001/XMLSchema#";
const EX = "http://example.org/shapes#";
const PREFIXES = { cim: CIM, xsd: XSD, ex: EX };

const TERMS = [
    {
        kind: "ENUM_MEMBER",
        iri: `${CIM}WindingConnection.D`,
        namespace: CIM,
        localName: "WindingConnection.D",
    },
    {
        kind: "ENUM_MEMBER",
        iri: `${CIM}WindingConnection.Y`,
        namespace: CIM,
        localName: "WindingConnection.Y",
    },
    {
        kind: "ENUM_MEMBER",
        iri: `${CIM}PhaseCode.A`,
        namespace: CIM,
        localName: "PhaseCode.A",
    },
];

let mounted = null;
let target = null;

function render(component, props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(component, { target, props });
    flushSync();
    return target;
}

function rule(overrides = {}) {
    return {
        sourceIndex: 0,
        path: `${CIM}PowerTransformerEnd.connectionKind`,
        retained: [],
        usedBy: [],
        editable: true,
        ...overrides,
    };
}

function card(overrides = {}) {
    const property = rule(overrides);
    const onchange = vi.fn();
    const onedit = vi.fn();
    render(PropertyShapeCard, {
        property,
        terms: TERMS,
        prefixes: PREFIXES,
        onchange,
        onedit,
    });
    return { property, onchange, onedit };
}

function field(label) {
    const found = [...target.querySelectorAll("label")].find(
        element => element.textContent.trim() === label,
    );
    return found
        ? target.querySelector(`#${CSS.escape(found.getAttribute("for"))}`)
        : null;
}

function type(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

function commit(element, value) {
    type(element, value);
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

describe("a bound of a value range", () => {
    test("keeps the digits as they were typed", () => {
        const { property, onchange } = card({ minInclusive: "0.0" });

        commit(field("At least"), "2.50");

        expect(property.minInclusive).toBe("2.50");
        expect(onchange).toHaveBeenCalled();
    });

    test("halfway through a number, is neither cleared nor sent", () => {
        const { property, onedit } = card({ minInclusive: "0.0" });

        type(field("At least"), "1.");

        expect(property.minInclusive).toBe("0.0");
        expect(onedit).not.toHaveBeenCalled();
        expect(field("At least").getAttribute("aria-invalid")).toBe("true");
    });

    test("is typed as text, so the browser does not respell it", () => {
        card({ minInclusive: "0.0" });

        expect(field("At least").type).toBe("text");
        expect(field("At least").getAttribute("inputmode")).toBe("decimal");
    });
});

describe("a count", () => {
    test("is a whole number", () => {
        const { property, onchange } = card();

        commit(field("Minimum values"), "1.5");

        expect(property.minCount).toBeUndefined();
        expect(onchange).not.toHaveBeenCalled();
        expect(target.textContent).toContain("A whole number, 0 or more.");
    });

    test("is not sent while it exceeds the maximum", () => {
        const { property, onchange } = card({ maxCount: 1 });

        commit(field("Minimum values"), "3");

        expect(property.minCount).toBe(3);
        expect(onchange).not.toHaveBeenCalled();
        expect(target.textContent).toContain("Less than the minimum.");
    });
});

describe("checking a rule", () => {
    test("finds bounds the wrong way round, numerically", () => {
        expect(
            ruleProblems({ minInclusive: "10", maxInclusive: "9.5" }),
        ).toEqual({ maxInclusive: "Below the lowest value allowed." });
        expect(ruleProblems({ minInclusive: "9", maxInclusive: "10" })).toEqual(
            {},
        );
        expect(ruleProblems({ minLength: 2 })).toEqual({});
    });

    test("finds a pattern that is not a regular expression", () => {
        expect(ruleProblems({ pattern: "[a-z" }).pattern).toMatch(
            /Not a regular expression/,
        );
        expect(ruleProblems({ pattern: "^[A-Z]{2}$" })).toEqual({});
    });

    test("finds flags XPath does not have", () => {
        expect(ruleProblems({ flags: "g" }).flags).toBeDefined();
        expect(ruleProblems({ flags: "iq" })).toEqual({});
    });
});

describe("the value type", () => {
    function valueType() {
        return field("Value type");
    }

    test("offers the datatypes the official profiles use", () => {
        card();

        const offered = [...valueType().options].map(option => option.value);
        for (const name of [
            "duration",
            "anyURI",
            "gMonthDay",
            "dateTimeStamp",
            "time",
        ]) {
            expect(offered).toContain(`${XSD}${name}`);
        }
        expect(offered).toContain(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString",
        );
    });

    test("shows a datatype it does not list rather than an empty box", () => {
        card({ dataType: `${XSD}NOTATION` });

        const select = valueType();
        expect(select.options[select.selectedIndex].textContent.trim()).toBe(
            "xsd:NOTATION",
        );
    });

    test("and the other selects are labelled", () => {
        card();

        expect(field("Value form")?.tagName).toBe("SELECT");
        expect(field("Severity")?.tagName).toBe("SELECT");
    });
});

describe("a value a rule allows", () => {
    test("typed as an enum value's local name, is written as that value", () => {
        const { property } = card();

        commit(field("Must be exactly"), "WindingConnection.D");

        expect(property.hasValue).toBe(`${CIM}WindingConnection.D`);
    });

    test("with a prefix the document does not bind, says it will be a string", () => {
        card({ hasValue: "foo:Kind.b" });

        expect(target.textContent).toContain(
            '"foo:" is not a prefix this document binds',
        );
    });

    test("under a datatype other than string, says a string never matches", () => {
        card({ hasValue: "1", dataType: `${XSD}integer` });

        expect(target.textContent).toContain(
            "never equals a xsd:integer value",
        );
    });

    test("offers the rule's own enumeration first", () => {
        card({ classIri: `${CIM}PhaseCode` });

        const input = field("Must be exactly");
        input.dispatchEvent(new FocusEvent("focusin", { bubbles: true }));
        flushSync();
        const offered = [
            ...target.querySelectorAll(
                `#${CSS.escape(input.getAttribute("list"))} option`,
            ),
        ].map(option => option.value);

        expect(offered[0]).toBe("cim:PhaseCode.A");
    });

    test("lists no suggestions until the box is used", () => {
        card();

        expect(target.querySelectorAll("datalist option")).toHaveLength(0);
    });
});

describe("a shape's card", () => {
    function shape(overrides = {}) {
        return {
            iri: `${EX}BreakerShape`,
            targetClasses: [],
            properties: [],
            retained: [],
            editable: true,
            ...overrides,
        };
    }

    test("says whether it is open", () => {
        render(NodeShapeCard, { shape: shape(), expanded: true });

        expect(
            target
                .querySelector("[aria-expanded]")
                .getAttribute("aria-expanded"),
        ).toBe("true");
    });

    test("asks before deleting the whole shape", () => {
        const onremove = vi.fn();
        render(NodeShapeCard, { shape: shape(), onremove });

        target.querySelector('[aria-label="Delete this shape"]').click();
        flushSync();
        expect(onremove).not.toHaveBeenCalled();

        [...target.querySelectorAll("button")]
            .find(button => button.textContent.trim() === "Delete")
            .click();
        expect(onremove).toHaveBeenCalled();
    });
});

describe("a shape just added", () => {
    test("is listed whatever the filter says", () => {
        const shapes = [{ iri: `${EX}BreakerShape` }, { iri: `${EX}NewShape` }];

        expect(
            matchingShapes(shapes, {
                filter: "Breaker",
                pinned: new Set([`${EX}NewShape`]),
            }).map(shape => shape.iri),
        ).toEqual([`${EX}BreakerShape`, `${EX}NewShape`]);
    });
});

describe("the question about a shared rule", () => {
    function dialog() {
        return document.querySelector("[role='dialog']");
    }

    function button(text) {
        return [...dialog().querySelectorAll("button")].find(element =>
            element.textContent.trim().startsWith(text),
        );
    }

    async function open(props) {
        const state = { open: true };
        render(SharedRuleDialog, {
            get showDialog() {
                return state.open;
            },
            set showDialog(value) {
                state.open = value;
            },
            rule: rule({
                iri: `${EX}NameCardinality`,
                usedBy: [`${EX}BreakerShape`, `${EX}SwitchShape`],
            }),
            shapeIri: `${EX}BreakerShape`,
            prefixes: PREFIXES,
            ...props,
        });
        await vi.waitFor(() => expect(dialog()).not.toBeNull());
        return state;
    }

    test("suggests the copy's name the way the document writes names", async () => {
        await open();

        expect(dialog().querySelector("input").value).toBe(
            "ex:BreakerNameCardinality",
        );
    });

    test("closes once the rule is changed for everybody", async () => {
        const onall = vi.fn();
        const state = await open({ onall });

        button("Change it for all").click();

        expect(onall).toHaveBeenCalledTimes(1);
        expect(state.open).toBe(false);
    });

    test("stays open and says why when the copy's name is refused", async () => {
        const onsplit = vi
            .fn()
            .mockResolvedValue("The document already has a shape named that.");
        const state = await open({ onsplit });

        button("Give this shape its own copy").click();
        await vi.waitFor(() =>
            expect(dialog().textContent).toContain(
                "The document already has a shape named that.",
            ),
        );

        expect(onsplit).toHaveBeenCalledWith("ex:BreakerNameCardinality");
        expect(state.open).toBe(true);
    });

    test("closes once the copy is made", async () => {
        const state = await open({ onsplit: vi.fn().mockResolvedValue(null) });

        button("Give this shape its own copy").click();

        await vi.waitFor(() => expect(state.open).toBe(false));
    });
});
