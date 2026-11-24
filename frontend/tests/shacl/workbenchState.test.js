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
    ShapesWorkbench,
    generatedId,
    revisionOf,
} from "$lib/shacl/workbenchState.svelte.js";

const EQ = "eq-document-id";
const CUSTOM = "custom-document-id";

const EQ_SCHEMA = "EQ";
const SSH_SCHEMA = "SSH";
const GENERATED_ID = generatedId(EQ_SCHEMA);

const SHAPES = `@prefix sh: <http://www.w3.org/ns/shacl#> .

# a comment that must survive the round trip
ex:Shape a sh:NodeShape .
`;

// Read-only is a property of the workspace, fetched separately from everything the fake server
// below answers. Writable unless a test says otherwise.
const readOnly = vi.fn().mockResolvedValue(false);
/** The workspace's schemas, as the graph store answers them. */
const getGraphs = vi.fn();
/** Whoever follows the workspace list, so a test can announce a change to it. */
const workspaceListeners = new Set();

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
                schemas: [EQ_SCHEMA],
            },
            {
                id: EQ,
                name: "eq.ttl",
                order: 0,
                enabled: true,
                tripleCount: 12,
                schemas: [EQ_SCHEMA],
            },
        ],
        texts: { [EQ]: SHAPES, [CUSTOM]: "" },
        generated:
            "# generated from the schema\nex:Generated a sh:NodeShape .\n",
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
        if (entry.path.endsWith("/shacl/generate/string")) {
            return new Response(server.generated, {
                status: 200,
                headers: { "content-type": "text/plain" },
            });
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

function graph(keyword) {
    return { name: keyword, keyword };
}

function workbenchFor(server, preferredSchema = EQ_SCHEMA) {
    return new ShapesWorkbench({
        workspaceName: "cgmes",
        preferredSchema,
        requestOptions: { fetch: server.fetch },
    });
}

function announceWorkspaces(data) {
    workspaceListeners.forEach(listener => listener({ data }));
}

/** A promise a test resolves by hand, to hold a response until it chooses. */
function held() {
    let release;
    const promise = new Promise(resolve => (release = resolve));
    return { promise, release };
}

/** Holds every request `matches` accepts until the returned gate is released. */
function holdRequests(matches) {
    const gate = held();
    const answer = server.respond;
    server.respond = async (entry, url) => {
        if (matches(entry)) {
            await gate.promise;
        }
        return answer(entry, url);
    };
    return gate;
}

/** What the text endpoint answers, holding one error with this message. */
function bufferReportWith(message, line = 4) {
    return {
        valid: false,
        errorCount: 1,
        warningCount: 0,
        infoCount: 0,
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
                        message,
                        line,
                        column: 1,
                    },
                ],
            },
        ],
    };
}

/** What the stored-documents endpoint answers when it finds nothing. */
function cleanReport() {
    return {
        valid: true,
        errorCount: 0,
        warningCount: 0,
        infoCount: 0,
        profiles: [],
        documents: [
            {
                documentId: EQ,
                documentName: "eq.ttl",
                valid: true,
                errorCount: 0,
                warningCount: 0,
                infoCount: 0,
                findings: [],
            },
        ],
    };
}

// $env/dynamic/public has no SvelteKit runtime under vitest, and the generated client reaches it
// while resolving the backend base url. It has to be absolute, because the client builds a
// Request and a relative url has no base to resolve against.
vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: (...args) => getGraphs(...args) },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        isReadOnly: (...args) => readOnly(...args),
        subscribe: listener => {
            workspaceListeners.add(listener);
            return () => workspaceListeners.delete(listener);
        },
    },
}));

beforeEach(async () => {
    vi.useRealTimers();
    workspaceListeners.clear();
    readOnly.mockResolvedValue(false);
    getGraphs.mockResolvedValue([graph("EQ"), graph("SSH")]);
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

    test("opens the first document with content, not an empty one before it", async () => {
        server.documents = [
            { ...server.documents[0], order: 0, tripleCount: 0 },
            { ...server.documents[1], order: 1 },
        ];

        await workbench.load();

        expect(workbench.documents[0].id).toBe(CUSTOM);
        expect(workbench.selectedId).toBe(EQ);
    });

    test("opens the first document when every one is empty", async () => {
        server.documents = server.documents.map(document => ({
            ...document,
            tripleCount: 0,
        }));

        await workbench.load();

        expect(workbench.selectedId).toBe(workbench.documents[0].id);
    });

    test("opens the generated shapes when the schema has no document", async () => {
        server.documents = [];

        await workbench.load();

        expect(workbench.selectedId).toBe(GENERATED_ID);
        expect(workbench.text).toContain("generated from the schema");
        expect(workbench.editorReadOnly).toBe(true);
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

        expect(await workbench.save()).toEqual({ saved: true, reason: null });
        expect(workbench.dirty).toBe(false);
    });

    test("sends the Turtle verbatim rather than JSON-quoted", async () => {
        // The endpoint takes a plain String @RequestBody, which Spring reads as-is: a serialized
        // body arrives at Jena with its quotes and escapes and fails to parse.
        await workbench.load();
        const edited = `${SHAPES}# "quoted" and \\escaped\n`;
        workbench.text = edited;
        server.requests.length = 0;

        await workbench.save();

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.contentType).toBe("text/plain");
        expect(put.body).toBe(edited);
    });

    test("names the text it was edited from, so a stale save can be refused", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# edited\n`;
        server.requests.length = 0;

        await workbench.save();

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.query.get("expectedRevision")).toBe(
            await revisionOf(SHAPES),
        );
    });

    test("names an emptied document's text as the server stores it", async () => {
        // The server keeps whitespace-only Turtle as the empty document it means. Naming the
        // blank text sent instead made the next save look stale, and refused it.
        await workbench.load();
        workbench.text = "\n  \n";
        await workbench.save();
        workbench.text = "# started again\n";
        server.requests.length = 0;

        await workbench.save();

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.query.get("expectedRevision")).toBe(await revisionOf(""));
    });

    test("names a document holding only a no-break space by that text", async () => {
        // Java's isBlank, which the server uses, keeps it as content; trim() would not.
        await workbench.load();
        workbench.text = "\u00A0";
        await workbench.save();
        workbench.text = "# started again\n";
        server.requests.length = 0;

        await workbench.save();

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.query.get("expectedRevision")).toBe(
            await revisionOf("\u00A0"),
        );
    });

    test("reports a save refused because the document changed elsewhere", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# mine\n`;
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "PUT"
                ? new Response(
                      JSON.stringify({ status: 412, detail: "changed since" }),
                      {
                          status: 412,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : answer(entry, url);

        const result = await workbench.save();

        expect(result).toMatchObject({ saved: false, stale: true });
        expect(workbench.dirty).toBe(true);
    });

    test("overwrites without naming a revision when asked to", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# mine\n`;
        server.requests.length = 0;

        await workbench.save({ overwrite: true });

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.query.has("expectedRevision")).toBe(false);
    });

    test("can throw the edits away for the stored version", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# mine\n`;
        server.texts[EQ] = `${SHAPES}# theirs\n`;

        expect(await workbench.loadStored()).toBe(true);

        expect(workbench.text).toBe(`${SHAPES}# theirs\n`);
        expect(workbench.dirty).toBe(false);
    });

    test("revisions are the hex SHA-256 of the UTF-8 text, as the backend computes them", async () => {
        expect(await revisionOf("ä")).toBe(
            "33e6d73fee82904c8d7afb78de1154d1e8dc2a0edb08120e63df5b9385c2d9cc",
        );
    });

    test("does not write a document that has not changed", async () => {
        await workbench.load();
        server.requests.length = 0;

        expect(await workbench.save()).toEqual({
            saved: true,
            reason: null,
            unchanged: true,
        });
        expect(server.requests).toEqual([]);
    });

    test("says it is saving while the write is on its way", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# edited\n`;
        const gate = holdRequests(entry => entry.method === "PUT");

        const saving = workbench.save();
        await vi.waitFor(() => expect(workbench.saving).toBe(true));
        gate.release();
        await saving;

        expect(workbench.saving).toBe(false);
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

    test("keeps the server's reason when an import is refused", async () => {
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "POST" && entry.path.endsWith("/documents/file")
                ? new Response(
                      JSON.stringify({
                          status: 400,
                          detail: "[line: 7, col: 3 ] Undefined prefix: ex",
                      }),
                      {
                          status: 400,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : answer(entry, url);

        expect(await workbench.importFile(new Blob(["x"]), "f.ttl")).toBeNull();
        expect(workbench.lastError).toContain("Undefined prefix");
    });

    test("keeps the buffer when the save fails", async () => {
        await workbench.load();
        const edited = "ex:X a sh:NodeShape .";
        workbench.text = edited;
        server.respond = entry =>
            entry.method === "PUT"
                ? new Response(
                      JSON.stringify({
                          status: 400,
                          detail: "[line: 3, col: 1 ] Undefined prefix: ex",
                      }),
                      {
                          status: 400,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : new Response("{}", {
                      status: 200,
                      headers: { "content-type": "application/json" },
                  });

        const { saved, reason } = await workbench.save();

        expect(saved).toBe(false);
        // The server's own explanation, not a generic "could not be saved".
        expect(reason).toBe("[line: 3, col: 1 ] Undefined prefix: ex");
        expect(workbench.text).toBe(edited);
        expect(workbench.dirty).toBe(true);
    });
});

describe("a read-only workspace", () => {
    // Read-only is advisory: the backend reports it but does not enforce it, so every write here
    // has to check. The class editor already did; this workbench did not.
    beforeEach(() => {
        readOnly.mockResolvedValue(true);
        server = fakeServer();
        workbench = workbenchFor(server);
    });

    test("is read before anything can be edited", async () => {
        await workbench.load();

        expect(workbench.readOnly).toBe(true);
        expect(workbench.documents).toHaveLength(2);
    });

    test("refuses to save, and says why", async () => {
        await workbench.load();
        workbench.text = "ex:X a sh:NodeShape .";
        server.requests.length = 0;

        const { saved, reason } = await workbench.save();

        expect(saved).toBe(false);
        expect(reason).toContain("read-only");
        expect(server.requests).toHaveLength(0);
    });

    test("refuses every change to the document list", async () => {
        await workbench.load();
        server.requests.length = 0;

        expect(await workbench.create("new.ttl")).toBeNull();
        expect(await workbench.importFile(new Blob(["x"]), "f.ttl")).toBeNull();
        expect(await workbench.rename(EQ, "other.ttl")).toBe(false);
        expect(await workbench.setEnabled(EQ, false)).toBe(false);
        expect(await workbench.move(EQ, 1, EQ_SCHEMA)).toBe(false);
        expect(await workbench.remove(EQ)).toBe(false);

        expect(server.requests).toHaveLength(0);
    });

    test("says a refusal is because of it, not what was refused before", async () => {
        readOnly.mockResolvedValue(false);
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "PATCH"
                ? new Response(
                      JSON.stringify({
                          detail: "Another document is already named x.ttl.",
                      }),
                      {
                          status: 409,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : answer(entry, url);
        await workbench.rename(EQ, "x.ttl");
        expect(workbench.lastError).toContain("already named");
        readOnly.mockResolvedValue(true);

        for (const refused of [
            () => workbench.setEnabled(EQ, false),
            () => workbench.create("new.ttl"),
            () => workbench.importFile(new Blob(["x"]), "f.ttl"),
        ]) {
            workbench.lastError = "something refused before";
            await refused();
            expect(workbench.lastError).toContain("read-only");
        }
    });

    test("still reads and validates, because looking is not changing", async () => {
        await workbench.load();

        expect(workbench.text).toBe(SHAPES);
        expect(workbench.report).not.toBeNull();
        expect(await workbench.validateBuffer()).not.toBeNull();
    });

    test("stays locked when the workspace cannot be asked", async () => {
        // An unknown answer must not be read as permission to write.
        readOnly.mockResolvedValue(null);
        const unknown = workbenchFor(fakeServer());

        await unknown.load();

        expect(unknown.readOnly).toBe(true);
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

    test("shows the buffer's findings for the open document once they arrive", async () => {
        await workbench.load();
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "stored finding",
        ]);

        workbench.text = `${SHAPES}\n`;
        await workbench.validateBuffer();

        expect(workbench.findings).toEqual([]);
    });

    test("keeps the buffer's findings while the text changes, until the next answer", async () => {
        await workbench.load();
        server.bufferReport = bufferReportWith("buffer finding");
        workbench.text = `\n${SHAPES}`;
        await workbench.validateBuffer();
        const shown = workbench.findings;

        // The stored findings belong to the saved text's lines. The editor moves the markers it
        // has with the text being typed, so they stay until the next answer replaces them.
        workbench.text = `\n\n${SHAPES}`;
        expect(workbench.findings).toBe(shown);
        workbench.text = SHAPES;
        expect(workbench.findings).toBe(shown);
        expect(
            workbench.results.find(result => result.documentId === EQ),
        ).toMatchObject({
            errorCount: 1,
        });
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

    test("a document validation has not reported on has no result", async () => {
        await workbench.load();

        // custom.ttl is not in the stored report, as a disabled document is not.
        expect(workbench.resultFor(CUSTOM)).toBeNull();
        expect(
            workbench.results.find(result => result.documentId === CUSTOM),
        ).toMatchObject({ validated: false });
        expect(
            workbench.results.find(result => result.documentId === EQ),
        ).toMatchObject({ validated: true, errorCount: 1 });
    });

    test("no document has a result before the first validation is back", async () => {
        const gate = holdRequests(entry => entry.path.endsWith("/validate"));
        const loading = workbench.load();
        await vi.waitFor(() => expect(workbench.text).toBe(SHAPES));

        expect(workbench.results.map(result => result.validated)).toEqual([
            false,
            false,
        ]);
        expect(workbench.validated).toBe(false);
        gate.release();
        await loading;
        expect(workbench.validated).toBe(true);
    });

    test("does not try to move a document past either end", async () => {
        await workbench.load();
        server.requests.length = 0;

        expect(await workbench.move(EQ, -1, EQ_SCHEMA)).toBe(false);
        expect(await workbench.move(CUSTOM, 1, EQ_SCHEMA)).toBe(false);
        expect(server.requests).toHaveLength(0);

        expect(await workbench.move(EQ, 1, EQ_SCHEMA)).toBe(true);
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

    test("deleting the last document opens the generated shapes", async () => {
        server.documents = server.documents.filter(
            document => document.id === EQ,
        );
        await workbench.load();
        server.documents = [];

        expect(await workbench.remove(EQ)).toBe(true);
        expect(workbench.selectedId).toBe(GENERATED_ID);
        expect(workbench.text).toContain("generated from the schema");
    });

    test("deleting the open document loads the text of the one it opens", async () => {
        // The regression this guards: relisting moves the selection off the document that is gone,
        // so asking afterwards whether the deleted one was open always said no. The editor kept
        // the deleted document's text, and saving wrote it into the document now selected.
        await workbench.load();
        expect(workbench.text).toBe(SHAPES);
        server.documents = server.documents.filter(
            document => document.id !== EQ,
        );

        await workbench.remove(EQ);

        expect(workbench.text).toBe("");
        expect(workbench.savedText).toBe("");
        expect(workbench.dirty).toBe(false);
    });
});

describe("the workspace's schemas", () => {
    function document(id, schemas, extra = {}) {
        return {
            id,
            name: `${id}.ttl`,
            order: 0,
            enabled: true,
            schemas,
            ...extra,
        };
    }

    test("are listed with the names the navigation uses", async () => {
        await workbench.load();

        expect(workbench.schemas).toEqual([
            { name: EQ_SCHEMA, label: "EQ" },
            { name: SSH_SCHEMA, label: "SSH" },
        ]);
    });

    test("are listed in the order the navigation lists them", async () => {
        getGraphs.mockResolvedValue([graph("SSH"), graph("EQ")]);

        await workbench.load();

        expect(workbench.schemas.map(({ label }) => label)).toEqual([
            "EQ",
            "SSH",
        ]);
    });

    test("say which documents apply to them, a document possibly to two", async () => {
        server.documents = [
            document("a", [EQ_SCHEMA]),
            document("both", [EQ_SCHEMA, SSH_SCHEMA]),
        ];
        await workbench.load();

        expect(workbench.documentsOf(EQ_SCHEMA).map(({ id }) => id)).toEqual([
            "a",
            "both",
        ]);
        expect(workbench.documentsOf(SSH_SCHEMA).map(({ id }) => id)).toEqual([
            "both",
        ]);
    });

    test("leave a document naming none of them unassigned", async () => {
        server.documents = [
            document("none", []),
            document("gone", ["Deleted"]),
            document("kept", ["Deleted", EQ_SCHEMA]),
        ];
        await workbench.load();

        expect(workbench.unassigned.map(({ id }) => id)).toEqual([
            "none",
            "gone",
        ]);
    });

    test("reread them on a reload, so a schema deleted elsewhere is gone", async () => {
        await workbench.load();
        getGraphs.mockResolvedValue([graph("EQ")]);

        await workbench.reload();

        expect(getGraphs).toHaveBeenLastCalledWith("cgmes", true);
        expect(workbench.schemas.map(({ name }) => name)).toEqual([EQ_SCHEMA]);
    });

    test("open the selected schema's first document holding anything", async () => {
        server.documents = [
            document("elsewhere", [SSH_SCHEMA], { tripleCount: 3 }),
            document("empty", [EQ_SCHEMA], { order: 1 }),
            document("own", [EQ_SCHEMA], { order: 2, tripleCount: 3 }),
        ];
        await workbench.load();

        expect(workbench.selectedId).toBe("own");
    });

    test("open a schema's generated shapes rather than another schema's document", async () => {
        server.documents = [
            document("elsewhere", [SSH_SCHEMA], { tripleCount: 3 }),
        ];
        await workbench.load();

        expect(workbench.selectedId).toBe(generatedId(EQ_SCHEMA));
    });

    test("with none selected, the workspace's first document opens", async () => {
        server.documents = [
            document("elsewhere", [SSH_SCHEMA], { tripleCount: 3 }),
        ];
        workbench = workbenchFor(server, null);

        await workbench.load();

        expect(workbench.selectedId).toBe("elsewhere");
    });

    test("with no document at all, the first schema's generated shapes open", async () => {
        server.documents = [];
        workbench = workbenchFor(server, null);

        await workbench.load();

        expect(workbench.selectedId).toBe(generatedId(EQ_SCHEMA));
    });

    test("with no schema and no document, nothing opens", async () => {
        server.documents = [];
        getGraphs.mockResolvedValue([]);
        workbench = workbenchFor(server, null);

        await workbench.load();

        expect(workbench.selectedId).toBeNull();
        expect(workbench.editorReadOnly).toBe(false);
    });

    test("say where a new document goes, from what is in view", async () => {
        server.documents = [document("both", [EQ_SCHEMA, SSH_SCHEMA])];
        await workbench.load();
        expect(workbench.contextSchemas).toEqual([EQ_SCHEMA, SSH_SCHEMA]);

        await workbench.select(generatedId(EQ_SCHEMA));
        expect(workbench.contextSchemas).toEqual([EQ_SCHEMA]);
    });

    test("a name with no schema behind it is never where a new document goes", async () => {
        server.documents = [document("gone", ["Deleted"], { tripleCount: 3 })];
        workbench = workbenchFor(server, null);
        await workbench.load();

        expect(workbench.contextSchemas).toEqual([]);
    });
});

describe("what a document applies to", () => {
    test("is sent when creating and importing", async () => {
        await workbench.load();
        server.requests.length = 0;

        await workbench.create("new.ttl", "", [EQ_SCHEMA, SSH_SCHEMA]);
        await workbench.importFile(new File(["x"], "f.ttl"), "f.ttl", [
            SSH_SCHEMA,
        ]);

        const posts = server.requests.filter(
            entry =>
                entry.method === "POST" && entry.path.includes("documents"),
        );
        expect(posts[0].query.getAll("schema")).toEqual([
            EQ_SCHEMA,
            SSH_SCHEMA,
        ]);
        expect(posts[1].query.getAll("schema")).toEqual([SSH_SCHEMA]);
    });

    test("can be replaced, and cleared with one empty value", async () => {
        await workbench.load();
        server.requests.length = 0;

        await workbench.setSchemas(EQ, [SSH_SCHEMA, EQ_SCHEMA]);
        await workbench.setSchemas(EQ, []);

        const patches = server.requests.filter(
            entry => entry.method === "PATCH",
        );
        expect(patches[0].query.getAll("schema")).toEqual([
            SSH_SCHEMA,
            EQ_SCHEMA,
        ]);
        expect(patches[1].query.getAll("schema")).toEqual([""]);
    });

    test("is left alone by any other change", async () => {
        await workbench.load();
        server.requests.length = 0;

        await workbench.setEnabled(EQ, false);

        const patch = server.requests.find(entry => entry.method === "PATCH");
        expect(patch.query.has("schema")).toBe(false);
    });

    test("moves the revision anything derived from the documents follows", async () => {
        await workbench.load();
        const before = workbench.documentsRevision;

        await workbench.setSchemas(EQ, []);

        expect(workbench.documentsRevision).toBeGreaterThan(before);
    });
});

describe("moving a document", () => {
    function listed(id, order, schemas) {
        return { id, name: `${id}.ttl`, order, enabled: true, schemas };
    }

    beforeEach(() => {
        server.documents = [
            listed("a", 0, [EQ_SCHEMA]),
            listed("elsewhere", 1, [SSH_SCHEMA]),
            listed("b", 2, [EQ_SCHEMA]),
        ];
    });

    test("goes past its neighbour in the schema, over those of other schemas", async () => {
        await workbench.load();
        server.requests.length = 0;

        expect(await workbench.move("a", 1, EQ_SCHEMA)).toBe(true);

        const patch = server.requests.find(entry => entry.method === "PATCH");
        expect(patch.query.get("order")).toBe("2");
    });

    test("stops at the ends of where it is listed", async () => {
        await workbench.load();

        expect(workbench.canMove("a", -1, EQ_SCHEMA)).toBe(false);
        expect(workbench.canMove("a", 1, EQ_SCHEMA)).toBe(true);
        expect(workbench.canMove("b", 1, EQ_SCHEMA)).toBe(false);
        expect(await workbench.move("b", 1, EQ_SCHEMA)).toBe(false);
    });
});

describe("the generated shapes", () => {
    test("are one schema's each", async () => {
        await workbench.load();
        server.requests.length = 0;

        await workbench.select(generatedId(SSH_SCHEMA));

        expect(workbench.generatedSchema).toBe(SSH_SCHEMA);
        expect(server.requests[0].path).toBe(
            "/api/workspaces/cgmes/schemas/SSH/shacl/generate/string",
        );
        expect(workbench.resultFor(generatedId(SSH_SCHEMA))).toBeNull();
        expect(workbench.findings).toEqual([]);
    });

    test("leave when their schema does", async () => {
        await workbench.load();
        await workbench.select(generatedId(SSH_SCHEMA));
        getGraphs.mockResolvedValue([graph("EQ")]);

        await workbench.reload();

        expect(workbench.selectedId).toBe(EQ);
        expect(workbench.text).toBe(SHAPES);
    });

    test("open into the editor, read-only", async () => {
        await workbench.load();

        await workbench.select(GENERATED_ID);

        expect(workbench.text).toContain("generated from the schema");
        expect(workbench.showingGenerated).toBe(true);
        expect(workbench.editorReadOnly).toBe(true);
        expect(workbench.dirty).toBe(false);
    });

    test("are fetched once and then remembered", async () => {
        await workbench.load();

        await workbench.select(GENERATED_ID);
        await workbench.select(EQ);
        server.requests.length = 0;
        await workbench.select(GENERATED_ID);

        expect(
            server.requests.filter(request =>
                request.path.endsWith("/shacl/generate/string"),
            ),
        ).toHaveLength(0);
    });

    test("cannot be saved over", async () => {
        await workbench.load();
        await workbench.select(GENERATED_ID);
        workbench.text = "edited anyway";
        server.requests.length = 0;

        const { saved, reason } = await workbench.save();

        expect(saved).toBe(false);
        expect(reason).toContain("come from the schema itself");
        expect(server.requests).toHaveLength(0);
    });

    test("are not sent for validation", async () => {
        await workbench.load();
        await workbench.select(GENERATED_ID);
        server.requests.length = 0;

        expect(await workbench.validateBuffer()).toBeNull();
        expect(server.requests).toHaveLength(0);
    });

    test("stay selected when the document list is refreshed", async () => {
        await workbench.load();
        await workbench.select(GENERATED_ID);

        await workbench.validateAll();
        // A refresh used to fall back to the first document, because the generated entry is in no
        // document list.
        await workbench.setEnabled(EQ, false);

        expect(workbench.selectedId).toBe(GENERATED_ID);
    });
});

describe("switching documents", () => {
    test("a slow answer for a document no longer selected is dropped", async () => {
        await workbench.load();
        server.texts[CUSTOM] = "ex:Custom a sh:NodeShape .\n";
        const gate = holdRequests(
            entry => entry.method === "GET" && entry.path.endsWith(EQ),
        );

        const slow = workbench.select(EQ);
        await workbench.select(CUSTOM);
        gate.release();
        await slow;

        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.text).toBe(server.texts[CUSTOM]);
    });

    test("a document that cannot be read leaves an empty, locked buffer", async () => {
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "GET" && entry.path.endsWith(CUSTOM)
                ? new Response("boom", { status: 500 })
                : answer(entry, url);

        await workbench.select(CUSTOM);

        expect(workbench.error).toBe("The document could not be loaded.");
        expect(workbench.text).toBe("");
        expect(workbench.editorReadOnly).toBe(true);
        workbench.text = SHAPES;
        server.requests.length = 0;
        expect((await workbench.save()).saved).toBe(false);
        expect(server.requests.filter(entry => entry.method === "PUT")).toEqual(
            [],
        );
    });

    test("a failed read's message goes once another document opens", async () => {
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "GET" && entry.path.endsWith(CUSTOM)
                ? new Response("boom", { status: 500 })
                : answer(entry, url);
        await workbench.select(CUSTOM);
        expect(workbench.error).not.toBeNull();

        await workbench.select(EQ);

        expect(workbench.error).toBeNull();
        expect(workbench.text).toBe(SHAPES);
    });

    test("a document that is not listed opens what is there instead", async () => {
        await workbench.load();
        await workbench.select(CUSTOM);
        server.requests.length = 0;

        await workbench.select("no-such-document");

        expect(workbench.selectedId).toBe(EQ);
        expect(workbench.text).toBe(SHAPES);
        expect(workbench.error).toBeNull();
        expect(
            server.requests.some(entry =>
                entry.path.endsWith("no-such-document"),
            ),
        ).toBe(false);
    });

    test("is locked while the text is on its way", async () => {
        await workbench.load();
        const gate = holdRequests(
            entry => entry.method === "GET" && entry.path.endsWith(CUSTOM),
        );

        const opening = workbench.select(CUSTOM);
        expect(workbench.editorReadOnly).toBe(true);
        expect(workbench.dirty).toBe(false);
        gate.release();
        await opening;

        expect(workbench.editorReadOnly).toBe(false);
    });

    test("starts a new buffer for each document, but not for an edit", async () => {
        await workbench.load();
        const opened = workbench.bufferKey;

        workbench.text = `${SHAPES}# typed\n`;
        expect(workbench.bufferKey).toBe(opened);

        await workbench.select(CUSTOM);
        expect(workbench.bufferKey).not.toBe(opened);
    });
});

describe("saving while a save is on its way", () => {
    test("what is typed meanwhile stays unsaved", async () => {
        await workbench.load();
        const gate = holdRequests(entry => entry.method === "PUT");
        workbench.text = `${SHAPES}# first\n`;

        const saving = workbench.save();
        await vi.waitFor(() =>
            expect(server.requests.some(entry => entry.method === "PUT")).toBe(
                true,
            ),
        );
        workbench.text = `${SHAPES}# first\n# typed meanwhile\n`;
        gate.release();
        await saving;

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.body).not.toContain("typed meanwhile");
        expect(workbench.savedText).toBe(`${SHAPES}# first\n`);
        expect(workbench.dirty).toBe(true);
    });

    test("writes what was open when Save was pressed, though another document opens meanwhile", async () => {
        await workbench.load();
        const typed = `${SHAPES}# typed\n`;
        workbench.text = typed;
        const asked = held();
        readOnly.mockImplementationOnce(() => asked.promise);
        server.requests.length = 0;

        const saving = workbench.save();
        await workbench.select(CUSTOM);
        asked.release(false);
        await saving;

        const put = server.requests.find(entry => entry.method === "PUT");
        expect(put.path).toBe(`/api/workspaces/cgmes/shacl/documents/${EQ}`);
        expect(put.body).toBe(typed);
        expect(put.query.get("expectedRevision")).toBe(
            await revisionOf(SHAPES),
        );
        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.dirty).toBe(false);
    });

    test("a second Save waits for the first rather than failing", async () => {
        await workbench.load();
        const gate = holdRequests(entry => entry.method === "PUT");
        workbench.text = `${SHAPES}# edited\n`;

        const first = workbench.save();
        const second = workbench.save();
        gate.release();

        expect(await first).toEqual({ saved: true, reason: null });
        expect(await second).toEqual({ saved: true, reason: null });
        expect(
            server.requests.filter(entry => entry.method === "PUT"),
        ).toHaveLength(1);
    });
});

describe("revalidating what has changed elsewhere", () => {
    /** Types into the open document and has the buffer validated, finding one error. */
    async function typedWithAnError() {
        await workbench.load();
        server.bufferReport = bufferReportWith("buffer finding");
        workbench.text = `${SHAPES}# typed\n`;
        await workbench.validateBuffer();
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "buffer finding",
        ]);
    }

    /** The schema is fixed elsewhere: from now on the server finds nothing anywhere. */
    function fixedElsewhere() {
        server.report = cleanReport();
        server.bufferReport = {
            ...cleanReport(),
            profiles: undefined,
        };
    }

    test("Validate all validates the unsaved text again", async () => {
        await typedWithAnError();
        fixedElsewhere();

        await workbench.validateAll();

        expect(workbench.findings).toEqual([]);
        expect(workbench.totals.errorCount).toBe(0);
        expect(workbench.text).toBe(`${SHAPES}# typed\n`);
    });

    test("a reload after an undo elsewhere validates the unsaved text again", async () => {
        await typedWithAnError();
        fixedElsewhere();

        await workbench.reload();

        expect(workbench.findings).toEqual([]);
        expect(workbench.totals.errorCount).toBe(0);
    });

    test("switching another document off validates the unsaved text again", async () => {
        await typedWithAnError();
        fixedElsewhere();

        await workbench.setEnabled(CUSTOM, false);

        expect(workbench.findings).toEqual([]);
        expect(workbench.totals.errorCount).toBe(0);
    });

    test("once the text is as saved, a newer stored report replaces the buffer's", async () => {
        await typedWithAnError();
        workbench.revert();
        fixedElsewhere();
        server.requests.length = 0;

        await workbench.validateAll();

        expect(workbench.findings).toEqual([]);
        expect(workbench.totals.errorCount).toBe(0);
        expect(
            server.requests.filter(entry =>
                entry.path.endsWith("/validate/text"),
            ),
        ).toHaveLength(0);
    });

    test("the last Validate all wins, whichever answer arrives last", async () => {
        await workbench.load();
        const gates = [held(), held()];
        const reports = [server.report, cleanReport()];
        let run = 0;
        const answer = server.respond;
        server.respond = async (entry, url) => {
            if (entry.path.endsWith("/validate")) {
                const mine = run++;
                await gates[mine].promise;
                return new Response(JSON.stringify(reports[mine]), {
                    status: 200,
                    headers: { "content-type": "application/json" },
                });
            }
            return answer(entry, url);
        };

        const first = workbench.validateAll();
        const second = workbench.validateAll();
        await vi.waitFor(() => expect(run).toBe(2));
        gates[1].release();
        await second;
        gates[0].release();
        await first;

        expect(workbench.totals.errorCount).toBe(0);
        expect(workbench.report).toEqual(reports[1]);
    });
});

describe("a validation that fails", () => {
    test("says so for the buffer, and keeps the findings it had", async () => {
        await workbench.load();
        server.bufferReport = bufferReportWith("buffer finding");
        workbench.text = `${SHAPES}# typed\n`;
        await workbench.validateBuffer();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.path.endsWith("/validate/text")
                ? new Response("boom", { status: 500 })
                : answer(entry, url);

        workbench.text = `${SHAPES}# typed more\n`;
        await workbench.validateBuffer();

        expect(workbench.validationError).toContain("could not be validated");
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "buffer finding",
        ]);

        server.respond = answer;
        await workbench.validateBuffer();
        expect(workbench.validationError).toBeNull();
    });

    test("says no more about the buffer once another document opens", async () => {
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.path.endsWith("/validate/text")
                ? new Response("boom", { status: 500 })
                : answer(entry, url);
        workbench.text = `${SHAPES}# typed\n`;
        await workbench.validateBuffer();
        expect(workbench.validationError).not.toBeNull();

        workbench.revert();
        await workbench.select(CUSTOM);

        expect(workbench.validationError).toBeNull();
    });

    test("says a text too large for the server was refused as such", async () => {
        await workbench.load();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.path.endsWith("/validate/text")
                ? new Response("<html>413 Request Entity Too Large</html>", {
                      status: 413,
                      headers: { "content-type": "text/html" },
                  })
                : answer(entry, url);

        workbench.text = `${SHAPES}# typed\n`;
        await workbench.validateBuffer();

        expect(workbench.validationError).toContain("too large");
    });

    test("says so for the stored documents, and leaves the editor usable", async () => {
        server.respond = (entry, url) =>
            entry.path.endsWith("/validate")
                ? new Response("boom", { status: 500 })
                : fakeServer().respond(entry, url);

        await workbench.load();

        expect(workbench.error).toBeNull();
        expect(workbench.text).toBe(SHAPES);
        expect(workbench.validationError).toContain("could not be validated");
        expect(workbench.results.every(result => !result.validated)).toBe(true);
    });
});

describe("validation in flight", () => {
    test("stays marked as running until the last run is back", async () => {
        await workbench.load();
        const gates = [held(), held()];
        let run = 0;
        const answer = server.respond;
        server.respond = async (entry, url) => {
            if (entry.path.endsWith("/validate/text")) {
                await gates[run++].promise;
            }
            return answer(entry, url);
        };

        workbench.text = "first";
        const first = workbench.validateBuffer();
        workbench.text = "second";
        const second = workbench.validateBuffer();
        await vi.waitFor(() => expect(run).toBe(2));
        gates[0].release();
        await first;

        expect(workbench.validating).toBe(true);
        gates[1].release();
        await second;
        expect(workbench.validating).toBe(false);
    });
});

describe("a workspace whose read-only flag changes", () => {
    test("is followed while the workbench is open", async () => {
        await workbench.load();
        workbench.watchReadOnly();

        announceWorkspaces([{ label: "cgmes", readOnly: true }]);
        expect(workbench.readOnly).toBe(true);
        expect(workbench.editorReadOnly).toBe(true);

        announceWorkspaces([{ label: "cgmes", readOnly: false }]);
        expect(workbench.readOnly).toBe(false);

        // A workspace the list no longer knows is not one that may be written to.
        announceWorkspaces([{ label: "other", readOnly: false }]);
        expect(workbench.readOnly).toBe(true);

        workbench.dispose();
        announceWorkspaces([{ label: "cgmes", readOnly: false }]);
        expect(workbench.readOnly).toBe(true);
    });

    test("is asked again before every write", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# edited\n`;
        readOnly.mockResolvedValue(true);
        server.requests.length = 0;

        const { saved, reason } = await workbench.save();

        expect(saved).toBe(false);
        expect(reason).toContain("read-only");
        expect(await workbench.create("new.ttl")).toBeNull();
        expect(server.requests).toHaveLength(0);
    });
});

describe("reloading after the schema changed elsewhere", () => {
    test("re-reads a clean buffer", async () => {
        await workbench.load();
        server.texts[EQ] = `${SHAPES}# changed by an undo\n`;

        await workbench.reload();

        expect(workbench.text).toBe(server.texts[EQ]);
        expect(workbench.dirty).toBe(false);
    });

    test("keeps an unsaved buffer", async () => {
        await workbench.load();
        const typed = `${SHAPES}# typed\n`;
        workbench.text = typed;
        server.texts[EQ] = `${SHAPES}# changed elsewhere\n`;

        await workbench.reload();

        expect(workbench.text).toBe(typed);
        expect(workbench.dirty).toBe(true);
    });

    test("re-reads a buffer whose edits were discarded first", async () => {
        await workbench.load();
        workbench.text = `${SHAPES}# typed, then discarded\n`;
        server.texts[EQ] = `${SHAPES}# changed by an undo\n`;

        workbench.revert();
        await workbench.reload();

        expect(workbench.text).toBe(server.texts[EQ]);
        expect(workbench.dirty).toBe(false);
    });

    test("keeps the editor's history when nothing changed", async () => {
        await workbench.load();
        const opened = workbench.bufferKey;

        await workbench.reload();

        expect(workbench.bufferKey).toBe(opened);
    });

    test("picks up a document added elsewhere", async () => {
        await workbench.load();
        server.documents = [
            ...server.documents,
            { id: "added", name: "added.ttl", order: 2, enabled: true },
        ];

        await workbench.reload();

        expect(workbench.documents.map(document => document.id)).toContain(
            "added",
        );
    });
});

describe("when the open document is gone from the list", () => {
    const TYPED = `${SHAPES}# typed, not saved\n`;

    /** Takes the open document away on the server, as an undo or another tab does. */
    function takeAway(documentId = EQ) {
        server.documents = server.documents.filter(
            document => document.id !== documentId,
        );
    }

    /** Lists a document the server is asked to create, as the server does. */
    function listCreated() {
        const answer = server.respond;
        server.respond = (entry, url) => {
            if (entry.method === "POST") {
                server.documents = [
                    ...server.documents,
                    {
                        id: "new-id",
                        name: entry.query.get("name"),
                        order: server.documents.length,
                        enabled: true,
                        schemas: entry.query.getAll("schema"),
                    },
                ];
                server.texts["new-id"] = entry.body;
            }
            return answer(entry, url);
        };
    }

    test("is validated as text of no document, and its findings are listed", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();
        await workbench.reload();
        server.bufferReport = bufferReportWith("detached finding");
        server.requests.length = 0;

        await workbench.validateBuffer();

        const post = server.requests.find(entry =>
            entry.path.endsWith("/validate/text"),
        );
        expect(post.query.has("documentId")).toBe(false);
        expect(post.query.get("name")).toBe("eq.ttl");
        expect(workbench.findings.map(finding => finding.message)).toEqual([
            "detached finding",
        ]);
        expect(
            workbench.results.find(result => result.documentId === EQ),
        ).toMatchObject({ documentName: "eq.ttl", errorCount: 1 });
    });

    test("keeps unsaved text, and under no other document", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();

        await workbench.reload();

        expect(workbench.text).toBe(TYPED);
        expect(workbench.dirty).toBe(true);
        expect(workbench.selected).toBeNull();
        expect(workbench.detached).toMatchObject({
            id: EQ,
            name: "eq.ttl",
            schemas: [EQ_SCHEMA],
        });
    });

    test("keeps unsaved text when a change to another document relists them", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();

        await workbench.setEnabled(CUSTOM, false);

        expect(workbench.text).toBe(TYPED);
        expect(workbench.selected).toBeNull();
        expect(workbench.detached?.name).toBe("eq.ttl");
    });

    test("keeps unsaved text when only the generated shapes are left", async () => {
        await workbench.load();
        workbench.text = TYPED;
        server.documents = [];

        await workbench.reload();

        expect(workbench.showingGenerated).toBe(false);
        expect(workbench.text).toBe(TYPED);
        expect(workbench.dirty).toBe(true);
    });

    test("saves the kept text as a new document, never into another", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();
        await workbench.reload();
        listCreated();
        server.requests.length = 0;

        expect(await workbench.save()).toMatchObject({ saved: true });

        expect(server.requests.some(entry => entry.method === "PUT")).toBe(
            false,
        );
        const post = server.requests.find(entry => entry.method === "POST");
        expect(post.path).toBe("/api/workspaces/cgmes/shacl/documents");
        expect(post.query.get("name")).toBe("eq.ttl");
        expect(post.query.getAll("schema")).toEqual([EQ_SCHEMA]);
        expect(post.body).toBe(TYPED);
        expect(workbench.detached).toBeNull();
        expect(workbench.selectedId).toBe("new-id");
        expect(workbench.text).toBe(TYPED);
        expect(workbench.dirty).toBe(false);
    });

    test("gives the new document a free name and only schemas that are there", async () => {
        server.documents = [
            ...server.documents,
            {
                id: "third",
                name: "third.ttl",
                order: 2,
                enabled: true,
                tripleCount: 4,
                schemas: [EQ_SCHEMA, "Deleted"],
            },
        ];
        await workbench.load();
        await workbench.select("third");
        workbench.text = TYPED;
        server.documents = server.documents
            .filter(document => document.id !== "third")
            .map(document =>
                document.id === CUSTOM
                    ? { ...document, name: "third.ttl" }
                    : document,
            );
        await workbench.reload();
        server.requests.length = 0;

        await workbench.save();

        const post = server.requests.find(entry => entry.method === "POST");
        expect(post.query.get("name")).toBe("third (2).ttl");
        expect(post.query.getAll("schema")).toEqual([EQ_SCHEMA]);
    });

    test("can be told to drop the kept text, and opens what is left", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();
        await workbench.reload();

        await workbench.discardDetached();

        expect(workbench.detached).toBeNull();
        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.text).toBe("");
        expect(workbench.dirty).toBe(false);
    });

    test("finds its document again when it comes back", async () => {
        const all = server.documents;
        await workbench.load();
        workbench.text = TYPED;
        takeAway();
        await workbench.reload();
        server.documents = all;

        await workbench.reload();

        expect(workbench.detached).toBeNull();
        expect(workbench.selectedId).toBe(EQ);
        expect(workbench.text).toBe(TYPED);
        expect(workbench.dirty).toBe(true);
    });

    test("opens what is left when nothing was unsaved", async () => {
        await workbench.load();
        takeAway();

        await workbench.setEnabled(CUSTOM, false);

        expect(workbench.detached).toBeNull();
        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.text).toBe("");
        expect(workbench.textReady).toBe(true);
        expect(workbench.dirty).toBe(false);
    });

    test("opens what is left on a reload when nothing was unsaved", async () => {
        await workbench.load();
        const opened = workbench.bufferKey;
        takeAway();

        await workbench.reload();

        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.text).toBe("");
        expect(workbench.bufferKey).not.toBe(opened);
    });

    test("is noticed by a save the server refuses for that reason", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();
        const answer = server.respond;
        server.respond = (entry, url) =>
            entry.method === "PUT"
                ? new Response(
                      JSON.stringify({
                          detail: "No constraints document with id eq-document-id in this workspace.",
                      }),
                      {
                          status: 404,
                          headers: { "content-type": "application/json" },
                      },
                  )
                : answer(entry, url);

        const result = await workbench.save();

        expect(result.saved).toBe(false);
        expect(workbench.detached?.name).toBe("eq.ttl");
        expect(workbench.text).toBe(TYPED);
    });

    test("is dropped with its text when the user deletes it", async () => {
        await workbench.load();
        workbench.text = TYPED;
        takeAway();

        await workbench.remove(EQ);

        expect(workbench.detached).toBeNull();
        expect(workbench.selectedId).toBe(CUSTOM);
        expect(workbench.dirty).toBe(false);
    });
});

describe("a new document", () => {
    test("starts with the text it is given", async () => {
        await workbench.load();
        server.requests.length = 0;
        const seed = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n";

        await workbench.create("new.ttl", seed);

        const post = server.requests.find(entry => entry.method === "POST");
        expect(post.body).toBe(seed);
        expect(post.contentType).toBe("text/plain");
    });
});

test("reads any document's stored text, for a download", async () => {
    await workbench.load();

    expect(await workbench.textOf(EQ)).toBe(SHAPES);
});
