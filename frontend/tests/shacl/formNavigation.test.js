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

import {
    entryAtLine,
    matchingRules,
    matchingShapes,
} from "$lib/shacl/formNavigation.js";

import FormEditor from "../../src/routes/shacl/workbench/FormEditor.svelte";

/**
 * Getting to one shape in a document that holds a hundred and forty-five.
 *
 * The filter is the tab's answer to a file nobody scrolls, and `entryAtLine` is both halves of the
 * link between the two views: a card knows the line it came from, and a line has to find its card.
 */

const CIM = "http://iec.ch/TC57/CIM100#";
const EX = "http://example.org/shapes#";
const PREFIXES = { cim: CIM, ex: EX };

const SHAPES = [
    {
        iri: `${EX}ACLineSegmentShape`,
        line: 6,
        targetClasses: [`${CIM}ACLineSegment`],
        editable: true,
        properties: [
            { path: `${CIM}ACLineSegment.r`, editable: true, line: 8 },
            { message: "The resistance is required", editable: true, line: 9 },
        ],
    },
    {
        iri: `${EX}TerminalShape`,
        line: 20,
        targetClasses: [`${CIM}Terminal`],
        editable: false,
        readOnlyReason: "written as two statements",
        properties: [],
    },
    {
        iri: `${EX}BayShape`,
        line: 30,
        targetClasses: [`${CIM}Bay`],
        editable: true,
        properties: [{ path: `${CIM}Bay.name`, editable: false }],
    },
];

const RULES = [{ iri: `${EX}NameCardinality`, line: 25, editable: true }];

/** Only the prefixes matter: the form abbreviates through the document it is showing. */
const TURTLE = `@prefix cim: <${CIM}> .\n@prefix ex: <${EX}> .\n`;

// -----------------------------------------------------------------------------
// The same, through the form itself
// -----------------------------------------------------------------------------

let mounted = null;
let target = null;

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(FormEditor, { target, props });
    flushSync();
    return target;
}

function fakeForm(overrides = {}) {
    return {
        shapes: SHAPES,
        propertyShapes: RULES,
        parseError: null,
        loading: false,
        applying: false,
        error: null,
        expanded: new Set(),
        toggle: vi.fn(),
        filter: "",
        lockedOnly: false,
        focusLine: null,
        read: vi.fn(),
        reload: vi.fn(),
        applyShape: vi.fn(),
        applyRule: vi.fn(),
        schedule: vi.fn(),
        scheduleRule: vi.fn(),
        removeShape: vi.fn(),
        describes: () => true,
        ...overrides,
    };
}

function cards(view) {
    return [...view.querySelectorAll("[data-shape]")].map(card =>
        card.getAttribute("data-shape"),
    );
}

describe("filtering the shapes", () => {
    test("keeps everything when nothing is typed", () => {
        expect(matchingShapes(SHAPES, {}, PREFIXES)).toHaveLength(3);
    });

    test("matches the class a shape applies to, written the way the card writes it", () => {
        const found = matchingShapes(SHAPES, { filter: "cim:Bay" }, PREFIXES);

        expect(found).toHaveLength(1);
        expect(found[0].iri).toBe(`${EX}BayShape`);
    });

    test("matches a full IRI too, which is what the Turtle view puts on the clipboard", () => {
        const found = matchingShapes(
            SHAPES,
            { filter: `${CIM}Terminal` },
            PREFIXES,
        );

        expect(found).toHaveLength(1);
        expect(found[0].iri).toBe(`${EX}TerminalShape`);
    });

    test("finds a shape by a property one of its rules is about", () => {
        // How a shape is usually looked for: something went wrong on a property, not on a shape.
        const found = matchingShapes(
            SHAPES,
            { filter: "ACLineSegment.r" },
            PREFIXES,
        );

        expect(found).toHaveLength(1);
        expect(found[0].iri).toBe(`${EX}ACLineSegmentShape`);
    });

    test("finds a shape by the message one of its rules reports", () => {
        const found = matchingShapes(
            SHAPES,
            { filter: "resistance is required" },
            PREFIXES,
        );

        expect(found).toHaveLength(1);
    });

    test("ignores case and surrounding space", () => {
        expect(
            matchingShapes(SHAPES, { filter: "  BAYSHAPE " }, PREFIXES),
        ).toHaveLength(1);
    });

    test("locked only keeps a shape the form will not write", () => {
        const found = matchingShapes(SHAPES, { lockedOnly: true }, PREFIXES);

        expect(found.map(shape => shape.iri)).toEqual([
            `${EX}TerminalShape`,
            // Editable itself, but holding a rule that is not — which is what the reader is after.
            `${EX}BayShape`,
        ]);
    });

    test("locked only and a filter both apply", () => {
        expect(
            matchingShapes(
                SHAPES,
                { filter: "cim:Bay", lockedOnly: true },
                PREFIXES,
            ),
        ).toHaveLength(1);
        expect(
            matchingShapes(
                SHAPES,
                { filter: "ACLineSegment.r", lockedOnly: true },
                PREFIXES,
            ),
        ).toHaveLength(0);
    });

    test("a shared rule is filtered on what it is called and what it is about", () => {
        expect(
            matchingRules(RULES, { filter: "NameCard" }, PREFIXES),
        ).toHaveLength(1);
        expect(
            matchingRules(RULES, { filter: "nothing" }, PREFIXES),
        ).toHaveLength(0);
        expect(matchingRules(RULES, { lockedOnly: true }, PREFIXES)).toEqual(
            [],
        );
    });

    test("survives a shape with nothing on it", () => {
        expect(matchingShapes(null, { filter: "x" }, PREFIXES)).toEqual([]);
        expect(matchingShapes([{}], { filter: "x" }, PREFIXES)).toEqual([]);
    });
});

describe("the card a line belongs to", () => {
    test("is the last thing written at or above it", () => {
        expect(entryAtLine(SHAPES, RULES, 10)).toEqual({
            kind: "shape",
            iri: `${EX}ACLineSegmentShape`,
            line: 6,
        });
    });

    test("is the shape itself on the line it starts", () => {
        expect(entryAtLine(SHAPES, RULES, 20).iri).toBe(`${EX}TerminalShape`);
    });

    test("can be a shared rule, which is where most of a Simple profile's lines are", () => {
        expect(entryAtLine(SHAPES, RULES, 26)).toEqual({
            kind: "rule",
            iri: `${EX}NameCardinality`,
            line: 25,
        });
    });

    test("is nothing above the first shape, where the prefixes are", () => {
        expect(entryAtLine(SHAPES, RULES, 2)).toBeNull();
    });

    test("is nothing at all for a line nobody named", () => {
        expect(entryAtLine(SHAPES, RULES, null)).toBeNull();
        expect(entryAtLine(SHAPES, RULES, undefined)).toBeNull();
    });

    test("ignores a shape the document does not write as a statement", () => {
        const implied = [{ iri: `${EX}Implied`, line: null }];

        expect(entryAtLine(implied, [], 5)).toBeNull();
    });
});

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("the form's list", () => {
    test("shows the shapes in the order the document writes them", () => {
        // Not IRI order: ACLineSegmentShape, TerminalShape, BayShape is what the document says,
        // and BayShape would come first alphabetically.
        const view = render({ form: fakeForm(), turtle: TURTLE });

        expect(cards(view)).toEqual([
            `${EX}ACLineSegmentShape`,
            `${EX}TerminalShape`,
            `${EX}BayShape`,
        ]);
    });

    test("filtering it leaves only what matches", () => {
        const form = fakeForm({ filter: "cim:Bay" });
        const view = render({ form, turtle: TURTLE });

        expect(cards(view)).toEqual([`${EX}BayShape`]);
    });

    test("says so when nothing matches, rather than showing an empty page", () => {
        const view = render({
            form: fakeForm({ filter: "nothing here" }),
            turtle: "",
        });

        expect(view.textContent).toContain("Nothing matches");
    });

    test("more than one card can be open at a time", () => {
        const expanded = new Set([`${EX}ACLineSegmentShape`, `${EX}BayShape`]);
        const view = render({ form: fakeForm({ expanded }), turtle: "" });

        // Two cards open means two "Applies to" blocks, which only an expanded card renders.
        expect(view.textContent.match(/Applies to/g)).toHaveLength(2);
    });

    test("a card sends the reader to the line it was read from", () => {
        const onreveal = vi.fn();
        const view = render({ form: fakeForm(), turtle: "", onreveal });

        [...view.querySelectorAll("button")]
            .find(
                button =>
                    button.getAttribute("aria-label") ===
                    "Show this shape in the Turtle view",
            )
            .click();

        expect(onreveal).toHaveBeenCalledWith(6);
    });

    test("a line asked for from elsewhere opens the card holding it", () => {
        const form = fakeForm({ focusLine: 21 });
        const view = render({ form, turtle: "" });

        expect(form.expanded.has(`${EX}TerminalShape`)).toBe(true);
        // Taken, so switching views does not keep re-opening the same card.
        expect(form.focusLine).toBeNull();
        expect(view).toBeTruthy();
    });

    test("a line inside a shared rule opens the section that holds it", () => {
        const form = fakeForm({ focusLine: 26 });
        const view = render({ form, turtle: "" });

        expect(form.expanded.size).toBe(0);
        expect(view.textContent).toContain("used by");
    });
});
