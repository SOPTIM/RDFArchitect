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

import { SchemaTermSource } from "$lib/shacl/schemaTermSource.svelte.js";

const CIM = "http://iec.ch/TC57/CIM100#";

const TERMS = {
    profiles: ["http://ex.org/EQ/1.0"],
    terms: [
        {
            kind: "CLASS",
            iri: `${CIM}ACLineSegment`,
            namespace: CIM,
            localName: "ACLineSegment",
        },
    ],
};

const DETAIL = {
    kind: "CLASS",
    iri: `${CIM}ACLineSegment`,
    namespace: CIM,
    localName: "ACLineSegment",
    comment: "A wire.",
};

let server;
let source;

function fakeServer(overrides = {}) {
    const server = {
        requests: [],
        terms: TERMS,
        detail: DETAIL,
        detailStatus: 200,
        ...overrides,
    };
    server.fetch = async request => {
        const url = new URL(request.url);
        server.requests.push({
            path: url.pathname,
            schemas: url.searchParams.getAll("schema"),
            iri: url.searchParams.get("iri"),
        });
        const json = (value, status = 200) =>
            new Response(JSON.stringify(value), {
                status,
                headers: { "content-type": "application/json" },
            });
        return url.pathname.endsWith("/detail")
            ? json(server.detail, server.detailStatus)
            : json(server.terms);
    };
    return server;
}

function sourceFor(server) {
    return new SchemaTermSource({
        workspaceName: "cgmes",
        requestOptions: { fetch: server.fetch },
    });
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

beforeEach(() => {
    server = fakeServer();
    source = sourceFor(server);
});

describe("loading the term list", () => {
    test("fetches once however often it is asked", async () => {
        // Every keystroke calls load(); the request must not be repeated per keystroke.
        await Promise.all([source.load(), source.load(), source.load()]);
        await source.load();

        expect(
            server.requests.filter(r => !r.path.endsWith("/detail")),
        ).toHaveLength(1);
        expect(source.terms).toHaveLength(1);
        expect(source.profiles).toEqual(["http://ex.org/EQ/1.0"]);
        expect(source.loaded).toBe(true);
    });

    test("stays empty and usable when the schema cannot be read", async () => {
        server.fetch = async () => new Response("nope", { status: 500 });
        const failing = sourceFor(server);

        await failing.load();

        expect(failing.terms).toEqual([]);
        expect(failing.loaded).toBe(false);
    });
});

describe("term details", () => {
    test("describes a term and remembers the answer", async () => {
        expect(await source.detailOf(`${CIM}ACLineSegment`)).toMatchObject({
            comment: "A wire.",
        });
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(
            server.requests.filter(r => r.path.endsWith("/detail")),
        ).toHaveLength(1);
    });

    test("asks once when the same term is hovered twice in quick succession", async () => {
        const [first, second] = await Promise.all([
            source.detailOf(`${CIM}ACLineSegment`),
            source.detailOf(`${CIM}ACLineSegment`),
        ]);

        expect(first).toEqual(second);
        expect(
            server.requests.filter(r => r.path.endsWith("/detail")),
        ).toHaveLength(1);
    });

    test("remembers that a term is unknown, so reading a file is not a flood of requests", async () => {
        server.detailStatus = 404;

        expect(await source.detailOf("http://ex.org/Nonsense")).toBeNull();
        expect(await source.detailOf("http://ex.org/Nonsense")).toBeNull();

        expect(
            server.requests.filter(r => r.path.endsWith("/detail")),
        ).toHaveLength(1);
    });

    test("a term answered with no content has no detail", async () => {
        const answer = server.fetch;
        server.fetch = async request => {
            if (!new URL(request.url).pathname.endsWith("/detail")) {
                return answer(request);
            }
            server.requests.push({ path: "/detail" });
            // With a JSON content type, as a gateway may add, the client reads the body as `{}`.
            return new Response(null, {
                status: 204,
                headers: { "content-type": "application/json" },
            });
        };
        const quiet = sourceFor(server);

        expect(await quiet.detailOf("http://ex.org/Nonsense")).toBeNull();
        expect(await quiet.detailOf("http://ex.org/Nonsense")).toBeNull();

        expect(
            server.requests.filter(r => r.path.endsWith("/detail")),
        ).toHaveLength(1);
    });

    test("asks for the class in the schemas it prefers, first to last", async () => {
        source.preferSchemas(["SSH", "EQ"]);

        await source.detailOf(`${CIM}ACLineSegment`);

        expect(server.requests.at(-1).schemas).toEqual(["SSH", "EQ"]);
    });

    test("asks again once other schemas are preferred, and remembers both", async () => {
        source.preferSchemas(["EQ"]);
        await source.detailOf(`${CIM}ACLineSegment`);
        source.preferSchemas(["SSH"]);
        await source.detailOf(`${CIM}ACLineSegment`);
        source.preferSchemas(["EQ"]);
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(
            server.requests
                .filter(r => r.path.endsWith("/detail"))
                .map(r => r.schemas),
        ).toEqual([["EQ"], ["SSH"]]);
    });

    test("passes the IRI as a query parameter", async () => {
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(server.requests.at(-1).iri).toBe(`${CIM}ACLineSegment`);
    });
});

describe("the standard vocabulary", () => {
    test("SHACL's own terms are offered even though no profile declares them", async () => {
        await source.load();

        // The schema index knows CIM. A constraints file is written almost entirely in sh:, and
        // completing none of it is what made the editor feel like a plain text box.
        expect(source.completionTerms).toContainEqual(
            expect.objectContaining({
                iri: "http://www.w3.org/ns/shacl#minCount",
                kind: "PROPERTY",
            }),
        );
        expect(source.completionTerms).toContainEqual(
            expect.objectContaining({
                iri: "http://www.w3.org/ns/shacl#NodeShape",
                kind: "CLASS",
            }),
        );
        expect(source.completionTerms).toContainEqual(
            expect.objectContaining({
                iri: "http://www.w3.org/2001/XMLSchema#gMonthDay",
            }),
        );
    });

    test("the schema's own terms are still there", async () => {
        await source.load();

        expect(source.completionTerms).toContainEqual(
            expect.objectContaining({ iri: `${CIM}ACLineSegment` }),
        );
    });

    test("terms stays the schema's own, without sh: in it", async () => {
        await source.load();

        // "Which property of my schema is this rule about?" is never answered by sh:minCount.
        expect(source.terms).toHaveLength(1);
        expect(source.terms[0].iri).toBe(`${CIM}ACLineSegment`);
    });

    test("a standard term is explained without asking the server", async () => {
        const detail = await source.detailOf("http://www.w3.org/ns/shacl#path");

        expect(detail.comment).toContain("property");
        expect(server.requests).toHaveLength(0);
    });

    test("says sh:ask belongs to a validator, which SHACL gives it to", async () => {
        const detail = await source.detailOf("http://www.w3.org/ns/shacl#ask");

        // A sh:SPARQLConstraint takes exactly one sh:select; ASK is for sh:SPARQLAskValidator.
        expect(detail.comment).toContain("validator");
        expect(detail.comment).toContain("conforms");
    });

    test("anything else is still a question for the schema", async () => {
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(server.requests.at(-1).iri).toBe(`${CIM}ACLineSegment`);
    });
});

describe("invalidate", () => {
    /** Holds the answer to the first request `matches` accepts, until the gate is released. */
    function holdAnswers(matches) {
        let release;
        let holding = true;
        const gate = new Promise(resolve => (release = resolve));
        const answer = server.fetch;
        server.fetch = async request => {
            const hold = holding && matches(new URL(request.url));
            holding &&= !hold;
            const response = await answer(request);
            if (hold) {
                await gate;
            }
            return response;
        };
        source = sourceFor(server);
        return () => release();
    }

    test("drops a term list asked for before it", async () => {
        const release = holdAnswers(url => !url.pathname.endsWith("/detail"));
        const stale = source.load();
        await vi.waitFor(() => expect(server.requests).toHaveLength(1));

        source.invalidate();
        server.terms = { profiles: [], terms: [] };
        await source.load();
        release();
        await stale;

        expect(source.terms).toEqual([]);
        expect(source.loaded).toBe(true);
    });

    test("drops a detail asked for before it", async () => {
        const release = holdAnswers(url => url.pathname.endsWith("/detail"));
        const stale = source.detailOf(`${CIM}ACLineSegment`);
        await vi.waitFor(() => expect(server.requests).toHaveLength(1));

        source.invalidate();
        release();
        await stale;
        server.detail = { ...DETAIL, comment: "A wire, edited." };

        expect(await source.detailOf(`${CIM}ACLineSegment`)).toMatchObject({
            comment: "A wire, edited.",
        });
    });

    test("makes the next question re-read a schema that has changed", async () => {
        await source.load();
        await source.detailOf(`${CIM}ACLineSegment`);
        server.requests.length = 0;

        source.invalidate();
        await source.load();
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(server.requests).toHaveLength(2);
    });
});
