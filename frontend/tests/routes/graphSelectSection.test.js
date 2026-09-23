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

import { mount, unmount } from "svelte";
import { afterEach, describe, expect, test } from "vitest";

import { graphLabeller } from "$lib/utils/graph-label.js";

import GraphSelectSection from "../../src/routes/mainpage/packageNavigation/custom-diagram-dialogs/GraphSelectSection.svelte";

/** Two CGMES 2.4.15 profiles that name themselves alike, as the backend lists them. */
const CORE = {
    uri: { prefix: "http://example.org/graphs/", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
};

const CORE_OPERATION = {
    uri: {
        prefix: "http://example.org/graphs/",
        suffix: "EquipmentCoreOperation",
    },
    keyword: "EQ",
    label: "EquipmentProfile",
};

let mounted = null;
let target = null;

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(GraphSelectSection, {
        target,
        props: { packages: [], classesByPackage: {}, ...props },
    });
    return target.querySelector(".nav-entry__label").textContent.trim();
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("GraphSelectSection", () => {
    test("names a schema by its profile when it stands on its own", () => {
        expect(render({ graph: { ...CORE } })).toBe("EquipmentProfile");
    });

    test("takes the name its workspace gives it, so lookalikes stay apart", () => {
        const nameOf = graphLabeller([CORE, CORE_OPERATION]);

        expect(render({ graph: { ...CORE }, label: nameOf(CORE) })).toBe(
            "EquipmentProfile (EquipmentCore)",
        );
    });
});
