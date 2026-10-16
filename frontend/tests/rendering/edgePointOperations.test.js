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
    applyLayoutedEdges,
    createBendPoint,
    createDockingPoint,
    dissolveCollinearBendPoints,
    findEdgePointAtPosition,
    getBendPoints,
    getEdgePoints,
    getSourceDockingPoint,
    getTargetDockingPoint,
    insertBendPointAt,
    isDockingPoint,
    moveEdgePointsWithDraggedClasses,
    removeBendPoints,
    removeDockingPoints,
    removeEdgePoint,
    toEdgePoints,
    updateDockingPoints,
} from "$lib/rendering/svelteflow/interaction/edgePointOperations.js";

function bend(id, x, y) {
    return { id, x, y };
}

function docking(id, x, y, side) {
    return { id, x, y, isDockingPoint: true, side };
}

function edge(id, source, target, edgePoints) {
    return { id, source, target, data: { edgePoints } };
}

describe("docking points", () => {
    test("are recognized by their flag", () => {
        expect(isDockingPoint(docking("e", 0, 0, "source"))).toBe(true);
        expect(isDockingPoint(bend("b", 0, 0))).toBe(false);
        expect(isDockingPoint(undefined)).toBe(false);
    });

    test("are found at their side", () => {
        const source = docking("s", 0, 0, "source");
        const target = docking("t", 9, 9, "target");
        const points = [source, bend("b", 5, 5), target];

        expect(getSourceDockingPoint(points)).toBe(source);
        expect(getTargetDockingPoint(points)).toBe(target);
        expect(getBendPoints(points).map(point => point.id)).toEqual(["b"]);
    });

    test("a lone docking point belongs to its own side only", () => {
        const lonelyTarget = [docking("t", 9, 9, "target")];
        const lonelySource = [docking("s", 0, 0, "source")];

        expect(getSourceDockingPoint(lonelyTarget)).toBeNull();
        expect(getTargetDockingPoint(lonelyTarget).id).toBe("t");
        expect(getSourceDockingPoint(lonelySource).id).toBe("s");
        expect(getTargetDockingPoint(lonelySource)).toBeNull();
    });

    test("without a side, the first and the last point are taken", () => {
        const first = { id: "f", x: 0, y: 0, isDockingPoint: true };
        const last = { id: "l", x: 9, y: 9, isDockingPoint: true };

        expect(getSourceDockingPoint([first, bend("b", 5, 5), last])).toBe(
            first,
        );
        expect(getTargetDockingPoint([first, bend("b", 5, 5), last])).toBe(
            last,
        );
        expect(getTargetDockingPoint([first])).toBeNull();
    });

    test("an edge without edge points has none", () => {
        expect(getSourceDockingPoint([])).toBeNull();
        expect(getTargetDockingPoint([])).toBeNull();
        expect(getEdgePoints({ data: {} })).toEqual([]);
        expect(getEdgePoints(undefined)).toEqual([]);
    });
});

describe("editing points", () => {
    test("a bend point is inserted between the bend points, behind a source docking point", () => {
        const points = [
            docking("s", 0, 0, "source"),
            bend("a", 1, 1),
            bend("b", 2, 2),
        ];

        expect(
            insertBendPointAt(points, 1, bend("new", 0, 0)).map(
                point => point.id,
            ),
        ).toEqual(["s", "a", "new", "b"]);
        expect(
            insertBendPointAt([bend("a", 1, 1)], 0, bend("new", 0, 0)).map(
                point => point.id,
            ),
        ).toEqual(["new", "a"]);
        expect(points).toHaveLength(3);
    });

    test("an edge point is removed by its id", () => {
        const points = [bend("a", 1, 1), bend("b", 2, 2)];

        expect(removeEdgePoint(points, "a").map(point => point.id)).toEqual([
            "b",
        ]);
        expect(removeEdgePoint(points, "unknown")).toEqual(points);
    });

    test("bend points and docking points are removed separately", () => {
        const points = [
            docking("s", 0, 0, "source"),
            bend("b", 5, 5),
            docking("t", 9, 9, "target"),
        ];

        expect(removeBendPoints(points).map(point => point.id)).toEqual([
            "s",
            "t",
        ]);
        expect(removeDockingPoints(points).map(point => point.id)).toEqual([
            "b",
        ]);
    });

    test("created points get unique ids, docking points their side", () => {
        const first = createBendPoint(1, 2);
        const second = createBendPoint(1, 2);
        const dockingPoint = createDockingPoint(3, 4, "target");

        expect(first.id).not.toBe(second.id);
        expect(first).toEqual({ id: first.id, x: 1, y: 2 });
        expect(dockingPoint).toEqual({
            id: dockingPoint.id,
            x: 3,
            y: 4,
            isDockingPoint: true,
            side: "target",
        });
    });

    test("the point under the cursor is found within the hit radius", () => {
        const points = [bend("a", 0, 0), bend("b", 100, 0)];

        expect(findEdgePointAtPosition(points, { x: 98, y: 3 }, 5).id).toBe(
            "b",
        );
        expect(findEdgePointAtPosition(points, { x: 50, y: 0 }, 5)).toBeNull();
    });

    test("routing points of the layout become sided docking points and bend points", () => {
        const points = toEdgePoints([
            { x: 0, y: 0, role: "source" },
            { x: 5, y: 5, role: "bend" },
            { x: 9, y: 9, role: "target" },
        ]);

        expect(points.map(point => point.side)).toEqual([
            "source",
            undefined,
            "target",
        ]);
        expect(points.map(isDockingPoint)).toEqual([true, false, true]);
        expect(points[1]).toMatchObject({ x: 5, y: 5 });
    });
});

describe("dissolveCollinearBendPoints", () => {
    test("removes bend points on the line between their neighbours", () => {
        const points = [
            bend("a", 0, 0),
            bend("b", 50, 2),
            bend("c", 100, 0),
            bend("d", 100, 100),
        ];

        expect(
            dissolveCollinearBendPoints(points, 5).map(point => point.id),
        ).toEqual(["a", "c", "d"]);
    });

    test("removes several collinear points in a row", () => {
        const points = [
            bend("a", 0, 0),
            bend("b", 25, 0),
            bend("c", 50, 1),
            bend("d", 100, 0),
        ];

        expect(
            dissolveCollinearBendPoints(points, 5).map(point => point.id),
        ).toEqual(["a", "d"]);
    });

    test("never removes docking points", () => {
        const points = [
            docking("s", 0, 0, "source"),
            docking("t", 50, 0, "target"),
            bend("c", 100, 0),
        ];

        expect(
            dissolveCollinearBendPoints(points, 5).map(point => point.id),
        ).toEqual(["s", "t", "c"]);
    });

    test("returns the same array if nothing is removed", () => {
        const corner = [
            bend("a", 0, 0),
            bend("b", 100, 0),
            bend("c", 100, 100),
        ];
        const short = [bend("a", 0, 0), bend("b", 1, 1)];

        expect(dissolveCollinearBendPoints(corner, 5)).toBe(corner);
        expect(dissolveCollinearBendPoints(short, 5)).toBe(short);
    });
});

describe("edges following their classes", () => {
    test("updateDockingPoints passes each docking point with its class and keeps unchanged edges", () => {
        const edges = [
            edge("e", "a", "b", [
                docking("s", 0, 0, "source"),
                bend("m", 5, 5),
            ]),
        ];

        const moved = updateDockingPoints(edges, (point, classId) =>
            classId === "a" ? { ...point, x: 1 } : point,
        );

        expect(moved[0].data.edgePoints.map(point => point.x)).toEqual([1, 5]);
        expect(updateDockingPoints(edges, point => point)).toBe(edges);
    });

    test("a dragged class moves its docking points, both classes dragged move the bend points too", () => {
        const edges = [
            edge("e", "a", "b", [
                docking("s", 0, 0, "source"),
                bend("m", 50, 50),
                docking("t", 100, 0, "target"),
            ]),
        ];
        const lastPositions = new Map([
            ["a", { x: 0, y: 0 }],
            ["b", { x: 0, y: 0 }],
        ]);
        const classA = { id: "a", position: { x: 10, y: 5 } };
        const classB = { id: "b", position: { x: 10, y: 5 } };

        const oneDragged = moveEdgePointsWithDraggedClasses(
            edges,
            [classA],
            lastPositions,
        );
        const bothDragged = moveEdgePointsWithDraggedClasses(
            edges,
            [classA, classB],
            lastPositions,
        );

        expect(oneDragged[0].data.edgePoints.map(point => point.x)).toEqual([
            10, 50, 100,
        ]);
        expect(bothDragged[0].data.edgePoints.map(point => point.x)).toEqual([
            10, 60, 110,
        ]);
    });

    test("applyLayoutedEdges takes over the routing ELK computed", () => {
        const edges = [edge("e", "a", "b", []), edge("other", "a", "b", [])];
        const layouted = new Map([
            [
                "e",
                [
                    { x: 0, y: 0, role: "source" },
                    { x: 50, y: 80, role: "bend" },
                    { x: 100, y: 0, role: "target" },
                ],
            ],
        ]);

        const result = applyLayoutedEdges(edges, layouted, 8);

        expect(result[0].data.edgePoints).toHaveLength(3);
        expect(result[1]).toBe(edges[1]);
    });
});
