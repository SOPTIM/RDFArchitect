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
    describeSize,
    groupState,
    hasOwnRow,
    isSchema,
    nest,
    ownRowId,
    PackageScope,
    selectedSchemas,
    selectionOf,
    sizeOf,
    toggledGroup,
    toggledSchema,
} from "../../src/lib/utils/packageTree.svelte.js";

/**
 * Model / TC57CIM / IEC61970 / Core, plus a profile beside it. TC57CIM declares nothing of its
 * own; IEC61970 declares classes beside Core, which is the case that needs a row of its own.
 */
const PACKAGES = [
    pkg("model", null, "Model", { declared: 100 }),
    pkg("cim", "model", "TC57CIM", { declared: 60 }),
    pkg("iec", "cim", "IEC61970", { declared: 40, own: 7, ownDeps: 2 }),
    pkg("core", "iec", "Core", { declared: 10, dependencies: 3 }),
    pkg("profiles", "model", "TC57CIMProfiles", { declared: 40 }),
    pkg("topology", "profiles", "TopologyProfile", {
        declared: 26,
        dependencies: 7,
        profile: true,
        candidates: ["https://example.com/Topology/1"],
    }),
];

function pkg(
    id,
    parentId,
    name,
    {
        declared = 0,
        dependencies = 0,
        own = 0,
        ownDeps = 0,
        profile = false,
        candidates = [],
    } = {},
) {
    return {
        fileIndex: 0,
        fileName: "model.xml",
        id,
        parentId,
        name,
        profile,
        namespaceCandidates: candidates,
        declaredClasses: declared,
        dependencyClasses: dependencies,
        ownClasses: own,
        ownDependencyClasses: ownDeps,
    };
}

function node(id) {
    const find = nodes =>
        nodes.reduce(
            (found, candidate) =>
                found ??
                (candidate.id === id ? candidate : find(candidate.children)),
            null,
        );
    return find(nest(PACKAGES));
}

describe("nest", () => {
    it("hangs every package under the one containing it", () => {
        const roots = nest(PACKAGES);

        expect(roots).toHaveLength(1);
        expect(roots[0].children.map(child => child.name)).toEqual([
            "TC57CIM",
            "TC57CIMProfiles",
        ]);
    });

    it("makes a root of a package whose parent was not reported", () => {
        const orphaned = [pkg("core", "missing", "Core", { declared: 10 })];

        expect(nest(orphaned).map(found => found.name)).toEqual(["Core"]);
    });
});

describe("isSchema", () => {
    it("holds for a profile, which a release ships as one file", () => {
        expect(isSchema(node("topology"))).toBe(true);
    });

    it("holds for a package with nothing below it", () => {
        expect(isSchema(node("core"))).toBe(true);
    });

    it("does not hold for a package holding others", () => {
        expect(isSchema(node("cim"))).toBe(false);
    });
});

describe("hasOwnRow", () => {
    it("holds where a package declares classes beside its sub-packages", () => {
        expect(hasOwnRow(node("iec"))).toBe(true);
    });

    it("does not hold where a package only holds others", () => {
        expect(hasOwnRow(node("cim"))).toBe(false);
    });

    it("does not hold for a profile, which is taken whole", () => {
        expect(hasOwnRow(node("topology"))).toBe(false);
    });
});

describe("groupState", () => {
    it("is none while nothing below is picked", () => {
        expect(groupState(node("cim"), new Set())).toBe("none");
    });

    it("is some while part of it is", () => {
        expect(groupState(node("cim"), new Set(["core"]))).toBe("some");
    });

    it("is all once every schema below it is picked", () => {
        const all = new Set(["core", ownRowId("iec")]);

        expect(groupState(node("cim"), all)).toBe("all");
    });
});

describe("toggledGroup", () => {
    it("picks every schema below it, the own-classes row included", () => {
        const picked = toggledGroup(node("cim"), new Set());

        expect([...picked].sort()).toEqual(["core", ownRowId("iec")].sort());
    });

    it("clears them again once all of them are picked", () => {
        const all = new Set(["core", ownRowId("iec")]);

        expect(toggledGroup(node("cim"), all).size).toBe(0);
    });

    it("picks the rest when only some are picked", () => {
        const picked = toggledGroup(node("cim"), new Set(["core"]));

        expect(picked.has(ownRowId("iec"))).toBe(true);
        expect(picked.has("core")).toBe(true);
    });

    it("leaves what sits beside it alone", () => {
        expect(toggledGroup(node("cim"), new Set()).has("topology")).toBe(
            false,
        );
    });
});

describe("toggledSchema", () => {
    it("adds and removes the one row", () => {
        const on = toggledSchema("topology", new Set());

        expect(on.has("topology")).toBe(true);
        expect(toggledSchema("topology", on).has("topology")).toBe(false);
    });
});

describe("selectedSchemas", () => {
    it("reports a profile as one schema of its whole package", () => {
        const [schema] = selectedSchemas(PACKAGES, new Set(["topology"]));

        expect(schema.node.name).toBe("TopologyProfile");
        expect(schema.scope).toBe(PackageScope.WITH_SUB_PACKAGES);
    });

    it("reports an own-classes row as a schema of the package alone", () => {
        const [schema] = selectedSchemas(PACKAGES, new Set([ownRowId("iec")]));

        expect(schema.node.name).toBe("IEC61970");
        expect(schema.scope).toBe(PackageScope.PACKAGE_ONLY);
    });

    it("reports one schema per picked row rather than one for the group", () => {
        const picked = toggledGroup(node("cim"), new Set());

        expect(selectedSchemas(PACKAGES, picked)).toHaveLength(2);
    });
});

describe("selectionOf", () => {
    it("sends the scope each row stands for", () => {
        const selection = selectionOf(
            PACKAGES,
            new Set(["topology", ownRowId("iec")]),
        );

        expect(selection.map(entry => [entry.packageId, entry.scope])).toEqual([
            ["iec", PackageScope.PACKAGE_ONLY],
            ["topology", PackageScope.WITH_SUB_PACKAGES],
        ]);
    });

    it("leaves the schema to be named after its package", () => {
        const [entry] = selectionOf(PACKAGES, new Set(["topology"]));

        expect(entry.graphUri).toBeNull();
        expect(entry.name).toBe("TopologyProfile");
    });

    it("passes a supplied extension namespace to every chosen package", () => {
        const selection = selectionOf(
            PACKAGES,
            new Set(["core", "topology"]),
            "  https://example.com/eu#  ",
        );

        expect(selection.map(entry => entry.extensionNamespace)).toEqual([
            "https://example.com/eu#",
            "https://example.com/eu#",
        ]);
    });

    it("sends no extension namespace when none was typed", () => {
        const [entry] = selectionOf(PACKAGES, new Set(["topology"]), "   ");

        expect(entry.extensionNamespace).toBeNull();
    });

    it("is empty when nothing is picked", () => {
        expect(selectionOf(PACKAGES, new Set())).toEqual([]);
    });
});

describe("describeSize", () => {
    it("keeps what a package holds apart from what it depends on", () => {
        expect(describeSize(node("topology"))).toBe(
            "26 classes + 7 dependencies",
        );
    });

    it("says dependency when there is only one", () => {
        expect(describeSize({ declaredClasses: 4, dependencyClasses: 1 })).toBe(
            "4 classes + 1 dependency",
        );
    });

    it("counts only the package itself for an own-classes row", () => {
        expect(describeSize(node("iec"), PackageScope.PACKAGE_ONLY)).toBe(
            "7 classes + 2 dependencies",
        );
    });
});

describe("sizeOf", () => {
    it("is what the schema ends up holding", () => {
        expect(sizeOf(node("topology"))).toBe(33);
        expect(sizeOf(node("iec"), PackageScope.PACKAGE_ONLY)).toBe(9);
    });
});
