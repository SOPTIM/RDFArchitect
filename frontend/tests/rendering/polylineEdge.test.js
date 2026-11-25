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

import { createRawSnippet, flushSync, mount, unmount } from "svelte";
import { afterEach, describe, expect, test, vi } from "vitest";

import PolylineEdge from "$lib/rendering/svelteflow/components/edge/PolylineEdge.svelte";

const flow = vi.hoisted(() => ({
    updateEdgeData: vi.fn(),
}));

function internalNode(id, x, y) {
    return {
        id,
        internals: { positionAbsolute: { x, y } },
        measured: { width: 100, height: 50 },
    };
}

// The hooks return what @xyflow/svelte 2 returns: useEdges() has no update or set any more.
vi.mock("@xyflow/svelte", async importOriginal => ({
    ...(await importOriginal()),
    useEdges: () => ({
        current: [{ id: "edge", source: "a", target: "b", selected: true }],
    }),
    useInternalNode: getParams => ({
        get current() {
            const { id } = getParams();
            return id === "a"
                ? internalNode("a", 0, 0)
                : internalNode("b", 400, 0);
        },
    }),
    useSvelteFlow: () => ({
        updateEdgeData: flow.updateEdgeData,
        screenToFlowPosition: position => position,
        getInternalNode: vi.fn(),
        getViewport: () => ({ x: 0, y: 0, zoom: 1 }),
        setViewport: vi.fn(),
    }),
}));

describe("PolylineEdge", () => {
    let component;

    afterEach(() => {
        unmount(component);
        document.body.innerHTML = "";
        vi.clearAllMocks();
    });

    function mountSelectedEdge() {
        const svg = document.createElementNS(
            "http://www.w3.org/2000/svg",
            "svg",
        );
        document.body.appendChild(svg);
        component = mount(PolylineEdge, {
            target: svg,
            props: {
                id: "edge",
                source: "a",
                target: "b",
                data: { edgePoints: [] },
                selected: true,
                children: createRawSnippet(() => ({
                    render: () => "<g></g>",
                })),
            },
        });
        flushSync();
    }

    test("activateInactiveBendPoint_pointerDown_updatesEdgeDataWithNewBendPoint", () => {
        mountSelectedEdge();
        const inactiveBendPoint = document.querySelector(
            '[aria-label="Add bend point"]',
        );

        inactiveBendPoint.dispatchEvent(
            new PointerEvent("pointerdown", {
                bubbles: true,
                button: 0,
                clientX: 200,
                clientY: 25,
            }),
        );

        expect(flow.updateEdgeData).toHaveBeenCalledTimes(1);
        const [edgeId, dataPatch] = flow.updateEdgeData.mock.calls[0];
        expect(edgeId).toBe("edge");
        expect(dataPatch.edgePoints).toHaveLength(1);
        expect(dataPatch.edgePoints[0]).toMatchObject({ x: 200, y: 25 });
    });
});
