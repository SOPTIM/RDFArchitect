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
import { afterEach, describe, expect, test, vi } from "vitest";

import GraphSection from "../../src/routes/mainpage/packageNavigation/GraphSection.svelte";

/** A schema entry as build-nav-object hands it over. */
const GRAPH_NAV_ENTRY = {
    id: "http://example.org/graphs/Equipment",
    label: "Core Equipment Vocabulary",
    tooltip: [
        "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
        "The core equipment profile.",
    ].join("\n"),
    children: [],
    isOpen: false,
    data: { keyword: "EQ" },
};

/**
 * A prefix that covers the graph's own namespace, so that a schema entry which
 * went back to labelling itself by the shortened IRI would be caught below.
 */
const NAMESPACES = [
    { prefix: "http://example.org/graphs/", substitutedPrefix: "graphs:" },
];

let mounted = null;
let target = null;

function render(graphNavEntry = GRAPH_NAV_ENTRY) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(GraphSection, {
        target,
        context: new Map([
            ["packageNavigation", { reloadTrigger: { subscribe: () => {} } }],
        ]),
        props: {
            workspaceNavEntry: { id: "cgmes", label: "cgmes" },
            graphNavEntry,
            namespaces: NAMESPACES,
        },
    });
    return target.querySelector("button.nav-entry");
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/stores/ontologyStore.ts", () => ({
    ontologyStore: { getOntologyForGraph: vi.fn().mockResolvedValue(null) },
}));

vi.mock("$lib/stores/versionControlStore.ts", () => ({
    versionControlStore: {
        refresh: vi.fn().mockResolvedValue(undefined),
        canUndo: vi.fn().mockResolvedValue(false),
        canRedo: vi.fn().mockResolvedValue(false),
    },
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("GraphSection", () => {
    test("shows the profile's name and its keyword badge", () => {
        const entry = render();

        expect(entry.textContent.replace(/\s+/g, " ").trim()).toBe(
            "Core Equipment Vocabulary EQ",
        );
    });

    test("hovering the schema shows everything the profile says about itself", () => {
        const entry = render();

        expect(entry.getAttribute("title")).toBe(GRAPH_NAV_ENTRY.tooltip);
    });

    test("falls back to the bare name for a graph that is no profile", () => {
        const entry = render({
            ...GRAPH_NAV_ENTRY,
            label: "Notes",
            tooltip: "http://example.org/graphs/Notes",
            data: { keyword: "" },
        });

        expect(entry.textContent.replace(/\s+/g, " ").trim()).toBe("Notes");
    });
});
