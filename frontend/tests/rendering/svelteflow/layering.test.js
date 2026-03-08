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

import {
    assignDeterministicNodeZIndices,
    bringNodeToFront,
    initializeNextFrontZIndex,
} from "$lib/rendering/svelteflow/layering.ts";

describe("svelteflow layering", () => {
    test("assignDeterministicNodeZIndices is deterministic", () => {
        const nodes = [
            { id: "class-c", position: { x: 0, y: 50 } },
            { id: "class-a", position: { x: 50, y: 0 } },
            { id: "class-b", position: { x: 20, y: 0 } },
        ];

        const first = assignDeterministicNodeZIndices(nodes);
        const second = assignDeterministicNodeZIndices(nodes);

        expect(first).toEqual(second);
    });

    test("assignDeterministicNodeZIndices breaks ties by id", () => {
        const nodes = [
            { id: "c", position: { x: 10, y: 10 } },
            { id: "a", position: { x: 10, y: 10 } },
            { id: "b", position: { x: 10, y: 10 } },
        ];

        const layeredNodes = assignDeterministicNodeZIndices(nodes);
        const zById = Object.fromEntries(
            layeredNodes.map(node => [node.id, node.zIndex]),
        );

        expect(zById.a).toBeLessThan(zById.b);
        expect(zById.b).toBeLessThan(zById.c);
    });

    test("bringNodeToFront promotes only the requested node", () => {
        const nodes = [
            { id: "a", zIndex: 1 },
            { id: "b", zIndex: 2 },
            { id: "c", zIndex: 3 },
        ];

        const result = bringNodeToFront(nodes, "b", 4);

        expect(result.changed).toBe(true);
        expect(result.nextFrontZIndex).toBe(5);

        const zById = Object.fromEntries(
            result.nodes.map(node => [node.id, node.zIndex]),
        );

        expect(zById.a).toBe(1);
        expect(zById.b).toBe(4);
        expect(zById.c).toBe(3);
    });

    test("bringNodeToFront is a no-op for unknown ids", () => {
        const nodes = [
            { id: "a", zIndex: 1 },
            { id: "b", zIndex: 2 },
        ];

        const result = bringNodeToFront(nodes, "missing", 3);

        expect(result.changed).toBe(false);
        expect(result.nodes).toBe(nodes);
        expect(result.nextFrontZIndex).toBe(3);
    });

    test("initializeNextFrontZIndex starts above current max", () => {
        const nodes = [
            { id: "a", zIndex: 7 },
            { id: "b", zIndex: 3 },
            { id: "c", zIndex: 12 },
        ];

        const nextFrontZIndex = initializeNextFrontZIndex(nodes);

        expect(nextFrontZIndex).toBe(13);
    });
});
