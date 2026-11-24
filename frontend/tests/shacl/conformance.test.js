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

import {
    CONFORMANCE_KIND,
    ConformanceView,
    foldFindings,
} from "$lib/shacl/conformanceState.svelte.js";

import ConformanceReportView from "../../src/routes/shacl/workbench/ConformanceReportView.svelte";

const CIM = "http://iec.ch/TC57/CIM100#";
const DOCUMENT = "eq-document-id";
const SCHEMA = "EQ";
const DOCUMENTS = [{ id: DOCUMENT, name: "official.ttl" }];

const REPORT = {
    schemaName: SCHEMA,
    documents: DOCUMENTS,
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
    const server = {
        requests: [],
        report: REPORT,
        status: 200,
        /** Answers held back, per schema, until `release` is called. */
        held: new Map(),
        holding: new Set(),
        ...overrides,
    };
    server.hold = schema => server.holding.add(schema);
    server.release = schema => {
        server.holding.delete(schema);
        server.held.get(schema)?.();
        server.held.delete(schema);
    };
    server.fetch = async request => {
        const url = new URL(request.url);
        const schema = url.searchParams.get("schema");
        server.requests.push({ path: url.pathname, schema });
        if (server.holding.has(schema)) {
            await new Promise(resolve => server.held.set(schema, resolve));
        }
        if (server.failing) {
            throw new TypeError("Failed to fetch");
        }
        return new Response(JSON.stringify(server.report), {
            status: server.status,
            headers: { "content-type": "application/json" },
        });
    };
    return server;
}

function viewFor(server) {
    return new ConformanceView({
        workspaceName: "cgmes",
        requestOptions: { fetch: server.fetch },
    });
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

describe("foldFindings", () => {
    const inherited = targetClass => ({
        kind: "DIFFERENT",
        targetClass: `${CIM}${targetClass}`,
        path: `${CIM}RotatingMachineDynamics.damping`,
        schemaSays: "1..1, xsd:float",
        documentSays: "1..1",
        message: "Both can be satisfied, but they do not say the same thing.",
        statedIn: ["official.ttl"],
    });

    test("folds one rule stated on many subclasses into one finding", () => {
        const folded = foldFindings([
            inherited("SynchronousMachineSimplified"),
            inherited("AsynchronousMachineUserDefined"),
            inherited("SynchronousMachineDetailed"),
        ]);

        expect(folded).toHaveLength(1);
        expect(folded[0].targetClasses).toEqual([
            `${CIM}SynchronousMachineSimplified`,
            `${CIM}AsynchronousMachineUserDefined`,
            `${CIM}SynchronousMachineDetailed`,
        ]);
    });

    test("keeps apart findings that would read differently", () => {
        const folded = foldFindings([
            inherited("A"),
            { ...inherited("B"), documentSays: "0..1" },
            { ...inherited("C"), statedIn: ["other.ttl"] },
            { ...inherited("D"), kind: "CONTRADICTED" },
        ]);

        expect(folded).toHaveLength(4);
    });
});

describe("ConformanceView", () => {
    let server;
    let view;

    beforeEach(() => {
        server = fakeServer();
        view = viewFor(server);
    });

    test("asks about the schema it was given, for the workspace", async () => {
        await view.run(SCHEMA);

        expect(server.requests[0].schema).toBe(SCHEMA);
        expect(server.requests[0].path).toBe(
            "/api/workspaces/cgmes/shacl/conformance",
        );
        expect(view.reportFor(SCHEMA)).toEqual(REPORT);
    });

    test("does nothing without a schema", async () => {
        expect(await view.run(null)).toBeNull();
        expect(server.requests).toHaveLength(0);
    });

    test("reports a failure against the schema it was about", async () => {
        server.status = 500;
        server.report = { detail: "boom" };

        expect(await view.run(SCHEMA)).toBeNull();

        expect(view.errorFor(SCHEMA)).toBeTruthy();
        expect(view.errorFor("other")).toBeNull();
        expect(view.reportFor(SCHEMA)).toBeNull();
    });

    test("keeps each schema's report, and a success clears a failure", async () => {
        server.status = 500;
        await view.run(SCHEMA);
        server.status = 200;
        server.report = REPORT;

        await view.run(SCHEMA);

        expect(view.errorFor(SCHEMA)).toBeNull();
        expect(view.reportFor(SCHEMA)).toEqual(REPORT);
        expect(view.reportFor("other")).toBeNull();
    });

    test("drops an answer about documents as they were before a change", async () => {
        server.hold(SCHEMA);
        const running = view.run(SCHEMA);
        await vi.waitFor(() => expect(server.requests).toHaveLength(1));

        view.forget();
        server.release(SCHEMA);

        expect(await running).toBeNull();
        expect(view.reportFor(SCHEMA)).toBeNull();
        expect(view.isRunning(SCHEMA)).toBe(false);
    });

    test("compares another schema while one is still running", async () => {
        server.hold(SCHEMA);
        const first = view.run(SCHEMA);
        await vi.waitFor(() => expect(view.isRunning(SCHEMA)).toBe(true));

        expect(await view.run("SSH")).toEqual(REPORT);
        expect(view.isRunning("SSH")).toBe(false);
        expect(view.isRunning(SCHEMA)).toBe(true);
        expect(await view.run(SCHEMA)).toBeNull();
        expect(server.requests.map(request => request.schema)).toEqual([
            SCHEMA,
            "SSH",
        ]);

        server.release(SCHEMA);
        await first;
        expect(view.reportFor(SCHEMA)).toEqual(REPORT);
    });

    test("says so when the request gets no answer at all", async () => {
        server.failing = true;

        expect(await view.run(SCHEMA)).toBeNull();

        expect(view.errorFor(SCHEMA)).toBe("The schema could not be compared.");
        expect(view.isRunning(SCHEMA)).toBe(false);
    });

    test("names what the server said went wrong", async () => {
        server.status = 404;
        server.report = { detail: "No schema named EQ." };

        await view.run(SCHEMA);

        expect(view.errorFor(SCHEMA)).toBe(
            "The schema could not be compared: No schema named EQ.",
        );
    });

    test("forgetting drops every report", async () => {
        await view.run(SCHEMA);

        view.forget();

        expect(view.reportFor(SCHEMA)).toBeNull();
    });
});

describe("ConformanceReportView", () => {
    let mounted = null;
    let target = null;

    function render(props) {
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(ConformanceReportView, {
            target,
            props: {
                schemaName: SCHEMA,
                schemaLabel: "Equipment",
                documents: DOCUMENTS,
                ...props,
            },
        });
        return target;
    }

    afterEach(() => {
        if (mounted) unmount(mounted);
        target?.remove();
        mounted = null;
        target = null;
    });

    const conformance = ({
        report = REPORT,
        reportedOn = SCHEMA,
        ...overrides
    } = {}) => ({
        isRunning: () => false,
        errorFor: () => null,
        reportFor: id => (id && id === reportedOn ? report : null),
        run: vi.fn(),
        forget: vi.fn(),
        ...overrides,
    });

    test("opens the document a finding is stated in", async () => {
        const onopen = vi.fn();
        const view = render({
            conformance: conformance(),
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
        });

        expect(view.textContent).toContain("official.ttl");
    });

    test("lists a rule stated on many classes once, naming them", () => {
        const many = ["A", "B", "C", "D", "E"].map(name => ({
            ...REPORT.findings[1],
            targetClass: `${CIM}${name}`,
        }));
        const view = render({
            conformance: conformance({
                report: { ...REPORT, findings: many },
            }),
            prefixes: { cim: CIM },
        });

        expect(view.querySelectorAll("li")).toHaveLength(1);
        expect(view.textContent).toContain(
            "cim:A, cim:B, cim:C and 2 more · cim:ACLineSegment.r",
        );
    });

    test("offers the comparison before it has been run", () => {
        const view = render({
            conformance: conformance({ report: null, reportedOn: null }),
        });

        expect(
            [...view.querySelectorAll("button")].find(
                button => button.textContent.trim() === "Compare",
            ).disabled,
        ).toBe(false);
        expect(view.textContent).not.toContain("property constraints");
    });

    test("groups the findings by kind, worst first", () => {
        const view = render({
            conformance: conformance(),
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
            prefixes: { cim: CIM, xsd: "http://www.w3.org/2001/XMLSchema#" },
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain(
            "1341 of the 1343 property constraints both sides state agree",
        );
        expect(text).toContain("cim:Season · cim:Season.endDate");
        expect(text).toContain("xsd:MonthDay");
        expect(text).toContain("xsd:gMonthDay");
        expect(text).toContain("Reads 1 document: official.ttl");
    });

    test("names the document a finding came from", () => {
        const view = render({
            conformance: conformance(),
            prefixes: { cim: CIM },
        });

        // The comparison merges every enabled document, so without this a finding says which rule
        // is wrong but not which of several files to open.
        expect(view.textContent.replace(/\s+/g, " ")).toContain(
            "stated in: official.ttl",
        );
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
        });

        expect(view.textContent).toContain(
            "The constraints and the schema agree",
        );
        expect(view.querySelectorAll("h3")).toHaveLength(0);
    });

    test("a document that covers nothing is not called a disagreement", () => {
        // What the CGMES 3.0 DiagramLayout cross-profile files do: one rule each, against a schema
        // implying 49 constraints.
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

        expect(state.run).toHaveBeenCalledWith(SCHEMA);
    });

    test("does not compare until asked: a comparison can take seconds", () => {
        const state = conformance({ report: null, reportedOn: null });
        render({ conformance: state });
        flushSync();

        expect(state.run).not.toHaveBeenCalled();
    });

    test("shows a schema's own report, not the last one made", () => {
        const view = render({
            conformance: conformance({ reportedOn: "other" }),
        });

        expect(view.textContent).not.toContain("contradict each other");
    });

    test("says what it compares, how it differs from Problems, and which documents it reads", () => {
        const text = render({
            conformance: conformance({ report: null, reportedOn: null }),
        }).textContent.replace(/\s+/g, " ");

        expect(text).toContain("Compare Equipment with its documents");
        expect(text).toContain("It says nothing about instance data");
        expect(text).toContain("Problems below is a different check");
        expect(text).toContain("Reads 1 document: official.ttl");
    });

    test("with no document there is nothing to compare, and it says what to do", () => {
        const state = conformance({ report: null, reportedOn: null });
        const view = render({ conformance: state, documents: [] });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("No enabled document applies to this schema");
        expect(text).toContain("Applies to");
        expect(
            [...view.querySelectorAll("button")].find(button =>
                button.textContent.includes("Compare"),
            ).disabled,
        ).toBe(true);
    });

    test("every kind of finding explains itself with an example", () => {
        const view = render({ conformance: conformance() });

        const examples = [...view.querySelectorAll("h3[title]")].map(heading =>
            heading.getAttribute("title"),
        );
        expect(examples).toHaveLength(2);
        examples.forEach(example => expect(example).toMatch(/^For example: /));
    });

    test("shows progress only for the schema being compared", () => {
        const state = conformance({
            report: null,
            reportedOn: null,
            isRunning: name => name === "other",
        });
        const view = render({ conformance: state });

        expect(
            [...view.querySelectorAll("button")].find(button =>
                button.textContent.includes("Compare"),
            ).disabled,
        ).toBe(false);
    });

    test("lists what the documents state in a way it does not compare", () => {
        const view = render({
            conformance: conformance({
                report: {
                    ...REPORT,
                    notCompared: [
                        {
                            document: "official.ttl",
                            reason: "OTHER_TARGET",
                            count: 270,
                            terms: [
                                "http://www.w3.org/ns/shacl#targetSubjectsOf",
                            ],
                            message:
                                "270 shapes choose what they apply to with sh:targetSubjectsOf rather than by class.",
                        },
                    ],
                },
            }),
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("Not compared");
        expect(text).toContain(
            "official.ttl: 270 shapes choose what they apply to with sh:targetSubjectsOf",
        );
    });

    test("explains each kind of finding for every case it covers", () => {
        expect(CONFORMANCE_KIND.CONTRADICTED.explanation).toContain(
            "a value the schema does not list",
        );
        expect(CONFORMANCE_KIND.DIFFERENT.explanation).toContain(
            "does not declare the classes needed to tell",
        );
        expect(CONFORMANCE_KIND.NOT_IN_SCHEMA.explanation).toContain(
            "does not have on that class",
        );
    });

    test("says unsaved edits are not part of the comparison", () => {
        const view = render({ conformance: conformance(), dirty: true });

        expect(view.textContent).toContain("Compares the saved versions");
    });
});
