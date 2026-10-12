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
import { afterEach, describe, expect, test } from "vitest";

import NamespacePrefixComparison from "$lib/components/NamespacePrefixComparison.svelte";

const CIM16 = "http://iec.ch/TC57/2013/CIM-schema-cim16#";
const CIM18 = "http://iec.ch/TC57/2023/CIM-schema-cim18#";
const RDFS = "http://www.w3.org/2000/01/rdf-schema#";
const EU_2020 = "http://entsoe.eu/ns/2020#";
const EU_2024 = "http://entsoe.eu/ns/2024#";

let mounted = null;
let target = null;
let state = null;

function contested(overrides = {}) {
    return {
        prefix: "cim:",
        workspace: { iri: CIM16, fileNames: [] },
        imported: [{ iri: CIM18, fileNames: ["dl30.ttl"] }],
        contested: true,
        ...overrides,
    };
}

/** A prefix the dataset and the import agree on, so nothing is decided about it. */
function identical() {
    return {
        prefix: "rdfs:",
        workspace: { iri: RDFS, fileNames: [] },
        imported: [{ iri: RDFS, fileNames: ["dl30.ttl"] }],
        contested: false,
    };
}

/** A prefix the dataset holds that no file of the import declares. */
function workspaceOnly() {
    return {
        prefix: "dcat:",
        workspace: { iri: "http://www.w3.org/ns/dcat#", fileNames: [] },
        imported: [],
        contested: false,
    };
}

/** Two files claiming the same prefix for different namespaces, with nothing in the workspace. */
function contestedBetweenFiles() {
    return {
        prefix: "eu:",
        workspace: null,
        imported: [
            { iri: EU_2020, fileNames: ["a.ttl"] },
            { iri: EU_2024, fileNames: ["b.ttl"] },
        ],
        contested: true,
    };
}

/**
 * Stands in for the `bind:` props of the component: what it writes back lands in `state`. The
 * component only ever writes them, so plain accessors do; a prop the test wrote back would need
 * reactive state, which a `.test.js` file cannot declare.
 */
function bindableProps(state) {
    const props = {};
    for (const key of Object.keys(state)) {
        Object.defineProperty(props, key, {
            get: () => state[key],
            set: value => {
                state[key] = value;
            },
            enumerable: true,
        });
    }
    return props;
}

function render(comparison) {
    target = document.createElement("div");
    document.body.appendChild(target);
    state = { resolutions: [], isValid: true };
    mounted = mount(NamespacePrefixComparison, {
        target,
        props: Object.assign(bindableProps(state), { comparison }),
    });
    flushSync();
    return target;
}

function resolutionFor(iri) {
    return state.resolutions.find(resolution => resolution.iri === iri);
}

function prefixInputs(panel) {
    return [...panel.querySelectorAll('input[placeholder="no prefix"]')];
}

/** The inputs a conflict marks, which is what the red border on their group stands for. */
function markedInputs(panel) {
    return [...panel.querySelectorAll(".border-red input")];
}

function type(input, value) {
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

function typePrefix(panel, value, index) {
    type(prefixInputs(panel)[index], value);
}

function click(element) {
    element.click();
    flushSync();
}

function controlButtons(panel, title) {
    return [...panel.querySelectorAll(`button[title="${title}"]`)];
}

function violationsOf(panel, index) {
    const row = panel.querySelectorAll(".col-start-1")[index];
    return [...row.children].map(message => message.textContent.trim());
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    state = null;
});

describe("NamespacePrefixComparison", () => {
    test("puts every namespace claiming a prefix next to the others", () => {
        const panel = render([contested()]);

        expect(panel.textContent).toContain('2 namespaces claim "cim:"');
        expect(panel.textContent).toContain(CIM16);
        expect(panel.textContent).toContain(CIM18);
        expect(panel.textContent).toContain("the workspace");
        expect(panel.textContent).toContain("dl30.ttl");
        expect(prefixInputs(panel).map(input => input.value)).toEqual([
            "cim:",
            "cim:",
        ]);
    });

    test("marks both prefixes of a conflict, either one can give way", () => {
        const panel = render([contested()]);

        expect(state.isValid).toBe(false);
        expect(markedInputs(panel)).toEqual(prefixInputs(panel));
        expect(violationsOf(panel, 0)).toEqual(["must be unique"]);
        expect(violationsOf(panel, 1)).toEqual(["must be unique"]);
    });

    test("a new prefix for the import settles the conflict", () => {
        const panel = render([contested()]);

        typePrefix(panel, "cim18", 1);

        expect(state.isValid).toBe(true);
        expect(resolutionFor(CIM16).action).toBe("KEEP");
        expect(resolutionFor(CIM18)).toEqual({
            prefix: "cim:",
            iri: CIM18,
            action: "RENAME",
            newPrefix: "cim18:",
        });
    });

    test("clearing the field imports the namespace without a prefix", () => {
        const panel = render([contested()]);

        typePrefix(panel, "", 1);

        expect(state.isValid).toBe(true);
        expect(resolutionFor(CIM18).action).toBe("DROP");
        expect(resolutionFor(CIM18).newPrefix).toBeNull();
    });

    test("moving the workspace prefix hands it to the import", () => {
        const panel = render([contested()]);

        typePrefix(panel, "cim16", 0);

        expect(state.isValid).toBe(true);
        expect(resolutionFor(CIM16).action).toBe("RENAME");
        expect(resolutionFor(CIM16).newPrefix).toBe("cim16:");
        expect(resolutionFor(CIM18).action).toBe("KEEP");
    });

    test("the revert button puts a field back to what was brought", () => {
        const panel = render([contested()]);

        typePrefix(panel, "cim18", 1);

        expect(controlButtons(panel, "Revert Changes")).toHaveLength(2);
        click(controlButtons(panel, "Revert Changes")[1]);

        expect(prefixInputs(panel)[1].value).toBe("cim:");
    });

    test("the clear button empties a field", () => {
        const panel = render([contested()]);

        click(controlButtons(panel, "Clear Value")[1]);

        expect(prefixInputs(panel)[1].value).toBe("");
        expect(resolutionFor(CIM18).action).toBe("DROP");
    });

    test("a prefix that is no name for one is refused", () => {
        const panel = render([contested()]);

        typePrefix(panel, "cim 18", 1);

        expect(state.isValid).toBe(false);
        expect(panel.textContent).toContain("must not contain");
    });

    test("a prefix an uncontested namespace holds is refused", () => {
        const panel = render([contested(), identical()]);

        typePrefix(panel, "rdfs", 1);

        expect(prefixInputs(panel)).toHaveLength(2);
        expect(state.isValid).toBe(false);
        expect(violationsOf(panel, 1)).toEqual(["must be unique"]);
    });

    test("prefixes without a conflict are not asked about at all", () => {
        const panel = render([contested(), identical(), workspaceOnly()]);

        expect(prefixInputs(panel)).toHaveLength(2);
        expect(panel.textContent).not.toContain(RDFS);
        expect(state.resolutions.map(resolution => resolution.iri)).toEqual([
            CIM16,
            CIM18,
        ]);
    });

    test("every contested prefix is its own group", () => {
        const panel = render([
            contested(),
            contested({
                prefix: "md:",
                workspace: { iri: "http://md/2020#", fileNames: [] },
                imported: [{ iri: "http://md/2024#", fileNames: ["nc.ttl"] }],
            }),
        ]);

        expect(panel.textContent).toContain('2 namespaces claim "cim:"');
        expect(panel.textContent).toContain('2 namespaces claim "md:"');
        expect(prefixInputs(panel)).toHaveLength(4);
        expect(panel.textContent).toContain("nc.ttl");
    });

    test("two files contesting a prefix are both asked about it", () => {
        const panel = render([contestedBetweenFiles()]);

        expect(panel.textContent).toContain(EU_2020);
        expect(panel.textContent).toContain(EU_2024);
        expect(prefixInputs(panel).map(input => input.value)).toEqual([
            "eu:",
            "eu:",
        ]);
        expect(markedInputs(panel)).toHaveLength(2);

        typePrefix(panel, "eu24", 1);

        expect(state.isValid).toBe(true);
        expect(resolutionFor(EU_2020).action).toBe("KEEP");
        expect(resolutionFor(EU_2024).newPrefix).toBe("eu24:");
    });
});
