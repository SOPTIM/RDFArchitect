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

import { mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import ConfirmDefaults from "../../src/routes/migrate/steps/ConfirmDefaults.svelte";
import ConfirmAttributeDefaults from "../../src/routes/migrate/steps/defaults/ConfirmAttributeDefaults.svelte";

const PREFIX = "http://example.org#";

const getDefaultValuesViews = vi.hoisted(() => vi.fn());
const submitDefaultValues = vi.hoisted(() => vi.fn());

/** A required attribute that is added by the new schema, so it has to be initialized. */
const ADDED_REQUIRED = {
    iri: `${PREFIX}Switch.normalOpen`,
    label: "normalOpen",
    semanticResourceChangeType: "ADD",
    optional: false,
    dataType: `${PREFIX}Boolean`,
    primitiveDataType: "http://www.w3.org/2001/XMLSchema#boolean",
    changes: [{ semanticFieldChangeType: "DATATYPE_CHANGE", to: "Boolean" }],
};

/**
 * An optional attribute whose data type changed. Its existing values have to be converted, so a
 * default value is required even though the attribute itself is optional.
 */
const OPTIONAL_DATATYPE_CHANGED = {
    iri: `${PREFIX}Switch.ratedCurrent`,
    label: "ratedCurrent",
    semanticResourceChangeType: "CHANGE",
    optional: true,
    dataType: `${PREFIX}CurrentFlow`,
    oldDataType: `${PREFIX}Decimal`,
    primitiveDataType: "http://www.w3.org/2001/XMLSchema#decimal",
    changes: [
        {
            semanticFieldChangeType: "DATATYPE_CHANGE",
            from: `${PREFIX}Decimal`,
            to: `${PREFIX}CurrentFlow`,
        },
    ],
};

/** A required attribute the old schema already provided a default for. */
const MADE_REQUIRED_WITH_DEFAULT = {
    iri: `${PREFIX}Breaker.inTransitTime`,
    label: "inTransitTime",
    semanticResourceChangeType: "CHANGE",
    optional: false,
    dataType: `${PREFIX}Seconds`,
    primitiveDataType: "http://www.w3.org/2001/XMLSchema#decimal",
    defaultValue: "0",
    changes: [{ semanticFieldChangeType: "MADE_REQUIRED" }],
};

const CLASSES = [
    {
        classLabel: "Switch",
        attributes: [ADDED_REQUIRED, OPTIONAL_DATATYPE_CHANGED],
        associations: [],
        enumEntries: [],
    },
    {
        classLabel: "Breaker",
        attributes: [MADE_REQUIRED_WITH_DEFAULT],
        associations: [],
        enumEntries: [],
    },
];

let mounted = null;
let target = null;
let disableNext = null;

/**
 * Mounts the step through its parent, which owns the reactive class list the substep edits.
 * `disableNext` is passed as an accessor pair, exactly as `bind:disableNext` would.
 */
async function render(classes = CLASSES) {
    getDefaultValuesViews.mockResolvedValue({
        data: structuredClone(classes),
        error: undefined,
    });

    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ConfirmDefaults, {
        target,
        props: {
            substeps: [
                { title: "Attributes", component: ConfirmAttributeDefaults },
            ],
            currentSubstepIndex: 0,
            get disableNext() {
                return disableNext;
            },
            set disableNext(value) {
                disableNext = value;
            },
        },
    });

    const expectedRows = classes.flatMap(cls => cls.attributes).length;
    await vi.waitFor(() => expect(rows()).toHaveLength(expectedRows));
    return target;
}

function rows() {
    return [...target.querySelectorAll("tbody tr")];
}

function row(label) {
    return rows().find(tableRow =>
        tableRow.querySelector("td").textContent.includes(label),
    );
}

function defaultValueInput(label) {
    return row(label).querySelectorAll("td")[4].querySelector("input, select");
}

function dontInitBox(label) {
    return row(label).querySelectorAll("td")[5].querySelector("input");
}

function button(caption) {
    return [...target.querySelectorAll("button")].find(
        candidate => candidate.textContent.trim() === caption,
    );
}

function alertText() {
    return target.querySelector("[role='alert']")?.textContent ?? "";
}

vi.mock("$lib/api/generated/index.ts", () => ({
    getDefaultValuesViews,
    submitDefaultValues,
}));

beforeEach(() => {
    submitDefaultValues.mockResolvedValue({ error: undefined });
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    disableNext = null;
    vi.clearAllMocks();
});

describe("ConfirmAttributeDefaults", () => {
    test("blocks the step while a required default value is missing", async () => {
        await render();

        expect(disableNext).toBe(true);
    });

    test("offers Don't Init for every attribute that requires a default value", async () => {
        await render();

        // Also for an optional attribute: its changed data type needs a default all the same.
        expect(dontInitBox(OPTIONAL_DATATYPE_CHANGED.label)).not.toBeNull();
        expect(dontInitBox(ADDED_REQUIRED.label)).not.toBeNull();
    });

    test("unblocks the step once every required attribute is waived", async () => {
        await render();

        dontInitBox(ADDED_REQUIRED.label).click();
        dontInitBox(OPTIONAL_DATATYPE_CHANGED.label).click();

        await vi.waitFor(() => expect(disableNext).toBe(false));
    });

    test("marks the attributes that still block the step", async () => {
        await render();

        expect(
            defaultValueInput(ADDED_REQUIRED.label).getAttribute(
                "aria-invalid",
            ),
        ).toBe("true");
        expect(alertText()).toContain("Switch");
        expect(alertText()).not.toContain("Breaker");

        dontInitBox(ADDED_REQUIRED.label).click();

        await vi.waitFor(() =>
            expect(
                defaultValueInput(ADDED_REQUIRED.label).getAttribute(
                    "aria-invalid",
                ),
            ).toBe("false"),
        );
    });

    test("clears the alert once nothing is open anymore", async () => {
        await render();

        button("Don't Init All").click();

        await vi.waitFor(() =>
            expect(target.querySelector("[role='alert']")).toBeNull(),
        );
    });

    test("waives every required attribute at once", async () => {
        await render();

        button("Don't Init All").click();

        await vi.waitFor(() => expect(disableNext).toBe(false));
        expect(dontInitBox(ADDED_REQUIRED.label).checked).toBe(true);
        expect(dontInitBox(OPTIONAL_DATATYPE_CHANGED.label).checked).toBe(true);
        expect(dontInitBox(MADE_REQUIRED_WITH_DEFAULT.label).checked).toBe(
            true,
        );
    });

    test("drops a default value that is waived, so nothing is initialized", async () => {
        await render();

        // The attribute arrives with a default value from the old schema.
        expect(defaultValueInput(MADE_REQUIRED_WITH_DEFAULT.label).value).toBe(
            MADE_REQUIRED_WITH_DEFAULT.defaultValue,
        );

        dontInitBox(MADE_REQUIRED_WITH_DEFAULT.label).click();

        await vi.waitFor(() =>
            expect(
                defaultValueInput(MADE_REQUIRED_WITH_DEFAULT.label).value,
            ).toBe(""),
        );
    });

    test("takes back the waiver for every attribute at once", async () => {
        await render();

        button("Don't Init All").click();
        await vi.waitFor(() => expect(disableNext).toBe(false));

        button("Clear Don't Init").click();

        await vi.waitFor(() => expect(disableNext).toBe(true));
        expect(dontInitBox(ADDED_REQUIRED.label).checked).toBe(false);
    });

    test("keeps the bulk controls out of the way when nothing requires a default", async () => {
        await render([
            {
                classLabel: "Switch",
                attributes: [{ ...ADDED_REQUIRED, optional: true }],
                associations: [],
                enumEntries: [],
            },
        ]);

        expect(disableNext).toBe(false);
        expect(button("Don't Init All")).toBeUndefined();
        expect(target.querySelector("[role='alert']")).toBeNull();
    });
});
