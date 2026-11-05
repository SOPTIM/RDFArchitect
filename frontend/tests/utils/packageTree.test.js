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
    classNamesOf,
    declaredOf,
    dependenciesOf,
    dependencyNamesOf,
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
    withoutWrapper,
} from "../../src/lib/utils/packageTree.js";

/**
 * Model / TC57CIM / IEC61970 / Core, plus a profile beside it. TC57CIM declares nothing of its
 * own; IEC61970 declares classes beside Core, which is the case that needs a row of its own.
 */
const PACKAGES = [
    pkg("model", null, "Model", { declared: 100 }),
    pkg("cim", "model", "TC57CIM", { declared: 60 }),
    pkg("iec", "cim", "IEC61970", {
        declared: 40,
        own: [0, 1, 2, 3, 4, 5, 6],
        ownDeps: [7, 8],
    }),
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
        own = [],
        ownDeps = [],
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
        ownDependencies: ownDeps,
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

describe("withoutWrapper", () => {
    it("resolves the one package everything hangs under, naming the tree after it", () => {
        const tree = withoutWrapper(PACKAGES);

        expect(tree.title).toBe("Model");
        expect(tree.nodes.map(node => node.id)).toEqual(["cim", "profiles"]);
    });

    it("keeps a single outermost package that becomes a schema itself", () => {
        const alone = [pkg("solo", null, "Solo", { declared: 4 })];

        expect(withoutWrapper(alone)).toEqual({
            title: null,
            nodes: nest(alone),
        });
    });

    it("keeps one that declares classes beside its sub-packages, which are pickable", () => {
        const wrapping = [
            pkg("root", null, "Root", { declared: 9, own: [0, 1] }),
            pkg("inner", "root", "Inner", { declared: 7 }),
        ];

        expect(withoutWrapper(wrapping).title).toBeNull();
    });

    it("keeps several outermost packages apart, there being no one wrapper", () => {
        const two = [
            pkg("one", null, "One", { declared: 3 }),
            pkg("two", null, "Two", { declared: 3 }),
        ];

        expect(withoutWrapper(two).nodes).toHaveLength(2);
    });

    it("reports nothing for an export that was never read", () => {
        expect(withoutWrapper([])).toEqual({ title: null, nodes: [] });
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

    it("passes the supplied namespaces to every chosen package", () => {
        const selection = selectionOf(PACKAGES, new Set(["core", "topology"]), {
            model: "  https://example.com/cim#  ",
            extension: "  https://example.com/eu#  ",
        });

        expect(
            selection.map(entry => [
                entry.modelNamespace,
                entry.extensionNamespace,
            ]),
        ).toEqual([
            ["https://example.com/cim#", "https://example.com/eu#"],
            ["https://example.com/cim#", "https://example.com/eu#"],
        ]);
    });

    it("sends no namespace at all when none was given", () => {
        const [entry] = selectionOf(PACKAGES, new Set(["topology"]));

        expect(entry.modelNamespace).toBeNull();
        expect(entry.extensionNamespace).toBeNull();
    });

    it("sends no extension namespace when none was typed", () => {
        const [entry] = selectionOf(PACKAGES, new Set(["topology"]), {
            extension: "   ",
        });

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

describe("declaredOf and dependenciesOf", () => {
    it("report the two halves of what a schema holds", () => {
        expect(declaredOf(node("topology"))).toBe(26);
        expect(dependenciesOf(node("topology"))).toBe(7);
    });

    it("count only the package itself for an own-classes row", () => {
        expect(declaredOf(node("iec"), PackageScope.PACKAGE_ONLY)).toBe(7);
        expect(dependenciesOf(node("iec"), PackageScope.PACKAGE_ONLY)).toBe(2);
    });

    it("read a package that states neither as holding nothing", () => {
        expect(declaredOf({})).toBe(0);
        expect(dependenciesOf({})).toBe(0);
    });
});

describe("classNamesOf and dependencyNamesOf", () => {
    // What the read reports: a pool of names, and per package the classes it declares on its own.
    // Two classes of one name stay two, which is why they are referred to by position.
    const NAMES = ["Terminal", "TopologicalNode", "String", "String", "Float"];
    const CORE = {
        id: "core",
        ownClasses: [1],
        ownDependencies: [2],
        children: [],
    };
    const TOPOLOGY = {
        id: "topology",
        ownClasses: [0],
        ownDependencies: [2, 4],
        children: [CORE],
    };

    it("unions what the package and its sub-packages declare", () => {
        expect(
            classNamesOf(TOPOLOGY, PackageScope.WITH_SUB_PACKAGES, NAMES),
        ).toEqual(["Terminal", "TopologicalNode"]);
    });

    it("takes only the package itself for an own-classes row", () => {
        expect(
            classNamesOf(TOPOLOGY, PackageScope.PACKAGE_ONLY, NAMES),
        ).toEqual(["Terminal"]);
    });

    it("unions the dependencies and names each of them once", () => {
        // Both packages depend on the same String; it is one dependency, not two.
        expect(
            dependencyNamesOf(TOPOLOGY, PackageScope.WITH_SUB_PACKAGES, NAMES),
        ).toEqual(["Float", "String"]);
    });

    it("leaves out a dependency the selection declares anyway", () => {
        const declaringIt = {
            ...TOPOLOGY,
            children: [{ ...CORE, ownClasses: [1, 2], ownDependencies: [] }],
        };

        expect(
            dependencyNamesOf(
                declaringIt,
                PackageScope.WITH_SUB_PACKAGES,
                NAMES,
            ),
        ).toEqual(["Float"]);
    });

    it("keeps two classes of one name apart", () => {
        const both = { ...CORE, ownClasses: [2, 3], ownDependencies: [] };

        expect(classNamesOf(both, PackageScope.PACKAGE_ONLY, NAMES)).toEqual([
            "String",
            "String",
        ]);
    });

    it("reads a package the pool says nothing about as holding nothing", () => {
        expect(
            classNamesOf({ children: [] }, PackageScope.WITH_SUB_PACKAGES, []),
        ).toEqual([]);
    });
});

describe("sizeOf", () => {
    it("adds what a row declares and what its closure brings", () => {
        expect(sizeOf(node("topology"))).toBe(33);
        expect(sizeOf(node("iec"), PackageScope.PACKAGE_ONLY)).toBe(9);
    });
});
