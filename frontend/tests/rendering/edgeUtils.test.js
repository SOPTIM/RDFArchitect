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
    distanceToPolyline,
    distanceToSegment,
    getClosestSegmentInsertionIndex,
    getEdgeParams,
    getInactiveBendPoints,
    getPolylinePath,
    getRoundedCornerPolylinePath,
    projectPointOntoNodeBorder,
} from "$lib/rendering/svelteflow/components/edge/edgeUtils.ts";

const CORNER = [
    { x: 0, y: 0 },
    { x: 100, y: 0 },
    { x: 100, y: 100 },
];

function node(x, y, width, height) {
    return {
        internals: { positionAbsolute: { x, y } },
        measured: { width, height },
    };
}

describe("paths", () => {
    test("a polyline connects the points directly", () => {
        expect(getPolylinePath(CORNER)).toBe("M 0 0 L 100 0 L 100 100");
        expect(getPolylinePath([{ x: 0, y: 0 }])).toBe("");
    });

    test("rounded corners start and end on the segments around the corner", () => {
        const path = getRoundedCornerPolylinePath(CORNER, 50);
        const numbers = path.match(/-?\d+(\.\d+)?/g).map(Number);
        const expected = [0, 0, 85, 0, 100, 0, 100, 15, 100, 100];

        expect(
            path
                .replace(/[\d.,-]/g, "")
                .replace(/\s+/g, " ")
                .trim(),
        ).toBe("M L Q L");
        expect(numbers).toHaveLength(expected.length);
        numbers.forEach((number, index) =>
            expect(number).toBeCloseTo(expected[index]),
        );
    });

    test("the rounding is clamped to 0..100 percent", () => {
        expect(getRoundedCornerPolylinePath(CORNER, 0)).toBe(
            "M 0 0 L 100 0 Q 100 0, 100 0 L 100 100",
        );
        expect(getRoundedCornerPolylinePath(CORNER, 500)).toBe(
            getRoundedCornerPolylinePath(CORNER, 100),
        );
    });

    test("the rounding never reaches over half of a short segment", () => {
        const shortCorner = [
            { x: 0, y: 0 },
            { x: 10, y: 0 },
            { x: 10, y: 10 },
        ];

        expect(getRoundedCornerPolylinePath(shortCorner, 100)).toBe(
            "M 0 0 L 5 0 Q 10 0, 10 5 L 10 10",
        );
    });

    test("a straight edge stays straight", () => {
        expect(
            getRoundedCornerPolylinePath([
                { x: 0, y: 0 },
                { x: 9, y: 9 },
            ]),
        ).toBe("M 0 0 L 9 9");
    });
});

describe("segments", () => {
    test("every segment gets an inactive bend point in its middle", () => {
        expect(getInactiveBendPoints(CORNER)).toEqual([
            { x: 50, y: 0, insertionIndex: 0 },
            { x: 100, y: 50, insertionIndex: 1 },
        ]);
    });

    test("a new point is inserted on the segment closest to the cursor", () => {
        expect(getClosestSegmentInsertionIndex(CORNER, { x: 40, y: 5 })).toBe(
            0,
        );
        expect(getClosestSegmentInsertionIndex(CORNER, { x: 95, y: 60 })).toBe(
            1,
        );
    });

    test("distances are measured to the closest point of a segment", () => {
        const start = { x: 0, y: 0 };
        const end = { x: 100, y: 0 };

        expect(distanceToSegment({ x: 50, y: 10 }, start, end)).toBe(10);
        expect(distanceToSegment({ x: 103, y: 4 }, start, end)).toBe(5);
        expect(distanceToSegment({ x: 3, y: 4 }, start, start)).toBe(5);
        expect(distanceToPolyline({ x: 90, y: 50 }, CORNER)).toBe(10);
    });
});

describe("class borders", () => {
    const classNode = node(100, 100, 200, 100);

    test("a point is projected onto the closest side of the class", () => {
        expect(projectPointOntoNodeBorder(classNode, { x: 0, y: 150 })).toEqual(
            { x: 100, y: 150 },
        );
        expect(
            projectPointOntoNodeBorder(classNode, { x: 200, y: 105 }),
        ).toEqual({ x: 200, y: 100 });
        expect(
            projectPointOntoNodeBorder(classNode, { x: 250, y: 500 }),
        ).toEqual({ x: 250, y: 200 });
    });

    test("a straight edge docks on the borders facing the other class", () => {
        const params = getEdgeParams(
            node(0, 0, 100, 50),
            node(300, 0, 100, 50),
        );

        expect([params.sx, params.sy]).toEqual([100, 25]);
        expect([params.tx, params.ty]).toEqual([300, 25]);
    });

    test("an edge with bend points docks on the border facing its first and last bend point", () => {
        const params = getEdgeParams(
            node(0, 0, 100, 50),
            node(300, 0, 100, 50),
            0,
            [
                { x: 50, y: 200 },
                { x: 350, y: 200 },
            ],
        );

        expect([params.sx, params.sy]).toEqual([50, 50]);
        expect([params.tx, params.ty]).toEqual([350, 50]);
    });

    test("active end points replace the docking, the border point stays available", () => {
        const params = getEdgeParams(
            node(0, 0, 100, 50),
            node(300, 0, 100, 50),
            0,
            [],
            { source: { x: 100, y: 10 }, target: null },
        );

        expect([params.sx, params.sy]).toEqual([100, 10]);
        expect([params.borderSx, params.borderSy]).toEqual([100, 25]);
        expect([params.tx, params.ty]).toEqual([300, 25]);
    });
});
