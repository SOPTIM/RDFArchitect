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

import { buildLabelNodes } from "$lib/rendering/svelteflow/diagram/labelNodes.js";

const CLASS_SIZE = { width: 100, height: 50 };

function classNode(id, x, y) {
    return {
        id,
        type: "class",
        position: { x, y },
        measured: CLASS_SIZE,
    };
}

function label(uuid, text, position = null) {
    return { identifiedObjectUUID: uuid, text, position };
}

function associationEdge(source, target, labelFields) {
    return {
        id: "edge",
        type: "association",
        source,
        target,
        data: labelFields,
    };
}

function nodeById(nodes, id) {
    return nodes.find(node => node.id === id);
}

describe("buildLabelNodes", () => {
    test.each([
        ["a horizontal edge", classNode("target", 300, 0)],
        ["a vertical edge", classNode("target", 0, 300)],
        ["a diagonal edge", classNode("target", 300, 300)],
    ])("stacks the two labels of an edge end of %s", (_, target) => {
        const nodes = [classNode("source", 0, 0), target];
        const edges = [
            associationEdge("source", "target", {
                sourceMultiplicityLabel: label("from", "1..1"),
                sourceAssociationLabel: label("from", "Child"),
                targetMultiplicityLabel: label("to", "0..n"),
                targetAssociationLabel: label("to", "Terminals"),
            }),
        ];

        const labelNodes = buildLabelNodes(nodes, edges);

        expect(labelNodes.map(node => node.id)).toEqual([
            "from:multiplicity",
            "from:associationLabel",
            "to:multiplicity",
            "to:associationLabel",
        ]);
        for (const uuid of ["from", "to"]) {
            const multiplicity = nodeById(labelNodes, `${uuid}:multiplicity`);
            const associationLabel = nodeById(
                labelNodes,
                `${uuid}:associationLabel`,
            );

            expect(associationLabel.position.x).toBe(multiplicity.position.x);
            expect(
                associationLabel.position.y - multiplicity.position.y,
            ).toBeGreaterThanOrEqual(20);
        }
    });

    test("stacks the labels of a self loop into its arc", () => {
        const nodes = [classNode("class", 0, 0)];
        const edges = [
            associationEdge("class", "class", {
                sourceMultiplicityLabel: label("from", "1..1"),
                sourceAssociationLabel: label("from", "Parent"),
            }),
        ];

        const labelNodes = buildLabelNodes(nodes, edges);

        const multiplicity = nodeById(labelNodes, "from:multiplicity");
        const associationLabel = nodeById(labelNodes, "from:associationLabel");
        expect(associationLabel.position.x).toBe(multiplicity.position.x);
        expect(multiplicity.position.y - associationLabel.position.y).toBe(24);
    });

    test("keeps a manually placed association label at its stored position", () => {
        const nodes = [
            classNode("source", 10, 20),
            classNode("target", 300, 0),
        ];
        const edges = [
            associationEdge("source", "target", {
                sourceAssociationLabel: label("from", "Child", {
                    x: 90,
                    y: -80,
                }),
            }),
        ];

        const labelNodes = buildLabelNodes(nodes, edges);

        expect(labelNodes).toHaveLength(1);
        expect(labelNodes[0].position).toEqual({ x: 90, y: -80 });
    });

    test("keeps a manually placed label following its class when the class moves", () => {
        const nodes = [
            classNode("source", 10, 20),
            classNode("target", 300, 0),
        ];
        const edges = [
            associationEdge("source", "target", {
                sourceAssociationLabel: label("from", "Child", {
                    x: 90,
                    y: -80,
                }),
            }),
        ];
        const cache = new Map();
        const overrides = new Map();

        const first = buildLabelNodes(nodes, edges, overrides, cache);
        const movedNodes = [classNode("source", 110, 20), nodes[1]];
        const second = buildLabelNodes(movedNodes, edges, overrides, cache);

        expect(second[0].position).toEqual({
            x: first[0].position.x + 100,
            y: first[0].position.y,
        });
    });
});
