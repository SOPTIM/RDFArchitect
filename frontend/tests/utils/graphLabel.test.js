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
    graphLabel,
    graphLabelOf,
    graphLabeller,
    graphTooltip,
    graphUri,
    graphVersion,
} from "$lib/utils/graph-label.js";

/** A CGMES 3.0 profile, which names and versions itself. */
const CURRENT = {
    uri: { prefix: "http://example.org/graphs/", suffix: "Equipment" },
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
    description: "The core equipment profile.",
    versionIris: ["http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0"],
    versionInfo: "3.0.0",
};

/** A CGMES 2.4.15 profile, which has a name but no version. */
const LEGACY = {
    uri: { prefix: "http://example.org/graphs/", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
    versionIris: ["http://entsoe.eu/CIM/EquipmentCore/3/1"],
};

/** A graph that is no CIM profile at all. */
const PLAIN = {
    uri: { prefix: "http://example.org/graphs/", suffix: "Notes" },
};

describe("graphUri", () => {
    test("joins the prefix and suffix the backend splits a URI into", () => {
        expect(graphUri(CURRENT)).toBe("http://example.org/graphs/Equipment");
    });

    test("accepts a bare URI, so a locked graph can be passed in as is", () => {
        expect(graphUri("http://example.org/graphs/Equipment")).toBe(
            "http://example.org/graphs/Equipment",
        );
    });
});

describe("graphLabel", () => {
    test("prefers the name the profile gives itself", () => {
        expect(graphLabel(CURRENT)).toBe("Core Equipment Vocabulary");
    });

    test("falls back to the keyword when the profile has no name", () => {
        expect(graphLabel({ ...CURRENT, label: null })).toBe("EQ");
    });

    test("falls back to the URI suffix when the graph is no profile", () => {
        expect(graphLabel(PLAIN)).toBe("Notes");
    });
});

describe("graphLabeller", () => {
    test("leaves an unambiguous name alone", () => {
        const nameOf = graphLabeller([CURRENT, LEGACY, PLAIN]);

        expect(nameOf(CURRENT)).toBe("Core Equipment Vocabulary");
        expect(nameOf(PLAIN)).toBe("Notes");
    });

    test("appends the graph name where two schemas read alike", () => {
        const core = { ...LEGACY };
        const coreOperation = {
            ...LEGACY,
            uri: {
                prefix: "http://example.org/graphs/",
                suffix: "EquipmentCoreOperation",
            },
        };

        const nameOf = graphLabeller([core, coreOperation]);

        expect(nameOf(core)).toBe("EquipmentProfile (EquipmentCore)");
        expect(nameOf(coreOperation)).toBe(
            "EquipmentProfile (EquipmentCoreOperation)",
        );
    });

    test("does not repeat a name that already is the URI suffix", () => {
        const one = { uri: { prefix: "http://a.example/", suffix: "Notes" } };
        const two = { uri: { prefix: "http://b.example/", suffix: "Notes" } };

        expect(graphLabeller([one, two])(one)).toBe("Notes");
    });
});

describe("graphVersion", () => {
    test("reports the version a profile states", () => {
        expect(graphVersion(CURRENT)).toBe("3.0.0");
    });

    test("is empty for a profile that states none", () => {
        expect(graphVersion(LEGACY)).toBe("");
    });
});

describe("graphTooltip", () => {
    test("names the graph, the profile version and what it is for", () => {
        expect(graphTooltip(CURRENT)).toBe(
            [
                "http://example.org/graphs/Equipment",
                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                "The core equipment profile.",
            ].join("\n"),
        );
    });

    test("holds just the graph for one that says nothing else", () => {
        expect(graphTooltip(PLAIN)).toBe("http://example.org/graphs/Notes");
    });
});

describe("graphLabelOf", () => {
    test("names the graph with the given URI", () => {
        expect(
            graphLabelOf(
                [CURRENT, LEGACY],
                "http://example.org/graphs/Equipment",
            ),
        ).toBe("Core Equipment Vocabulary");
    });

    test("falls back to the URI suffix while the list is still loading", () => {
        expect(graphLabelOf([], "http://example.org/graphs/Equipment")).toBe(
            "Equipment",
        );
    });
});
