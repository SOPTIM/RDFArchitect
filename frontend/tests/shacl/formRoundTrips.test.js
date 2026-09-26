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

import { ShapesFormView } from "$lib/shacl/formState.svelte.js";

import FormEditorHarness from "./FormEditorHarness.svelte";
import FormEditor from "../../src/routes/shacl/workbench/FormEditor.svelte";

/**
 * What happens to the form while an edit is on its way to the server and back.
 *
 * An edit is a round trip — on a large profile well under a second, but long enough to type into —
 * and the document is read again once it lands. These pin that nothing typed, and nothing in the
 * Turtle view, is lost to either half of that.
 */

const EX = "http://example.org/shapes#";
const CIM = "http://iec.ch/TC57/CIM100#";

let mounted = null;
let target = null;

function json(value, status = 200) {
    return new Response(JSON.stringify(value), {
        status,
        headers: { "content-type": "application/json" },
    });
}

function shape(overrides = {}) {
    return {
        iri: `${EX}BreakerShape`,
        targetClasses: [`${CIM}Breaker`],
        properties: [],
        retained: [],
        editable: true,
        ...overrides,
    };
}

/**
 * A server that writes a shape's name into the text and reads it back out.
 *
 * The text is `name=<value>`, so what the server was sent and what it answers can be told apart
 * without a Turtle parser. `hold` delays the answers to edits until released.
 */
function fakeServer() {
    const server = { edits: [], reads: [], gates: [] };
    server.fetch = async request => {
        const url = new URL(request.url);
        const body = await request.text();
        if (url.pathname.endsWith("/apply")) {
            const edit = JSON.parse(body);
            server.edits.push(edit);
            if (server.hold) {
                await new Promise(resolve => server.gates.push(resolve));
            }
            return json({
                turtle: `name=${edit.shape?.name ?? ""}`,
                warnings: [],
            });
        }
        server.reads.push(body);
        return json({
            shapes: [shape({ name: body.replace(/^name=/, "") || null })],
            propertyShapes: [],
            parseError: null,
        });
    };
    server.release = () => server.gates.splice(0).forEach(open => open());
    return server;
}

function viewFor(server) {
    return new ShapesFormView({
        datasetName: "ds",
        graphUri: "http://ex.org/EQ",
        requestOptions: { fetch: server.fetch },
    });
}

function nameField() {
    const label = [...target.querySelectorAll("label")].find(
        element => element.textContent.trim() === "Name",
    );
    return label
        ? target.querySelector(`#${CSS.escape(label.getAttribute("for"))}`)
        : null;
}

function typeInto(input, value) {
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

/** Only what FormEditor reads; no requests are made. */
function fakeForm(overrides = {}) {
    return {
        shapes: [shape()],
        propertyShapes: [],
        parseError: null,
        expanded: new Set(),
        added: new Set(),
        toggle: vi.fn(),
        filter: "",
        lockedOnly: false,
        focusLine: null,
        loading: false,
        applying: false,
        error: null,
        failure: null,
        read: vi.fn(),
        reload: vi.fn(),
        flush: vi.fn(),
        showDocument: vi.fn(),
        failureOf: () => null,
        applyShape: vi.fn().mockResolvedValue({
            turtle: "new turtle",
            base: "old turtle",
            warnings: [],
        }),
        removeShape: vi.fn(),
        describes: () => true,
        ...overrides,
    };
}

function renderEditor(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(FormEditor, { target, props });
    flushSync();
    return target;
}

function openAndAdd(view) {
    [...view.querySelectorAll("button")]
        .find(button => button.textContent.includes("Add shape"))
        .click();
    flushSync();
    [...view.querySelectorAll("button")]
        .find(button => button.textContent.trim() === "Add")
        .click();
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.useRealTimers();
});

describe("typing while an edit is on its way", () => {
    test("a read that lands meanwhile keeps what was typed since", async () => {
        const server = fakeServer();
        const view = viewFor(server);
        await view.read("name=Br");

        view.shapes[0].name = "Brea";
        const sent = view.applyShape("name=Br", view.shapes[0]);
        view.shapes[0].name = "Break";
        view.schedule("name=Br", view.shapes[0], () => {});
        const { turtle } = await sent;
        view.shapes[0].name = "Breake";
        // The first edit's own text is read back while the next is still waiting to be sent.
        await view.read(turtle);

        expect(view.shapes[0].name).toBe("Breake");
    });

    test("typing on the card that read put up joins the edit already waiting", async () => {
        const server = fakeServer();
        const view = viewFor(server);
        await view.read("name=");

        const before = view.shapes[0];
        before.name = "ab";
        const sent = view.applyShape("name=", before);
        before.name = "abc";
        view.schedule("name=", before, () => {});
        await view.read((await sent).turtle);
        // A new object is on screen now; typing into it is the same edit, not a second one.
        expect(view.shapes[0]).not.toBe(before);
        view.shapes[0].name = "abcd";
        view.schedule("name=", view.shapes[0], () => {});
        await view.settle();

        expect(server.edits.map(edit => edit.shape.name)).toEqual([
            "abc",
            "abcd",
        ]);
    });

    test("once the edit is through, the document's answer is shown again", async () => {
        const server = fakeServer();
        const view = viewFor(server);
        await view.read("name=a");

        view.shapes[0].name = "b";
        const result = await view.applyShape("name=a", view.shapes[0]);
        await view.read(result.turtle);

        // Nothing is held any more, so a read of other text is shown as it is.
        await view.read("name=c");
        expect(view.shapes[0].name).toBe("c");
    });

    test("in a form on screen, a character typed during the round trip survives", async () => {
        const server = fakeServer();
        server.hold = true;
        const view = viewFor(server);
        view.expanded.add(`${EX}BreakerShape`);
        target = document.createElement("div");
        document.body.appendChild(target);
        const log = [];
        mounted = mount(FormEditorHarness, {
            target,
            props: { form: view, initial: "name=ab", log },
        });
        await vi.waitFor(() => expect(nameField()?.value).toBe("ab"));

        vi.useFakeTimers();
        typeInto(nameField(), "abc");
        await vi.advanceTimersByTimeAsync(500);
        // The edit is on its way; the user goes on typing.
        typeInto(nameField(), "abcd");
        vi.useRealTimers();
        server.release();
        await vi.waitFor(() => expect(server.reads).toContain("name=abc"));
        flushSync();
        expect(nameField().value).toBe("abcd");

        server.hold = false;
        await view.settle();
        await vi.waitFor(() => expect(log.at(-1)).toBe("name=abcd"));
    });
});

describe("the cards and the state they edit", () => {
    test("edit the form's shapes through bindings, not by mutating props", async () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
        const view = viewFor(fakeServer());
        view.expanded.add(`${EX}BreakerShape`);
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(FormEditorHarness, {
            target,
            props: { form: view, initial: "name=a" },
        });
        await vi.waitFor(() => expect(nameField()?.value).toBe("a"));

        typeInto(nameField(), "ab");

        expect(warn.mock.calls.flat().join(" ")).not.toContain(
            "ownership_invalid_mutation",
        );
        warn.mockRestore();
    });

    test("keep a rule's card with that rule when another one is removed", async () => {
        const rule = (sourceIndex, name) => ({
            sourceIndex,
            name,
            path: `${CIM}Breaker.ratedCurrent`,
            retained: [],
            usedBy: [],
            editable: true,
        });
        const view = new ShapesFormView({
            datasetName: "ds",
            graphUri: "g",
            requestOptions: {
                fetch: async () =>
                    json({
                        shapes: [
                            shape({
                                properties: [
                                    rule(0, "first"),
                                    rule(1, "second"),
                                ],
                            }),
                        ],
                        propertyShapes: [],
                        parseError: null,
                    }),
            },
        });
        view.expanded.add(`${EX}BreakerShape`);
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(FormEditorHarness, {
            target,
            props: { form: view, initial: "ttl" },
        });
        const second = () =>
            [...target.querySelectorAll("input")].find(
                input => input.value === "second",
            );
        await vi.waitFor(() => expect(second()).toBeDefined());
        const card = second();

        target.querySelectorAll('[aria-label="Remove this rule"]')[0].click();
        flushSync();

        expect(card.isConnected).toBe(true);
        expect(card.value).toBe("second");
    });
});

describe("what an edit is made to", () => {
    test("says which text the shapes on screen were read from", async () => {
        const server = fakeServer();
        const view = viewFor(server);
        await view.read("name=a");

        const first = await view.applyShape("name=a", {
            ...view.shapes[0],
            name: "b",
        });
        await view.applyShape("name=a", { ...view.shapes[0], name: "c" });

        expect(server.edits[0].baseTurtle).toBe("name=a");
        // The first edit's text is what the model says now, and what the second is made to.
        expect(server.edits[1].turtle).toBe(first.turtle);
        expect(server.edits[1].baseTurtle).toBe(first.turtle);
    });

    test("hands back the text it was made to, so the caller can tell if that is still there", async () => {
        const server = fakeServer();
        const view = viewFor(server);

        const result = await view.applyShape("name=a", shape({ name: "b" }));

        expect(result).toMatchObject({ turtle: "name=b", base: "name=a" });
    });

    test("remembers why an edit was refused, against what it was made to", async () => {
        const view = new ShapesFormView({
            datasetName: "ds",
            graphUri: "g",
            requestOptions: {
                fetch: async () =>
                    json({ detail: "The document has changed." }, 409),
            },
        });

        expect(await view.applyShape("x", shape())).toBeNull();
        expect(view.failureOf(`shape:${EX}BreakerShape`)).toBe(
            "The document has changed.",
        );
        expect(view.failureOf(`shape:${EX}OtherShape`)).toBeNull();
    });
});

describe("another document", () => {
    test("starts with no filter, no open cards and nothing waiting", async () => {
        const server = fakeServer();
        const view = viewFor(server);
        view.showDocument("first");
        view.filter = "Breaker";
        view.lockedOnly = true;
        view.expanded.add(`${EX}BreakerShape`);
        view.schedule("name=", shape({ name: "typed" }), () => {});

        view.showDocument("second");
        await view.settle();

        expect(view.filter).toBe("");
        expect(view.lockedOnly).toBe(false);
        expect(view.expanded.size).toBe(0);
        expect(server.edits).toHaveLength(0);
    });

    test("the same document shown again keeps them", () => {
        const view = viewFor(fakeServer());
        view.showDocument("first");
        view.filter = "Breaker";

        view.showDocument("first");

        expect(view.filter).toBe("Breaker");
    });
});

describe("the form editor and the buffer", () => {
    test("hands over the text an edit was made to along with its result", async () => {
        const form = fakeForm();
        const onturtle = vi.fn();
        const view = renderEditor({ form, turtle: "old turtle", onturtle });

        openAndAdd(view);
        await vi.waitFor(() => expect(onturtle).toHaveBeenCalled());

        expect(onturtle).toHaveBeenCalledWith("new turtle", "old turtle");
    });

    test("reads the buffer again when the result was not taken", async () => {
        const form = fakeForm();
        const view = renderEditor({
            form,
            turtle: "typed in the Turtle view",
            onturtle: () => false,
        });

        openAndAdd(view);
        await vi.waitFor(() =>
            expect(form.reload).toHaveBeenCalledWith(
                "typed in the Turtle view",
            ),
        );
    });

    test("puts a refused edit back and says why on the card", async () => {
        const form = fakeForm({
            applyShape: vi.fn().mockResolvedValue(null),
            error: "The shape is written as 2 statements.",
            failureOf: key =>
                key === `shape:${EX}BreakerShape`
                    ? "The shape is written as 2 statements."
                    : null,
        });
        const view = renderEditor({ form, turtle: "ttl" });

        expect(view.textContent).toContain(
            "Not applied: The shape is written as 2 statements.",
        );
        openAndAdd(view);
        await vi.waitFor(() => expect(form.reload).toHaveBeenCalledWith("ttl"));
    });

    test("sends what is still waiting for a pause when it goes", () => {
        const form = fakeForm();
        renderEditor({ form, turtle: "ttl" });

        unmount(mounted);
        mounted = null;

        expect(form.flush).toHaveBeenCalled();
    });

    test("tells the form which document it is showing", () => {
        const form = fakeForm();
        renderEditor({ form, turtle: "ttl", documentId: "doc-1" });

        expect(form.showDocument).toHaveBeenCalledWith("doc-1");
    });
});
