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

import { importChoiceOf } from "../../src/lib/utils/importChoice.js";

/** One export: two profiles, each with a diagram of its own. */
const READ = {
    packages: [
        scanned("topology", "TopologyProfile", 26, 7),
        scanned("core", "CoreEquipmentProfile", 118, 21),
    ],
    diagrams: [
        {
            fileIndex: 0,
            id: "d1",
            packageId: "topology",
            name: "Topology overview",
            classes: 12,
            supplierGroups: [0],
        },
        {
            fileIndex: 0,
            id: "d2",
            packageId: "core",
            name: "Connectivity",
            classes: 18,
            supplierGroups: [1],
        },
    ],
    supplierGroups: [
        {
            fileIndex: 0,
            index: 0,
            packageIds: ["topology"],
            packageOnlyIds: [],
        },
        { fileIndex: 0, index: 1, packageIds: ["core"], packageOnlyIds: [] },
    ],
    classNames: [],
};

function scanned(id, name, declared, dependencies) {
    return {
        fileIndex: 0,
        fileName: "export.xml",
        id,
        parentId: null,
        name,
        profile: true,
        namespaceCandidates: [],
        declaredClasses: declared,
        dependencyClasses: dependencies,
        ownClasses: [],
        ownDependencies: [],
        extensionNamespace: "http://example.com/ext#",
    };
}

describe("importChoiceOf", () => {
    it("reports the schemas the ticked rows produce", () => {
        const choice = importChoiceOf(
            READ,
            new Set(["topology", "core"]),
            new Set(),
        );

        expect(choice.schemas.map(row => row.node.name)).toEqual([
            "TopologyProfile",
            "CoreEquipmentProfile",
        ]);
        expect(choice.orderedRowIds).toEqual(["topology", "core"]);
    });

    it("adds up what those schemas hold", () => {
        const choice = importChoiceOf(READ, new Set(["topology"]), new Set());

        expect(choice.classes).toBe(33);
    });

    it("files each diagram under the schema it would be stored in", () => {
        const choice = importChoiceOf(
            READ,
            new Set(["topology", "core"]),
            new Set(),
        );

        expect(
            choice.homes.byRow.get("topology").map(diagram => diagram.id),
        ).toEqual(["d1"]);
        expect(choice.homes.workspace).toEqual([]);
    });

    it("takes the diagrams of the ticked rows and no others", () => {
        const choice = importChoiceOf(READ, new Set(["topology"]), new Set());

        expect(choice.diagramIds).toEqual(["d1"]);
    });

    it("leaves out a diagram that was turned down", () => {
        const choice = importChoiceOf(
            READ,
            new Set(["topology", "core"]),
            new Set(["d1"]),
        );

        expect(choice.diagramIds).toEqual(["d2"]);
    });

    it("offers the diagrams of every package, ticked or not", () => {
        // The tree lists them under their package before anything is picked.
        const choice = importChoiceOf(READ, new Set(), new Set());

        expect(choice.diagramsByOwner.get("core").map(one => one.id)).toEqual([
            "d2",
        ]);
        expect(choice.diagramIds).toEqual([]);
    });

    it("is empty before an export has been read", () => {
        const choice = importChoiceOf(null, new Set(), new Set());

        expect(choice.schemas).toEqual([]);
        expect(choice.diagramIds).toEqual([]);
        expect(choice.classes).toBe(0);
    });
});
