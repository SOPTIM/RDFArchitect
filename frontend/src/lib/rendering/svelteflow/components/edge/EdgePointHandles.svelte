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
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
    import { edgePointDrag } from "$lib/rendering/svelteflow/interaction/edgeLayoutPersistence.js";
    import {
        createBendPoint,
        createDockingPoint,
        insertBendPointAt,
        getSourceDockingPoint,
        getTargetDockingPoint,
    } from "$lib/rendering/svelteflow/interaction/edgePointOperations.js";

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
        edgePoints = [],
        bendPoints = [],
        sourceDockingPoint = null,
        targetDockingPoint = null,
        readOnly = false,
        sourceNodeId,
        targetNodeId,
        onPointsChange,
        onPointsCommit,
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
    let inactiveBendPoints = $derived(
        readOnly ? [] : getInactiveBendPoints(fullPoints),
    );

    let inactiveDockingPoints = $derived(
        readOnly
            ? []
            : [
                  sourceDockingPoint
                      ? null
                      : { side: "source", ...sourceBorderPoint },
                  targetDockingPoint
                      ? null
                      : { side: "target", ...targetBorderPoint },
              ].filter(Boolean),
    );

    function beginPointDrag(pointId, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        drag = { id: pointId };
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
        const newPoints = insertBendPointAt(
            edgePoints,
            insertionIndex,
            newBendPoint,
        );
        onPointsChange(newPoints);
        drag = { id: newBendPoint.id };
        addDragListeners();
    }

    function activateInactiveDockingPoint(side, event) {
        if (event.button === 2) return;
        event.stopPropagation();
        const flowPosition = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });
        const projected = projectOntoSide(side, flowPosition);
        const newDockingPoint = createDockingPoint(
            projected.x,
            projected.y,
            side,
        );
        const newPoints =
            side === "source"
                ? [newDockingPoint, ...edgePoints]
                : [...edgePoints, newDockingPoint];
        onPointsChange(newPoints);
        drag = { id: newDockingPoint.id };
        addDragListeners();
    }

    function addDragListeners() {
        edgePointDrag.active = true;
        window.addEventListener("pointermove", onMove);
        window.addEventListener("pointerup", releaseDrag, {
            capture: true,
            once: true,
        });
        window.addEventListener("contextmenu", endDrag, { once: true });
    }

    /**
     * Ends the drag before anything else hears the release. A quick drag is often released beside
     * the handle, over the empty pane, where SvelteFlow would deselect the edge while the drag
     * still holds its save back, so the edge would end up neither selected nor saved.
     */
    function releaseDrag(event) {
        event.stopPropagation();
        endDrag();
    }

    function endDrag() {
        const wasDragging = drag !== null;
        drag = null;
        edgePointDrag.active = false;
        autoPan.stop();
        window.removeEventListener("pointermove", onMove);
        window.removeEventListener("pointerup", releaseDrag, { capture: true });
        window.removeEventListener("contextmenu", endDrag);
        if (wasDragging) {
            onPointsCommit?.();
        }
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

        const isSourceDocking =
            getSourceDockingPoint(edgePoints)?.id === drag.id;
        const isTargetDocking =
            getTargetDockingPoint(edgePoints)?.id === drag.id;

        let position = flowPosition;
        if (isSourceDocking) {
            position = projectOntoSide("source", flowPosition);
        } else if (isTargetDocking) {
            position = projectOntoSide("target", flowPosition);
        }

        const newPoints = edgePoints.map(point =>
            point.id === drag.id
                ? { ...point, x: position.x, y: position.y }
                : point,
        );
        onPointsChange(newPoints);
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
            class="edge-point-handle nodrag nopan fill-bendpoint cursor-pointer opacity-50"
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

    {#each inactiveDockingPoints as inactiveDocking (inactiveDocking.side)}
        <circle
            class="edge-point-handle nodrag nopan fill-dockingpoint cursor-pointer opacity-50"
            role="button"
            aria-label="Pin docking point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={inactiveDocking.x}
            cy={inactiveDocking.y}
            r={EDGE_INTERACTION_CONFIG.inactivePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={e =>
                activateInactiveDockingPoint(inactiveDocking.side, e)}
        />
    {/each}

    {#each bendPoints as bendPoint (bendPoint.id)}
        <circle
            class={`edge-point-handle nodrag nopan stroke-white stroke-[1.5] ${
                readOnly
                    ? "fill-text-subtle cursor-default"
                    : "fill-bendpoint cursor-grab"
            }`}
            role="button"
            aria-label="Move bend point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={bendPoint.x}
            cy={bendPoint.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={readOnly
                ? undefined
                : e => beginPointDrag(bendPoint.id, e)}
        />
    {/each}

    {#if sourceDockingPoint}
        <circle
            class={`edge-point-handle nodrag nopan stroke-white stroke-[1.5] ${
                readOnly
                    ? "fill-text-subtle cursor-default"
                    : "fill-dockingpoint cursor-grab"
            }`}
            role="button"
            aria-label="Move source docking point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={sourceDockingPoint.x}
            cy={sourceDockingPoint.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={readOnly
                ? undefined
                : e => beginPointDrag(sourceDockingPoint.id, e)}
        />
    {/if}

    {#if targetDockingPoint}
        <circle
            class={`edge-point-handle nodrag nopan stroke-white stroke-[1.5] ${
                readOnly
                    ? "fill-text-subtle cursor-default"
                    : "fill-dockingpoint cursor-grab"
            }`}
            role="button"
            aria-label="Move target docking point"
            tabindex="-1"
            data-edge-id={edgeId}
            cx={targetDockingPoint.x}
            cy={targetDockingPoint.y}
            r={EDGE_INTERACTION_CONFIG.activePointRadiusPx}
            style="pointer-events: all;"
            onpointerdown={readOnly
                ? undefined
                : e => beginPointDrag(targetDockingPoint.id, e)}
        />
    {/if}
</g>

<style>
    .edge-point-handle:focus,
    .edge-point-handle:focus-visible {
        outline: none;
    }
</style>
