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
    import { getStraightPath, useEdges, useInternalNode } from "@xyflow/svelte";

    import {
        getInnerBendPoints,
        getSourceEndPoint,
        getTargetEndPoint,
    } from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";
    import { userSettings } from "$lib/userSettings.svelte.js";

    import EdgeBendPoints from "./EdgeBendPoints.svelte";
    import {
        getEdgeParams,
        getPolylinePath,
        getRoundedCornerPolylinePath,
    } from "./edgeUtils.ts";

    let {
        id,
        source,
        target,
        data,
        selected,
        allowSelfConnecting = false,
        children,
    } = $props();

    const edges = useEdges();

    let sourceNode = useInternalNode(source);
    let targetNode = useInternalNode(target);

    let allPoints = $derived(data?.bendPoints ?? []);
    let bendPoints = $derived(getInnerBendPoints(allPoints));
    let sourceEndPoint = $derived(getSourceEndPoint(allPoints));
    let targetEndPoint = $derived(getTargetEndPoint(allPoints));

    let useRoundedCorners = $derived(
        userSettings.get("useRoundedEdges", false),
    );
    let cornerRoundingFactor = $derived(
        userSettings.get("cornerRoundingFactor", 50),
    );

    let edgeParams = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getEdgeParams(
                sourceNode.current,
                targetNode.current,
                0,
                bendPoints,
                {
                    source: sourceEndPoint,
                    target: targetEndPoint,
                },
            );
        }
    });

    let path = $derived(getPath());

    function getPath() {
        if (!edgeParams) return "";
        if (allowSelfConnecting && target === source && sourceNode.current) {
            return getSelfConnectingPath();
        }
        if (bendPoints.length > 0) {
            return getBendPointPath();
        }
        return getStraightEdgePath();
    }

    function getBendPointPath() {
        const orderedPoints = [
            { x: edgeParams.sx, y: edgeParams.sy },
            ...bendPoints,
            { x: edgeParams.tx, y: edgeParams.ty },
        ];
        return useRoundedCorners
            ? getRoundedCornerPolylinePath(orderedPoints, cornerRoundingFactor)
            : getPolylinePath(orderedPoints);
    }

    function getStraightEdgePath() {
        return getStraightPath({
            sourceX: edgeParams.sx,
            sourceY: edgeParams.sy,
            targetX: edgeParams.tx,
            targetY: edgeParams.ty,
        })[0];
    }

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

    function patchEdgeData(dataPatch) {
        edges.update(current =>
            current.map(edge =>
                edge.id === id
                    ? { ...edge, data: { ...edge.data, ...dataPatch } }
                    : edge,
            ),
        );
    }

    function handleBendPointsChange(nextPoints) {
        patchEdgeData({ bendPoints: nextPoints });
    }
</script>

{@render children(path, edgeParams)}

{#if selected && target !== source}
    <path
        d={path}
        fill="none"
        class="pointer-events-none"
        style="stroke: var(--color-blue); stroke-width: 1.5px; stroke-dasharray: 6 4; opacity: 0.9;"
    />
{/if}

{#if selected && edgeParams && target !== source}
    <EdgeBendPoints
        edgeId={id}
        sourcePoint={{ x: edgeParams.sx, y: edgeParams.sy }}
        targetPoint={{ x: edgeParams.tx, y: edgeParams.ty }}
        sourceBorderPoint={{ x: edgeParams.borderSx, y: edgeParams.borderSy }}
        targetBorderPoint={{ x: edgeParams.borderTx, y: edgeParams.borderTy }}
        {allPoints}
        {bendPoints}
        {sourceEndPoint}
        {targetEndPoint}
        sourceNodeId={source}
        targetNodeId={target}
        onPointsChange={handleBendPointsChange}
    />
{/if}
