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
    createBendPoint,
    createEndPoint,
    dissolveCollinearBendPoints,
    findBendPointAtPosition,
    getBendPoints,
    getInnerBendPoints,
    getSourceEndPoint,
    getTargetEndPoint,
    insertBendPointAt,
    isEndPoint,
    removeBendPoint,
    toEdgePoints,
} from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";

function bend(id, x, y) {
    return { id, x, y };
}

function end(id, x, y, side) {
    return { id, x, y, isEndPoint: true, side };
}

describe("end points", () => {
    test("are recognized by their flag", () => {
        expect(isEndPoint(end("e", 0, 0, "source"))).toBe(true);
        expect(isEndPoint({ id: "e", endPoint: true })).toBe(true);
        expect(isEndPoint(bend("b", 0, 0))).toBe(false);
        expect(isEndPoint(undefined)).toBe(false);
    });

    test("are found at their side", () => {
        const source = end("s", 0, 0, "source");
        const target = end("t", 9, 9, "target");
        const points = [source, bend("b", 5, 5), target];

        expect(getSourceEndPoint(points)).toBe(source);
        expect(getTargetEndPoint(points)).toBe(target);
        expect(getInnerBendPoints(points).map(point => point.id)).toEqual([
            "b",
        ]);
    });

    test("a lone end point belongs to its own side only", () => {
        const lonelyTarget = [end("t", 9, 9, "target")];
        const lonelySource = [end("s", 0, 0, "source")];

        expect(getSourceEndPoint(lonelyTarget)).toBeNull();
        expect(getTargetEndPoint(lonelyTarget).id).toBe("t");
        expect(getSourceEndPoint(lonelySource).id).toBe("s");
        expect(getTargetEndPoint(lonelySource)).toBeNull();
    });

    test("without a side, the first and the last point are taken", () => {
        const first = { id: "f", x: 0, y: 0, isEndPoint: true };
        const last = { id: "l", x: 9, y: 9, isEndPoint: true };

        expect(getSourceEndPoint([first, bend("b", 5, 5), last])).toBe(first);
        expect(getTargetEndPoint([first, bend("b", 5, 5), last])).toBe(last);
        expect(getTargetEndPoint([first])).toBeNull();
    });

    test("an edge without points has none", () => {
        expect(getSourceEndPoint([])).toBeNull();
        expect(getTargetEndPoint([])).toBeNull();
        expect(getBendPoints({ data: {} })).toEqual([]);
        expect(getBendPoints(undefined)).toEqual([]);
    });
});

describe("editing points", () => {
    test("a bend point is inserted between the inner points, behind a source end point", () => {
        const points = [
            end("s", 0, 0, "source"),
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

    test("a point is removed by its id", () => {
        const points = [bend("a", 1, 1), bend("b", 2, 2)];

        expect(removeBendPoint(points, "a").map(point => point.id)).toEqual([
            "b",
        ]);
        expect(removeBendPoint(points, "unknown")).toEqual(points);
    });

    test("created points get unique ids, end points their side", () => {
        const first = createBendPoint(1, 2);
        const second = createBendPoint(1, 2);
        const endPoint = createEndPoint(3, 4, "target");

        expect(first.id).not.toBe(second.id);
        expect(first).toEqual({ id: first.id, x: 1, y: 2 });
        expect(endPoint).toEqual({
            id: endPoint.id,
            x: 3,
            y: 4,
            isEndPoint: true,
            side: "target",
        });
    });

    test("the point under the cursor is found within the hit radius", () => {
        const points = [bend("a", 0, 0), bend("b", 100, 0)];

        expect(findBendPointAtPosition(points, { x: 98, y: 3 }, 5).id).toBe(
            "b",
        );
        expect(findBendPointAtPosition(points, { x: 50, y: 0 }, 5)).toBeNull();
    });

    test("routing points of the layout become sided end points and bend points", () => {
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
        expect(points.map(isEndPoint)).toEqual([true, false, true]);
        expect(points[1]).toMatchObject({ x: 5, y: 5 });
    });
});

describe("dissolveCollinearBendPoints", () => {
    test("removes inner points on the line between their neighbours", () => {
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

    test("never removes end points", () => {
        const points = [
            end("s", 0, 0, "source"),
            end("t", 50, 0, "target"),
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
