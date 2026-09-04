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

import { describe, expect, test, vi } from "vitest";

import { listShapesDocuments } from "$lib/api/generated/index.ts";

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

/**
 * The export endpoint's contract, as the dialog uses it.
 *
 * The dialog itself is mostly checkboxes; what matters is that the selection reaches the server as
 * repeated documentId parameters, and that the document list it offers comes from the graph.
 */
describe("exporting a selection of constraints", () => {
    const CIM = "cgmes";
    const GRAPH = "http://example.org/EQ";

    /** The URL the dialog builds, kept here so its shape is pinned by a test. */
    function exportUrl(
        base,
        workspaceName,
        graphURI,
        documentIds,
        includeGenerated,
    ) {
        const query = documentIds.map(
            id => `documentId=${encodeURIComponent(id)}`,
        );
        query.push(`includeGenerated=${includeGenerated}`);
        return (
            base +
            "/api/datasets/" +
            encodeURIComponent(workspaceName) +
            "/graphs/" +
            encodeURIComponent(graphURI) +
            "/shacl/export/file?" +
            query.join("&")
        );
    }

    test("names each chosen document as its own parameter", () => {
        const url = new URL(
            exportUrl("http://backend.test", CIM, GRAPH, ["a", "b"], true),
        );

        expect(url.pathname).toBe(
            "/api/datasets/cgmes/graphs/http%3A%2F%2Fexample.org%2FEQ/shacl/export/file",
        );
        expect(url.searchParams.getAll("documentId")).toEqual(["a", "b"]);
        expect(url.searchParams.get("includeGenerated")).toBe("true");
    });

    test("can ask for the generated shapes on their own", () => {
        const url = new URL(
            exportUrl("http://backend.test", CIM, GRAPH, [], true),
        );

        expect(url.searchParams.getAll("documentId")).toEqual([]);
        expect(url.searchParams.get("includeGenerated")).toBe("true");
    });

    test("can ask for documents without the generated shapes", () => {
        const url = new URL(
            exportUrl("http://backend.test", CIM, GRAPH, ["a"], false),
        );

        expect(url.searchParams.getAll("documentId")).toEqual(["a"]);
        expect(url.searchParams.get("includeGenerated")).toBe("false");
    });

    test("the document list the dialog offers comes from the graph", async () => {
        const requests = [];
        const fetchImpl = async request => {
            requests.push(new URL(request.url).pathname);
            return new Response(
                JSON.stringify([
                    { id: "b", name: "second.ttl", order: 1, enabled: true },
                    { id: "a", name: "first.ttl", order: 0, enabled: false },
                ]),
                {
                    status: 200,
                    headers: { "content-type": "application/json" },
                },
            );
        };

        const { data } = await listShapesDocuments({
            fetch: fetchImpl,
            path: { datasetName: CIM, graphURI: GRAPH },
        });
        const ordered = [...data].sort(
            (x, y) => (x.order ?? 0) - (y.order ?? 0),
        );

        expect(requests[0]).toContain("/shacl/documents");
        // Offered in the graph's own order, disabled ones included: disabled means "takes no part
        // in validation", not "cannot be exported".
        expect(ordered.map(d => d.name)).toEqual(["first.ttl", "second.ttl"]);
        expect(ordered[0].enabled).toBe(false);
    });
});
