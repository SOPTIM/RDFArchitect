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

import PropertyShapeCard from "../../src/routes/shacl/workbench/form/PropertyShapeCard.svelte";
import TermPicker from "../../src/routes/shacl/workbench/form/TermPicker.svelte";

/**
 * The fields on a rule card, one per defect they used to have.
 *
 * Five of the eight did not work at all: the three dropdowns were not bound, so the change was
 * dropped and the field snapped back on the next read; the two number fields read `event.target`
 * on a callback that is handed the input's value, so everything typed into them became "no bound
 * stated"; and the message field threw on every keystroke for the same reason.
 */

const CIM = "http://iec.ch/TC57/CIM100#";
const SHACL = "http://www.w3.org/ns/shacl#";
const XSD = "http://www.w3.org/2001/XMLSchema#";

const TERMS = [
    {
        kind: "PROPERTY",
        iri: `${CIM}ACLineSegment.r`,
        namespace: CIM,
        localName: "ACLineSegment.r",
    },
    {
        kind: "CLASS",
        iri: `${CIM}Terminal`,
        namespace: CIM,
        localName: "Terminal",
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

function card(overrides = {}) {
    const property = { path: `${CIM}ACLineSegment.r`, ...overrides };
    const onchange = vi.fn();
    const onedit = vi.fn();
    const view = render(PropertyShapeCard, {
        property,
        terms: TERMS,
        prefixes: { cim: CIM, sh: SHACL, xsd: XSD },
        onchange,
        onedit,
    });
    return { property, onchange, onedit, view };
}

function select(view, label) {
    const labels = ["Value type", "Value form", "Severity"];
    return view.querySelectorAll("select")[labels.indexOf(label)];
}

function choose(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

function type(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

function commit(element, value) {
    element.value = value;
    element.dispatchEvent(new Event("input", { bubbles: true }));
    element.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("the dropdowns on a rule", () => {
    test("the value type is written and applied", () => {
        const { property, onchange } = card();

        choose(select(target, "Value type"), `${XSD}string`);

        expect(property.dataType).toBe(`${XSD}string`);
        expect(onchange).toHaveBeenCalled();
    });

    test("the value form is written and applied", () => {
        const { property, onchange } = card();

        choose(select(target, "Value form"), `${SHACL}IRI`);

        expect(property.nodeKind).toBe(`${SHACL}IRI`);
        expect(onchange).toHaveBeenCalled();
    });

    test("the severity is written and applied", () => {
        const { property, onchange } = card();

        choose(select(target, "Severity"), `${SHACL}Warning`);

        expect(property.severity).toBe(`${SHACL}Warning`);
        expect(onchange).toHaveBeenCalled();
    });

    test("choosing the empty option clears the field rather than writing a name for it", () => {
        const { property } = card({ dataType: `${XSD}string` });

        choose(select(target, "Value type"), "__NULL__");

        expect(property.dataType).toBeNull();
    });
});

describe("the counts on a rule", () => {
    test("a typed minimum is written as a number", () => {
        const { property, onedit, onchange } = card();

        type(target.querySelectorAll("input[type=number]")[0], "3");

        expect(property.minCount).toBe(3);
        // Still being typed in, so it is sent once the typing stops rather than per keystroke.
        expect(onedit).toHaveBeenCalled();
        expect(onchange).not.toHaveBeenCalled();
    });

    test("leaving the field applies the edit at once", () => {
        const { property, onchange } = card();

        commit(target.querySelectorAll("input[type=number]")[1], "1");

        expect(property.maxCount).toBe(1);
        expect(onchange).toHaveBeenCalled();
    });

    test("clearing it says no bound, which is not the same as zero", () => {
        const { property } = card({ minCount: 2 });

        commit(target.querySelectorAll("input[type=number]")[0], "");

        expect(property.minCount).toBeNull();
    });
});

describe("the message on a rule", () => {
    test("typing it writes the text instead of throwing", () => {
        const { property, onedit } = card();
        const message = [...target.querySelectorAll("input[type=text]")].at(-1);

        type(message, "Missing required property");

        expect(property.message).toBe("Missing required property");
        expect(onedit).toHaveBeenCalled();
    });

    test("emptying it removes the message rather than writing an empty one", () => {
        const { property } = card({ message: "was here" });
        const message = [...target.querySelectorAll("input[type=text]")].at(-1);

        commit(message, "");

        expect(property.message).toBeNull();
    });
});

describe("picking a term", () => {
    function picker(props = {}) {
        const onpick = vi.fn();
        const view = render(TermPicker, {
            label: "Property",
            kind: "PROPERTY",
            terms: TERMS,
            prefixes: { cim: CIM },
            onpick,
            ...props,
        });
        return { onpick, view };
    }

    test("a term from the list is handed over as its IRI", () => {
        const { onpick } = picker();

        commit(target.querySelector("input"), "cim:ACLineSegment.r");

        expect(onpick).toHaveBeenCalledWith(`${CIM}ACLineSegment.r`);
    });

    test("a prefixed name the document binds is resolved, list or no list", () => {
        const { onpick } = picker();

        commit(target.querySelector("input"), "cim:Terminal.sequenceNumber");

        expect(onpick).toHaveBeenCalledWith(`${CIM}Terminal.sequenceNumber`);
    });

    test("an absolute IRI typed without brackets is accepted", () => {
        const { onpick } = picker();

        commit(target.querySelector("input"), "http://other.org/Thing.name");

        expect(onpick).toHaveBeenCalledWith("http://other.org/Thing.name");
    });

    test("something that is not a term at all is refused, not written", () => {
        // It used to go straight into sh:path, where a phrase with a space in it was written as
        // `<a phrase>` and the document stopped parsing.
        const { onpick } = picker({ value: `${CIM}ACLineSegment.r` });

        commit(target.querySelector("input"), "the length one");

        expect(onpick).not.toHaveBeenCalled();
        // The box is put back to the term the rule still holds.
        expect(target.querySelector("input").value).toBe("cim:ACLineSegment.r");
    });

    test("clearing the box clears the field", () => {
        const { onpick } = picker({ value: `${CIM}ACLineSegment.r` });

        commit(target.querySelector("input"), "");

        expect(onpick).toHaveBeenCalledWith(null);
    });
});
