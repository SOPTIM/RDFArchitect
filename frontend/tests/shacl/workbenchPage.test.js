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
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { confirmUnsavedChanges } from "$lib/eventhandling/unsavedChanges.js";
import { editorState, forceReloadTrigger } from "$lib/sharedState.svelte.js";
import { graphStore } from "$lib/stores/graphStore.ts";

import WorkbenchPage from "../../src/routes/shacl/+page.svelte";

import { goto } from "$app/navigation";

const EQ = "eq-document-id";
const CUSTOM = "custom-document-id";
const SHAPES = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n";
const TYPED = `${SHAPES}# typed\n`;

/** What the page registered with `beforeNavigate`. */
const navigation = vi.hoisted(() => ({ guard: null }));

let server;
let mounted = null;
let target = null;

/** A server with two documents that remembers what it was asked. */
function fakeServer() {
    const server = {
        requests: [],
        documents: [
            {
                id: EQ,
                name: "eq.ttl",
                order: 0,
                enabled: true,
                tripleCount: 3,
                schemas: ["EQ"],
            },
            {
                id: CUSTOM,
                name: "custom.ttl",
                order: 1,
                enabled: true,
                schemas: ["EQ"],
            },
        ],
        texts: { [EQ]: SHAPES, [CUSTOM]: "" },
        /** Answers a PUT instead of the default when set. */
        answerPut: null,
    };
    server.fetch = async request => {
        const url = new URL(request.url);
        const entry = {
            method: request.method,
            path: url.pathname,
            query: url.searchParams,
            body: await request.text(),
        };
        server.requests.push(entry);
        const json = (value, status = 200) =>
            new Response(JSON.stringify(value), {
                status,
                headers: { "content-type": "application/json" },
            });
        const documentId = entry.path.match(/\/documents\/([^/]+)$/)?.[1];
        if (entry.path.endsWith("/shacl/documents") && entry.method === "GET") {
            return json(server.documents);
        }
        if (
            entry.path.endsWith("/shacl/documents") &&
            entry.method === "POST"
        ) {
            const created = {
                id: "new-id",
                name: entry.query.get("name"),
                order: server.documents.length,
                enabled: true,
                schemas: entry.query.getAll("schema"),
            };
            server.documents = [...server.documents, created];
            server.texts["new-id"] = entry.body;
            return json(created);
        }
        if (entry.path.endsWith("/generate/string")) {
            return new Response("# generated\n", { status: 200 });
        }
        if (documentId && entry.method === "PUT") {
            if (server.answerPut) {
                return server.answerPut(entry);
            }
            server.texts[documentId] = entry.body;
            return json({ id: documentId });
        }
        if (documentId && entry.method === "GET") {
            return new Response(server.texts[documentId] ?? "", {
                status: 200,
                headers: { "content-type": "text/plain" },
            });
        }
        return json({});
    };
    return server;
}

function editor() {
    return target.querySelector("textarea[aria-label=Turtle]");
}

function type(text) {
    editor().value = text;
    editor().dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

function buttonNamed(name, root = document) {
    return [...root.querySelectorAll("button")].find(
        button => button.textContent.trim() === name,
    );
}

/** The question about unsaved changes, while it is open. */
function unsavedQuestion() {
    return [...document.querySelectorAll("[role=alertdialog]")].find(dialog =>
        dialog.textContent.includes("Unsaved changes"),
    );
}

async function answer(name) {
    let button;
    await vi.waitFor(() => {
        button = buttonNamed(name, unsavedQuestion());
        expect(button).toBeDefined();
    });
    button.click();
    flushSync();
}

function openDocument(name) {
    [...target.querySelectorAll("button")]
        .find(button => button.textContent.includes(name))
        .click();
}

function pressCtrlS() {
    const event = new KeyboardEvent("keydown", {
        key: "s",
        code: "KeyS",
        ctrlKey: true,
        bubbles: true,
        cancelable: true,
    });
    window.dispatchEvent(event);
    return event;
}

function puts() {
    return server.requests.filter(entry => entry.method === "PUT");
}

async function loaded() {
    await vi.waitFor(() => expect(editor()?.value).toBe(SHAPES));
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));
vi.mock("$app/navigation", () => ({
    beforeNavigate: guard => (navigation.guard = guard),
    goto: vi.fn(async () => {}),
}));
vi.mock("$app/state", () => ({
    page: { url: new URL("http://localhost/shacl") },
}));
vi.mock("$lib/monaco/TurtleEditor.svelte", async () => ({
    default: (await import("./TurtleEditorStub.svelte")).default,
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn() },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        isReadOnly: vi.fn(async () => false),
        getNamespaces: vi.fn(async () => []),
        getWorkspaces: vi.fn(async () => [{ label: "cgmes" }]),
        subscribe: vi.fn(() => () => {}),
    },
}));

beforeEach(async () => {
    server = fakeServer();
    vi.stubGlobal("fetch", server.fetch);
    graphStore.getGraphs.mockResolvedValue([{ name: "EQ", keyword: "EQ" }]);
    editorState.reset();
    editorState.selectGraph("cgmes", "EQ");
    navigation.guard = null;
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(WorkbenchPage, { target });
    flushSync();
    await loaded();
});

afterEach(async () => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    editorState.reset();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    vi.restoreAllMocks();
});

describe("opening another document over unsaved changes", () => {
    test("asks first, and Cancel keeps them", async () => {
        type(TYPED);

        openDocument("custom.ttl");
        await answer("Cancel");

        expect(editor().value).toBe(TYPED);
        expect(target.textContent).toContain("unsaved changes");
    });

    test("opens the other document once they are discarded", async () => {
        type(TYPED);

        openDocument("custom.ttl");
        await answer("Discard");

        await vi.waitFor(() => expect(editor().value).toBe(""));
        expect(puts()).toEqual([]);
    });

    test("opens the other document only once a save asked for there is written", async () => {
        server.answerPut = () =>
            new Response(JSON.stringify({ detail: "Bad Turtle at 2:1." }), {
                status: 400,
                headers: { "content-type": "application/json" },
            });
        const error = vi.spyOn(toastStore, "error");
        type(TYPED);

        openDocument("custom.ttl");
        await answer("Save");

        await vi.waitFor(() =>
            expect(error).toHaveBeenCalledWith(
                "Not saved",
                "Bad Turtle at 2:1.",
            ),
        );
        expect(editor().value).toBe(TYPED);
    });
});

describe("something outside the page about to replace it", () => {
    test("is told to go ahead when nothing is unsaved, without a question", async () => {
        expect(await confirmUnsavedChanges()).toBe(true);
        expect(unsavedQuestion()).toBeUndefined();
    });

    test("is not offered Save where it would take the save back, and Discard reverts the edits", async () => {
        type(TYPED);

        const agreed = confirmUnsavedChanges({ allowSave: false });
        await vi.waitFor(() => expect(unsavedQuestion()).toBeDefined());
        expect(buttonNamed("Save", unsavedQuestion())).toBeUndefined();
        await answer("Discard");

        expect(await agreed).toBe(true);
        expect(editor().value).toBe(SHAPES);
    });
});

describe("leaving the page", () => {
    function leave({ to = "/mainpage", willUnload = false } = {}) {
        const cancel = vi.fn();
        navigation.guard({
            to: to ? { url: new URL(`http://localhost${to}`) } : null,
            willUnload,
            cancel,
        });
        return cancel;
    }

    test("goes ahead when nothing is unsaved", () => {
        expect(leave()).not.toHaveBeenCalled();
    });

    test("is held until the question is answered, then made without asking again", async () => {
        type(TYPED);
        goto.mockImplementation(async () => {
            // SvelteKit runs the guard again for the navigation the page re-issues.
            expect(leave()).not.toHaveBeenCalled();
        });

        expect(leave()).toHaveBeenCalled();
        await answer("Discard");

        await vi.waitFor(() =>
            expect(goto).toHaveBeenCalledWith(
                new URL("http://localhost/mainpage"),
            ),
        );
    });

    test("stays when the answer is Cancel", async () => {
        type(TYPED);

        expect(leave()).toHaveBeenCalled();
        await answer("Cancel");

        expect(goto).not.toHaveBeenCalled();
    });

    test("leaves a reload or a closed tab to the browser's own question", async () => {
        type(TYPED);

        expect(leave({ to: null, willUnload: true })).toHaveBeenCalled();
        await new Promise(resolve => setTimeout(resolve, 20));

        expect(unsavedQuestion()).toBeUndefined();
    });

    test("within the page, as following a link does, is not leaving", () => {
        type(TYPED);

        expect(leave({ to: "/shacl" })).not.toHaveBeenCalled();
    });
});

describe("a save refused because the document changed elsewhere", () => {
    beforeEach(() => {
        server.answerPut = entry =>
            entry.query.has("expectedRevision")
                ? new Response(
                      JSON.stringify({ detail: "Changed since it was read." }),
                      {
                          status: 412,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : new Response(JSON.stringify({ id: EQ }), {
                      status: 200,
                      headers: { "content-type": "application/json" },
                  });
    });

    function staleDialog() {
        return [...document.querySelectorAll("[role=dialog]")].find(dialog =>
            dialog.textContent.includes("Changed elsewhere"),
        );
    }

    test("can load the stored version instead", async () => {
        server.texts[EQ] = `${SHAPES}# saved in another tab\n`;
        type(TYPED);

        pressCtrlS();
        await vi.waitFor(() => expect(staleDialog()).toBeDefined());
        expect(staleDialog().textContent).toContain(
            "Changed since it was read.",
        );
        buttonNamed("Load the stored version", staleDialog()).click();

        await vi.waitFor(() => expect(editor().value).toBe(server.texts[EQ]));
    });

    test("can overwrite it", async () => {
        type(TYPED);

        pressCtrlS();
        await vi.waitFor(() => expect(staleDialog()).toBeDefined());
        buttonNamed("Overwrite", staleDialog()).click();

        await vi.waitFor(() => expect(puts()).toHaveLength(2));
        expect(puts()[1].query.has("expectedRevision")).toBe(false);
        expect(puts()[1].body).toBe(TYPED);
    });
});

describe("the app-wide reload", () => {
    test("is not answered by reading back what the page has just written", async () => {
        type(TYPED);
        graphStore.getGraphs.mockClear();

        pressCtrlS();
        await vi.waitFor(() => expect(puts()).toHaveLength(1));
        await new Promise(resolve => setTimeout(resolve, 20));

        expect(graphStore.getGraphs).not.toHaveBeenCalledWith("cgmes", true);
    });

    test("reads everything again when something else fired it", async () => {
        graphStore.getGraphs.mockClear();

        forceReloadTrigger.trigger();

        await vi.waitFor(() =>
            expect(graphStore.getGraphs).toHaveBeenCalledWith("cgmes", true),
        );
    });

    test("keeps unsaved changes to a document that is gone, and can save them as a new one", async () => {
        type(TYPED);
        server.documents = server.documents.filter(
            document => document.id !== EQ,
        );

        forceReloadTrigger.trigger();
        await vi.waitFor(() =>
            expect(target.textContent).toContain(
                '"eq.ttl" is no longer in this workspace',
            ),
        );
        expect(editor().value).toBe(TYPED);
        buttonNamed("Save as a new document", target).click();

        await vi.waitFor(() => expect(server.texts["new-id"]).toBe(TYPED));
        expect(puts()).toEqual([]);
    });
});

describe("Ctrl+S", () => {
    test("saves unsaved changes wherever the focus is", async () => {
        type(TYPED);

        expect(pressCtrlS().defaultPrevented).toBe(true);

        await vi.waitFor(() => expect(puts()).toHaveLength(1));
        expect(puts()[0].body).toBe(TYPED);
    });

    test("says nothing when there is nothing to save", async () => {
        const error = vi.spyOn(toastStore, "error");
        openDocument("Generated shapes");
        await vi.waitFor(() => expect(editor().value).toBe("# generated\n"));

        expect(pressCtrlS().defaultPrevented).toBe(true);
        await new Promise(resolve => setTimeout(resolve, 20));

        expect(error).not.toHaveBeenCalled();
        expect(puts()).toEqual([]);
    });
});
