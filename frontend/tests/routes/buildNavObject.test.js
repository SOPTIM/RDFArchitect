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

import { graphStore } from "$lib/stores/graphStore.ts";

import { getWorkspaceNavEntry } from "../../src/routes/mainpage/packageNavigation/build-nav-object.js";

/** A CGMES 3.0 profile, which names and versions itself. */
const CURRENT = {
    uri: { prefix: "http://example.org/graphs/", suffix: "Equipment" },
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
    description: "The core equipment profile.",
    versionIris: ["http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0"],
    versionInfo: "3.0.0",
};

/**
 * Two CGMES 2.4.15 profiles as the official library writes them: same keyword,
 * same package label, so only the graph they were imported into separates them.
 */
const LEGACY_CORE = {
    uri: { prefix: "http://example.org/graphs/", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
    versionIris: ["http://entsoe.eu/CIM/EquipmentCore/3/1"],
};

const LEGACY_CORE_OPERATION = {
    uri: {
        prefix: "http://example.org/graphs/",
        suffix: "EquipmentCoreOperation",
    },
    keyword: "EQ",
    label: "EquipmentProfile",
    versionIris: ["http://entsoe.eu/CIM/EquipmentOperation/3/1"],
};

/** The schema entries of a freshly built workspace, in the order they are shown. */
async function schemaEntries(existing) {
    const workspace = await getWorkspaceNavEntry("cgmes", existing);
    return workspace.children;
}

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn() },
}));

vi.mock("$lib/stores/packageStore.ts", () => ({
    packageStore: {
        getPackages: vi.fn().mockResolvedValue({ internal: [], external: [] }),
    },
}));

vi.mock("$lib/stores/classStore.ts", () => ({
    classStore: { getClasses: vi.fn().mockResolvedValue([]) },
}));

beforeEach(() => {
    graphStore.getGraphs.mockResolvedValue([
        CURRENT,
        LEGACY_CORE,
        LEGACY_CORE_OPERATION,
    ]);
});

describe("getWorkspaceNavEntry", () => {
    test("names a schema by its profile and tells apart those that read alike", async () => {
        const entries = await schemaEntries();

        expect(entries.map(entry => entry.label)).toEqual([
            "Core Equipment Vocabulary",
            "EquipmentProfile (EquipmentCore)",
            "EquipmentProfile (EquipmentCoreOperation)",
        ]);
        expect(entries.map(entry => entry.id)).toEqual([
            "http://example.org/graphs/Equipment",
            "http://example.org/graphs/EquipmentCore",
            "http://example.org/graphs/EquipmentCoreOperation",
        ]);
    });

    test("carries the keyword the entry shows as a badge", async () => {
        const [current, legacy] = await schemaEntries();

        expect(current.data).toEqual({ keyword: "EQ" });
        expect(legacy.data).toEqual({ keyword: "EQ" });
    });

    test("hovering a schema names its version IRIs and what it is for", async () => {
        const [current] = await schemaEntries();

        expect(current.tooltip).toBe(
            [
                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                "The core equipment profile.",
            ].join("\n"),
        );
    });

    test("refreshes the name and the badge on a rebuilt workspace", async () => {
        const workspace = await getWorkspaceNavEntry("cgmes");
        const [before] = workspace.children;

        graphStore.getGraphs.mockResolvedValue([
            { ...CURRENT, keyword: "EQX", label: null },
        ]);
        const rebuilt = await getWorkspaceNavEntry("cgmes", workspace);
        const [after] = rebuilt.children;

        // The entry is reused, so a stale badge would survive the rebuild.
        expect(after).toBe(before);
        expect(after.label).toBe("EQX");
        expect(after.data).toEqual({ keyword: "EQX" });
    });
});
