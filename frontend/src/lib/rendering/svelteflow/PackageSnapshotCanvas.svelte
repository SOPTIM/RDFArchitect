<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import "@xyflow/svelte/dist/style.css";
    import {
        SvelteFlow,
        useNodesInitialized,
        useSvelteFlow,
    } from "@xyflow/svelte";
    import { tick } from "svelte";

    import AssociationEdge from "./components/AssociationEdge.svelte";
    import ClassNode from "./components/ClassNode.svelte";
    import EdgeMarkers from "./components/EdgeMarkers.svelte";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import {
        decorateEdges,
        hasDefaultNodeLayout,
    } from "./diagram/diagramElements.js";
    import { getLayoutedNodes } from "./layout/elkLayout.js";

    let {
        inputNodes,
        inputEdges,
        ready = $bindable(false),
        size = $bindable(null),
    } = $props();

    const nodeTypes = { class: ClassNode };
    const edgeTypes = {
        association: AssociationEdge,
        inheritance: InheritanceEdge,
    };
    const nodesInit = useNodesInitialized();
    const svelteFlow = useSvelteFlow();

    let nodes = $state.raw([...(inputNodes ?? [])]);
    let edges = $state.raw(decorateEdges(inputEdges ?? []));

    let laidOut = false;
    $effect(() => {
        if (nodesInit.current && nodes.length && !laidOut) {
            layoutOnce();
        }
    });

    async function layoutOnce() {
        laidOut = true;
        if (hasDefaultNodeLayout(nodes)) {
            const layouted = await getLayoutedNodes(nodes, edges);
            nodes = [...layouted];
        }

        const padding = 60;
        const xs = nodes.map(n => n.position.x);
        const ys = nodes.map(n => n.position.y);
        const rights = nodes.map(
            n => n.position.x + (n.measured?.width ?? n.width ?? 200),
        );
        const bottoms = nodes.map(
            n => n.position.y + (n.measured?.height ?? n.height ?? 100),
        );
        const minX = Math.min(...xs);
        const minY = Math.min(...ys);
        const width = Math.max(...rights) - minX + padding * 2;
        const height = Math.max(...bottoms) - minY + padding * 2;
        size = { width, height };

        await tick();
        await new Promise(r => requestAnimationFrame(r));
        await new Promise(r => requestAnimationFrame(r));

        svelteFlow.fitView({ padding: 0.05, duration: 0 });

        await tick();
        await new Promise(r => requestAnimationFrame(r));
        ready = true;
    }
</script>

<div style="width:{size?.width ?? 1600}px;height:{size?.height ?? 1000}px;">
    <SvelteFlow
        bind:nodes
        bind:edges
        {nodeTypes}
        {edgeTypes}
        fitView
        nodesDraggable={false}
        elementsSelectable={false}
        connectionMode={"loose"}
        minZoom={0.01}
        maxZoom={5}
    >
        <EdgeMarkers />
    </SvelteFlow>
</div>
