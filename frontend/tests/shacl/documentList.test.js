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

const EQ = "http://ex.org/EQ";
const SSH = "http://ex.org/SSH";

const SCHEMAS = [
    { uri: EQ, label: "Equipment" },
    { uri: SSH, label: "Hypothesis" },
];

const DOCUMENTS = [
    {
        id: "eq",
        name: "eq.ttl",
        order: 0,
        enabled: true,
        tripleCount: 51,
        schemas: [EQ],
    },
    {
        id: "custom",
        name: "custom.ttl",
        order: 1,
        enabled: false,
        tripleCount: 0,
        schemas: [EQ],
    },
];

/** The props every ActionDialog was given, so a test can call its handlers. */
const dialogs = vi.hoisted(() => []);

let mounted = null;
let target = null;

function fakeWorkbench(overrides = {}) {
    return {
        documents: DOCUMENTS,
        schemas: SCHEMAS,
        comparedSchema: null,
        contextSchemas: [EQ],
        documentsOf(uri) {
            return this.documents.filter(document =>
                document.schemas?.includes(uri),
            );
        },
        get unassigned() {
            return this.documents.filter(
                document =>
                    !document.schemas?.some(uri =>
                        this.schemas.some(schema => schema.uri === uri),
                    ),
            );
        },
        canMove(documentId, offset, schemaUri) {
            const peers = schemaUri
                ? this.documentsOf(schemaUri)
                : this.unassigned;
            const from = peers.findIndex(entry => entry.id === documentId);
            return from >= 0 && peers[from + offset] !== undefined;
        },
        selectedId: "eq",
        select: vi.fn(),
        compare: vi.fn(),
        closeComparison: vi.fn(),
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
    flushSync();
    return target;
}

/** The rows under the schemas, which hold documents and the generated shapes. */
function childRows(list) {
    return [...list.querySelectorAll("ul ul li")];
}

/** The document rows under the schemas: the generated shapes are never a document. */
function documentRows(list) {
    return childRows(list).filter(row =>
        row.querySelector("input[type='checkbox']"),
    );
}

function generatedRows(list) {
    return childRows(list).filter(row =>
        row.textContent.includes("Generated shapes"),
    );
}

/** The row's controls, by their accessible name. */
function button(row, label) {
    return [...row.querySelectorAll("button")].find(
        candidate => candidate.getAttribute("aria-label") === label,
    );
}

vi.mock("$lib/dialog/ActionDialog.svelte", () => ({
    default: function ActionDialogStub(_anchor, props) {
        dialogs.push(props);
    },
}));

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
    test("shows each document with its triple count", () => {
        const list = render({ workbench: fakeWorkbench() });
        const rows = documentRows(list);

        expect(rows).toHaveLength(2);
        expect(rows[0].textContent.replace(/\s+/g, " ")).toContain(
            "51 triples",
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

        expect(workbench.move).toHaveBeenCalledWith("eq", 1, EQ);
    });

    test("the ends of the list cannot be moved past", () => {
        const list = render({ workbench: fakeWorkbench() });
        const rows = documentRows(list);

        expect(button(rows[0], "Move up").disabled).toBe(true);
        expect(button(rows[0], "Move down").disabled).toBe(false);
        expect(button(rows[1], "Move up").disabled).toBe(false);
        expect(button(rows[1], "Move down").disabled).toBe(true);
    });

    test("closing the delete dialog is not refused", () => {
        // ActionDialog stays open when onClose returns a falsy value other than undefined, and
        // the handler used to return the null it assigned: the close button turned the title
        // into "Delete document?" and left the dialog open.
        render({ workbench: fakeWorkbench() });
        const deleteDialog = dialogs.find(props =>
            props.primaryLabel?.startsWith("Delete"),
        );

        expect(deleteDialog.onClose()).toBeUndefined();
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

    test("each schema's generated shapes head its documents, read-only and with no checkbox", () => {
        const list = render({ workbench: fakeWorkbench() });
        const generated = generatedRows(list);

        expect(generated).toHaveLength(SCHEMAS.length);
        // Not a document: nothing stores it, so nothing about it can be changed.
        expect(generated[0].textContent.replace(/\s+/g, " ")).toContain(
            "from the schema · read-only",
        );
        expect(generated[0].querySelector("input[type='checkbox']")).toBeNull();
        expect(button(generated[0], "Delete")).toBeUndefined();
        expect(button(generated[0], "Rename")).toBeUndefined();
        expect(button(generated[0], "Move down")).toBeUndefined();
    });

    test("opening a schema's generated shapes goes through the workbench like a document", async () => {
        const workbench = fakeWorkbench();
        const list = render({
            workbench,
            onbeforeswitch: () => Promise.resolve(true),
        });

        generatedRows(list)[1].querySelector("button").click();
        await Promise.resolve();
        await Promise.resolve();

        expect(workbench.select).toHaveBeenCalledWith(`generated:${SSH}`);
    });

    test("a document applying to two schemas is listed under both", () => {
        const list = render({
            workbench: fakeWorkbench({
                documents: [
                    {
                        id: "both",
                        name: "both.ttl",
                        order: 0,
                        enabled: true,
                        schemas: [EQ, SSH],
                    },
                ],
                results: [],
            }),
        });

        expect(documentRows(list)).toHaveLength(2);
        expect(list.textContent).not.toContain("Not assigned");
    });

    test("a document applying to no schema here is listed as not assigned", () => {
        const list = render({
            workbench: fakeWorkbench({
                documents: [
                    {
                        id: "none",
                        name: "none.ttl",
                        order: 0,
                        enabled: true,
                        schemas: [],
                    },
                    {
                        id: "gone",
                        name: "gone.ttl",
                        order: 1,
                        enabled: true,
                        schemas: ["http://ex.org/deleted"],
                    },
                ],
                results: [],
            }),
        });

        expect(list.textContent).toContain("Not assigned");
        expect(documentRows(list).map(row => row.textContent)).toEqual([
            expect.stringContaining("none.ttl"),
            expect.stringContaining("gone.ttl"),
        ]);
    });

    test("a schema can be collapsed", () => {
        const list = render({ workbench: fakeWorkbench() });

        button(list, "Collapse Equipment").click();
        flushSync();

        expect(documentRows(list)).toHaveLength(0);
        expect(
            button(list, "Expand Equipment").getAttribute("aria-expanded"),
        ).toBe("false");
        expect(generatedRows(list)).toHaveLength(1);
    });

    test("a new document made from a schema's row applies to that schema", async () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });

        button(list, "New document for Hypothesis").click();

        await vi.waitFor(() =>
            expect(workbench.create).toHaveBeenCalledWith(
                "constraints.ttl",
                "",
                [SSH],
            ),
        );
    });

    test("nothing can be added to a read-only workspace", () => {
        const list = render({ workbench: fakeWorkbench({ readOnly: true }) });

        expect(button(list, "New document")).toBeUndefined();
        expect(button(list, "New document for Equipment")).toBeUndefined();
        expect(button(list, "Import a constraints file")).toBeUndefined();
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

        await vi.waitFor(() =>
            expect(workbench.create).toHaveBeenCalledWith(
                "constraints (2).ttl",
                "",
                [EQ],
            ),
        );
    });

    test("a new document starts with the text the page provides", async () => {
        const workbench = fakeWorkbench();
        const seed = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n";
        const list = render({
            workbench,
            newDocumentText: () => Promise.resolve(seed),
        });

        button(list, "New document").click();

        await vi.waitFor(() =>
            expect(workbench.create).toHaveBeenCalledWith(
                "constraints.ttl",
                seed,
                [EQ],
            ),
        );
    });

    test("creating a document asks about the open one first", async () => {
        const workbench = fakeWorkbench();
        const onbeforeswitch = vi.fn().mockResolvedValue(false);
        const list = render({ workbench, onbeforeswitch });

        button(list, "New document").click();
        await vi.waitFor(() => expect(onbeforeswitch).toHaveBeenCalled());
        await Promise.resolve();

        expect(workbench.create).not.toHaveBeenCalled();
    });

    function chooseFile(list, name = "shapes.ttl") {
        const input = list.querySelector('input[type="file"]');
        Object.defineProperty(input, "files", {
            configurable: true,
            value: [new File(["x"], name)],
        });
        input.dispatchEvent(new Event("change", { bubbles: true }));
        flushSync();
    }

    function importDialog() {
        return dialogs.findLast(props => props.primaryLabel === "Import");
    }

    test("importing a file asks about the open one first", async () => {
        const workbench = fakeWorkbench();
        const onbeforeswitch = vi.fn().mockResolvedValue(false);
        const list = render({ workbench, onbeforeswitch });

        chooseFile(list);
        await importDialog().onPrimary();

        expect(onbeforeswitch).toHaveBeenCalled();
        expect(workbench.importFile).not.toHaveBeenCalled();
    });

    test("importing from a schema's row starts from that schema", async () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });
        const input = list.querySelector('input[type="file"]');
        input.click = vi.fn();

        button(list, "Import a constraints file for Hypothesis").click();
        chooseFile(list);
        expect(importDialog().showDialog).toBe(true);
        await importDialog().onPrimary();

        expect(workbench.importFile).toHaveBeenCalledWith(
            expect.any(File),
            "shapes.ttl",
            [SSH],
        );
    });

    test("importing from the header starts from what is in view", async () => {
        const workbench = fakeWorkbench({ contextSchemas: [SSH] });
        const list = render({ workbench });
        const input = list.querySelector('input[type="file"]');
        input.click = vi.fn();

        button(list, "Import a constraints file").click();
        chooseFile(list);
        await importDialog().onPrimary();

        expect(workbench.importFile).toHaveBeenCalledWith(
            expect.any(File),
            "shapes.ttl",
            [SSH],
        );
    });

    test("closing the import dialog imports nothing and is not refused", async () => {
        const workbench = fakeWorkbench();
        const list = render({ workbench });

        chooseFile(list);

        expect(importDialog().onClose()).toBeUndefined();
        await importDialog().onPrimary();
        expect(workbench.importFile).not.toHaveBeenCalled();
    });

    test("marks the open document for assistive technology", () => {
        const list = render({ workbench: fakeWorkbench() });
        const [eq, custom] = documentRows(list).map(row =>
            row.querySelector("button[aria-current]"),
        );

        expect(eq?.getAttribute("aria-current")).toBe("true");
        expect(custom).toBeNull();
    });
});
