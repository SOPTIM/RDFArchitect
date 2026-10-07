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

import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
import { editorState } from "$lib/sharedState.svelte.js";
import { graphStore } from "$lib/stores/graphStore.ts";

import EditMenuHarness from "./EditMenuHarness.svelte";

const WORKSPACE = "cgmes";
const GRAPH = "http://graph#EquipmentCore";

/** A CGMES 2.4.15 profile: no ontology object, its name is fixed on a version class. */
const LEGACY = {
    uri: { prefix: "http://graph#", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
    profileClassIri:
        "http://entsoe.eu/CIM/SchemaExtension/3/1#EquipmentVersion",
    profileClassUuid: "uuid-equipment-version",
};

let mounted = null;
let target = null;

function headerHintButton() {
    return [...document.querySelectorAll("button")].find(candidate =>
        candidate.textContent.trim().startsWith("Edit on"),
    );
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn(), renameGraph: vi.fn() },
}));
vi.mock("$lib/stores/ontologyStore.ts", () => ({
    ontologyStore: { getOntologyForGraph: vi.fn(async () => null) },
}));
vi.mock("$lib/stores/packageStore.ts", () => ({
    packageStore: { getPackages: vi.fn(async () => []) },
}));
vi.mock("$lib/stores/versionControlStore.ts", () => ({
    versionControlStore: { undo: vi.fn(), redo: vi.fn() },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        getNamespaces: vi.fn(async () => []),
        updateReadonly: vi.fn(),
    },
}));

beforeEach(() => {
    graphStore.getGraphs.mockResolvedValue([LEGACY]);
    editorState.reset();
    editorState.selectGraph(WORKSPACE, GRAPH);

    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(EditMenuHarness, {
        target,
        props: { canUndo: false, canRedo: false, isWorkspaceReadOnly: false },
    });
    flushSync();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    editorState.reset();
    vi.clearAllMocks();
});

describe("Edit menu", () => {
    test("the rename dialog's header link opens the class a legacy profile names itself on", async () => {
        shortcutStore.handleEvent(
            new KeyboardEvent("keydown", {
                key: "F6",
                code: "F6",
                shiftKey: true,
            }),
        );
        await vi.waitFor(() => expect(headerHintButton()).toBeDefined());

        headerHintButton().click();
        flushSync();

        expect(editorState.selectedClass.getValue()).toMatchObject({
            id: "uuid-equipment-version",
        });
        expect(editorState.selectedClassGraph.getValue()).toBe(GRAPH);
        expect(editorState.selectedClassWorkspace.getValue()).toBe(WORKSPACE);
    });
});
