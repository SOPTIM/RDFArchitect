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
        Background,
        SvelteFlow,
        useEdges,
        useNodes,
        useNodesInitialized,
        useSvelteFlow,
    } from "@xyflow/svelte";
    import { onDestroy, onMount, tick, untrack } from "svelte";

    import {
        updateClassPositions,
        updateDatasetClassPositions,
    } from "$lib/api/generated/index.ts";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import SvelteFlowEdgeContextMenu from "$lib/rendering/svelteflow/components/contextmenu/SvelteFlowEdgeContextMenu.svelte";
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
    import {
        editorState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import ClassNode from "./components/ClassNode.svelte";
    import SvelteFlowClassContextMenu from "./components/contextmenu/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/contextmenu/SvelteFlowPaneContextMenu.svelte";
    import AssociationEdge from "./components/edge/AssociationEdge.svelte";
    import EdgeMarkers from "./components/edge/EdgeMarkers.svelte";
    import {
        getEdgeParams,
        getClosestSegmentInsertionIndex,
        distanceToPolyline,
    } from "./components/edge/edgeUtils.ts";
    import InheritanceEdge from "./components/edge/InheritanceEdge.svelte";
    import {
        decorateEdges,
        hasDefaultNodeLayout,
    } from "./diagram/diagramElements.js";
    import {
        createBendPoint,
        insertBendPointAt,
        removeBendPoint,
        getBendPoints,
        getSourceEndPoint,
        getTargetEndPoint,
        getInnerBendPoints,
        toEdgePoints,
        findBendPointAtPosition,
        isEndPoint,
    } from "./interaction/bendPointOperations.js";
    import { ContextMenuController } from "./interaction/contextMenus.svelte.js";
    import { DiagramSelectionController } from "./interaction/diagramSelection.svelte.js";
    import { NodeOrderController } from "./interaction/nodeOrder.svelte.js";
    import { PanController } from "./interaction/panController.svelte.js";
    import { layoutDiagram } from "./layout/elkLayout.js";

    let {
        nodes: inputNodes,
        edges: inputEdges,
        svelteFlowAPI = $bindable({}),
        isLoading = $bindable(false),
    } = $props();

    const nodeTypes = {
        class: ClassNode,
    };
    const edgeTypes = {
        association: AssociationEdge,
        inheritance: InheritanceEdge,
    };

    const nodeOrderCtrl = new NodeOrderController({
        getNodes: () => nodes,
        setNodes: value => (nodes = value),
        getSelectedIds: () => selectedNodeIdSet(),
    });

    const contextMenus = new ContextMenuController({
        getSvelteFlow: () => svelteFlowAPI?.svelteFlow,
        getIsReadOnly: () => isWorkspaceReadOnly,
        getEdges: () => edges,
        selectEdge: edgeId => selectOnlyEdge(edgeId),
    });

    const pan = new PanController({
        getSvelteFlow: () => svelteFlowAPI?.svelteFlow,
        getContainer: () => containerEl,
    });

    const selection = new DiagramSelectionController({
        getNodes: () => nodes,
        setNodes: value => (nodes = value),
        getEdges: () => edges,
        setEdges: value => (edges = value),
        pan,
        contextMenus,
        nodeOrder: nodeOrderCtrl,
    });

    // svelte-ignore state_referenced_locally
    let nodes = $state.raw([...inputNodes]);
    // svelte-ignore state_referenced_locally
    let edges = $state.raw([...inputEdges]);
    let isWorkspaceReadOnly = $state();
    let containerEl;
    let lastCursorPosition = null;

    let lastSelectedDiagramId = null;

    let selectionZKey = "";

    let nodesInitialized = useNodesInitialized();
    let layouted = $state(false);

    let selectionZFrame = null;
    let boxSelecting = false;
    // Tracks the last seen position per dragged node id, to compute the delta
    // for moving attached end points live during a class drag.
    let lastDragPositions = new Map();

    let hasFittedInitially = false;
    let hasDefaultLayout = $derived(hasDefaultNodeLayout(nodes));
    let applyLayout = $derived(
        nodesInitialized.current && !layouted && hasDefaultLayout,
    );

    $effect(() => {
        if (!inputNodes || !inputEdges) {
            return;
        }
        untrack(syncDiagramElements);
    });

    $effect(() => {
        forceReloadTrigger.subscribe();
        applyAutoLayoutIfNeeded();
    });

    $effect(() => {
        forceReloadTrigger.subscribe();
        editorState.selectedWorkspace.subscribe();
        refreshReadOnlyState();
    });

    $effect(() => {
        editorState.focusedClassUUID.subscribe();
        focusRequestedClassInDiagram();
    });

    $effect(() => {
        editorState.selectedClass.subscribe();
        untrack(resetTempFrontWhenNoClassOpen);
    });

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        untrack(clearSelectionOnDiagramChange);
    });

    $effect(() => {
        multiSelectState.subscribe();
        untrack(scheduleSelectionZIndices);
    });

    $effect(() => {
        multiSelectState.subscribe();
        editorState.selectedClass.subscribe();
        untrack(keepEscapeHandlerOnTop);
    });

    onMount(() => {
        svelteFlowAPI = {
            svelteFlow: useSvelteFlow(),
            nodes: useNodes(),
            edges: useEdges(),
            useNodesInitialized: useNodesInitialized(),
        };

        const el = containerEl;
        el.addEventListener("pointerdown", onContainerPointerDown, true);
        el.addEventListener("pointermove", onContainerPointerMove, true);
        el.addEventListener("click", onContainerClick, true);
        el.addEventListener("contextmenu", onContainerContextMenu, true);

        const unregisterAddBendPoint = shortcutStore.register(
            "diagram-add-bend-point-at-cursor",
            ["ctrl", "q"],
            addBendPointAtCursor,
        );
        const unregisterDeleteBendPoint = shortcutStore.register(
            "diagram-delete-bend-point-at-cursor",
            ["ctrl", "shift", "q"],
            deleteBendPointAtCursor,
        );

        return () => {
            el.removeEventListener("pointerdown", onContainerPointerDown, true);
            el.removeEventListener("pointermove", onContainerPointerMove, true);
            el.removeEventListener("click", onContainerClick, true);
            el.removeEventListener("contextmenu", onContainerContextMenu, true);
            unregisterAddBendPoint();
            unregisterDeleteBendPoint();
        };
    });

    onDestroy(() => {
        eventStack.removeEvent(selection.escapeClearSelection);
        if (selectionZFrame !== null) {
            cancelAnimationFrame(selectionZFrame);
        }
    });

    /*TODO SEHR WICHTIG: AM ENDE AUFRÄUMEN
        bend point code vllt auslagern, andere sachen, etc
        es muss ja nicht alles hier im svelteFlowWrapper liegen*/

    function onContainerPointerDown(event) {
        selection.notifyPointerDown();
        pan.handleContainerPointerDown(event);
    }

    function onContainerPointerMove(event) {
        lastCursorPosition = { x: event.clientX, y: event.clientY };
    }

    function onContainerClick(event) {
        pan.handleContainerClickCapture(event);
    }

    function onContainerContextMenu(event) {
        pan.handleContainerContextMenuCapture(event);
        if (event.defaultPrevented) {
            return;
        }
        routeBendPointContextMenu(event);
    }

    function routeBendPointContextMenu(event) {
        const target = event.target;
        if (!(target instanceof Element)) return;

        const bendPointCircle = target.closest("[data-edge-id]");
        if (!bendPointCircle) return;

        const edgeId = bendPointCircle.getAttribute("data-edge-id");
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;

        contextMenus.handleEdgeContextMenu({ event, edge });
    }
    function applyAutoLayoutIfNeeded() {
        if (applyLayout) {
            applyELKLayout();
        } else if (!hasDefaultLayout) {
            isLoading = false;
            fitInitiallyIfNeeded();
        }
    }

    function fitInitiallyIfNeeded() {
        if (hasFittedInitially || !nodesInitialized.current) {
            return;
        }
        hasFittedInitially = true;
        untrack(() => fitViewIncludingBendPoints({ duration: 0 }));
    }

    async function refreshReadOnlyState() {
        const workspace = editorState.selectedWorkspace.getValue();
        isWorkspaceReadOnly = workspace
            ? await workspaceStore.isReadOnly(workspace)
            : false;
    }

    function resetTempFrontWhenNoClassOpen() {
        if (!editorState.selectedClass.getProperty("id")) {
            nodeOrderCtrl.resetTemporaryFront();
        }
    }

    function clearSelectionOnDiagramChange() {
        const diagramId = editorState.selectedDiagram.getProperty("id");
        if (diagramId === lastSelectedDiagramId) {
            return;
        }
        lastSelectedDiagramId = diagramId;
        pan.clearBoxMode();
        multiSelectState.clear();
    }

    function scheduleSelectionZIndices() {
        if (boxSelecting || selectionZFrame !== null) {
            return;
        }
        selectionZFrame = requestAnimationFrame(() => {
            selectionZFrame = null;
            applySelectionZIndices();
        });
    }

    function applySelectionZIndices() {
        const selectedNodeIds = selectedNodeIdSet();
        const key = selectionContentKey(selectedNodeIds);
        if (key === selectionZKey) {
            return;
        }
        selectionZKey = key;
        nodes = nodeOrderCtrl.applyZIndices(nodes);
    }

    function keepEscapeHandlerOnTop() {
        eventStack.removeEvent(selection.escapeClearSelection);
        if (multiSelectState.getSelected().length === 0) {
            return;
        }
        eventStack.addEvent(selection.escapeClearSelection);
        tick().then(() => {
            if (multiSelectState.getSelected().length === 0) {
                return;
            }
            eventStack.removeEvent(selection.escapeClearSelection);
            eventStack.addEvent(selection.escapeClearSelection);
        });
    }

    function syncDiagramElements() {
        const nextNodes = [...inputNodes];
        const nextHasDefaultLayout = hasDefaultNodeLayout(nextNodes);

        nodeOrderCtrl.sync(nextNodes);
        nodes = nodeOrderCtrl.applyZIndices(nextNodes);
        selectionZKey = selectionContentKey(selectedNodeIdSet());
        edges = decorateEdges(inputEdges);
        resetDiagramSyncState(nextHasDefaultLayout);
    }

    function selectedNodeIdSet() {
        return new Set(
            multiSelectState.getSelected().map(entry => entry.classUuid),
        );
    }

    function selectionContentKey(idSet) {
        return [...idSet].sort().join("|");
    }

    function resetDiagramSyncState(hasDefaultLayoutAfterSync) {
        layouted = false;
        hasFittedInitially = false;

        // Keep the loading state active until persisted positions or ELK layout
        if (!hasDefaultLayoutAfterSync) {
            isLoading = false;
        }
    }

    function focusRequestedClassInDiagram() {
        const focusClassUUID = editorState.focusedClassUUID.getValue();
        if (!focusClassUUID || !nodesInitialized.current) {
            return;
        }

        if (!svelteFlowAPI?.svelteFlow) {
            return;
        }

        const focusNode = nodes.find(node => node.id === focusClassUUID);
        if (!focusNode) {
            return;
        }

        queueMicrotask(() => {
            nodeOrderCtrl.bringToFrontTemporarily(focusNode.id);
            svelteFlowAPI.svelteFlow.fitView({
                nodes: [focusNode],
                padding: 0.4,
                duration: 400,
                maxZoom: 1.6,
            });
            editorState.focusedClassUUID.updateValue(null);
        });
    }

    function handleNodeDragStart({ nodes: draggedNodes }) {
        lastDragPositions.clear();
        for (const node of draggedNodes) {
            lastDragPositions.set(node.id, {
                x: node.position.x,
                y: node.position.y,
            });
        }
    }

    function handleNodeDrag({ nodes: draggedNodes }) {
        const draggedNodeIds = new Set(draggedNodes.map(node => node.id));
        const dragDelta = computeDragDelta(draggedNodes);
        let anyPointMoved = false;

        const updatedEdges = edges.map(edge => {
            const points = edge.data?.bendPoints ?? [];
            let nextPoints = points;

            const sourceEnd = getSourceEndPoint(points);
            const targetEnd = getTargetEndPoint(points);
            if (sourceEnd || targetEnd) {
                for (const node of draggedNodes) {
                    const previous = lastDragPositions.get(node.id);
                    if (!previous) continue;
                    const dx = node.position.x - previous.x;
                    const dy = node.position.y - previous.y;
                    if (dx === 0 && dy === 0) continue;
                    nextPoints = shiftEndPointsForNode(
                        nextPoints,
                        edge,
                        node.id,
                        dx,
                        dy,
                    );
                }
            }

            const bothClassesDragged =
                draggedNodeIds.has(edge.source) &&
                draggedNodeIds.has(edge.target);
            if (bothClassesDragged && dragDelta) {
                nextPoints = shiftInnerBendPoints(
                    nextPoints,
                    dragDelta.dx,
                    dragDelta.dy,
                );
            }

            if (nextPoints === points) {
                return edge;
            }
            anyPointMoved = true;
            return {
                ...edge,
                data: { ...edge.data, bendPoints: nextPoints },
            };
        });

        if (anyPointMoved) {
            edges = updatedEdges;
        }

        for (const node of draggedNodes) {
            lastDragPositions.set(node.id, {
                x: node.position.x,
                y: node.position.y,
            });
        }
    }

    function computeDragDelta(draggedNodes) {
        for (const node of draggedNodes) {
            const previous = lastDragPositions.get(node.id);
            if (!previous) continue;
            const dx = node.position.x - previous.x;
            const dy = node.position.y - previous.y;
            if (dx === 0 && dy === 0) continue;
            return { dx, dy };
        }
        return null;
    }

    function shiftInnerBendPoints(points, dx, dy) {
        return points.map(point =>
            isEndPoint(point)
                ? point
                : { ...point, x: point.x + dx, y: point.y + dy },
        );
    }

    function shiftEndPointsForNode(points, edge, movedNodeId, dx, dy) {
        const sourceEnd = getSourceEndPoint(points);
        const targetEnd = getTargetEndPoint(points);
        const shiftSource = edge.source === movedNodeId && sourceEnd;
        const shiftTarget = edge.target === movedNodeId && targetEnd;
        if (!shiftSource && !shiftTarget) return points;

        return points.map(point => {
            if (shiftSource && point.id === sourceEnd.id) {
                return { ...point, x: point.x + dx, y: point.y + dy };
            }
            if (shiftTarget && point.id === targetEnd.id) {
                return { ...point, x: point.x + dx, y: point.y + dy };
            }
            return point;
        });
    }

    function handleNodeMove(nodeMoveEvent) {
        updateNodePositions(nodeMoveEvent.nodes);
    }

    function updateNodePositions(movedNodes) {
        let classPositionDTOList = [];
        for (const node of movedNodes) {
            const classPositionDTO = {
                classUUID: node.id,
                xPosition: node.position.x,
                yPosition: node.position.y,
                zPosition: nodeOrderCtrl.rankOf(node.id),
            };
            classPositionDTOList.push(classPositionDTO);
        }

        const diagramUUID = editorState.selectedDiagram.getProperty("id");
        if (!diagramUUID) return;

        if (editorState.selectedGraph.getValue()) {
            updateClassPositions({
                path: {
                    datasetName: editorState.selectedWorkspace.getValue(),
                    graphURI: editorState.selectedGraph.getValue(),
                    diagramUUID: diagramUUID,
                },
                body: classPositionDTOList,
            });
        } else {
            updateDatasetClassPositions({
                path: {
                    datasetName: editorState.selectedWorkspace.getValue(),
                    diagramUUID: diagramUUID,
                },
                body: classPositionDTOList,
            });
        }
    }
    function selectOnlyEdge(edgeId) {
        edges = edges.map(edge => ({
            ...edge,
            selected: edge.id === edgeId,
        }));
    }
    // Selects only the clicked edge on a plain left click. Independent of the
    // class-based auto selection: clicking an edge always selects just that edge.
    function handleEdgeClick({ edge }) {
        if (!edge?.id) return;
        selectOnlyEdge(edge.id);
    }

    function updateEdgeBendPoints(edgeId, newBendPoints) {
        patchEdgeData(edgeId, { bendPoints: newBendPoints });
    }

    function edgeEndpoints(edge, innerBendPoints) {
        const svelteFlow = svelteFlowAPI?.svelteFlow;
        if (!svelteFlow?.getInternalNode) return null;
        const sourceNode = svelteFlow.getInternalNode(edge.source);
        const targetNode = svelteFlow.getInternalNode(edge.target);
        if (!sourceNode || !targetNode) return null;
        const allPoints = edge.data?.bendPoints ?? [];
        const params = getEdgeParams(
            sourceNode,
            targetNode,
            0,
            innerBendPoints,
            {
                source: getSourceEndPoint(allPoints),
                target: getTargetEndPoint(allPoints),
            },
        );
        return {
            source: { x: params.sx, y: params.sy },
            target: { x: params.tx, y: params.ty },
        };
    }

    function handleEdgeAddBendPoint({ edgeId, flowPosition }) {
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;
        const allPoints = getBendPoints(edge);
        const innerBendPoints = getInnerBendPoints(allPoints);

        const endpoints = edgeEndpoints(edge, innerBendPoints);
        let insertionIndex = innerBendPoints.length;
        if (endpoints) {
            const orderedPoints = [
                endpoints.source,
                ...innerBendPoints,
                endpoints.target,
            ];
            insertionIndex = getClosestSegmentInsertionIndex(
                orderedPoints,
                flowPosition,
            );
        }

        const newBendPoints = insertBendPointAt(
            allPoints,
            insertionIndex,
            createBendPoint(flowPosition.x, flowPosition.y),
        );
        updateEdgeBendPoints(edgeId, newBendPoints);
    }

    function handleEdgeDeleteBendPoint({ edgeId, bendPointId }) {
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;
        const next = removeBendPoint(getBendPoints(edge), bendPointId);
        updateEdgeBendPoints(edgeId, next);
    }

    function handleEdgeClearBendPoints({ edgeId }) {
        patchEdgeData(edgeId, { bendPoints: [] });
    }

    function handleEdgeDeleteEndPoint({ edgeId, endPointId }) {
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;
        const points = edge.data?.bendPoints ?? [];
        const nextPoints = points.filter(point => point.id !== endPointId);
        patchEdgeData(edgeId, { bendPoints: nextPoints });
    }

    // Finds the edge whose drawn polyline is closest to the given flow position,
    // within the configured edge hit radius. Considers association and inheritance
    // edges (both use polyline routing). Returns the edge or null.
    function findEdgeAtFlowPosition(flowPosition, hitRadius) {
        let closestEdge = null;
        let closestDistance = Infinity;
        for (const edge of edges) {
            if (edge.source === edge.target) continue;
            const innerBendPoints = getInnerBendPoints(getBendPoints(edge));
            const endpoints = edgeEndpoints(edge, innerBendPoints);
            if (!endpoints) continue;
            const orderedPoints = [
                endpoints.source,
                ...innerBendPoints,
                endpoints.target,
            ];
            const distance = distanceToPolyline(flowPosition, orderedPoints);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEdge = edge;
            }
        }
        return closestDistance <= hitRadius ? closestEdge : null;
    }

    // Resolves the current cursor position to a flow position and a zoom-corrected
    // hit radius, or null if the cursor position or SvelteFlow is unavailable.
    function cursorFlowContext(baseHitRadiusPx) {
        const svelteFlow = svelteFlowAPI?.svelteFlow;
        if (!svelteFlow || !lastCursorPosition) return null;
        const flowPosition = svelteFlow.screenToFlowPosition(
            { x: lastCursorPosition.x, y: lastCursorPosition.y },
            { snapToGrid: false },
        );
        const zoom = svelteFlow.getViewport?.().zoom ?? 1;
        return { flowPosition, hitRadius: baseHitRadiusPx / (zoom || 1) };
    }

    // Ctrl+Q: creates a bend point on the edge closest to the cursor. Selects that
    // edge if it was not selected yet.
    function addBendPointAtCursor() {
        if (isDatasetReadOnly) return;
        const context = cursorFlowContext(
            EDGE_INTERACTION_CONFIG.edgeHitRadiusPx,
        );
        if (!context) return;
        const edge = findEdgeAtFlowPosition(
            context.flowPosition,
            context.hitRadius,
        );
        if (!edge) return;

        if (!edge.selected) selectOnlyEdge(edge.id);
        handleEdgeAddBendPoint({
            edgeId: edge.id,
            flowPosition: context.flowPosition,
        });
    }

    // Ctrl+Shift+Q: deletes the bend or end point under the cursor, across all
    // edges. Selects the affected edge if it was not selected yet.
    function deleteBendPointAtCursor() {
        if (isDatasetReadOnly) return;
        const context = cursorFlowContext(
            EDGE_INTERACTION_CONFIG.pointHitRadiusPx,
        );
        if (!context) return;

        for (const edge of edges) {
            const hitPoint = findBendPointAtPosition(
                getBendPoints(edge),
                context.flowPosition,
                context.hitRadius,
            );
            if (!hitPoint) continue;

            if (!edge.selected) selectOnlyEdge(edge.id);
            if (isEndPoint(hitPoint)) {
                handleEdgeDeleteEndPoint({
                    edgeId: edge.id,
                    endPointId: hitPoint.id,
                });
            } else {
                handleEdgeDeleteBendPoint({
                    edgeId: edge.id,
                    bendPointId: hitPoint.id,
                });
            }
            return;
        }
    }

    function patchEdgeData(edgeId, dataPatch) {
        edges = edges.map(edge =>
            edge.id === edgeId
                ? { ...edge, data: { ...edge.data, ...dataPatch } }
                : edge,
        );
    }

    export async function applyELKLayout() {
        if (!isLoading) isLoading = true;
        layouted = true;
        const { nodes: layoutedNodes, layoutedEdges } = await layoutDiagram(
            nodes,
            edges,
        );
        nodes = [...layoutedNodes];
        applyLayoutedEdges(layoutedEdges);
        updateNodePositions(nodes);
        await tick();
        await fitViewIncludingBendPoints();
        isLoading = false;
    }

    // Applies ELK's computed routing to the association edges. Source/target
    // points become sided end points, interior points become bend points.
    // Inheritance edges are skipped until they move to the shared routing.
    function applyLayoutedEdges(layoutedEdges) {
        edges = edges.map(edge => {
            const routingPoints = layoutedEdges.get(edge.id);
            if (!routingPoints || routingPoints.length === 0) {
                return edge;
            }
            return {
                ...edge,
                data: {
                    ...edge.data,
                    bendPoints: toEdgePoints(routingPoints),
                },
            };
        });
    }

    export async function fitViewIncludingBendPoints({ duration = 400 } = {}) {
        const bounds = getDiagramBounds();

        if (!bounds) {
            await svelteFlowAPI.svelteFlow.fitView({ duration });
            return;
        }

        return svelteFlowAPI.svelteFlow.fitBounds(bounds, {
            padding: 0.1, //matches the same padding of SvelteFlows fitView
            duration,
        });
    }

    function getDiagramBounds() {
        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const node of nodes) {
            const internalNode = svelteFlowAPI.svelteFlow.getInternalNode(
                node.id,
            );
            const position =
                internalNode?.internals?.positionAbsolute ?? node.position;
            const width = internalNode?.measured?.width ?? 0;
            const height = internalNode?.measured?.height ?? 0;
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x + width);
            maxY = Math.max(maxY, position.y + height);
        }

        for (const edge of edges) {
            for (const bendPoint of edge.data?.bendPoints ?? []) {
                minX = Math.min(minX, bendPoint.x);
                minY = Math.min(minY, bendPoint.y);
                maxX = Math.max(maxX, bendPoint.x);
                maxY = Math.max(maxY, bendPoint.y);
            }
        }
        if (
            !Number.isFinite(minX) ||
            !Number.isFinite(minY) ||
            !Number.isFinite(maxX) ||
            !Number.isFinite(maxY)
        ) {
            return null;
        }

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    }
</script>

<svelte:window
    onkeydown={e => pan.syncModifierKeys(e)}
    onkeyup={e => pan.syncModifierKeys(e)}
    onblur={() => pan.clearModifiers()}
/>

<div
    bind:this={containerEl}
    class={`relative h-full w-full ${pan.panningActive ? "ctrl-panning" : ""}`}
>
    <SvelteFlow
        bind:nodes
        bind:edges
        {nodeTypes}
        {edgeTypes}
        nodesDraggable={!isWorkspaceReadOnly && !pan.shiftHeld && !pan.ctrlHeld}
        elementsSelectable={true}
        nodesFocusable={false}
        zIndexMode={"manual"}
        onnodeclick={e => selection.handleNodeClick(e)}
        onnodecontextmenu={e => contextMenus.handleNodeContextMenu(e)}
        onpaneclick={() => contextMenus.close()}
        onpanecontextmenu={e => contextMenus.handlePaneContextMenu(e)}
        onedgecontextmenu={e => contextMenus.handleEdgeContextMenu(e)}
        onselectionchange={e =>
            selection.handleSelectionChange(e, boxSelecting)}
        onselectionstart={() => {
            boxSelecting = true;
        }}
        onedgeclick={e => handleEdgeClick(e)}
        onselectionend={() => {
            boxSelecting = false;
            selection.handleSelectionEnd();
            applySelectionZIndices();
        }}
        onnodedragstart={e => {
            selection.notifyNodeDragStart();
            nodeOrderCtrl.bringToFrontTemporarily(e.node?.id);
            handleNodeDragStart(e);
        }}
        onnodedrag={e => handleNodeDrag(e)}
        onnodedragstop={e => {
            selection.notifyNodeDragStop();
            handleNodeMove(e);
        }}
        selectionMode={"partial"}
        selectionOnDrag={true}
        panOnDrag={false}
        selectionKey={"Shift"}
        connectionMode={"loose"}
        multiSelectionKey={"Shift"}
        deleteKey={null}
        minZoom={0.1}
        maxZoom={5}
    >
        <EdgeMarkers />
        <Background patternColor="#aaa" gap={16} />
    </SvelteFlow>

    <SvelteFlowPaneContextMenu
        request={contextMenus.paneRequest}
        disabled={isWorkspaceReadOnly}
        lockedWorkspaceName={editorState.selectedWorkspace.getValue()}
        lockedGraphUri={editorState.selectedGraph.getValue()}
        lockedPackage={editorState.selectedDiagram.getProperty("id")}
        classes={nodes.map(node => ({
            id: node.id,
            graphUri: node.data?.graphUri,
        }))}
        onClose={() => contextMenus.close()}
    />
    <SvelteFlowClassContextMenu
        request={contextMenus.classRequest}
        disabled={!contextMenus.contextMenuClass}
        readOnly={isWorkspaceReadOnly}
        contextMenuClass={contextMenus.contextMenuClass}
        workspaceName={editorState.selectedWorkspace.getValue()}
        graphUri={editorState.selectedGraph.getValue()}
        nodeOrder={nodeOrderCtrl.nodeOrder}
        nodeCount={nodes.length}
        onClose={() => contextMenus.close()}
        onMoveClass={e => nodeOrderCtrl.moveClass(e)}
        onSetLayer={e => nodeOrderCtrl.setLayer(e)}
        onPersistLayer={e => nodeOrderCtrl.persistLayer(e)}
    />
    <SvelteFlowEdgeContextMenu
        request={contextMenus.edgeRequest}
        disabled={isDatasetReadOnly || !contextMenus.edgeRequest}
        onClose={() => contextMenus.close()}
        onAddBendPoint={handleEdgeAddBendPoint}
        onDeleteBendPoint={handleEdgeDeleteBendPoint}
        onDeleteEndPoint={handleEdgeDeleteEndPoint}
        onClearBendPoints={handleEdgeClearBendPoints}
    />
</div>

<style>
    /* Hide SvelteFlow's persistent multi-selection bounding box*/
    :global(.svelte-flow__selection-wrapper) {
        display: none;
    }

    .ctrl-panning :global(.svelte-flow__pane),
    .ctrl-panning :global(.svelte-flow__node) {
        cursor: grabbing;
    }

    :global(.svelte-flow__selection) {
        border: 2px solid var(--color-border-select);
        background: var(--color-background-select);
    }
</style>
