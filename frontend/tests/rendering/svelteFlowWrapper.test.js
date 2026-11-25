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

import { flushSync, mount, tick, unmount } from "svelte";
import { fromStore, writable } from "svelte/store";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { layoutDiagram } from "$lib/rendering/svelteflow/layout/elkLayout.js";
import SvelteFlowWrapper from "$lib/rendering/svelteflow/svelteFlowWrapper.svelte";
import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

const flow = vi.hoisted(() => ({
    svelteFlow: {
        fitView: vi.fn(async () => true),
        fitBounds: vi.fn(async () => true),
        getInternalNode: vi.fn(() => undefined),
        getViewport: vi.fn(() => ({ x: 0, y: 0, zoom: 1 })),
        setViewport: vi.fn(),
    },
    nodesInitialized: null,
}));

function classNode(id, x, y) {
    return {
        id,
        type: "class",
        position: { x, y },
        data: { label: id, attributes: [], enumEntries: [] },
    };
}

// SvelteFlow cannot measure nodes in jsdom, so it is replaced and the tests mark the nodes as
// measured themselves.
vi.mock("@xyflow/svelte", async importOriginal => {
    const { fromStore, writable } = await import("svelte/store");
    flow.nodesInitialized = writable(false);
    const nodesInitialized = fromStore(flow.nodesInitialized);
    return {
        ...(await importOriginal()),
        SvelteFlow: () => {},
        Background: () => {},
        useSvelteFlow: () => flow.svelteFlow,
        useNodes: () => ({ current: [] }),
        useEdges: () => ({ current: [] }),
        useNodesInitialized: () => nodesInitialized,
    };
});

vi.mock("$lib/rendering/svelteflow/layout/elkLayout.js", () => ({
    layoutDiagram: vi.fn(),
}));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: {
        isReadOnly: vi.fn(async () => false),
        getNamespaces: vi.fn(async () => []),
    },
}));

describe("svelteFlowWrapper", () => {
    let component;
    let diagramNodes;

    beforeEach(() => {
        vi.clearAllMocks();
        flow.nodesInitialized.set(false);
    });

    afterEach(() => {
        unmount(component);
        document.body.innerHTML = "";
    });

    async function mountDiagram(nodes) {
        diagramNodes = writable(nodes);
        const currentNodes = fromStore(diagramNodes);
        component = mount(SvelteFlowWrapper, {
            target: document.body,
            props: {
                get nodes() {
                    return currentNodes.current;
                },
                edges: [],
            },
        });
        flushSync();
        await measureNodes();
    }

    async function measureNodes() {
        flow.nodesInitialized.set(false);
        flushSync();
        flow.nodesInitialized.set(true);
        flushSync();
        await tick();
    }

    async function reloadDiagram(nodes) {
        forceReloadTrigger.trigger();
        flushSync();
        diagramNodes.set(nodes);
        flushSync();
        await measureNodes();
    }

    async function triggerReload() {
        forceReloadTrigger.trigger();
        flushSync();
        await tick();
    }

    function fitCount() {
        return (
            flow.svelteFlow.fitBounds.mock.calls.length +
            flow.svelteFlow.fitView.mock.calls.length
        );
    }

    test("mount_diagramWithLayout_fitsViewOnce", async () => {
        await mountDiagram([classNode("a", 10, 20), classNode("b", 300, 40)]);

        expect(fitCount()).toBe(1);
    });

    test("reload_sameDiagramWithNewNodes_keepsView", async () => {
        await mountDiagram([classNode("a", 10, 20), classNode("b", 300, 40)]);

        await reloadDiagram([classNode("a", 10, 20), classNode("b", 320, 40)]);
        await triggerReload();

        expect(fitCount()).toBe(1);
    });

    test("reload_afterAutoLayout_keepsView", async () => {
        layoutDiagram.mockImplementation(async classNodes => ({
            nodes: classNodes.map((node, index) => ({
                ...node,
                position: { x: index * 300, y: 40 },
            })),
            layoutedEdges: [],
        }));
        await mountDiagram([classNode("a", 0, 0), classNode("b", 0, 0)]);
        await vi.waitFor(() => expect(layoutDiagram).toHaveBeenCalled());
        await new Promise(resolve => setTimeout(resolve));
        const fitsAfterLayout = fitCount();

        await triggerReload();

        expect(fitsAfterLayout).toBeGreaterThan(0);
        expect(fitCount()).toBe(fitsAfterLayout);
    });
});
