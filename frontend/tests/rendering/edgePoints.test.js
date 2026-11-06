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

import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
import {
    getBendPoints,
    getEdgePoints,
} from "$lib/rendering/svelteflow/interaction/edgePointOperations.js";
import { EdgePointController } from "$lib/rendering/svelteflow/interaction/edgePoints.svelte.js";

function classNode(id, x, y, width = 100) {
    return {
        id,
        type: "class",
        position: { x, y },
        measured: { width, height: 60 },
    };
}

function edge(id, source, target, edgePoints = []) {
    return { id, source, target, data: { edgePoints } };
}

function loopWithOneBendPoint() {
    return edge("loop", "a", "a", [
        { id: "s", x: 25, y: 0, isDockingPoint: true, side: "source" },
        { id: "b", x: 50, y: -50 },
        { id: "t", x: 75, y: 0, isDockingPoint: true, side: "target" },
    ]);
}

function controllerFor(edges, nodes = []) {
    const state = { edges };
    const nodesById = new Map(nodes.map(node => [node.id, node]));
    const controller = new EdgePointController({
        getEdges: () => state.edges,
        setEdges: value => (state.edges = value),
        getSvelteFlow: () => ({
            getInternalNode: id => {
                const node = nodesById.get(id);
                return (
                    node && {
                        internals: { positionAbsolute: node.position },
                        measured: node.measured,
                    }
                );
            },
        }),
        isReadOnly: () => false,
        selectEdge: () => {},
        contextMenus: {},
    });
    return { state, controller };
}

function pointsOf(state, edgeId) {
    return getEdgePoints(state.edges.find(e => e.id === edgeId));
}

describe("EdgePointController with self-associations", () => {
    test("gives a self-association without points its default shape", () => {
        const { state, controller } = controllerFor([
            edge("loop", "a", "a"),
            edge("ab", "a", "b"),
        ]);

        controller.addDefaultSelfLoops([
            classNode("a", 100, 200, 80),
            classNode("b", 400, 200),
        ]);

        const height = EDGE_INTERACTION_CONFIG.selfLoopHeightPx;
        expect(pointsOf(state, "loop").map(({ x, y }) => ({ x, y }))).toEqual([
            { x: 120, y: 200 },
            { x: 120, y: 200 - height },
            { x: 160, y: 200 - height },
            { x: 160, y: 200 },
        ]);
        expect(pointsOf(state, "ab")).toEqual([]);
    });

    test("keeps the stored points of a self-association and waits for its class to be measured", () => {
        const stored = loopWithOneBendPoint();
        const { state, controller } = controllerFor([
            stored,
            edge("other", "c", "c"),
        ]);
        const edgesBefore = state.edges;

        controller.addDefaultSelfLoops([
            classNode("a", 0, 0),
            { id: "c", type: "class", position: { x: 0, y: 0 } },
        ]);

        expect(state.edges).toBe(edgesBefore);
    });

    test("does not delete the last bend point or the last docking point of a self-association", () => {
        const { state, controller } = controllerFor([loopWithOneBendPoint()]);

        controller.handleEdgeDeleteEdgePoint({ edgeId: "loop", pointId: "b" });
        expect(getBendPoints(pointsOf(state, "loop"))).toHaveLength(1);

        controller.handleEdgeDeleteEdgePoint({ edgeId: "loop", pointId: "s" });
        expect(pointsOf(state, "loop").map(point => point.id)).toEqual([
            "b",
            "t",
        ]);

        controller.handleEdgeDeleteEdgePoint({ edgeId: "loop", pointId: "t" });
        expect(pointsOf(state, "loop").map(point => point.id)).toEqual([
            "b",
            "t",
        ]);
    });

    test("gives a self-association without docking points its default shape", () => {
        const { state, controller } = controllerFor([
            edge("loop", "a", "a", [{ id: "b", x: 50, y: -50 }]),
        ]);

        controller.addDefaultSelfLoops([classNode("a", 0, 0)]);

        expect(pointsOf(state, "loop")).toHaveLength(4);
    });

    test("does not remove all bend points, docking points or edge points of a self-association", () => {
        const { state, controller } = controllerFor([loopWithOneBendPoint()]);

        controller.handleEdgeRemoveBendPoints({ edgeId: "loop" });
        controller.handleEdgeRemoveDockingPoints({ edgeId: "loop" });
        controller.handleEdgeRemoveAllEdgePoints({ edgeId: "loop" });

        expect(pointsOf(state, "loop")).toHaveLength(3);
    });

    test("resets a self-association to its default shape", () => {
        const { state, controller } = controllerFor(
            [loopWithOneBendPoint()],
            [classNode("a", 0, 0)],
        );

        controller.handleEdgeResetSelfLoop({ edgeId: "loop" });

        const points = pointsOf(state, "loop");
        expect(points).toHaveLength(4);
        expect(getBendPoints(points)).toHaveLength(2);
    });
});
