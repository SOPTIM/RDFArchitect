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

import { graphLabel, graphLabelOf, graphUri } from "$lib/utils/graph-label.js";

const EQUIPMENT = {
    keyword: "Equipment",
    uri: { prefix: "http://iec.ch/TC57/ns/CIM/", suffix: "EquipmentProfile" },
};

const UNNAMED = {
    uri: { prefix: "http://example.org/", suffix: "Topology" },
};

describe("graphLabel", () => {
    test("names a schema by its dcat:keyword", () => {
        expect(graphLabel(EQUIPMENT)).toBe("Equipment");
    });

    test("falls back to the tail of the URI", () => {
        expect(graphLabel(UNNAMED)).toBe("Topology");
    });

    test("treats an empty keyword as no keyword", () => {
        expect(graphLabel({ ...UNNAMED, keyword: "" })).toBe("Topology");
    });
});

describe("graphUri", () => {
    test("joins the prefix and the suffix the backend splits", () => {
        expect(graphUri(EQUIPMENT)).toBe(
            "http://iec.ch/TC57/ns/CIM/EquipmentProfile",
        );
    });

    test("accepts a URI that is already a string", () => {
        expect(graphUri("http://example.org/Topology")).toBe(
            "http://example.org/Topology",
        );
    });
});

describe("graphLabelOf", () => {
    const graphs = [EQUIPMENT, UNNAMED];

    test("finds the graph by its full URI", () => {
        expect(
            graphLabelOf(graphs, "http://iec.ch/TC57/ns/CIM/EquipmentProfile"),
        ).toBe("Equipment");
    });

    // The list is fetched, so callers ask before it arrives; an approximate name beats a gap.
    test("falls back to the URI's tail while the list is still unknown", () => {
        expect(graphLabelOf(null, "http://example.org/Diagram")).toBe(
            "Diagram",
        );
    });
});
