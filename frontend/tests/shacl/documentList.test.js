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

import DocumentList from "../../src/routes/shacl/workbench/DocumentList.svelte";

const DOCUMENTS = [
    {
        id: "eq",
        name: "eq.ttl",
        order: 0,
        enabled: true,
        default: false,
        tripleCount: 51,
    },
    {
        id: "custom",
        name: "custom.ttl",
        order: 1,
        enabled: false,
        default: true,
        tripleCount: 0,
    },
];

let mounted = null;
let target = null;

function fakeWorkbench(overrides = {}) {
    return {
        documents: DOCUMENTS,
        // Mirrors the real getter: the generated rules are always the first row.
        get entries() {
            return [
                {
                    id: "generated",
                    name: "Generated rules",
                    generated: true,
                    enabled: true,
                },
                ...this.documents,
            ];
        },
        selectedId: "eq",
        results: [
            {
                documentId: "eq",
                documentName: "eq.ttl",
                errorCount: 2,
                warningCount: 0,
                infoCount: 0,
            },
            {
                documentId: "custom",
                documentName: "custom.ttl",
                errorCount: 0,
                warningCount: 0,
                infoCount: 0,
            },
        ],
        select: vi.fn(),
        create: vi.fn().mockResolvedValue({ id: "new" }),
        importFile: vi.fn().mockResolvedValue({ id: "new" }),
        rename: vi.fn().mockResolvedValue(true),
        setEnabled: vi.fn().mockResolvedValue(true),
        move: vi.fn().mockResolvedValue(true),
        remove: vi.fn().mockResolvedValue(true),
        ...overrides,
    };
}

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(DocumentList, { target, props });
    return target;
}

/** The document rows, without the generated-rules row the list always shows first. */
function documentRows(list) {
    return [...list.querySelectorAll("li")].slice(1);
}

/** The row's controls, by their accessible name. */
function button(row, label) {
    return [...row.querySelectorAll("button")].find(
        candidate => candidate.getAttribute("aria-label") === label,
    );
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

describe("DocumentList", () => {
    test("shows each document with its triple count and problem summary", () => {
        const list = render({ workbench: fakeWorkbench() });
        const rows = documentRows(list);

        expect(rows).toHaveLength(2);
        expect(rows[0].textContent.replace(/\s+/g, " ")).toContain(
            "51 triples · 2 errors",
        );
        expect(rows[1].textContent).toContain("custom.ttl");
        expect(rows[1].textContent.replace(/\s+/g, " ")).toContain("0 triples");
    });

    test("asks before switching away from a document with unsaved changes", async () => {
        const workbench = fakeWorkbench();
        const onbeforeswitch = vi.fn().mockResolvedValue(false);
        const list = render({ workbench, onbeforeswitch });

        documentRows(list)[1].querySelector("button").click();
        await Promise.resolve();

        expect(onbeforeswitch).toHaveBeenCalled();
        expect(workbench.select).not.toHaveBeenCalled();
    });

    test("switches once the answer is yes", async () => {
        const workbench = fakeWorkbench();
        const list = render({
            workbench,
            onbeforeswitch: () => Promise.resolve(true),
        });

        documentRows(list)[1].querySelector("button").click();
        await Promise.resolve();
        await Promise.resolve();

        expect(workbench.select).toHaveBeenCalledWith("custom");
    });

    test("does not ask when the document is already open", async () => {
        const onbeforeswitch = vi.fn();
        const list = render({ workbench: fakeWorkbench(), onbeforeswitch });

        documentRows(list)[0].querySelector("button").click();
        await Promise.resolve();

        expect(onbeforeswitch).not.toHaveBeenCalled();
    });

    test("reordering goes through the workbench", () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });
        const rows = documentRows(list);

        button(rows[0], "Move down").click();
        flushSync();

        expect(workbench.move).toHaveBeenCalledWith("eq", 1);
    });

    test("the ends of the list cannot be moved past", () => {
        const list = render({ workbench: fakeWorkbench() });
        const rows = documentRows(list);

        expect(button(rows[0], "Move up").disabled).toBe(true);
        expect(button(rows[0], "Move down").disabled).toBe(false);
        expect(button(rows[1], "Move up").disabled).toBe(false);
        expect(button(rows[1], "Move down").disabled).toBe(true);
    });

    test("the default document cannot be deleted", () => {
        const list = render({ workbench: fakeWorkbench() });
        const rows = documentRows(list);

        expect(button(rows[0], "Delete")).toBeDefined();
        expect(button(rows[1], "Delete")).toBeUndefined();
    });

    test("renaming replaces the label with an input and commits on Enter", async () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });
        const row = documentRows(list)[0];

        button(row, "Rename").click();
        flushSync();

        const input = list.querySelector("input[aria-label='Document name']");
        expect(input.value).toBe("eq.ttl");
        input.value = "renamed.ttl";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        flushSync();
        input.dispatchEvent(
            new KeyboardEvent("keydown", { key: "Enter", bubbles: true }),
        );
        await Promise.resolve();

        expect(workbench.rename).toHaveBeenCalledWith("eq", "renamed.ttl");
    });

    test("escape abandons a rename", () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });
        const row = documentRows(list)[0];

        button(row, "Rename").click();
        flushSync();
        list.querySelector("input[aria-label='Document name']").dispatchEvent(
            new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
        );
        flushSync();

        expect(workbench.rename).not.toHaveBeenCalled();
        expect(list.textContent).toContain("eq.ttl");
    });

    test("the generated rules head the list, read-only and with no checkbox", () => {
        const list = render({ workbench: fakeWorkbench() });
        const generated = [...list.querySelectorAll("li")][0];

        // Not a document: nothing stores it, so nothing about it can be changed.
        expect(generated.textContent).toContain("Generated rules");
        expect(generated.textContent.replace(/\s+/g, " ")).toContain(
            "from the schema · read-only",
        );
        expect(generated.querySelector("input[type='checkbox']")).toBeNull();
        expect(button(generated, "Delete")).toBeUndefined();
        expect(button(generated, "Rename")).toBeUndefined();
        expect(button(generated, "Move down")).toBeUndefined();
    });

    test("opening the generated rules goes through the workbench like a document", async () => {
        const workbench = fakeWorkbench();
        const list = render({
            workbench,
            onbeforeswitch: () => Promise.resolve(true),
        });

        [...list.querySelectorAll("li")][0].querySelector("button").click();
        await Promise.resolve();
        await Promise.resolve();

        expect(workbench.select).toHaveBeenCalledWith("generated");
    });

    test("a new document gets a name that is not taken yet", async () => {
        const workbench = fakeWorkbench({
            documents: [
                { id: "a", name: "constraints.ttl", order: 0, enabled: true },
            ],
            results: [],
        });
        const list = render({ workbench });

        [...list.querySelectorAll("button")]
            .find(
                candidate =>
                    candidate.getAttribute("aria-label") === "New document",
            )
            .click();
        await Promise.resolve();

        expect(workbench.create).toHaveBeenCalledWith("constraints.ttl (2)");
    });
});
