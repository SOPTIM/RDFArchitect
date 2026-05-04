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
    import {
        BaseEdge,
        EdgeLabel,
        getStraightPath,
        useInternalNode,
    } from "@xyflow/svelte";

    import { getEdgeParams } from "./edgeUtils.ts";

    let { id, source, target, data } = $props();

    const style = "stroke-width: 2px; stroke: #000";
    let markerEnd = data.useToAssociation ? "url(#associationTo)" : "";
    let markerStart = data.useFromAssociation ? "url(#associationFrom)" : "";
    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);

    let edgeParams = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getEdgeParams(sourceNode.current, targetNode.current);
        }
    });

    let path = $derived.by(() => {
        if (!edgeParams) return "";

        if (target === source && sourceNode.current) {
            const pos = sourceNode.current.internals.positionAbsolute ?? {
                x: 0,
                y: 0,
            };
            const w = sourceNode.current.measured.width ?? 100;

            const x1 = pos.x + w * 0.25;
            const y1 = pos.y;
            const x2 = pos.x + w * 0.75;
            const y2 = pos.y;

            const loopHeight = 70;
            return `M ${x1} ${y1} C ${x1} ${y1 - loopHeight}, ${x2} ${y2 - loopHeight}, ${x2} ${y2}`;
        }

        return getStraightPath({
            sourceX: edgeParams.sx,
            sourceY: edgeParams.sy,
            targetX: edgeParams.tx,
            targetY: edgeParams.ty,
        })[0];
    });
</script>

<BaseEdge {id} {path} {markerStart} {markerEnd} {style} />
<EdgeLabel>
    {#if data.fromMultiplicity}
        {#if target === source && sourceNode.current}
            {@const pos = sourceNode.current.internals.positionAbsolute ?? {
                x: 0,
                y: 0,
            }}
            {@const w = sourceNode.current.measured.width ?? 100}
            <div
                style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.25 - 12}px, ${pos.y - 30}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-[#303030] shadow-sm"
            >
                {data.fromMultiplicity}
            </div>
        {:else}
            <div
                style:transform={`translate(-50%, -50%) translate(${edgeParams.sx + edgeParams.startX}px, ${edgeParams.sy + edgeParams.startY}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-[#303030] shadow-sm"
            >
                {data.fromMultiplicity}
            </div>
        {/if}
    {/if}
    {#if data.toMultiplicity}
        {#if target === source && targetNode.current}
            {@const pos = targetNode.current.internals.positionAbsolute ?? {
                x: 0,
                y: 0,
            }}
            {@const w = targetNode.current.measured.width ?? 100}
            <div
                style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.75 + 12}px, ${pos.y - 30}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-[#303030] shadow-sm"
            >
                {data.toMultiplicity}
            </div>
        {:else}
            <div
                style:transform={`translate(-50%, -50%) translate(${edgeParams.tx + edgeParams.endX}px, ${edgeParams.ty + edgeParams.endY}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-[#303030] shadow-sm"
            >
                {data.toMultiplicity}
            </div>
        {/if}
    {/if}
</EdgeLabel>
