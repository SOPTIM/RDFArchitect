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
    createShapesDocument,
    createSnapshot,
    putShacl,
    replaceAssociationShacl,
    replaceAttributeShacl,
    replaceGraphWithGraphString,
    replaceShape,
    replaceShapesDocumentText,
    validateShapesText,
} from "../../src/lib/api/generated";

/**
 * Contract test for the endpoints that take a plain `@RequestBody String`.
 *
 * Spring reads those bodies verbatim via `StringHttpMessageConverter`, so the client must
 * not apply its default `jsonBodySerializer`: a quoted payload reaches Jena as
 * `"@prefix ..."` and fails with "Not a valid token for an RDF term". The backend declares
 * these operations as `consumes = text/plain`, which makes the generator emit
 * `bodySerializer: null` per operation — this test pins that outcome, so a codegen change
 * or a stale toolchain cannot silently reintroduce the quoting.
 */

/** Deliberately carries prefixes and a comment — nothing may be escaped away. */
const TTL = `@prefix sh: <http://www.w3.org/ns/shacl#> .

# a comment that must survive the round trip
ex:Shape a sh:NodeShape .
`;

const GRAPH = { datasetName: "cgmes", graphURI: "http://example.org/EQ" };
const CLASS = { ...GRAPH, classUUID: "a-class" };

const OPERATIONS = [
    ["createSnapshot", createSnapshot, {}],
    ["putShacl", putShacl, { path: CLASS }],
    [
        "replaceGraphWithGraphString",
        replaceGraphWithGraphString,
        { path: GRAPH },
    ],
    [
        "replaceShape",
        replaceShape,
        { path: { ...GRAPH, shaclShapeURI: "http://example.org/Shape" } },
    ],
    [
        "replaceAttributeShacl",
        replaceAttributeShacl,
        { path: { ...CLASS, attributeUUID: "an-attribute" } },
    ],
    [
        "replaceAssociationShacl",
        replaceAssociationShacl,
        { path: { ...CLASS, associationUUID: "an-association" } },
    ],
    [
        "createShapesDocument",
        createShapesDocument,
        { path: GRAPH, query: { name: "eq.ttl" } },
    ],
    ["validateShapesText", validateShapesText, { path: GRAPH }],
    [
        "replaceShapesDocumentText",
        replaceShapesDocumentText,
        {
            path: {
                ...GRAPH,
                documentId: "0f8fad5b-d9cb-469f-a165-70867728950e",
            },
        },
    ],
];

let requests;

/** Captures the Request the generated client would send, without hitting the network. */
const captureFetch = async request => {
    requests.push(request);
    return new Response("SUCCESS", {
        status: 200,
        headers: { "content-type": "text/plain" },
    });
};

// $env/dynamic/public has no SvelteKit runtime under vitest, and the generated
// client reaches it while resolving the backend base url. It has to be absolute:
// the client builds a Request, and a relative url has no base to resolve against.
vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

beforeEach(() => {
    requests = [];
});

describe("raw text request bodies", () => {
    test.each(OPERATIONS)(
        "%s sends the body verbatim",
        async (_name, operation, options) => {
            await operation({ ...options, body: TTL, fetch: captureFetch });

            expect(requests).toHaveLength(1);
            expect(await requests[0].text()).toBe(TTL);
            expect(requests[0].headers.get("content-type")).toBe("text/plain");
        },
    );
});
