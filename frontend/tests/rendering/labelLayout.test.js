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
    updateDatasetLabelPositions,
    updateLabelPositions,
} from "$lib/api/generated/index.ts";
import { LABEL_NODE_TYPE } from "$lib/rendering/svelteflow/diagram/labelNodes.js";
import { LabelLayoutController } from "$lib/rendering/svelteflow/interaction/labelLayout.js";

const CLASS_SIZE = { width: 100, height: 50 };

const GRAPH_TARGET = {
    datasetName: "ds",
    graphURI: "http://example.com/graph",
    diagramUUID: "diagram",
};

function classNode(id, x, y) {
    return { id, type: "class", position: { x, y }, measured: CLASS_SIZE };
}

function associationEdge(id, source, target, position = null) {
    return {
        id,
        type: "association",
        source,
        target,
        data: {
            sourceMultiplicityLabel: {
                identifiedObjectUUID: `${id}-end`,
                text: "1..1",
                position,
            },
        },
    };
}

function controllerFor(nodes, edges, target = GRAPH_TARGET) {
    const state = { nodes, edges };
    const labelLayout = new LabelLayoutController({
        getNodes: () => state.nodes,
        setNodes: value => (state.nodes = value),
        getEdges: () => state.edges,
        getTarget: () => target,
    });
    labelLayout.sync(state.nodes, state.edges);
    return { state, labelLayout };
}

function labelNodesOf(nodes) {
    return nodes.filter(node => node.type === LABEL_NODE_TYPE);
}

function dropped(labelNode, dx, dy) {
    return {
        ...labelNode,
        position: {
            x: labelNode.data.anchorPoint.x + dx,
            y: labelNode.data.anchorPoint.y + dy,
        },
    };
}

vi.mock("$lib/api/generated/index.ts", () => ({
    updateDatasetLabelPositions: vi.fn(),
    updateLabelPositions: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
});

describe("LabelLayoutController", () => {
    test("builds a label node for every label of the edges", () => {
        const { state } = controllerFor(
            [classNode("a", 0, 0), classNode("b", 300, 0)],
            [associationEdge("e", "a", "b")],
        );

        expect(labelNodesOf(state.nodes).map(node => node.id)).toEqual([
            "e-end:multiplicity",
        ]);
    });

    test("saves a dropped label over the graph route and keeps it there", () => {
        const { state, labelLayout } = controllerFor(
            [classNode("a", 0, 0), classNode("b", 300, 0)],
            [associationEdge("e", "a", "b")],
        );
        const moved = dropped(labelNodesOf(state.nodes)[0], 5, 10);

        labelLayout.handleMove([moved]);
        labelLayout.sync(state.nodes, state.edges);

        expect(updateLabelPositions).toHaveBeenCalledWith({
            path: GRAPH_TARGET,
            body: [
                {
                    identifiedObjectUUID: "e-end",
                    kind: "multiplicity",
                    x: moved.position.x,
                    y: moved.position.y,
                },
            ],
        });
        expect(labelNodesOf(state.nodes)[0].position).toEqual(moved.position);
    });

    test("saves a dropped label of a dataset diagram over the dataset route", () => {
        const { state, labelLayout } = controllerFor(
            [classNode("a", 0, 0), classNode("b", 300, 0)],
            [associationEdge("e", "a", "b")],
            { datasetName: "ds", graphURI: null, diagramUUID: "diagram" },
        );

        labelLayout.handleMove([dropped(labelNodesOf(state.nodes)[0], 5, 10)]);

        expect(updateLabelPositions).not.toHaveBeenCalled();
        expect(updateDatasetLabelPositions).toHaveBeenCalledWith(
            expect.objectContaining({
                path: { datasetName: "ds", diagramUUID: "diagram" },
            }),
        );
    });

    test("resets only the manually placed labels", () => {
        const { labelLayout } = controllerFor(
            [classNode("a", 0, 0), classNode("b", 300, 0)],
            [
                associationEdge("placed", "a", "b", { x: 50, y: 50 }),
                associationEdge("default", "b", "a"),
            ],
        );

        expect(labelLayout.resetPositions()).toEqual([
            {
                identifiedObjectUUID: "placed-end",
                kind: "multiplicity",
                x: null,
                y: null,
            },
        ]);
        expect(labelLayout.resetPositions()).toEqual([]);
    });

    test("collects the manually placed labels of the given edges only", () => {
        const placedEdge = associationEdge("placed", "a", "b", {
            x: 50,
            y: 50,
        });
        const otherEdge = associationEdge("other", "b", "a", { x: 60, y: 60 });
        const { labelLayout } = controllerFor(
            [classNode("a", 0, 0), classNode("b", 300, 0)],
            [placedEdge, otherEdge],
        );

        expect(
            labelLayout
                .manuallyPlacedLabelsOfEdges([placedEdge])
                .map(label => label.identifiedObjectUUID),
        ).toEqual(["placed-end"]);
    });
});
