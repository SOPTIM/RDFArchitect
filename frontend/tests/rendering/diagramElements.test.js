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

import {
    decorateEdges,
    EDGE_Z_INDEX,
    ELEVATED_EDGE_Z_INDEX,
    elevateEdges,
    onlySelectedEdgeId,
} from "$lib/rendering/svelteflow/diagram/diagramElements.js";
import { toEdgeLayoutDTO } from "$lib/rendering/svelteflow/interaction/edgeLayoutPersistence.js";
import {
    getSourceDockingPoint,
    getTargetDockingPoint,
} from "$lib/rendering/svelteflow/interaction/edgePointOperations.js";

function renderedPoint(id, x, y, side = null) {
    return { id, x, y, side };
}

function renderedEdge(edgePoints) {
    return {
        id: "edge",
        type: "association",
        source: "source-class",
        target: "target-class",
        data: { sourceObject: "a", targetObject: "b", edgePoints },
    };
}

vi.mock("$lib/api/generated/index.ts", () => ({}));

describe("decorateEdges", () => {
    test("marks the rendered docking points with their side", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("s", 0, 0, "source"),
                renderedPoint("m", 50, 50),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(edge.data.edgePoints).toEqual([
            { id: "s", x: 0, y: 0, isDockingPoint: true, side: "source" },
            { id: "m", x: 50, y: 50 },
            { id: "t", x: 100, y: 100, isDockingPoint: true, side: "target" },
        ]);
    });

    test("recognizes a lone docking point at the target", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("m", 50, 50),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(getSourceDockingPoint(edge.data.edgePoints)).toBeNull();
        expect(getTargetDockingPoint(edge.data.edgePoints).id).toBe("t");
    });

    test("sends the rendered points back as they were rendered", () => {
        const [edge] = decorateEdges([
            renderedEdge([
                renderedPoint("s", 0, 0, "source"),
                renderedPoint("t", 100, 100, "target"),
            ]),
        ]);

        expect(toEdgeLayoutDTO(edge).points).toEqual([
            { id: "s", x: 0, y: 0, side: "source" },
            { id: "t", x: 100, y: 100, side: "target" },
        ]);
    });
});

describe("elevateEdges", () => {
    test("raises the elevated edge and lowers the others", () => {
        const edges = elevateEdges(
            [
                { id: "a", selected: true, zIndex: EDGE_Z_INDEX },
                { id: "b", selected: false, zIndex: ELEVATED_EDGE_Z_INDEX },
            ],
            edge => edge.selected,
        );

        expect(edges.map(edge => edge.zIndex)).toEqual([
            ELEVATED_EDGE_Z_INDEX,
            EDGE_Z_INDEX,
        ]);
    });

    test("returns the same array when nothing changes", () => {
        const edges = [{ id: "a", selected: false, zIndex: EDGE_Z_INDEX }];

        expect(elevateEdges(edges, edge => edge.selected)).toBe(edges);
    });
});

describe("onlySelectedEdgeId", () => {
    test("returns the id of the only selected edge", () => {
        expect(
            onlySelectedEdgeId([
                { id: "a", selected: false },
                { id: "b", selected: true },
            ]),
        ).toBe("b");
    });

    test("returns null when several edges are selected", () => {
        expect(
            onlySelectedEdgeId([
                { id: "a", selected: true },
                { id: "b", selected: true },
            ]),
        ).toBeNull();
    });
});
