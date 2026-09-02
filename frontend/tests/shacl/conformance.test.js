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

import { ConformanceView } from "$lib/shacl/conformanceState.svelte.js";

import ConformanceReportView from "../../src/routes/shacl/workbench/ConformanceReportView.svelte";

const CIM = "http://iec.ch/TC57/CIM100#";
const DOCUMENT = "eq-document-id";

const REPORT = {
    documentId: DOCUMENT,
    documentName: "official.ttl",
    documents: [{ id: DOCUMENT, name: "official.ttl" }],
    conforms: false,
    compared: 1343,
    agreeing: 1341,
    impliedBySchema: 1343,
    stated: 1343,
    contradictedCount: 1,
    differentCount: 1,
    missingInDocumentCount: 0,
    notInSchemaCount: 0,
    findings: [
        {
            kind: "CONTRADICTED",
            targetClass: `${CIM}Season`,
            path: `${CIM}Season.endDate`,
            schemaSays: "1..1, xsd:MonthDay",
            documentSays: "1..1, xsd:gMonthDay",
            message: "A value cannot be both xsd:MonthDay and xsd:gMonthDay.",
            statedIn: ["official.ttl"],
        },
        {
            kind: "DIFFERENT",
            targetClass: `${CIM}ACLineSegment`,
            path: `${CIM}ACLineSegment.r`,
            schemaSays: "0..5",
            documentSays: "0..1",
            message:
                "Both can be satisfied, but they do not say the same thing.",
            statedIn: ["official.ttl"],
        },
    ],
};

function fakeServer(overrides = {}) {
    const server = { requests: [], report: REPORT, status: 200, ...overrides };
    server.fetch = async request => {
        const url = new URL(request.url);
        server.requests.push({
            path: url.pathname,
            documentId: url.searchParams.get("documentId"),
        });
        return new Response(JSON.stringify(server.report), {
            status: server.status,
            headers: { "content-type": "application/json" },
        });
    };
    return server;
}

function viewFor(server) {
    return new ConformanceView({
        datasetName: "cgmes",
        graphUri: "http://ex.org/EQ",
        requestOptions: { fetch: server.fetch },
    });
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

describe("ConformanceView", () => {
    let server;
    let view;

    beforeEach(() => {
        server = fakeServer();
        view = viewFor(server);
    });

    test("asks about the document it was given", async () => {
        await view.run(DOCUMENT);

        expect(server.requests[0].path).toMatch(/\/shacl\/conformance$/);
        expect(server.requests[0].documentId).toBe(DOCUMENT);
        expect(view.report.agreeing).toBe(1341);
        expect(view.reportedOn).toBe(DOCUMENT);
    });

    test("does nothing without a document", async () => {
        expect(await view.run(null)).toBeNull();
        expect(server.requests).toHaveLength(0);
    });

    test("reports a failure rather than showing a stale answer", async () => {
        server.status = 500;

        await view.run(DOCUMENT);

        expect(view.error).toBe(
            "The document could not be compared with the schema.",
        );
        expect(view.report).toBeNull();
    });

    test("forgetting drops the report, so another document is not shown the wrong answer", async () => {
        await view.run(DOCUMENT);
        view.forget();

        expect(view.report).toBeNull();
        expect(view.reportedOn).toBeNull();
    });
});

describe("ConformanceReportView", () => {
    let mounted = null;
    let target = null;

    function render(props) {
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(ConformanceReportView, { target, props });
        return target;
    }

    afterEach(() => {
        if (mounted) unmount(mounted);
        target?.remove();
        mounted = null;
        target = null;
    });

    const conformance = (overrides = {}) => ({
        report: REPORT,
        reportedOn: DOCUMENT,
        running: false,
        error: null,
        run: vi.fn(),
        forget: vi.fn(),
        ...overrides,
    });

    test("opens the document a finding is stated in", async () => {
        const onopen = vi.fn();
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
            onopen,
        });

        // Naming the file is half an answer; getting to it is the other half.
        const link = [...view.querySelectorAll("button")].find(
            button => button.textContent.trim() === "official.ttl",
        );
        expect(link).toBeDefined();

        link.click();
        await Promise.resolve();

        expect(onopen).toHaveBeenCalledWith(DOCUMENT);
    });

    test("still names the documents when there is nowhere to open them", () => {
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
        });

        expect(view.textContent).toContain("Read together: official.ttl");
    });

    test("offers the comparison before it has been run", () => {
        const view = render({
            conformance: conformance({ report: null, reportedOn: null }),
            documentId: DOCUMENT,
        });

        expect(view.textContent).toContain("Compare with the schema");
        expect(view.textContent).toContain(
            "every enabled constraints document",
        );
    });

    test("groups the findings by kind, worst first", () => {
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
            prefixes: { cim: CIM, xsd: "http://www.w3.org/2001/XMLSchema#" },
        });

        const headings = [...view.querySelectorAll("h3")].map(h =>
            h.textContent.replace(/\s+/g, " ").trim(),
        );
        expect(headings[0]).toContain("Contradiction");
        expect(headings[1]).toContain("Difference");
        expect(headings[0]).toContain("cannot both be satisfied");
    });

    test("says how much agrees, and shows both sides of a disagreement", () => {
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
            prefixes: { cim: CIM, xsd: "http://www.w3.org/2001/XMLSchema#" },
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain(
            "1341 of the 1343 property constraints both sides state agree",
        );
        expect(text).toContain("cim:Season · cim:Season.endDate");
        expect(text).toContain("xsd:MonthDay");
        expect(text).toContain("xsd:gMonthDay");
        expect(text).toContain("Read together: official.ttl");
    });

    test("names the document a finding came from", () => {
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
            prefixes: { cim: CIM },
        });

        // The comparison merges every enabled document, so without this a finding says which rule
        // is wrong but not which of several files to open.
        expect(view.textContent.replace(/\s+/g, " ")).toContain(
            "stated in: official.ttl",
        );
    });

    test("does not show a report that belongs to another document", () => {
        const view = render({
            conformance: conformance(),
            documentId: "a-different-document",
        });

        expect(view.textContent).toContain("Compare with the schema");
        expect(view.textContent).not.toContain("1341");
    });

    test("says plainly when everything agrees", () => {
        const view = render({
            conformance: conformance({
                report: {
                    ...REPORT,
                    conforms: true,
                    findings: [],
                    agreeing: 1343,
                    contradictedCount: 0,
                    differentCount: 0,
                },
            }),
            documentId: DOCUMENT,
        });

        expect(view.textContent).toContain(
            "The constraints and the schema agree",
        );
        expect(view.querySelectorAll("h3")).toHaveLength(0);
    });

    test("a document that covers nothing is not called a disagreement", () => {
        // What the CGMES 3.0 DiagramLayout cross-profile files do: one rule each, against a schema
        // implying 49 constraints. The old report announced "0 of 49 property constraints agree".
        const view = render({
            conformance: conformance({
                report: {
                    ...REPORT,
                    conforms: false,
                    compared: 0,
                    agreeing: 0,
                    impliedBySchema: 49,
                    stated: 0,
                    contradictedCount: 0,
                    differentCount: 0,
                    missingInDocumentCount: 49,
                    findings: [],
                },
            }),
            documentId: DOCUMENT,
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("No contradictions");
        expect(text).toContain("nothing to agree or disagree about");
        expect(text).toContain("49 more the schema implies are not stated");
        expect(text).not.toContain("0 of 49");
    });

    test("a contradiction is what turns the headline red", () => {
        const view = render({
            conformance: conformance(),
            documentId: DOCUMENT,
        });

        expect(view.textContent).toContain(
            "The constraints and the schema contradict each other",
        );
    });

    test("running the comparison goes through the view state", () => {
        const state = conformance({ report: null, reportedOn: null });
        const view = render({ conformance: state, documentId: DOCUMENT });

        [...view.querySelectorAll("button")]
            .find(button => button.textContent.includes("Compare"))
            .click();
        flushSync();

        expect(state.run).toHaveBeenCalledWith(DOCUMENT);
    });
});
