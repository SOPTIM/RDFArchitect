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
    countByKind,
    descriptionOf,
    filterableKinds,
    labelOf,
} from "../../src/routes/changelog/changeKinds.js";

const changed = (...kinds) => ({ affectedKinds: kinds });

describe("labelOf", () => {
    test("names a kind the way the editor names it", () => {
        // The editor calls a graph a schema everywhere else, so the changelog does too.
        expect(labelOf("rdf")).toBe("Schema");
        expect(labelOf("dl")).toBe("Layout");
        expect(labelOf("graphs")).toBe("Schema list");
        expect(labelOf("shacl")).toBe("Constraints (SHACL)");
        expect(labelOf("prefixes")).toBe("Namespaces");
    });

    test("shows a kind it does not know under its own name", () => {
        expect(labelOf("something-new")).toBe("something-new");
    });
});

describe("descriptionOf", () => {
    test("says what a change of that kind is", () => {
        expect(descriptionOf("dl")).toContain("diagram");
    });

    test("says nothing about a kind it does not know", () => {
        expect(descriptionOf("something-new")).toBe("");
    });
});

describe("filterableKinds", () => {
    test("offers every known kind, whether or not the changes have any", () => {
        // The filter must not rearrange itself as the user moves between graphs.
        const offered = filterableKinds([changed("rdf")]);

        expect(offered).toContain("rdf");
        expect(offered).toContain("shacl");
        expect(offered).toContain("colors");
    });

    test("offers the same kinds for an empty changelog", () => {
        expect(filterableKinds([])).toEqual(filterableKinds([changed("rdf")]));
        expect(filterableKinds(undefined)).toEqual(filterableKinds([]));
    });

    test("adds a kind it does not know, after the ones it does", () => {
        const offered = filterableKinds([changed("something-new")]);

        expect(offered.at(-1)).toBe("something-new");
        expect(offered.indexOf("rdf")).toBeLessThan(
            offered.indexOf("something-new"),
        );
    });
});

describe("countByKind", () => {
    test("counts a change under every kind it touched", () => {
        const counts = countByKind([changed("rdf", "dl"), changed("dl")]);

        expect(counts.get("rdf")).toBe(1);
        expect(counts.get("dl")).toBe(2);
    });

    test("counts a kind a change names twice only once", () => {
        expect(countByKind([changed("rdf", "rdf")]).get("rdf")).toBe(1);
    });

    test("knows nothing of a kind no change touched", () => {
        expect(countByKind([changed("rdf")]).get("shacl")).toBeUndefined();
    });
});
