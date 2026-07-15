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
        useEdges,
        useInternalNode,
    } from "@xyflow/svelte";

    import { renderOptions } from "$lib/renderOptions.svelte.js";

    import { labelsOf } from "../diagram/labelNodes.js";
    import { labelHighlight } from "../interaction/labelHighlight.svelte.js";
    import EdgeBendPoints from "./EdgeBendPoints.svelte";
    import {
        getEdgeParams,
        getPolylinePath,
        getRoundedCornerPolylinePath,
    } from "./edgeUtils.ts";
    let { id, source, target, data, selected } = $props();

    /**
     * The highlight rises quickly and decays slowly, which is what makes a short press read as a
     * pulse rather than as a state the edge sits in. The transition of the state being entered is
     * the one that runs, so these are not interchangeable.
     */
    const HIGHLIGHT_IN_TRANSITION =
        "transition: stroke 120ms ease-out, stroke-width 120ms ease-out;";
    const HIGHLIGHT_OUT_TRANSITION =
        "transition: stroke 450ms ease-out, stroke-width 450ms ease-out;";

    /** The widths an edge swells between while one of its labels is pressed. */
    const BASE_STROKE_WIDTH = "2px";
    const HIGHLIGHT_STROKE_WIDTH = "3.2px";

    const edges = useEdges();

    let markerEnd = data.useToAssociation ? "url(#associationTo)" : "";
    let markerStart = data.useFromAssociation ? "url(#associationFrom)" : "";
    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);
    let bendPoints = $derived(data.bendPoints ?? []);
    let useRoundedCorners = $derived(
        userSettings.get("useRoundedEdges", false),
    );
    let cornerRoundingFactor = $derived(
        userSettings.get("cornerRoundingFactor", 50),
    );

    let held = $derived(labelHighlight.isHeld(labelsOf(data)));

    //TODO HIER MAXS EDGE SELECTION ANSCHWELLEN
    /*
    *     let style = $derived.by(() => {
        const stroke =
            renderOptions.get("useColoredPropertiesInMergedView") && data.color
                ? data.color
                : "#000";
        return held
            ? `${HIGHLIGHT_IN_TRANSITION} stroke-width: ${HIGHLIGHT_STROKE_WIDTH};` +
                  " stroke: var(--color-class-node-highlighted);"
            : `${HIGHLIGHT_OUT_TRANSITION} stroke-width: ${BASE_STROKE_WIDTH}; stroke: ${stroke};`;
    });*/

    let style = $derived(
        renderOptions.get("useColoredPropertiesInMergedView") && data.color
            ? `stroke-width: 2px; stroke: ${data.color};`
            : "stroke-width: 2px; stroke: #000;",
    );

    let edgeParams = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getEdgeParams(
                sourceNode.current,
                targetNode.current,
                0,
                bendPoints,
            );
        }
    });

    let path = $derived.by(() => {
        if (!edgeParams) return "";
        if (target === source && sourceNode.current)
            return getSelfConnectingPath();

        if (bendPoints.length > 0) {
            const orderedPoints = [
                { x: edgeParams.sx, y: edgeParams.sy },
                ...bendPoints,
                { x: edgeParams.tx, y: edgeParams.ty },
            ];
            return useRoundedCorners
                ? getRoundedCornerPolylinePath(
                      orderedPoints,
                      cornerRoundingFactor,
                  )
                : getPolylinePath(orderedPoints);
        }

        return getStraightPath({
            sourceX: edgeParams.sx,
            sourceY: edgeParams.sy,
            targetX: edgeParams.tx,
            targetY: edgeParams.ty,
        })[0];
    });

    function getSelfConnectingPath() {
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

    function handleBendPointsChange(nextPoints) {
        edges.update(current =>
            current.map(edge =>
                edge.id === id
                    ? {
                          ...edge,
                          data: { ...edge.data, bendPoints: nextPoints },
                      }
                    : edge,
            ),
        );
    }
</script>

<BaseEdge {id} {path} {markerStart} {markerEnd} {style} />

{#if selected && edgeParams && target !== source}
    <EdgeBendPoints
        edgeId={id}
        sourcePoint={{ x: edgeParams.sx, y: edgeParams.sy }}
        targetPoint={{ x: edgeParams.tx, y: edgeParams.ty }}
        {bendPoints}
        onPointsChange={handleBendPointsChange}
    />
{/if}

<EdgeLabel>
    {#if data.toMultiplicity}
        {#if target === source && sourceNode.current}
            {@const pos = sourceNode.current.internals.positionAbsolute ?? {
                x: 0,
                y: 0,
            }}
            {@const w = sourceNode.current.measured.width ?? 100}
            <div
                style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.25 - 12}px, ${pos.y - 30}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-default-text shadow-sm"
            >
                {data.toMultiplicity}
            </div>
        {:else}
            <div
                style:transform={`translate(-50%, -50%) translate(${edgeParams.sx + edgeParams.startX}px, ${edgeParams.sy + edgeParams.startY}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-default-text shadow-sm"
            >
                {data.toMultiplicity}
            </div>
        {/if}
    {/if}
    {#if data.fromMultiplicity}
        {#if target === source && targetNode.current}
            {@const pos = targetNode.current.internals.positionAbsolute ?? {
                x: 0,
                y: 0,
            }}
            {@const w = targetNode.current.measured.width ?? 100}
            <div
                style:transform={`translate(-50%, -50%) translate(${pos.x + w * 0.75 + 12}px, ${pos.y - 30}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-default-text shadow-sm"
            >
                {data.fromMultiplicity}
            </div>
        {:else}
            <div
                style:transform={`translate(-50%, -50%) translate(${edgeParams.tx + edgeParams.endX}px, ${edgeParams.ty + edgeParams.endY}px)`}
                class="nodrag nopan pointer-events-auto absolute z-50 cursor-pointer rounded bg-white/80 px-2 py-0.5 text-xs font-medium text-default-text shadow-sm"
            >
                {data.fromMultiplicity}
            </div>
        {/if}
    {/if}
</EdgeLabel>
