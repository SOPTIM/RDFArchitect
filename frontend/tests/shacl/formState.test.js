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
        targetClass: "http://iec.ch/TC57/CIM100#ACLineSegment",
        properties: [],
        unsupported: [],
        editable: true,
    },
];

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

    test("makes the next read fetch again, because the document changed", async () => {
        await view.read("original");
        await view.applyShape("original", SHAPES[0]);

        expect(view.describes("original")).toBe(false);
    });

    test("returns nothing and says so when the edit fails", async () => {
        server.fetch = async () => new Response("nope", { status: 500 });
        const failing = viewFor(server);

        expect(await failing.applyShape("original", SHAPES[0])).toBeNull();
        expect(failing.error).toBe("The change could not be applied.");
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
    });
});
