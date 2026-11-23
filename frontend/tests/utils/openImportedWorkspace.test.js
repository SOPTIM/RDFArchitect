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

import {
    DiagramType,
    editorState,
    multiSelectState,
    SelectionLevel,
} from "$lib/sharedState.svelte.js";
import { versionControlStore } from "$lib/stores/versionControlStore.ts";
import { openImportedWorkspace } from "$lib/utils/importJob.svelte.js";
import { workspaceState } from "$lib/workspaceState.svelte.js";

const GRAPH = "http://graph#A";
const IMPORTED_GRAPH = "http://graph#B";
const PACKAGE = { type: DiagramType.PACKAGE, id: "package" };

function selectClasses(workspaceName, ...classUuids) {
    multiSelectState.selectedClasses.updateValue(
        classUuids.map(classUuid => ({
            workspaceName,
            graphUri: GRAPH,
            classUuid,
            classLabel: classUuid,
        })),
    );
}

vi.mock("$lib/stores/graphStore.ts", () => ({ graphStore: {} }));
vi.mock("$lib/stores/workspaceStore.ts", () => ({ workspaceStore: {} }));
vi.mock("$lib/stores/versionControlStore.ts", () => ({
    versionControlStore: { invalidateWorkspace: vi.fn() },
}));

describe("openImportedWorkspace", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        editorState.reset();
    });

    test("drops everything cached under the workspace's name", () => {
        openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(versionControlStore.invalidateWorkspace).toHaveBeenCalledWith(
            "imported",
        );
    });

    test("opens the first imported graph", () => {
        workspaceState.activate("current");

        openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(editorState.selectedWorkspace.getValue()).toBe("imported");
        expect(editorState.selectedGraph.getValue()).toBe(IMPORTED_GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual({
            type: null,
            id: null,
        });
    });

    test("keeps the selection of the workspace it leaves for a return", () => {
        workspaceState.activate("current");
        editorState.selectedGraph.updateValue(GRAPH);
        editorState.selectedDiagram.updateValue(PACKAGE);

        openImportedWorkspace("imported", [IMPORTED_GRAPH]);
        workspaceState.activate("current");

        expect(editorState.selectedGraph.getValue()).toBe(GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual(PACKAGE);
    });

    test("selects nothing of the workspace it leaves", () => {
        workspaceState.activate("current");
        selectClasses("current", "first", "second");
        editorState.focusedClassUUID.updateValue("first");
        editorState.activeSelectionKind.updateValue(SelectionLevel.CLASS);

        openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(multiSelectState.getSelected()).toEqual([]);
        expect(editorState.focusedClassUUID.getValue()).toBeNull();
        expect(editorState.activeSelectionKind.getValue()).not.toBe(
            SelectionLevel.CLASS,
        );
    });

    test("an import into the open workspace opens the imported graph there", () => {
        workspaceState.activate("current");
        editorState.selectedGraph.updateValue(GRAPH);
        editorState.selectedDiagram.updateValue(PACKAGE);

        openImportedWorkspace("current", [IMPORTED_GRAPH]);

        expect(editorState.selectedWorkspace.getValue()).toBe("current");
        expect(editorState.selectedGraph.getValue()).toBe(IMPORTED_GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual({
            type: null,
            id: null,
        });
    });
});
