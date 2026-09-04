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

import { beforeEach, describe, expect, test, vi } from "vitest";

import {
    newShape,
    shapeNamespaceOf,
    ShapesFormView,
} from "$lib/shacl/formState.svelte.js";

const SHAPES = [
    {
        iri: "http://example.org/shapes#ACLineSegmentShape",
        targetClasses: ["http://iec.ch/TC57/CIM100#ACLineSegment"],
        properties: [],
        retained: [],
        editable: true,
    },
];

const RULE = "http://example.org/shapes#NameCardinality";

let server;
let view;

function fakeServer(overrides = {}) {
    const server = {
        requests: [],
        form: { shapes: SHAPES, parseError: null },
        edited: { turtle: "edited turtle", warnings: [] },
        ...overrides,
    };
    server.fetch = async request => {
        const url = new URL(request.url);
        server.requests.push({
            path: url.pathname,
            contentType: request.headers.get("content-type"),
            body: await request.text(),
        });
        const json = value =>
            new Response(JSON.stringify(value), {
                status: 200,
                headers: { "content-type": "application/json" },
            });
        return url.pathname.endsWith("/apply")
            ? json(server.edited)
            : json(server.form);
    };
    return server;
}

function viewFor(server) {
    return new ShapesFormView({
        datasetName: "cgmes",
        graphUri: "http://ex.org/EQ",
        requestOptions: { fetch: server.fetch },
    });
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

beforeEach(() => {
    server = fakeServer();
    view = viewFor(server);
});

describe("reading the buffer as shapes", () => {
    test("sends the Turtle verbatim and keeps the shapes", async () => {
        const turtle =
            "@prefix sh: <http://www.w3.org/ns/shacl#> .\n\n# a note\nex:S a sh:NodeShape .\n";

        await view.read(turtle);

        expect(server.requests[0].contentType).toBe("text/plain");
        expect(server.requests[0].body).toBe(turtle);
        expect(view.shapes).toHaveLength(1);
        expect(view.describes(turtle)).toBe(true);
    });

    test("does not re-read text it has already read", async () => {
        await view.read("same");
        await view.read("same");

        expect(server.requests).toHaveLength(1);
    });

    test("re-reads once the text has changed", async () => {
        await view.read("first");
        await view.read("second");

        expect(server.requests).toHaveLength(2);
        expect(view.describes("first")).toBe(false);
    });

    test("surfaces a syntax error instead of an empty list of shapes", async () => {
        server.form = {
            shapes: [],
            parseError: { message: "Undefined prefix: ex", line: 3, column: 1 },
        };

        await view.read("broken");

        expect(view.parseError.message).toBe("Undefined prefix: ex");
        expect(view.shapes).toEqual([]);
    });

    test("keeps the rules the document writes as shapes of their own", async () => {
        server.form = {
            shapes: SHAPES,
            propertyShapes: [{ iri: RULE, usedBy: [SHAPES[0].iri] }],
            parseError: null,
        };

        await view.read("shapes");

        expect(view.propertyShapes).toHaveLength(1);
        expect(view.propertyShapes[0].iri).toBe(RULE);
    });

    test("an earlier read that answers late does not replace a newer one", async () => {
        // The regression this guards: switching to the form and typing straight away leaves two
        // reads in flight. The older answering last used to leave the cards describing text the
        // buffer had moved past — and applying an edit from one rewrote the wrong statement.
        const gates = {};
        server.fetch = async request => {
            const body = await request.text();
            const shapes = [{ ...SHAPES[0], iri: `urn:shape:${body}` }];
            await new Promise(resolve => {
                gates[body] = resolve;
            });
            return new Response(JSON.stringify({ shapes, parseError: null }), {
                status: 200,
                headers: { "content-type": "application/json" },
            });
        };
        const racing = viewFor(server);

        const older = racing.read("older");
        const newer = racing.read("newer");
        await vi.waitFor(() => expect(Object.keys(gates)).toHaveLength(2));

        gates.newer();
        await newer;
        gates.older();
        await older;

        expect(racing.shapes[0].iri).toBe("urn:shape:newer");
        expect(racing.describes("newer")).toBe(true);
        expect(racing.loading).toBe(false);
    });

    test("reports a failure rather than showing a stale form", async () => {
        server.fetch = async () => new Response("nope", { status: 500 });
        const failing = viewFor(server);

        await failing.read("anything");

        expect(failing.error).toBe(
            "The constraints could not be read as a form.",
        );
    });
});

describe("applying an edit", () => {
    test("sends the document and the shape, and hands back the new text", async () => {
        const result = await view.applyShape("original", SHAPES[0]);

        expect(result.turtle).toBe("edited turtle");
        const sent = JSON.parse(server.requests.at(-1).body);
        expect(sent.turtle).toBe("original");
        expect(sent.shape.iri).toBe(
            "http://example.org/shapes#ACLineSegmentShape",
        );
    });

    test("passes on the warnings the rewrite produced", async () => {
        server.edited = { turtle: "x", warnings: ["Comments were removed"] };

        const result = await view.applyShape("original", SHAPES[0]);

        expect(result.warnings).toEqual(["Comments were removed"]);
    });

    test("names the shape when removing one", async () => {
        await view.removeShape("original", "http://example.org/shapes#Gone");

        const sent = JSON.parse(server.requests.at(-1).body);
        expect(sent.removeShapeIri).toBe("http://example.org/shapes#Gone");
        expect(sent.shape).toBeUndefined();
    });

    test("sends a rule the document names on its own, as a rule", async () => {
        // Its own statement is what a shared rule's clauses live in, so that is what is rewritten
        // — never the shape the change happened to be made under.
        const rule = { iri: RULE, minCount: 0, usedBy: [] };

        await view.applyRule("original", rule);

        const sent = JSON.parse(server.requests.at(-1).body);
        expect(sent.propertyShape.iri).toBe(RULE);
        expect(sent.shape).toBeUndefined();
    });

    test("asks for a copy when the change is meant for one shape only", async () => {
        const rule = { iri: RULE, minCount: 0, sourceIndex: 1 };

        await view.applyRule("original", rule, {
            newIri: "http://example.org/shapes#LineName",
            nodeShapeIri: SHAPES[0].iri,
            sourceIndex: 1,
        });

        const sent = JSON.parse(server.requests.at(-1).body);
        expect(sent.split.newIri).toBe("http://example.org/shapes#LineName");
        expect(sent.split.nodeShapeIri).toBe(SHAPES[0].iri);
        expect(sent.split.sourceIndex).toBe(1);
    });

    test("reads the buffer again on request, undoing what was typed", async () => {
        // What cancelling a shared-rule change needs: the card holds the typed value, and only
        // the document knows what stood there before it.
        await view.read("original");
        await view.reload("original");

        expect(server.requests).toHaveLength(2);
    });

    test("makes the next read fetch again, because the document changed", async () => {
        await view.read("original");
        await view.applyShape("original", SHAPES[0]);

        expect(view.describes("original")).toBe(false);
    });

    test("returns nothing and says so when the edit fails", async () => {
        server.fetch = async () => new Response("nope", { status: 500 });
        const failing = viewFor(server);

        expect(await failing.applyShape("original", SHAPES[0])).toBeNull();
        expect(failing.error).toBe("nope");
    });

    test("says why the server refused, because that is the actionable part", async () => {
        // The server refuses a shape written as two statements, or a rule with no property, and
        // says which. Replacing that with "could not be applied" threw the answer away.
        server.fetch = async () =>
            new Response(
                JSON.stringify({
                    detail: "This shape is written as 2 separate statements.",
                }),
                {
                    status: 409,
                    headers: { "content-type": "application/json" },
                },
            );
        const refusing = viewFor(server);

        expect(await refusing.applyShape("original", SHAPES[0])).toBeNull();
        expect(refusing.error).toBe(
            "This shape is written as 2 separate statements.",
        );
    });
});

describe("one edit at a time", () => {
    test("applies the next edit to the text the last one produced", async () => {
        // Both edits are handed the buffer as it stood before either of them, because the buffer
        // only catches up once an edit comes back. Sending both against that text applied one and
        // lost the other.
        let round = 0;
        server.fetch = async request => {
            const body = JSON.parse(await request.text());
            server.requests.push({ path: new URL(request.url).pathname, body });
            round += 1;
            return new Response(
                JSON.stringify({ turtle: `after ${round}`, warnings: [] }),
                {
                    status: 200,
                    headers: { "content-type": "application/json" },
                },
            );
        };
        const queued = viewFor(server);

        const first = queued.applyShape("original", SHAPES[0]);
        const second = queued.applyShape("original", SHAPES[0]);
        expect((await first).turtle).toBe("after 1");
        expect((await second).turtle).toBe("after 2");

        expect(server.requests.map(sent => sent.body.turtle)).toEqual([
            "original",
            "after 1",
        ]);
    });

    test("a failed edit does not stop the ones behind it", async () => {
        let firstCall = true;
        server.fetch = async () => {
            if (firstCall) {
                firstCall = false;
                return new Response("nope", { status: 500 });
            }
            return new Response(
                JSON.stringify({ turtle: "recovered", warnings: [] }),
                {
                    status: 200,
                    headers: { "content-type": "application/json" },
                },
            );
        };
        const queued = viewFor(server);

        const failed = queued.applyShape("original", SHAPES[0]);
        const next = queued.applyShape("original", SHAPES[0]);

        expect(await failed).toBeNull();
        expect((await next).turtle).toBe("recovered");
    });
});

describe("collecting what is being typed", () => {
    test("sends one request for a scheduled edit, not one per keystroke", async () => {
        const handled = [];

        view.schedule("original", SHAPES[0], result => handled.push(result));
        view.schedule("original", SHAPES[0], result => handled.push(result));
        view.schedule("original", SHAPES[0], result => handled.push(result));
        expect(server.requests).toHaveLength(0);

        await view.settle();

        expect(server.requests).toHaveLength(1);
        expect(handled).toHaveLength(1);
        expect(handled[0].turtle).toBe("edited turtle");
    });

    test("an edit to another shape goes first, because the later one builds on it", async () => {
        const other = { ...SHAPES[0], iri: "http://example.org/shapes#Other" };

        view.schedule("original", SHAPES[0], () => {});
        view.schedule("original", other, () => {});
        await view.settle();

        expect(
            server.requests.map(sent => JSON.parse(sent.body).shape.iri),
        ).toEqual([SHAPES[0].iri, other.iri]);
    });

    test("applying a shape at once sends what was typed into it as part of that edit", async () => {
        view.schedule("original", SHAPES[0], () => {});
        await view.applyShape("original", SHAPES[0]);

        // One request: the scheduled edit was superseded by the one that carries the same shape.
        expect(server.requests).toHaveLength(1);
    });

    test("a buffer that moved for another reason drops what was typed", async () => {
        // Typing in the Turtle view within the pause. Sending the scheduled edit would overwrite
        // that change with text that no longer exists.
        const handled = vi.fn();
        view.schedule("original", SHAPES[0], handled);
        await view.read("typed in the editor instead");
        await view.settle();

        expect(server.requests.map(sent => sent.path)).not.toContain(
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fex.org%2FEQ/shacl/form/apply",
        );
        expect(handled).not.toHaveBeenCalled();
    });
});

describe("naming a new shape", () => {
    test("puts it in the namespace the document's shapes already use", () => {
        expect(shapeNamespaceOf(SHAPES, {})).toBe("http://example.org/shapes#");
    });

    test("falls back to the document's own prefixes, then to something usable", () => {
        expect(shapeNamespaceOf([], { "": "http://example.org/" })).toBe(
            "http://example.org/",
        );
        expect(shapeNamespaceOf([], { ex: "http://other.org/" })).toBe(
            "http://other.org/",
        );
        expect(shapeNamespaceOf([], {})).toBe("urn:rdfa:shapes#");
    });

    test("names a shape after the class it targets", () => {
        const shape = newShape(
            "http://example.org/shapes#",
            "http://ex.org/Breaker",
            "Breaker",
        );

        expect(shape.iri).toBe("http://example.org/shapes#BreakerShape");
        expect(shape.editable).toBe(true);
        expect(shape.properties).toEqual([]);
        // A list, because a shape may target several classes; a shape added with no class yet
        // targets none rather than targeting null.
        expect(shape.targetClasses).toEqual(["http://ex.org/Breaker"]);
        expect(
            newShape("http://example.org/shapes#", null, "New").targetClasses,
        ).toEqual([]);
    });
});
