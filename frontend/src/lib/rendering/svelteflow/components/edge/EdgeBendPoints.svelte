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

    import {
        createBendPoint,
        insertBendPointAt,
    } from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";
    import { userSettings } from "$lib/userSettings.svelte.js";

    import { getInactiveBendPoints } from "./edgeUtils.ts";

    let {
        edgeId,
        sourcePoint,
        targetPoint,
        bendPoints = [],
        onPointsChange,
    } = $props();

    const { screenToFlowPosition } = useSvelteFlow();

    let draggingId = $state(null);

    let useRoundedEdges = $derived(userSettings.get("useRoundedEdges", false));
    let edgeTension = $derived(userSettings.get("edgeTension", 0.5));

    let fullPoints = $derived([sourcePoint, ...bendPoints, targetPoint]);
    let inactiveBendPoints = $derived(
        getInactiveBendPoints(fullPoints, useRoundedEdges, edgeTension),
    );

    function beginDrag(pointId, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        draggingId = pointId;
        window.addEventListener("pointermove", onMove);
        window.addEventListener("pointerup", endDrag, { once: true });
        window.addEventListener("contextmenu", endDrag, { once: true });
    }

    function activateInactivePoint(insertionIndex, event) {
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
        draggingId = newBendPoint.id;
        window.addEventListener("pointermove", onMove);
        window.addEventListener("pointerup", endDrag, { once: true });
        window.addEventListener("contextmenu", endDrag, { once: true });
    }

    function endDrag() {
        draggingId = null;
        window.removeEventListener("pointermove", onMove);
        window.removeEventListener("pointerup", endDrag);
        window.removeEventListener("contextmenu", endDrag);
    }

    function onMove(event) {
        if (!draggingId) return;
        const flowPosition = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });
        const newBendPoints = bendPoints.map(bendPoint =>
            bendPoint.id === draggingId
                ? { ...bendPoint, x: flowPosition.x, y: flowPosition.y }
                : bendPoint,
        );
        onPointsChange(newBendPoints);
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
            r="7"
            style="pointer-events: all;"
            onpointerdown={e =>
                activateInactivePoint(inactivePoint.insertionIndex, e)}
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
            r="8"
            style="pointer-events: all;"
            onpointerdown={e => beginDrag(bendPoint.id, e)}
        />
    {/each}
</g>

<style>
    .bend-point-handle:focus,
    .bend-point-handle:focus-visible {
        outline: none;
    }
</style>
