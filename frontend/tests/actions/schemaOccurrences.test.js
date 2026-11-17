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
    schemaLabel,
    sortSchemaOccurrences,
    sourceCandidates,
    stubsDiffer,
} from "../../src/lib/actions/schemaOccurrences.js";

function occurrence(schemaName, present, stub = null) {
    return { schemaName, present, classUUID: `${schemaName}-uuid`, stub };
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
            sourceCandidates(occurrences, "tp").map(c => c.schemaName),
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

describe("schemaLabel", () => {
    it("names a schema the way the navigation tree does", () => {
        const named = sortSchemaOccurrences([
            {
                schemaName: "http://graph#Equipment",
                keyword: "EQ",
                label: "Core Equipment Vocabulary",
            },
        ]);

        expect(schemaLabel(named[0])).toBe("Core Equipment Vocabulary");
    });

    it("falls back to the keyword and then to the graph", () => {
        expect(
            schemaLabel({ schemaName: "http://graph#EQ_", keyword: "EQ" }),
        ).toBe("EQ");
        expect(schemaLabel({ schemaName: "http://graph#Notes" })).toBe("Notes");
    });

    /**
     * The four official equipment profiles name themselves alike, so a list that offers a class
     * in "EquipmentProfile" four times says nothing about which one is meant.
     */
    it("tells apart the schemas that read alike", () => {
        const occurrences = sortSchemaOccurrences([
            {
                schemaName: "http://graph#EquipmentCore",
                label: "EquipmentProfile",
            },
            {
                schemaName: "http://graph#EquipmentOperation",
                label: "EquipmentProfile",
            },
        ]);

        expect(occurrences.map(schemaLabel)).toEqual([
            "EquipmentProfile (EquipmentCore)",
            "EquipmentProfile (EquipmentOperation)",
        ]);
    });
});

describe("sortSchemaOccurrences", () => {
    it("orders by the name the schema is shown under, not by its keyword", () => {
        const occurrences = [
            { schemaName: "http://graph#a", keyword: "ZZ", label: "Apples" },
            { schemaName: "http://graph#b", keyword: "AA", label: "Bananas" },
        ];

        expect(
            sortSchemaOccurrences(occurrences).map(o => o.schemaName),
        ).toEqual(["http://graph#a", "http://graph#b"]);
    });

    it("orders by short name and falls back to the uri", () => {
        const occurrences = [
            { schemaName: "http://graph#z", keyword: "OP" },
            { schemaName: "http://graph#a", keyword: "OP" },
            { schemaName: "http://graph#b", keyword: "EQ" },
            { schemaName: "http://graph#c", keyword: null },
        ];
        expect(
            sortSchemaOccurrences(occurrences).map(o => o.schemaName),
        ).toEqual([
            "http://graph#c",
            "http://graph#b",
            "http://graph#a",
            "http://graph#z",
        ]);
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
            groups.map(group => group.occurrences.map(o => o.schemaName)),
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
        expect(merged.map(entry => [entry.schemaName, entry.present])).toEqual([
            ["op", true],
            ["sc", false],
        ]);
    });
});
