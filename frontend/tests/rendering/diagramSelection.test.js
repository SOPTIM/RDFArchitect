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

import { DiagramSelectionController } from "$lib/rendering/svelteflow/interaction/diagramSelection.svelte.js";
import { multiSelectState } from "$lib/sharedState.svelte.js";

function classNode(id) {
    return { id, type: "class", data: { label: id } };
}

function edge(id, source, target, selected = false) {
    return { id, source, target, selected };
}

function controllerFor(nodes, edges) {
    const state = { nodes, edges };
    const selection = new DiagramSelectionController({
        getNodes: () => state.nodes,
        setNodes: value => (state.nodes = value),
        getEdges: () => state.edges,
        setEdges: value => (state.edges = value),
        pan: { boxToggle: false, boxAdditive: false },
        contextMenus: { close: () => {} },
        nodeOrder: {},
    });
    return { state, selection };
}

function selectClasses(selection, ...ids) {
    selection.handleSelectionChange(
        { nodes: ids.map(classNode), edges: [] },
        false,
    );
}

function selectedEdgeIds(state) {
    return state.edges.filter(e => e.selected).map(e => e.id);
}

vi.mock("$lib/api/generated/index.ts", () => ({}));

beforeEach(() => {
    multiSelectState.clear();
});

describe("DiagramSelectionController", () => {
    test("selects the edge between two selected classes", () => {
        const { state, selection } = controllerFor(
            [classNode("a"), classNode("b")],
            [edge("ab", "a", "b")],
        );

        selectClasses(selection, "a", "b");

        expect(selectedEdgeIds(state)).toEqual(["ab"]);
    });

    test("deselects that edge again once one of its classes leaves the selection", () => {
        const { state, selection } = controllerFor(
            [classNode("a"), classNode("b")],
            [edge("ab", "a", "b")],
        );
        selectClasses(selection, "a", "b");

        multiSelectState.setSelection([selection.buildEntry(classNode("a"))]);
        selection.reflectSelectionToNodes();

        expect(selectedEdgeIds(state)).toEqual([]);
    });

    test("deselects that edge again once the class selection is cleared", () => {
        const { state, selection } = controllerFor(
            [classNode("a"), classNode("b")],
            [edge("ab", "a", "b")],
        );
        selectClasses(selection, "a", "b");

        multiSelectState.clear();
        selection.reflectSelectionToNodes();

        expect(selectedEdgeIds(state)).toEqual([]);
    });

    test("keeps a clicked edge selected when the class selection changes", () => {
        const { state, selection } = controllerFor(
            [classNode("a"), classNode("b"), classNode("c")],
            [edge("ab", "a", "b", true)],
        );

        selectClasses(selection, "c");

        expect(selectedEdgeIds(state)).toEqual(["ab"]);
    });
});
