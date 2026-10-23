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
    chosenDiagramIds,
    diagramHomes,
    diagramsByPackage,
    groupsOf,
    homeOf,
    toggled,
    WORKSPACE,
} from "../../src/lib/utils/diagramSelection.svelte.js";
import { ownRowId } from "../../src/lib/utils/packageTree.svelte.js";

/**
 * Two diagrams over two supplier groups: d1 needs a class that only Topology supplies, d2 needs
 * one that either Topology or Core supplies, and Core supplies it even taken alone.
 */
const GROUPS = groupsOf([
    { fileIndex: 0, index: 0, packageIds: ["topology"], packageOnlyIds: [] },
    {
        fileIndex: 0,
        index: 1,
        packageIds: ["topology", "core"],
        packageOnlyIds: ["core"],
    },
]);

const DIAGRAMS = [
    {
        fileIndex: 0,
        id: "d1",
        name: "Topology overview",
        classes: 12,
        supplierGroups: [0],
    },
    {
        fileIndex: 0,
        id: "d2",
        name: "Terminals",
        classes: 4,
        supplierGroups: [1],
    },
];

describe("homeOf", () => {
    it("names the schema that supplies every class the diagram draws", () => {
        expect(homeOf(DIAGRAMS[0], GROUPS, ["topology"])).toBe("topology");
    });

    it("names none while a class it draws has no supplier picked", () => {
        expect(homeOf(DIAGRAMS[0], GROUPS, ["core"])).toBeNull();
    });

    it("takes any package of a group, since each supplies the whole class", () => {
        expect(homeOf(DIAGRAMS[1], GROUPS, ["core"])).toBe("core");
    });

    it("names none for a diagram that draws no class at all", () => {
        expect(
            homeOf({ fileIndex: 0, supplierGroups: [] }, GROUPS, ["topology"]),
        ).toBeNull();
    });

    it("takes the workspace for a diagram drawn across two schemas", () => {
        // d1 can only come from Topology, d2's class comes from Core as the later import.
        const across = {
            fileIndex: 0,
            id: "d3",
            supplierGroups: [0, 1],
        };

        expect(homeOf(across, GROUPS, ["topology", "core"])).toBe(WORKSPACE);
    });

    it("follows the order of the import where several schemas supply a class", () => {
        // Both carry the class of group 1; the import writes it last into whichever comes last.
        expect(homeOf(DIAGRAMS[1], GROUPS, ["topology", "core"])).toBe("core");
        expect(homeOf(DIAGRAMS[1], GROUPS, ["core", "topology"])).toBe(
            "topology",
        );
    });

    it("takes a package picked alone where it supplies the class alone", () => {
        expect(homeOf(DIAGRAMS[1], GROUPS, [ownRowId("core")])).toBe(
            ownRowId("core"),
        );
    });

    it("names none for a package picked alone that only supplies with its sub-packages", () => {
        expect(homeOf(DIAGRAMS[0], GROUPS, [ownRowId("topology")])).toBeNull();
    });

    it("keeps the groups of one export apart from those of another", () => {
        const groups = groupsOf([
            { fileIndex: 0, index: 0, packageIds: ["topology"] },
            { fileIndex: 1, index: 0, packageIds: ["other"] },
        ]);

        expect(
            homeOf({ fileIndex: 1, supplierGroups: [0] }, groups, ["topology"]),
        ).toBeNull();
    });
});

describe("diagramHomes", () => {
    it("files each diagram under the schema it would be stored in", () => {
        const homes = diagramHomes(DIAGRAMS, GROUPS, ["topology"], new Set());

        expect(homes.byRow.get("topology").map(d => d.id)).toEqual([
            "d1",
            "d2",
        ]);
        expect(homes.workspace).toEqual([]);
    });

    it("leaves out the ones turned down and the ones nothing supplies", () => {
        const homes = diagramHomes(DIAGRAMS, GROUPS, ["core"], new Set(["d2"]));

        expect(homes.byRow.size).toBe(0);
        expect(homes.workspace).toEqual([]);
    });

    it("puts a diagram drawn across two schemas in the workspace", () => {
        const across = { fileIndex: 0, id: "d3", supplierGroups: [0, 1] };

        const homes = diagramHomes(
            [across],
            GROUPS,
            ["topology", "core"],
            new Set(),
        );

        expect(homes.workspace.map(d => d.id)).toEqual(["d3"]);
    });
});

describe("diagramsByPackage", () => {
    it("keeps the diagrams of each package together, in their reported order", () => {
        const byPackage = diagramsByPackage([
            { id: "d1", packageId: "topology" },
            { id: "d2", packageId: "core" },
            { id: "d3", packageId: "topology" },
        ]);

        expect(byPackage.get("topology").map(d => d.id)).toEqual(["d1", "d3"]);
        expect(byPackage.get("core").map(d => d.id)).toEqual(["d2"]);
    });

    it("reports nothing for a package declaring none", () => {
        expect(diagramsByPackage([]).get("topology")).toBeUndefined();
    });
});

describe("groupsOf", () => {
    it("puts every group under its file and index", () => {
        expect(GROUPS.get("0:1")).toEqual([
            "topology",
            "core",
            ownRowId("core"),
        ]);
    });
});

describe("chosenDiagramIds", () => {
    it("takes every available diagram that was not turned down", () => {
        expect(
            chosenDiagramIds(DIAGRAMS, GROUPS, ["topology"], new Set(["d1"])),
        ).toEqual(["d2"]);
    });

    it("takes them all when none was turned down", () => {
        expect(
            chosenDiagramIds(DIAGRAMS, GROUPS, ["topology"], new Set()),
        ).toEqual(["d1", "d2"]);
    });

    it("takes none of an export that was not read", () => {
        expect(chosenDiagramIds([], new Map(), [], new Set())).toEqual([]);
    });

    it("leaves out one the selection cannot supply, turned down or not", () => {
        expect(chosenDiagramIds(DIAGRAMS, GROUPS, ["core"], new Set())).toEqual(
            ["d2"],
        );
    });
});

describe("toggled", () => {
    it("adds and removes the one diagram", () => {
        const on = toggled("d1", new Set());

        expect(on.has("d1")).toBe(true);
        expect(toggled("d1", on).has("d1")).toBe(false);
    });

    it("leaves what it was given alone", () => {
        const picked = new Set(["d1"]);

        toggled("d2", picked);

        expect([...picked]).toEqual(["d1"]);
    });
});
