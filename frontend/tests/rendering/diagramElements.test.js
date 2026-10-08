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

import { describe, expect, test, vi } from "vitest";

import { decorateEdges } from "$lib/rendering/svelteflow/diagram/diagramElements.js";
import {
    getSourceEndPoint,
    getTargetEndPoint,
} from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";
import { toEdgeLayoutDTO } from "$lib/rendering/svelteflow/interaction/edgeLayoutPersistence.js";

function renderedPoint(id, x, y, side = null) {
    return { id, position: { x, y, z: 0 }, side };
}

function renderedEdge(bendPoints) {
    return {
        id: "edge",
        type: "association",
        source: "source-class",
        target: "target-class",
        data: { sourceObject: "a", targetObject: "b", bendPoints },
    };
}

vi.mock("$lib/api/generated/index.ts", () => ({}));

describe("decorateEdges", () => {
    test("flattens the rendered points and keeps the side of the end points", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("s", 0, 0, "source"),
                renderedPoint("m", 50, 50),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(edge.data.bendPoints).toEqual([
            { id: "s", x: 0, y: 0, isEndPoint: true, side: "source" },
            { id: "m", x: 50, y: 50 },
            { id: "t", x: 100, y: 100, isEndPoint: true, side: "target" },
        ]);
    });

    test("recognizes a lone end point at the target", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("m", 50, 50),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(getSourceEndPoint(edge.data.bendPoints)).toBeNull();
        expect(getTargetEndPoint(edge.data.bendPoints).id).toBe("t");
    });

    test("sends the rendered points back as they were rendered", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("s", 0, 0, "source"),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(toEdgeLayoutDTO(edge).points).toEqual([
            { id: "s", xPosition: 0, yPosition: 0, side: "source" },
            { id: "t", xPosition: 100, yPosition: 100, side: "target" },
        ]);
    });
});
