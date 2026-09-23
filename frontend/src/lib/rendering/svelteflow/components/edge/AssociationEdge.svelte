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
    import { BaseEdge, EdgeLabel, useInternalNode } from "@xyflow/svelte";

    import { renderOptions } from "$lib/renderOptions.svelte.js";

    import PolylineEdge from "./PolylineEdge.svelte";
    //import { labelsOf } from "../diagram/labelNodes.js";
    //import { labelHighlight } from "../interaction/labelHighlight.svelte.js";

    //TODO FRAGE: STYLE VON SELECTED EDGE??

    let { id, source, target, data, selected } = $props();

    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);

    ///**
    // * The highlight rises quickly and decays slowly, which is what makes a short press read as a
    // * pulse rather than as a state the edge sits in. The transition of the state being entered is
    // * the one that runs, so these are not interchangeable.
    // */
    //const HIGHLIGHT_IN_TRANSITION =
    //    "transition: stroke 120ms ease-out, stroke-width 120ms ease-out;";
    //const HIGHLIGHT_OUT_TRANSITION =
    //    "transition: stroke 450ms ease-out, stroke-width 450ms ease-out;";
    ///** The widths an edge swells between while one of its labels is pressed. */
    //const BASE_STROKE_WIDTH = "2px";
    //const HIGHLIGHT_STROKE_WIDTH = "3.2px";

    let markerEnd = $derived(
        data.useToAssociation ? "url(#associationTo)" : "",
    );
    let markerStart = $derived(
        data.useFromAssociation ? "url(#associationFrom)" : "",
    );

    //let held = $derived(labelHighlight.isHeld(labelsOf(data)));

    let style = $derived(
        renderOptions.get("useColoredPropertiesInMergedView") && data.color
            ? `stroke-width: 2px; stroke: ${data.color};`
            : "stroke-width: 2px; stroke: #000;",
    );
</script>

<PolylineEdge {id} {source} {target} {data} {selected} allowSelfConnecting>
    {#snippet children(path, edgeParams)}
        <BaseEdge {id} {path} {markerStart} {markerEnd} {style} />

        <EdgeLabel>
            {#if data.toMultiplicity}
                {#if target === source && sourceNode.current}
                    {@const pos = sourceNode.current.internals
                        .positionAbsolute ?? { x: 0, y: 0 }}
                    {@const w = sourceNode.current.measured.width ?? 100}
                    <div
                        style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.25 - 12}px, ${pos.y - 30}px)`}
                        class="nodrag nopan text-default-text pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium shadow-sm"
                    >
                        {data.toMultiplicity}
                    </div>
                {:else}
                    <div
                        style:transform={`translate(-50%, -50%) translate(${edgeParams.sx + edgeParams.startX}px, ${edgeParams.sy + edgeParams.startY}px)`}
                        class="nodrag nopan text-default-text pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium shadow-sm"
                    >
                        {data.toMultiplicity}
                    </div>
                {/if}
            {/if}
            {#if data.fromMultiplicity}
                {#if target === source && targetNode.current}
                    {@const pos = targetNode.current.internals
                        .positionAbsolute ?? { x: 0, y: 0 }}
                    {@const w = targetNode.current.measured.width ?? 100}
                    <div
                        style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.75 + 12}px, ${pos.y - 30}px)`}
                        class="nodrag nopan text-default-text pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium shadow-sm"
                    >
                        {data.fromMultiplicity}
                    </div>
                {:else}
                    <div
                        style:transform={`translate(-50%, -50%) translate(${edgeParams.tx + edgeParams.endX}px, ${edgeParams.ty + edgeParams.endY}px)`}
                        class="nodrag nopan text-default-text pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium shadow-sm"
                    >
                        {data.fromMultiplicity}
                    </div>
                {/if}
            {/if}
        </EdgeLabel>
    {/snippet}
</PolylineEdge>
