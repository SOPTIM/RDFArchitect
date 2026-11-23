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

import { guardUnsavedChanges } from "$lib/eventhandling/unsavedChanges.js";
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
            schemaName: GRAPH,
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

    test("drops everything cached under the workspace's name", async () => {
        await openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(versionControlStore.invalidateWorkspace).toHaveBeenCalledWith(
            "imported",
        );
    });

    test("opens the first imported graph", async () => {
        workspaceState.activate("current");

        await openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(editorState.selectedWorkspace.getValue()).toBe("imported");
        expect(editorState.selectedGraph.getValue()).toBe(IMPORTED_GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual({
            type: null,
            id: null,
        });
    });

    test("keeps the selection of the workspace it leaves for a return", async () => {
        workspaceState.activate("current");
        editorState.selectedGraph.updateValue(GRAPH);
        editorState.selectedDiagram.updateValue(PACKAGE);

        await openImportedWorkspace("imported", [IMPORTED_GRAPH]);
        workspaceState.activate("current");

        expect(editorState.selectedGraph.getValue()).toBe(GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual(PACKAGE);
    });

    test("selects nothing of the workspace it leaves", async () => {
        workspaceState.activate("current");
        selectClasses("current", "first", "second");
        editorState.focusedClassUUID.updateValue("first");
        editorState.activeSelectionKind.updateValue(SelectionLevel.CLASS);

        await openImportedWorkspace("imported", [IMPORTED_GRAPH]);

        expect(multiSelectState.getSelected()).toEqual([]);
        expect(editorState.focusedClassUUID.getValue()).toBeNull();
        expect(editorState.activeSelectionKind.getValue()).not.toBe(
            SelectionLevel.CLASS,
        );
    });

    test("an import into the open workspace opens the imported graph there", async () => {
        workspaceState.activate("current");
        editorState.selectedGraph.updateValue(GRAPH);
        editorState.selectedDiagram.updateValue(PACKAGE);

        await openImportedWorkspace("current", [IMPORTED_GRAPH]);

        expect(editorState.selectedWorkspace.getValue()).toBe("current");
        expect(editorState.selectedGraph.getValue()).toBe(IMPORTED_GRAPH);
        expect(editorState.selectedDiagram.getValue()).toEqual({
            type: null,
            id: null,
        });
    });

    test("asks about unsaved work before leaving the open workspace", async () => {
        workspaceState.activate("current");
        const ask = vi.fn().mockResolvedValue(false);
        const release = guardUnsavedChanges(ask);
        try {
            expect(
                await openImportedWorkspace("imported", [IMPORTED_GRAPH]),
            ).toBe(false);

            expect(ask).toHaveBeenCalled();
            expect(editorState.selectedWorkspace.getValue()).toBe("current");
            expect(
                versionControlStore.invalidateWorkspace,
            ).toHaveBeenCalledWith("imported");
        } finally {
            release();
        }
    });

    test("leaves once the unsaved work is settled", async () => {
        workspaceState.activate("current");
        const release = guardUnsavedChanges(vi.fn().mockResolvedValue(true));
        try {
            expect(
                await openImportedWorkspace("imported", [IMPORTED_GRAPH]),
            ).toBe(true);

            expect(editorState.selectedWorkspace.getValue()).toBe("imported");
        } finally {
            release();
        }
    });

    test("asks nothing for an import into the open workspace", async () => {
        workspaceState.activate("current");
        const ask = vi.fn().mockResolvedValue(false);
        const release = guardUnsavedChanges(ask);
        try {
            await openImportedWorkspace("current", [IMPORTED_GRAPH]);

            expect(ask).not.toHaveBeenCalled();
            expect(editorState.selectedGraph.getValue()).toBe(IMPORTED_GRAPH);
        } finally {
            release();
        }
    });
});
