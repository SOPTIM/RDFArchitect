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
        useInternalNode,
        useSvelteFlow,
    } from "@xyflow/svelte";
    import { onDestroy } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { editorState } from "$lib/sharedState.svelte.js";

    import {
        getAssociationEdgeGeometry,
        getAssociationLabelTransform,
    } from "./edgeUtils.ts";

    let { id, source, target, data } = $props();

    const DECORATION_TYPE_LABEL = "label";
    const DECORATION_TYPE_MULTIPLICITY = "multiplicity";
    const MOVE_EPSILON = 0.5;
    const baseDecorationClass =
        "nodrag nopan select-none rounded border-0 px-2 py-0.5 text-left text-xs font-medium text-[#303030] shadow-sm touch-none";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const style = "stroke-width: 2px; stroke: #000";
    const svelteFlow = useSvelteFlow();
    let activeDrag = $state(null);
    let localOffsets = $state({});
    let sourceNode = $derived(useInternalNode(source));
    let targetNode = $derived(useInternalNode(target));
    let markerEnd = $derived(
        data?.useToAssociation ? "url(#associationTo)" : "",
    );
    let markerStart = $derived(
        data?.useFromAssociation ? "url(#associationFrom)" : "",
    );

    let edgeGeometry = $derived.by(() => {
        if (sourceNode.current && targetNode.current) {
            return getAssociationEdgeGeometry(
                sourceNode.current,
                targetNode.current,
                data?.parallelOffset ?? 0,
            );
        }
    });

    onDestroy(() => {
        removePointerListeners();
    });

    function getStoredOffset(offset) {
        return {
            x: offset?.x ?? 0,
            y: offset?.y ?? 0,
        };
    }

    function getPersistedOffset(offsetKey, offset) {
        return getStoredOffset(localOffsets[offsetKey] ?? offset);
    }

    function getDecorationPosition(anchor, offset) {
        return {
            x: anchor.x + offset.x,
            y: anchor.y + offset.y,
        };
    }

    function getDecorationClass(isLabel, canMove, isDragging) {
        return `${baseDecorationClass} ${isLabel ? "bg-white/90 whitespace-nowrap" : "bg-white/80"} ${canMove ? "cursor-grab" : "cursor-default"} ${isDragging ? "cursor-grabbing ring-1 ring-[#8a8a8a]" : ""}`;
    }

    function getLabelStyle(position, node) {
        if (!node) {
            return "";
        }

        return `transform: ${getAssociationLabelTransform(position, node)};`;
    }

    function hasMeaningfulMove(dragState) {
        return (
            Math.abs(dragState.currentOffset.x - dragState.initialOffset.x) >
                MOVE_EPSILON ||
            Math.abs(dragState.currentOffset.y - dragState.initialOffset.y) >
                MOVE_EPSILON
        );
    }

    function getFlowPointerPosition(pointerEvent) {
        return svelteFlow.screenToFlowPosition(
            {
                x: pointerEvent.clientX,
                y: pointerEvent.clientY,
            },
            { snapToGrid: false },
        );
    }

    function handlePointerMove(pointerEvent) {
        if (
            !activeDrag ||
            activeDrag.isPersisting ||
            pointerEvent.pointerId !== activeDrag.pointerId
        ) {
            return;
        }

        const currentPointerPosition = getFlowPointerPosition(pointerEvent);
        const deltaX =
            currentPointerPosition.x - activeDrag.startPointerPosition.x;
        const deltaY =
            currentPointerPosition.y - activeDrag.startPointerPosition.y;

        activeDrag = {
            ...activeDrag,
            currentOffset: {
                x: activeDrag.initialOffset.x + deltaX,
                y: activeDrag.initialOffset.y + deltaY,
            },
        };
    }

    async function finishDrag(pointerEvent, canceled = false) {
        if (!activeDrag || pointerEvent.pointerId !== activeDrag.pointerId) {
            return;
        }

        const dragState = activeDrag;
        removePointerListeners();
        releasePointerCapture(dragState);

        if (canceled || !hasMeaningfulMove(dragState)) {
            activeDrag = null;
            return;
        }

        activeDrag = {
            ...dragState,
            isPersisting: true,
        };

        const didPersist = await persistAssociationDecorationOffset({
            offsetKey: dragState.offsetKey,
            associationUUID: dragState.associationUUID,
            decorationType: dragState.decorationType,
            xPosition: dragState.currentOffset.x,
            yPosition: dragState.currentOffset.y,
        });

        activeDrag = null;

        if (!didPersist) {
            return;
        }
    }

    function handlePointerUp(pointerEvent) {
        finishDrag(pointerEvent);
    }

    function handlePointerCancel(pointerEvent) {
        finishDrag(pointerEvent, true);
    }

    function addPointerListeners(pointerTarget) {
        pointerTarget?.addEventListener("pointermove", handlePointerMove);
        pointerTarget?.addEventListener("pointerup", handlePointerUp);
        pointerTarget?.addEventListener("pointercancel", handlePointerCancel);
    }

    function removePointerListeners() {
        activeDrag?.pointerTarget?.removeEventListener(
            "pointermove",
            handlePointerMove,
        );
        activeDrag?.pointerTarget?.removeEventListener(
            "pointerup",
            handlePointerUp,
        );
        activeDrag?.pointerTarget?.removeEventListener(
            "pointercancel",
            handlePointerCancel,
        );
    }

    function releasePointerCapture(dragState) {
        dragState?.pointerTarget?.releasePointerCapture?.(dragState.pointerId);
    }

    function startDecorationDrag(pointerEvent, dragConfig) {
        if (
            !data?.canMoveAssociationDecoration ||
            !dragConfig.associationUUID
        ) {
            return;
        }

        pointerEvent.preventDefault();
        pointerEvent.stopPropagation();
        removePointerListeners();

        const storedOffset = getStoredOffset(dragConfig.storedOffset);
        pointerEvent.currentTarget?.setPointerCapture?.(pointerEvent.pointerId);
        activeDrag = {
            pointerId: pointerEvent.pointerId,
            pointerTarget: pointerEvent.currentTarget,
            associationUUID: dragConfig.associationUUID,
            decorationType: dragConfig.decorationType,
            offsetKey: dragConfig.offsetKey,
            startPointerPosition: getFlowPointerPosition(pointerEvent),
            initialOffset: storedOffset,
            currentOffset: storedOffset,
            isPersisting: false,
        };
        addPointerListeners(pointerEvent.currentTarget);
    }

    async function persistAssociationDecorationOffset({
        offsetKey,
        associationUUID,
        decorationType,
        xPosition,
        yPosition,
    }) {
        const packageUUID =
            editorState.selectedPackageUUID.getValue() ?? "default";
        const res = await bec.updateAssociationDecorationPositions(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            packageUUID,
            [
                {
                    associationUUID,
                    decorationType,
                    xPosition,
                    yPosition,
                },
            ],
        );

        if (!res.ok) {
            const errorText = await res.text();
            console.error(
                "Could not update association decoration position:",
                errorText,
            );
            return false;
        }

        localOffsets = {
            ...localOffsets,
            [offsetKey]: {
                x: xPosition,
                y: yPosition,
            },
        };

        return true;
    }
</script>

{#if edgeGeometry}
    <BaseEdge {id} path={edgeGeometry.path} {markerStart} {markerEnd} {style} />
    {#if data?.toLabel && data?.showAssociationLabels}
        {@const canMoveDecoration =
            (data?.canMoveAssociationDecoration ?? false) &&
            Boolean(data?.toAssociationUUID)}
        {@const isDragging = activeDrag?.offsetKey === "toLabelOffset"}
        {@const currentOffset = isDragging
            ? activeDrag.currentOffset
            : getPersistedOffset("toLabelOffset", data?.toLabelOffset)}
        {@const position = getDecorationPosition(
            edgeGeometry.startAssociationLabelAnchor,
            currentOffset,
        )}
        <EdgeLabel
            x={position.x}
            y={position.y}
            transparent
            class={`nodrag nopan ${canMoveDecoration ? "pointer-events-auto" : "pointer-events-none"} bg-transparent p-0`}
            onpointerdown={canMoveDecoration
                ? pointerEvent =>
                      startDecorationDrag(pointerEvent, {
                          associationUUID: data?.toAssociationUUID,
                          decorationType: DECORATION_TYPE_LABEL,
                          offsetKey: "toLabelOffset",
                          storedOffset: data?.toLabelOffset,
                      })
                : undefined}
        >
            <div
                class={getDecorationClass(true, canMoveDecoration, isDragging)}
                style={getLabelStyle(position, sourceNode.current)}
            >
                {data.toLabel}
            </div>
        </EdgeLabel>
    {/if}
    {#if data?.fromLabel && data?.showAssociationLabels}
        {@const canMoveDecoration =
            (data?.canMoveAssociationDecoration ?? false) &&
            Boolean(data?.fromAssociationUUID)}
        {@const isDragging = activeDrag?.offsetKey === "fromLabelOffset"}
        {@const currentOffset = isDragging
            ? activeDrag.currentOffset
            : getPersistedOffset("fromLabelOffset", data?.fromLabelOffset)}
        {@const position = getDecorationPosition(
            edgeGeometry.endAssociationLabelAnchor,
            currentOffset,
        )}
        <EdgeLabel
            x={position.x}
            y={position.y}
            transparent
            class={`nodrag nopan ${canMoveDecoration ? "pointer-events-auto" : "pointer-events-none"} bg-transparent p-0`}
            onpointerdown={canMoveDecoration
                ? pointerEvent =>
                      startDecorationDrag(pointerEvent, {
                          associationUUID: data?.fromAssociationUUID,
                          decorationType: DECORATION_TYPE_LABEL,
                          offsetKey: "fromLabelOffset",
                          storedOffset: data?.fromLabelOffset,
                      })
                : undefined}
        >
            <div
                class={getDecorationClass(true, canMoveDecoration, isDragging)}
                style={getLabelStyle(position, targetNode.current)}
            >
                {data.fromLabel}
            </div>
        </EdgeLabel>
    {/if}
    {#if data?.fromMultiplicity}
        {@const canMoveDecoration =
            (data?.canMoveAssociationDecoration ?? false) &&
            Boolean(data?.fromAssociationUUID)}
        {@const isDragging = activeDrag?.offsetKey === "fromMultiplicityOffset"}
        {@const currentOffset = isDragging
            ? activeDrag.currentOffset
            : getPersistedOffset(
                  "fromMultiplicityOffset",
                  data?.fromMultiplicityOffset,
              )}
        {@const position = getDecorationPosition(
            edgeGeometry.startMultiplicityAnchor,
            currentOffset,
        )}
        <EdgeLabel
            x={position.x}
            y={position.y}
            transparent
            class={`nodrag nopan ${canMoveDecoration ? "pointer-events-auto" : "pointer-events-none"} bg-transparent p-0`}
            onpointerdown={canMoveDecoration
                ? pointerEvent =>
                      startDecorationDrag(pointerEvent, {
                          associationUUID: data?.fromAssociationUUID,
                          decorationType: DECORATION_TYPE_MULTIPLICITY,
                          offsetKey: "fromMultiplicityOffset",
                          storedOffset: data?.fromMultiplicityOffset,
                      })
                : undefined}
        >
            <div
                class={getDecorationClass(false, canMoveDecoration, isDragging)}
            >
                {data.fromMultiplicity}
            </div>
        </EdgeLabel>
    {/if}
    {#if data?.toMultiplicity}
        {@const canMoveDecoration =
            (data?.canMoveAssociationDecoration ?? false) &&
            Boolean(data?.toAssociationUUID)}
        {@const isDragging = activeDrag?.offsetKey === "toMultiplicityOffset"}
        {@const currentOffset = isDragging
            ? activeDrag.currentOffset
            : getPersistedOffset(
                  "toMultiplicityOffset",
                  data?.toMultiplicityOffset,
              )}
        {@const position = getDecorationPosition(
            edgeGeometry.endMultiplicityAnchor,
            currentOffset,
        )}
        <EdgeLabel
            x={position.x}
            y={position.y}
            transparent
            class={`nodrag nopan ${canMoveDecoration ? "pointer-events-auto" : "pointer-events-none"} bg-transparent p-0`}
            onpointerdown={canMoveDecoration
                ? pointerEvent =>
                      startDecorationDrag(pointerEvent, {
                          associationUUID: data?.toAssociationUUID,
                          decorationType: DECORATION_TYPE_MULTIPLICITY,
                          offsetKey: "toMultiplicityOffset",
                          storedOffset: data?.toMultiplicityOffset,
                      })
                : undefined}
        >
            <div
                class={getDecorationClass(false, canMoveDecoration, isDragging)}
            >
                {data.toMultiplicity}
            </div>
        </EdgeLabel>
    {/if}
{/if}
