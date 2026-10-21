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

    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
    import {
        getBendPoints,
        getSourceDockingPoint,
        getTargetDockingPoint,
        dissolveCollinearBendPoints,
    } from "$lib/rendering/svelteflow/interaction/edgePointOperations.js";
    import { labelHighlight } from "$lib/rendering/svelteflow/interaction/labelHighlight.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";

    import EdgePointHandles from "./EdgePointHandles.svelte";
    import {
        getEdgeParams,
        getPolylinePath,
        getRoundedCornerPolylinePath,
    } from "./edgeUtils.ts";
    import { labelsOf } from "../../diagram/labelNodes.js";

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

    let edgePoints = $derived(data?.edgePoints ?? []);
    let bendPoints = $derived(getBendPoints(edgePoints));
    let sourceDockingPoint = $derived(getSourceDockingPoint(edgePoints));
    let targetDockingPoint = $derived(getTargetDockingPoint(edgePoints));
    let readOnly = $derived(data?.readOnly ?? false);

    /**
     * Whether one of this edge's own labels is currently being dragged. Inheritance edges never
     * carry labels, so this is always false for them without any extra branching.
     */
    let held = $derived(labelHighlight.isHeld(labelsOf(data)));

    /** Whether the edge should swell: on selection, or while one of its labels is held. */
    let highlighted = $derived(selected || held);

    /**
     * Whether the edge point handles are shown: only on an edge that is selected alone. With several
     * edges selected, e.g. after a box selection over a large diagram, no edge shows them, so they
     * are not mounted and moved for hundreds of edges at once. Only a selected edge looks for a
     * second selected one and stops at the first it finds.
     */
    let edited = $derived(
        selected &&
            !edges.current.some(edge => edge.selected && edge.id !== id),
    );

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
                bendPoints,
                {
                    source: sourceDockingPoint,
                    target: targetDockingPoint,
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

    function handleEdgePointsChange(nextPoints) {
        patchEdgeData({ edgePoints: nextPoints });
    }

    function handleEdgePointsCommit() {
        const current = data?.edgePoints ?? [];
        const dissolved = dissolveCollinearBendPoints(
            current,
            EDGE_INTERACTION_CONFIG.collinearBendPointTolerancePx,
        );
        if (dissolved !== current) {
            patchEdgeData({ edgePoints: dissolved });
        }
    }
</script>

{@render children(path, highlighted, edgeParams)}

{#if edited && edgeParams && target !== source}
    <EdgePointHandles
        edgeId={id}
        sourcePoint={{ x: edgeParams.sx, y: edgeParams.sy }}
        targetPoint={{ x: edgeParams.tx, y: edgeParams.ty }}
        sourceBorderPoint={{ x: edgeParams.borderSx, y: edgeParams.borderSy }}
        targetBorderPoint={{ x: edgeParams.borderTx, y: edgeParams.borderTy }}
        {edgePoints}
        {bendPoints}
        {sourceDockingPoint}
        {targetDockingPoint}
        {readOnly}
        sourceNodeId={source}
        targetNodeId={target}
        onPointsChange={handleEdgePointsChange}
        onPointsCommit={handleEdgePointsCommit}
    />
{/if}
