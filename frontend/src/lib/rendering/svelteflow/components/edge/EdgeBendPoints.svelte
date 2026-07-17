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
    import { useSvelteFlow } from "@xyflow/svelte";

    import { AutoPanController } from "$lib/rendering/svelteflow/interaction/autoPanController.svelte.js";
    import {
        createBendPoint,
        insertBendPointAt,
        createEndPoint,
    } from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";

    import {
        getInactiveBendPoints,
        projectPointOntoNodeBorder,
    } from "./edgeUtils.ts";

    let {
        edgeId,
        sourcePoint,
        targetPoint,
        sourceBorderPoint,
        targetBorderPoint,
        bendPoints = [],
        endPoints = {},
        sourceNodeId,
        targetNodeId,
        onPointsChange,
        onEndPointsChange,
    } = $props();

    const { screenToFlowPosition, getInternalNode, getViewport, setViewport } =
        useSvelteFlow();

    const autoPan = new AutoPanController({
        getViewport,
        setViewport,
        getContainerRect: () =>
            document.querySelector(".svelte-flow")?.getBoundingClientRect() ??
            null,
    });

    let drag = $state(null);

    let fullPoints = $derived([sourcePoint, ...bendPoints, targetPoint]);
    let inactiveBendPoints = $derived(getInactiveBendPoints(fullPoints));

    let inactiveEndPoints = $derived(
        [
            endPoints.source ? null : { side: "source", ...sourceBorderPoint },
            endPoints.target ? null : { side: "target", ...targetBorderPoint },
        ].filter(Boolean),
    );

    function beginBendDrag(pointId, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        drag = { kind: "bend", id: pointId };
        addDragListeners();
    }

    function beginEndDrag(side, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        drag = { kind: "end", side, id: endPoints[side]?.id ?? null };
        addDragListeners();
    }

    function activateInactiveBendPoint(insertionIndex, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        const flowPosition = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });
        const newBendPoint = createBendPoint(flowPosition.x, flowPosition.y);
        const newBendPoints = insertBendPointAt(
            bendPoints,
            insertionIndex,
            newBendPoint,
        );
        onPointsChange(newBendPoints);
        drag = { kind: "bend", id: newBendPoint.id };
        addDragListeners();
    }

    function activateInactiveEndPoint(side, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        const flowPosition = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });
        const projected = projectOntoSide(side, flowPosition);
        const newEndPoint = createEndPoint(projected.x, projected.y);
        onEndPointsChange({ ...endPoints, [side]: newEndPoint });
        drag = { kind: "end", side, id: newEndPoint.id };
        addDragListeners();
    }

    function addDragListeners() {
        window.addEventListener("pointermove", onMove);
        window.addEventListener("pointerup", endDrag, { once: true });
        window.addEventListener("contextmenu", endDrag, { once: true });
    }

    function endDrag() {
        drag = null;
        autoPan.stop();
        window.removeEventListener("pointermove", onMove);
        window.removeEventListener("pointerup", endDrag);
        window.removeEventListener("contextmenu", endDrag);
    }

    function onMove(event) {
        if (!drag) return;
        applyPointMove(event);
        autoPan.update(event, applyPointMove);
    }

    function applyPointMove(event) {
        if (!drag) return;
        const flowPosition = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });

        if (drag.kind === "end") {
            const projected = projectOntoSide(drag.side, flowPosition);
            onEndPointsChange({
                ...endPoints,
                [drag.side]: {
                    ...endPoints[drag.side],
                    id: drag.id,
                    x: projected.x,
                    y: projected.y,
                },
            });
            return;
        }

        const newBendPoints = bendPoints.map(bendPoint =>
            bendPoint.id === drag.id
                ? { ...bendPoint, x: flowPosition.x, y: flowPosition.y }
                : bendPoint,
        );
        onPointsChange(newBendPoints);
    }

    function projectOntoSide(side, flowPosition) {
        const nodeId = side === "source" ? sourceNodeId : targetNodeId;
        const node = getInternalNode(nodeId);
        return node
            ? projectPointOntoNodeBorder(node, flowPosition)
            : flowPosition;
    }
</script>

<g>
    {#each inactiveBendPoints as inactivePoint (inactivePoint.insertionIndex)}
        <circle
            class="bend-point-handle nodrag nopan cursor-pointer fill-blue opacity-50"
            role="button"
            aria-label="Add bend point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={inactivePoint.x}
            cy={inactivePoint.y}
            r={EDGE_INTERACTION_CONFIG.inactivePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e =>
                activateInactiveBendPoint(inactivePoint.insertionIndex, e)}
        />
    {/each}

    {#each inactiveEndPoints as inactiveEnd (inactiveEnd.side)}
        <circle
            class="bend-point-handle nodrag nopan cursor-pointer fill-green opacity-50"
            role="button"
            aria-label="Activate end point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={inactiveEnd.x}
            cy={inactiveEnd.y}
            r={EDGE_INTERACTION_CONFIG.inactivePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e => activateInactiveEndPoint(inactiveEnd.side, e)}
        />
    {/each}

    {#each bendPoints as bendPoint (bendPoint.id)}
        <circle
            class="bend-point-handle nodrag nopan cursor-grab fill-blue stroke-white stroke-[1.5]"
            role="button"
            aria-label="Move bend point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={bendPoint.x}
            cy={bendPoint.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e => beginBendDrag(bendPoint.id, e)}
        />
    {/each}

    {#if endPoints.source}
        <circle
            class="bend-point-handle nodrag nopan cursor-grab fill-green stroke-white stroke-[1.5]"
            role="button"
            aria-label="Move source end point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={endPoints.source.x}
            cy={endPoints.source.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e => beginEndDrag("source", e)}
        />
    {/if}

    {#if endPoints.target}
        <circle
            class="bend-point-handle nodrag nopan cursor-grab fill-green stroke-white stroke-[1.5]"
            role="button"
            aria-label="Move target end point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={endPoints.target.x}
            cy={endPoints.target.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e => beginEndDrag("target", e)}
        />
    {/if}
</g>

<style>
    .bend-point-handle:focus,
    .bend-point-handle:focus-visible {
        outline: none;
    }
</style>
