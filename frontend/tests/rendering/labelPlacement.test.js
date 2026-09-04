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

import { getEdgeParams } from "$lib/rendering/svelteflow/components/edgeUtils.ts";
import {
    buildLabelNodes,
    labelNodesChanged,
    LABEL_NODE_TYPE,
} from "$lib/rendering/svelteflow/diagram/labelNodes.js";

/** A class node as it arrives before a diagram has been laid out: every class sits at (0,0). */
function classNode(
    id,
    size = { width: 180, height: 88 },
    position = { x: 0, y: 0 },
) {
    return { id, type: "class", position, measured: size };
}

function associationEdge(source, target) {
    return {
        id: `${source}->${target}`,
        type: "association",
        source,
        target,
        data: {
            labels: [
                {
                    anchor: "SOURCE",
                    identifiedObjectUUID: `${source}-end`,
                    kind: "multiplicity",
                    text: "0..1",
                    offset: null,
                },
                {
                    anchor: "TARGET",
                    identifiedObjectUUID: `${target}-end`,
                    kind: "multiplicity",
                    text: "1..n",
                    offset: null,
                },
            ],
        },
    };
}

describe("edge geometry", () => {
    test("stays finite for two equally sized classes sharing a position", () => {
        const params = getEdgeParams(classNode("a"), classNode("b"));

        for (const [key, value] of Object.entries(params)) {
            expect(`${key}=${value}`).toBe(`${key}=${Number(value)}`);
            expect(Number.isFinite(value), key).toBe(true);
        }
    });

    test("puts both ends on the shared centre when there is no intersection", () => {
        const params = getEdgeParams(classNode("a"), classNode("b"));

        expect([params.sx, params.sy]).toEqual([90, 44]);
        expect([params.tx, params.ty]).toEqual([90, 44]);
    });

    test("still intersects the border of classes that do have a distance", () => {
        const params = getEdgeParams(
            classNode("a"),
            classNode("b", undefined, { x: 400, y: 0 }),
        );

        expect(params.sx).toBeGreaterThan(params.tx - 400);
        expect(Number.isFinite(params.startX)).toBe(true);
    });
});

describe("label nodes", () => {
    const nodes = [classNode("a"), classNode("b")];
    const edges = [associationEdge("a", "b")];

    test("are placed at a real position even before the diagram is laid out", () => {
        for (const label of buildLabelNodes(nodes, edges)) {
            expect(Number.isFinite(label.position.x), label.id).toBe(true);
            expect(Number.isFinite(label.position.y), label.id).toBe(true);
        }
    });

    test("a rebuild of an unchanged diagram reports no change", () => {
        const built = buildLabelNodes(nodes, edges);
        const withLabels = [...nodes, ...built];

        expect(labelNodesChanged(withLabels, built)).toBe(false);
        expect(
            labelNodesChanged(withLabels, buildLabelNodes(withLabels, edges)),
        ).toBe(false);
    });

    test("a label that cannot be placed does not report a change forever", () => {
        const notANumber = buildLabelNodes(nodes, edges).map(label => ({
            ...label,
            position: { x: NaN, y: NaN },
        }));

        expect(labelNodesChanged([...nodes, ...notANumber], notANumber)).toBe(
            false,
        );
    });

    test("a moved class does report a change", () => {
        const built = buildLabelNodes(nodes, edges);
        const moved = [
            classNode("a"),
            classNode("b", undefined, { x: 400, y: 120 }),
            ...built,
        ];

        expect(labelNodesChanged(moved, buildLabelNodes(moved, edges))).toBe(
            true,
        );
    });

    test("only label nodes are compared", () => {
        const built = buildLabelNodes(nodes, edges);
        expect(built.every(node => node.type === LABEL_NODE_TYPE)).toBe(true);
        expect(labelNodesChanged([...nodes, ...built], built)).toBe(false);
    });
});
