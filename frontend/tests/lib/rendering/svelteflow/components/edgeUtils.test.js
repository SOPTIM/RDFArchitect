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
    getAssociationEdgeGeometry,
    getAssociationLabelTransform,
} from "$lib/rendering/svelteflow/components/edgeUtils.ts";

function createNode(x, y, width, height) {
    return {
        measured: {
            width,
            height,
        },
        internals: {
            positionAbsolute: {
                x,
                y,
            },
        },
    };
}

describe("edgeUtils", () => {
    test("aligns labels away from the node when the anchor is left of center", () => {
        const node = createNode(100, 100, 120, 60);

        expect(
            getAssociationLabelTransform(
                {
                    x: 90,
                    y: 130,
                },
                node,
            ),
        ).toBe("translate(-100%, -50%)");
    });

    test("aligns labels away from the node when the anchor is right and below", () => {
        const node = createNode(100, 100, 120, 60);

        expect(
            getAssociationLabelTransform(
                {
                    x: 250,
                    y: 180,
                },
                node,
            ),
        ).toBe("translate(0%, 0%)");
    });

    test("centers labels when the anchor stays near the node center line", () => {
        const node = createNode(100, 100, 120, 60);

        expect(
            getAssociationLabelTransform(
                {
                    x: 160,
                    y: 130,
                },
                node,
            ),
        ).toBe("translate(-50%, -50%)");
    });

    test("returns finite association geometry even before a node has been measured", () => {
        const source = createNode(100, 100, 0, 0);
        const target = createNode(300, 100, 120, 60);

        const geometry = getAssociationEdgeGeometry(source, target);

        expect(Number.isFinite(geometry.sx)).toBe(true);
        expect(Number.isFinite(geometry.sy)).toBe(true);
        expect(Number.isFinite(geometry.tx)).toBe(true);
        expect(Number.isFinite(geometry.ty)).toBe(true);
        expect(geometry.path).not.toContain("NaN");
    });
});
