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
import { afterEach, describe, expect, test, vi } from "vitest";

import ChangesRow from "../../src/routes/changelog/ChangesRow.svelte";

const CHANGE = {
    changeId: "c1",
    message: "added a class",
    timestamp: "2026-11-04T10:00:00",
    affectedKinds: ["rdf"],
    contextDeltas: [
        {
            contextName: "rdf",
            graphUri: "http://example.org/a",
            additions: [{ subject: "s", predicate: "p", object: "o" }],
            deletions: [],
            values: [],
        },
    ],
};

let mounted = null;
let table = null;

function render(props = {}) {
    const expanded = new Map(Object.entries(props.expanded ?? {}));
    const given = {
        change: CHANGE,
        getExpanded: key => !!expanded.get(key),
        setExpanded: (key, value) => expanded.set(key, value),
        onRestore: vi.fn(),
        readonly: false,
        ...props,
    };
    table = document.createElement("table");
    const body = document.createElement("tbody");
    table.appendChild(body);
    document.body.appendChild(table);
    mounted = mount(ChangesRow, { target: body, props: given });
    return given;
}

function rows() {
    return table.querySelectorAll("tr");
}

function button(text) {
    return [...table.querySelectorAll("button")].find(
        each => each.textContent.trim() === text,
    );
}

afterEach(() => {
    if (mounted) unmount(mounted);
    table?.remove();
    mounted = null;
    table = null;
});

describe("ChangesRow", () => {
    test("marks the current version instead of offering to restore it", () => {
        render({ current: true });

        expect(table.textContent).toContain("Current version");
        expect(button("Restore")).toBeUndefined();
    });

    test("offers to restore an older version", () => {
        const props = render();

        button("Restore").click();

        expect(props.onRestore).toHaveBeenCalledWith(CHANGE);
    });

    test("offers to advance to an undone version", () => {
        const props = render({ change: { ...CHANGE, undone: true } });

        button("Advance").click();

        expect(props.onRestore).toHaveBeenCalledWith({
            ...CHANGE,
            undone: true,
        });
    });

    test("offers nothing to restore in a read-only workspace", () => {
        render({ readonly: true });

        expect(button("Restore").disabled).toBe(true);
    });

    test("shows the details of an expanded row", () => {
        render({ expanded: { "c1::row": true } });

        expect(rows()).toHaveLength(2);
        expect(table.textContent).toContain("Additions");
    });

    test("draws no empty details once every kind of an expanded row is hidden", () => {
        // Its caret goes with its kinds, so an empty detail row could not be
        // closed again.
        render({
            expanded: { "c1::row": true },
            hiddenKinds: new Set(["rdf"]),
        });

        expect(rows()).toHaveLength(1);
        expect(
            table.querySelector("button[title='Toggle details']"),
        ).toBeNull();
    });
});
