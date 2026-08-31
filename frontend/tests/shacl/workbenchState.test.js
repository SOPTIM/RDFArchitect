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

import { ShapesWorkbench } from "$lib/shacl/workbenchState.svelte.js";

const EQ = "eq-document-id";
const CUSTOM = "custom-document-id";

const SHAPES = `@prefix sh: <http://www.w3.org/ns/shacl#> .

# a comment that must survive the round trip
ex:Shape a sh:NodeShape .
`;

let server;
let workbench;

/** A server that remembers what it was asked, so the tests can assert on the requests. */
function fakeServer(overrides = {}) {
    const server = {
        requests: [],
        documents: [
            {
                id: CUSTOM,
                name: "custom.ttl",
                order: 1,
                enabled: true,
                default: true,
            },
            {
                id: EQ,
                name: "eq.ttl",
                order: 0,
                enabled: true,
                tripleCount: 12,
            },
        ],
        texts: { [EQ]: SHAPES, [CUSTOM]: "" },
        report: {
            valid: false,
            errorCount: 1,
            warningCount: 0,
            infoCount: 0,
            profiles: ["http://example.org/EQ/1.0"],
            documents: [
                {
                    documentId: EQ,
                    documentName: "eq.ttl",
                    valid: false,
                    errorCount: 1,
                    warningCount: 0,
                    infoCount: 0,
                    findings: [
                        {
                            severity: "ERROR",
                            source: "SHAPE",
                            code: "UNKNOWN_CLASS",
                            message: "stored finding",
                            line: 4,
                            column: 1,
                        },
                    ],
                },
            ],
        },
        bufferReport: {
            valid: true,
            errorCount: 0,
            warningCount: 0,
            infoCount: 0,
            documents: [
                {
                    documentId: EQ,
                    documentName: "eq.ttl",
                    valid: true,
                    findings: [],
                },
            ],
        },
        ...overrides,
    };

    server.fetch = async request => {
        const url = new URL(request.url);
        const entry = {
            method: request.method,
            path: url.pathname,
            query: url.searchParams,
            contentType: request.headers.get("content-type"),
            body: await request.text(),
        };
        server.requests.push(entry);
        return server.respond(entry, url);
    };

    server.respond ??= entry => {
        const json = value =>
            new Response(JSON.stringify(value), {
                status: 200,
                headers: { "content-type": "application/json" },
            });
        const documentId = entry.path.match(/\/documents\/([^/]+)$/)?.[1];

        if (entry.path.endsWith("/shacl/documents") && entry.method === "GET") {
            return json(server.documents);
        }
        if (entry.path.endsWith("/shacl/validate")) {
            return json(server.report);
        }
        if (entry.path.endsWith("/shacl/validate/text")) {
            return json(server.bufferReport);
        }
        if (documentId && entry.method === "GET") {
            return new Response(server.texts[documentId] ?? "", {
                status: 200,
                headers: { "content-type": "text/plain" },
            });
        }
        return json({ id: documentId ?? "new-id", name: "new" });
    };

    return server;
}

function workbenchFor(server) {
    return new ShapesWorkbench({
        datasetName: "cgmes",
        graphUri: "http://ex.org/EQ",
        requestOptions: { fetch: server.fetch },
    });
}

// $env/dynamic/public has no SvelteKit runtime under vitest, and the generated client reaches it
// while resolving the backend base url. It has to be absolute, because the client builds a
// Request and a relative url has no base to resolve against.
vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

beforeEach(async () => {
    vi.useRealTimers();
    server = fakeServer();
    workbench = workbenchFor(server);
});

describe("loading", () => {
    test("orders the documents, opens the first and validates what is stored", async () => {
        await workbench.load();

        expect(workbench.documents.map(document => document.name)).toEqual([
            "eq.ttl",
            "custom.ttl",
        ]);
        expect(workbench.selectedId).toBe(EQ);
        expect(workbench.text).toBe(SHAPES);
        expect(workbench.dirty).toBe(false);
        expect(workbench.profiles).toEqual(["http://example.org/EQ/1.0"]);
        expect(workbench.loading).toBe(false);
    });

    test("reports a listing failure instead of showing an empty workbench", async () => {
        server.respond = () => new Response("nope", { status: 500 });

        await workbench.load();

        expect(workbench.error).toBe(
            "The constraints documents could not be listed.",
        );
        expect(workbench.documents).toEqual([]);
    });
});

describe("editing and saving", () => {
    test("tracks unsaved changes", async () => {
        await workbench.load();

        workbench.text = `${SHAPES}ex:Other a sh:NodeShape .\n`;
        expect(workbench.dirty).toBe(true);

        expect(await workbench.save()).toBe(true);
        expect(workbench.dirty).toBe(false);
    });

    test("sends the Turtle verbatim rather than JSON-quoted", async () => {
        // The endpoint takes a plain String @RequestBody, which Spring reads as-is: a serialized
        // body arrives at Jena with its quotes and escapes and fails to parse.
        await workbench.load();
        workbench.text = SHAPES;
        server.requests.length = 0;

        await workbench.save();

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.contentType).toBe("text/plain");
        expect(put.body).toBe(SHAPES);
    });

    test("re-reads the documents and revalidates after a save", async () => {
        await workbench.load();
        workbench.text = "ex:X a sh:NodeShape .";
        server.requests.length = 0;

        await workbench.save();

        expect(
            server.requests.map(entry => `${entry.method} ${entry.path}`),
        ).toEqual([
            expect.stringContaining("PUT"),
            expect.stringContaining("GET"),
            expect.stringContaining("POST"),
        ]);
    });

    test("keeps the buffer when the save fails", async () => {
        await workbench.load();
        const edited = "ex:X a sh:NodeShape .";
        workbench.text = edited;
        server.respond = entry =>
            entry.method === "PUT"
                ? new Response("nope", { status: 500 })
                : new Response("{}", {
                      status: 200,
                      headers: { "content-type": "application/json" },
                  });

        expect(await workbench.save()).toBe(false);
        expect(workbench.text).toBe(edited);
        expect(workbench.dirty).toBe(true);
    });
});

describe("validation", () => {
    test("names the open document so its own saved copy is not counted as a conflict", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}\n`;

        await workbench.validateBuffer();

        const post = server.requests.at(-1);
        expect(post.path).toMatch(/\/shacl\/validate\/text$/);
        expect(post.query.get("documentId")).toBe(EQ);
        expect(post.query.get("name")).toBe("eq.ttl");
        expect(post.contentType).toBe("text/plain");
    });

    test("prefers the buffer result for the open document while it is current", async () => {
        await workbench.load();
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "stored finding",
        ]);

        workbench.text = `${SHAPES}\n`;
        await workbench.validateBuffer();
        expect(workbench.findings).toEqual([]);

        // Typing again makes the answer stale, so the stored report is shown until the next run.
        workbench.text = `${SHAPES}\n\n`;
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "stored finding",
        ]);
    });

    test("drops an answer that arrives after the text moved on", async () => {
        await workbench.load();
        let release;
        const held = new Promise(resolve => {
            release = () =>
                resolve(
                    new Response(JSON.stringify(server.bufferReport), {
                        status: 200,
                        headers: { "content-type": "application/json" },
                    }),
                );
        });
        server.respond = entry =>
            entry.path.endsWith("/validate/text")
                ? held
                : new Response("{}", {
                      status: 200,
                      headers: { "content-type": "application/json" },
                  });

        workbench.text = "first";
        const inFlight = workbench.validateBuffer();
        workbench.text = "second";
        release();
        await inFlight;

        expect(workbench.bufferReport).toBeNull();
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "stored finding",
        ]);
    });

    test("a burst of keystrokes produces one request", async () => {
        vi.useFakeTimers();
        await workbench.load();
        server.requests.length = 0;

        workbench.text = "a";
        workbench.scheduleValidation(50);
        workbench.text = "ab";
        workbench.scheduleValidation(50);
        workbench.text = "abc";
        workbench.scheduleValidation(50);

        expect(server.requests).toHaveLength(0);
        await vi.advanceTimersByTimeAsync(60);

        expect(
            server.requests.filter(entry =>
                entry.path.endsWith("/validate/text"),
            ),
        ).toHaveLength(1);
    });

    test("selecting another document cancels a pending run", async () => {
        vi.useFakeTimers();
        await workbench.load();
        workbench.text = "a";
        workbench.scheduleValidation(50);
        server.requests.length = 0;

        await workbench.select(CUSTOM);
        await vi.advanceTimersByTimeAsync(60);

        expect(
            server.requests.filter(entry =>
                entry.path.endsWith("/validate/text"),
            ),
        ).toHaveLength(0);
    });
});

describe("the document list", () => {
    test("totals the counts across documents", async () => {
        await workbench.load();

        expect(workbench.totals).toEqual({
            errorCount: 1,
            warningCount: 0,
            infoCount: 0,
        });
        expect(workbench.results.map(result => result.documentName)).toEqual([
            "eq.ttl",
            "custom.ttl",
        ]);
    });

    test("a document with no result yet counts as clean rather than missing", async () => {
        await workbench.load();

        expect(workbench.resultFor(CUSTOM)).toMatchObject({
            valid: true,
            errorCount: 0,
            findings: [],
        });
    });

    test("does not try to move a document past either end", async () => {
        await workbench.load();
        server.requests.length = 0;

        expect(await workbench.move(EQ, -1)).toBe(false);
        expect(await workbench.move(CUSTOM, 1)).toBe(false);
        expect(server.requests).toHaveLength(0);

        expect(await workbench.move(EQ, 1)).toBe(true);
        expect(server.requests[0].query.get("order")).toBe("1");
    });

    test("enabling a document is a change to it, not to the text", async () => {
        await workbench.load();
        server.requests.length = 0;

        await workbench.setEnabled(EQ, false);

        expect(server.requests[0].method).toBe("PATCH");
        expect(server.requests[0].query.get("enabled")).toBe("false");
    });

    test("deleting the open document opens whatever is left", async () => {
        await workbench.load();
        server.documents = server.documents.filter(
            document => document.id !== EQ,
        );

        expect(await workbench.remove(EQ)).toBe(true);
        expect(workbench.selectedId).toBe(CUSTOM);
    });
});
