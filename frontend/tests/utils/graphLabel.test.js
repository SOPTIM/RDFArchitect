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
    graphLabeller,
    graphTooltip,
    schemaName,
} from "$lib/utils/graph-label.js";

/** A CGMES 3.0 profile, which names and versions itself. */
const CURRENT = {
    name: "Equipment",
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
    description: "The core equipment profile.",
    versionIris: ["http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0"],
    versionInfo: "3.0.0",
};

/** A CGMES 2.4.15 profile, which has a name but no version. */
const LEGACY = {
    name: "EquipmentCore",
    keyword: "EQ",
    label: "EquipmentProfile",
    versionIris: ["http://entsoe.eu/CIM/EquipmentCore/3/1"],
};

/** A graph that is no CIM profile at all. */
const PLAIN = {
    name: "Notes",
};

describe("schemaName", () => {
    test("takes the name the backend sends", () => {
        expect(schemaName(CURRENT)).toBe("Equipment");
    });

    test("accepts a bare name, so a locked graph can be passed in as is", () => {
        expect(schemaName("Equipment")).toBe("Equipment");
    });

    test("is empty for no graph at all", () => {
        expect(schemaName(undefined)).toBe("");
    });
});

describe("graphLabel", () => {
    test("prefers the name the profile gives itself", () => {
        expect(graphLabel(CURRENT)).toBe("Core Equipment Vocabulary");
    });

    test("falls back to the keyword when the profile has no name", () => {
        expect(graphLabel({ ...CURRENT, label: null })).toBe("EQ");
    });

    test("falls back to the graph name when the graph is no profile", () => {
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
            name: "EquipmentCoreOperation",
        };

        const nameOf = graphLabeller([core, coreOperation]);

        expect(nameOf(core)).toBe("EquipmentProfile (EquipmentCore)");
        expect(nameOf(coreOperation)).toBe(
            "EquipmentProfile (EquipmentCoreOperation)",
        );
    });

    test("does not repeat a name that already is the graph name", () => {
        const two = { name: "Other", keyword: "Notes" };
        const three = { name: "Notes" };

        expect(graphLabeller([two, three])(three)).toBe("Notes");
        expect(graphLabeller([two, three])(two)).toBe("Notes (Other)");
    });
});

describe("graphTooltip", () => {
    test("names the profile version and what the schema is for", () => {
        expect(graphTooltip(CURRENT)).toBe(
            [
                "Version 3.0.0",
                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                "The core equipment profile.",
            ].join("\n"),
        );
    });

    test("says nothing about a version a profile has nowhere to write", () => {
        expect(graphTooltip(LEGACY)).toBe(
            "http://entsoe.eu/CIM/EquipmentCore/3/1",
        );
    });

    test("leaves out the graph name, which is already on screen", () => {
        expect(graphTooltip(CURRENT).split("\n")).not.toContain("Equipment");
    });

    test("falls back to the graph for one that says nothing about itself", () => {
        expect(graphTooltip(PLAIN)).toBe("Notes");
    });
});
