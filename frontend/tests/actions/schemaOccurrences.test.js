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
import { describe, expect, it } from "vitest";

import {
    groupCandidatesByStub,
    mergeSchemaOccurrences,
    sortSchemaOccurrences,
    sourceCandidates,
    stubsDiffer,
} from "../../src/lib/actions/schemaOccurrences.js";

function occurrence(graphUri, present, stub = null) {
    return { graphUri, present, classUUID: `${graphUri}-uuid`, stub };
}

const stub = overrides => ({
    label: "AnalogControl",
    comment: "An analog control.",
    superClassUri: "http://iec.ch/TC57/CIM100#Control",
    packageUri:
        "http://iec.ch/TC57/ns/CIM/Operation-EU#Package_OperationProfile",
    packageLabel: "Package_OperationProfile",
    stereotypes: [],
    ...overrides,
});

describe("sourceCandidates", () => {
    it("keeps the schemas that define the class, without the target", () => {
        const occurrences = [
            occurrence("op", true, stub()),
            occurrence("sc", false),
            occurrence("tp", true, stub()),
        ];
        expect(
            sourceCandidates(occurrences, "tp").map(c => c.graphUri),
        ).toEqual(["op"]);
    });
});

describe("stubsDiffer", () => {
    it("is false for a single candidate", () => {
        expect(stubsDiffer([occurrence("op", true, stub())])).toBe(false);
    });

    it("is false while the candidates would create the same class", () => {
        expect(
            stubsDiffer([
                occurrence("op", true, stub()),
                occurrence("tp", true, stub()),
            ]),
        ).toBe(false);
    });

    it("is true once a copied property differs", () => {
        expect(
            stubsDiffer([
                occurrence("op", true, stub()),
                occurrence(
                    "tp",
                    true,
                    stub({
                        superClassUri: "http://iec.ch/TC57/CIM100#IOPoint",
                    }),
                ),
            ]),
        ).toBe(true);
    });
});

describe("sortSchemaOccurrences", () => {
    it("orders by short name and falls back to the uri", () => {
        const occurrences = [
            { graphUri: "http://graph#z", keyword: "OP" },
            { graphUri: "http://graph#a", keyword: "OP" },
            { graphUri: "http://graph#b", keyword: "EQ" },
            { graphUri: "http://graph#c", keyword: null },
        ];
        expect(sortSchemaOccurrences(occurrences).map(o => o.graphUri)).toEqual(
            [
                "http://graph#c",
                "http://graph#b",
                "http://graph#a",
                "http://graph#z",
            ],
        );
    });
});

describe("groupCandidatesByStub", () => {
    it("offers schemas that agree on the class as one choice", () => {
        const groups = groupCandidatesByStub([
            occurrence("op", true, stub()),
            occurrence("tp", true, stub()),
            occurrence("sc", true, stub({ comment: "Something else." })),
        ]);
        expect(
            groups.map(group => group.occurrences.map(o => o.graphUri)),
        ).toEqual([["op", "tp"], ["sc"]]);
        expect(groups.map(group => group.key)).toEqual(["op", "sc"]);
    });
});

describe("mergeSchemaOccurrences", () => {
    it("keeps a schema extendable while it misses one of the classes", () => {
        const merged = mergeSchemaOccurrences([
            [occurrence("op", true, stub()), occurrence("sc", true, stub())],
            [occurrence("op", true, stub()), occurrence("sc", false)],
        ]);
        expect(merged.map(entry => [entry.graphUri, entry.present])).toEqual([
            ["op", true],
            ["sc", false],
        ]);
    });
});
