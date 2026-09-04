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

import { describe, expect, test } from "vitest";

import {
    classRules,
    constraintRows,
    originsOf,
    ruleCount,
    summaryOf,
} from "$lib/shacl/classConstraints.js";
import { workbenchHref, workbenchTarget } from "$lib/shacl/workbenchLink.js";

const DOCUMENT_ID = "11111111-2222-3333-4444-555555555555";

const CUSTOM = {
    nodeShapes: [{ id: "custom-node", triples: "", origins: [] }],
    propertyShapes: [
        wrapper("length", "0..1, xsd:float", [
            {
                id: "custom-length",
                origins: [
                    {
                        documentId: DOCUMENT_ID,
                        documentName: "eq.ttl",
                        line: 7,
                    },
                ],
            },
        ]),
    ],
    derivedPropertyShapes: [wrapper("IdentifiedObject.name", "1..1")],
};

const GENERATED = {
    nodeShapes: [],
    propertyShapes: [
        wrapper("length", "0..1, xsd:float", [
            { id: "gen-length", origins: [] },
        ]),
        wrapper("r", "0..1, xsd:float", [{ id: "gen-r", origins: [] }]),
    ],
    derivedPropertyShapes: [],
};

function wrapper(label, summary, shapes = [{ id: label, origins: [] }]) {
    return {
        label,
        propertyType: "attribute",
        summary,
        propertyShapes: shapes,
    };
}

describe("constraintRows", () => {
    test("folds both halves into one row per property", () => {
        const rows = constraintRows({ custom: CUSTOM, generated: GENERATED });

        // Generated and custom shapes share no naming convention, so the property is the only
        // thing they can be matched on — the same reason the conformance check matches on path.
        const length = rows.find(row => row.label === "length");
        expect(length.sources.map(source => source.side)).toEqual([
            "generated",
            "custom",
        ]);
    });

    test("keeps inherited rules apart, and sorts them last", () => {
        const rows = constraintRows({ custom: CUSTOM, generated: GENERATED });

        expect(rows.map(row => row.label)).toEqual([
            "length",
            "r",
            "IdentifiedObject.name",
        ]);
        expect(rows.at(-1).inherited).toBe(true);
    });

    test("a scope narrows the answer to one half", () => {
        expect(
            constraintRows({
                custom: CUSTOM,
                generated: GENERATED,
                scope: "custom",
            }).map(row => row.label),
        ).toEqual(["length", "IdentifiedObject.name"]);
        expect(
            constraintRows({
                custom: CUSTOM,
                generated: GENERATED,
                scope: "generated",
            }).map(row => row.label),
        ).toEqual(["length", "r"]);
    });

    test("the filter reads the rule as well as the name", () => {
        const byRule = constraintRows({
            custom: CUSTOM,
            generated: GENERATED,
            filter: "xsd:float",
        });

        expect(byRule.map(row => row.label)).toEqual(["length", "r"]);
    });

    test("copes with a half that is missing entirely", () => {
        expect(constraintRows({ custom: CUSTOM })).not.toHaveLength(0);
        expect(constraintRows({})).toEqual([]);
    });
});

describe("summaryOf", () => {
    test("says it once when both halves agree", () => {
        const row = constraintRows({
            custom: CUSTOM,
            generated: GENERATED,
        }).find(candidate => candidate.label === "length");

        expect(summaryOf(row)).toEqual([
            { text: "0..1, xsd:float", side: null },
        ]);
    });

    test("shows both, labelled, when they disagree", () => {
        const row = constraintRows({
            custom: {
                propertyShapes: [wrapper("length", "0..1, xsd:integer")],
            },
            generated: {
                propertyShapes: [wrapper("length", "0..1, xsd:float")],
            },
        })[0];

        // The disagreement is the most interesting thing the dialog can show; a tab hid it.
        expect(summaryOf(row)).toEqual([
            { text: "0..1, xsd:float", side: "generated" },
            { text: "0..1, xsd:integer", side: "custom" },
        ]);
    });

    test("says nothing when the shapes summarise to nothing", () => {
        const row = constraintRows({
            custom: { propertyShapes: [wrapper("sparqlRule", "")] },
        })[0];

        expect(summaryOf(row)).toEqual([]);
    });
});

describe("originsOf", () => {
    test("names the schema and each document, once each", () => {
        const row = constraintRows({
            custom: CUSTOM,
            generated: GENERATED,
        }).find(candidate => candidate.label === "length");

        expect(originsOf(row)).toEqual([
            { label: "generated", generated: true },
            {
                label: "eq.ttl",
                documentId: DOCUMENT_ID,
                line: 7,
                generated: false,
            },
        ]);
    });
});

describe("ruleCount and classRules", () => {
    test("counts every shape behind a row", () => {
        const row = constraintRows({
            custom: CUSTOM,
            generated: GENERATED,
        }).find(candidate => candidate.label === "length");

        expect(ruleCount(row)).toBe(2);
    });

    test("class-level shapes are tagged with the half they came from", () => {
        expect(classRules({ custom: CUSTOM, generated: GENERATED })).toEqual([
            { id: "custom-node", triples: "", origins: [], side: "custom" },
        ]);
    });
});

describe("workbenchHref", () => {
    test("plain link when there is no particular document to open", () => {
        expect(workbenchHref()).toBe("/shacl");
    });

    test("carries the document, and the line with it", () => {
        expect(workbenchHref(DOCUMENT_ID, 214)).toBe(
            `/shacl?document=${DOCUMENT_ID}&line=214`,
        );
    });

    test("a line without a document is not a destination", () => {
        // It would be a line number in whichever document happened to be open.
        expect(workbenchHref(null, 214)).toBe("/shacl");
    });
});

describe("workbenchTarget", () => {
    test("reads back what the link carried", () => {
        expect(
            workbenchTarget(
                new URL(`http://x${workbenchHref(DOCUMENT_ID, 9)}`),
            ),
        ).toEqual({ documentId: DOCUMENT_ID, line: 9 });
    });

    test("ignores a line it cannot use", () => {
        expect(workbenchTarget(new URL("http://x/shacl?line=9"))).toEqual({
            documentId: null,
            line: null,
        });
        expect(
            workbenchTarget(
                new URL(`http://x/shacl?document=${DOCUMENT_ID}&line=nope`),
            ),
        ).toEqual({ documentId: DOCUMENT_ID, line: null });
    });

    test("copes with no url at all", () => {
        expect(workbenchTarget(null)).toEqual({ documentId: null, line: null });
    });
});
