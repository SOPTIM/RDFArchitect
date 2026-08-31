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

    test("passes the IRI as a query parameter", async () => {
        await source.detailOf(`${CIM}ACLineSegment`);

        expect(server.requests.at(-1).iri).toBe(`${CIM}ACLineSegment`);
    });
});

describe("invalidate", () => {
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
