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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import SvelteFlowEdgeContextMenu from "$lib/rendering/svelteflow/components/contextmenu/SvelteFlowEdgeContextMenu.svelte";
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
    import {
        editorState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";

    import ClassNode from "./components/ClassNode.svelte";
    import SvelteFlowClassContextMenu from "./components/contextmenu/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/contextmenu/SvelteFlowPaneContextMenu.svelte";
    import AssociationEdge from "./components/edge/AssociationEdge.svelte";
    import EdgeMarkers from "./components/edge/EdgeMarkers.svelte";
    import {
        getEdgeParams,
        getClosestSegmentInsertionIndex,
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
        getEndPoints,
    } from "./interaction/bendPointOperations.js";
    import { ContextMenuController } from "./interaction/contextMenus.svelte.js";
    import { DiagramSelectionController } from "./interaction/diagramSelection.svelte.js";
    import { NodeOrderController } from "./interaction/nodeOrder.svelte.js";
    import { PanController } from "./interaction/panController.svelte.js";
    import { getLayoutedNodes } from "./layout/elkLayout.js";

    let {
        nodes: inputNodes,
        edges: inputEdges,
        svelteFlowAPI = $bindable({}),
        isLoading = $bindable(false),
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
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
        bec,
    });

    const contextMenus = new ContextMenuController({
        getSvelteFlow: () => svelteFlowAPI?.svelteFlow,
        getIsReadOnly: () => isDatasetReadOnly,
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
        pan,
        contextMenus,
        nodeOrder: nodeOrderCtrl,
    });

    // svelte-ignore state_referenced_locally
    let nodes = $state.raw([...inputNodes]);
    // svelte-ignore state_referenced_locally
    let edges = $state.raw([...inputEdges]);
    let isDatasetReadOnly = $state();
    let containerEl;

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
        editorState.selectedDataset.subscribe();
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
        el.addEventListener("click", onContainerClick, true);
        el.addEventListener("contextmenu", onContainerContextMenu, true);
        return () => {
            el.removeEventListener("pointerdown", onContainerPointerDown, true);
            el.removeEventListener("click", onContainerClick, true);
            el.removeEventListener("contextmenu", onContainerContextMenu, true);
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
        const dataset = editorState.selectedDataset.getValue();
        isDatasetReadOnly = dataset ? await isReadOnly(dataset) : false;
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

    async function isReadOnly(datasetName) {
        const res = await bec.isReadOnly(datasetName);
        return await res.json();
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
        let anyEndPointMoved = false;
        const updatedEdges = edges.map(edge => {
            const endPoints = edge.data?.endPoints;
            if (!endPoints || (!endPoints.source && !endPoints.target)) {
                return edge;
            }

            let nextEndPoints = endPoints;
            for (const node of draggedNodes) {
                const previous = lastDragPositions.get(node.id);
                if (!previous) continue;
                const dx = node.position.x - previous.x;
                const dy = node.position.y - previous.y;
                if (dx === 0 && dy === 0) continue;

                nextEndPoints = shiftEndPointsForNode(
                    nextEndPoints,
                    edge,
                    node.id,
                    dx,
                    dy,
                );
            }

            if (nextEndPoints === endPoints) {
                return edge;
            }
            anyEndPointMoved = true;
            return {
                ...edge,
                data: { ...edge.data, endPoints: nextEndPoints },
            };
        });

        if (anyEndPointMoved) {
            edges = updatedEdges;
        }

        for (const node of draggedNodes) {
            lastDragPositions.set(node.id, {
                x: node.position.x,
                y: node.position.y,
            });
        }
    }

    // Shifts the source and/or target end point of an edge by (dx, dy) if that
    // side is attached to the given moved node.
    function shiftEndPointsForNode(endPoints, edge, movedNodeId, dx, dy) {
        let result = endPoints;
        if (edge.source === movedNodeId && result.source) {
            result = {
                ...result,
                source: {
                    ...result.source,
                    x: result.source.x + dx,
                    y: result.source.y + dy,
                },
            };
        }
        if (edge.target === movedNodeId && result.target) {
            result = {
                ...result,
                target: {
                    ...result.target,
                    x: result.target.x + dx,
                    y: result.target.y + dy,
                },
            };
        }
        return result;
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
            bec.updateClassPositions(
                editorState.selectedDataset.getValue(),
                editorState.selectedGraph.getValue(),
                diagramUUID,
                classPositionDTOList,
            );
        } else {
            bec.updateGlobalClassPositions(
                editorState.selectedDataset.getValue(),
                diagramUUID,
                classPositionDTOList,
            );
        }
    }
    function selectOnlyEdge(edgeId) {
        edges = edges.map(edge => ({
            ...edge,
            selected: edge.id === edgeId,
        }));
    }

    function updateEdgeBendPoints(edgeId, newBendPoints) {
        patchEdgeData(edgeId, { bendPoints: newBendPoints });
    }

    function edgeEndpoints(edge, bendPoints) {
        const svelteFlow = svelteFlowAPI?.svelteFlow;
        if (!svelteFlow?.getInternalNode) return null;
        const sourceNode = svelteFlow.getInternalNode(edge.source);
        const targetNode = svelteFlow.getInternalNode(edge.target);
        if (!sourceNode || !targetNode) return null;
        const params = getEdgeParams(sourceNode, targetNode, 0, bendPoints);
        return {
            source: { x: params.sx, y: params.sy },
            target: { x: params.tx, y: params.ty },
        };
    }

    function handleEdgeAddBendPoint({ edgeId, flowPosition }) {
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;
        const bendPoints = getBendPoints(edge);
        if (bendPoints.length >= EDGE_INTERACTION_CONFIG.maxBendPointsPerEdge)
            return;

        const endpoints = edgeEndpoints(edge, bendPoints);
        let insertionIndex = bendPoints.length;
        if (endpoints) {
            const orderedPoints = [
                endpoints.source,
                ...bendPoints,
                endpoints.target,
            ];
            insertionIndex = getClosestSegmentInsertionIndex(
                orderedPoints,
                flowPosition,
            );
        }

        const newBendPoints = insertBendPointAt(
            bendPoints,
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
        patchEdgeData(edgeId, { bendPoints: [], endPoints: {} });
    }

    function handleEdgeDeleteEndPoint({ edgeId, side }) {
        const edge = edges.find(e => e.id === edgeId);
        if (!edge) return;
        const currentEndPoints = getEndPoints(edge);
        const nextEndPoints = { ...currentEndPoints, [side]: null };
        updateEdgeEndPoints(edgeId, nextEndPoints);
    }

    function updateEdgeEndPoints(edgeId, newEndPoints) {
        patchEdgeData(edgeId, { endPoints: newEndPoints });
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
        const layoutedNodes = await getLayoutedNodes(nodes, edges);
        nodes = [...layoutedNodes];
        updateNodePositions(nodes);
        await fitViewIncludingBendPoints();
        isLoading = false;
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
        nodesDraggable={!isDatasetReadOnly && !pan.shiftHeld && !pan.ctrlHeld}
        elementsSelectable={true}
        nodesFocusable={false}
        zIndexMode={"manual"}
        onnodeclick={e => selection.handleNodeClick(e)}
        onnodecontextmenu={e => contextMenus.handleNodeContextMenu(e)}
        onpaneclick={() => contextMenus.close()}
        onpanecontextmenu={e => contextMenus.handlePaneContextMenu(e)}
        onedgecontextmenu={e => contextMenus.handleEdgeContextMenu(e)}
        onselectionchange={e => selection.handleSelectionChange(e)}
        onselectionstart={() => {
            boxSelecting = true;
        }}
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
        disabled={isDatasetReadOnly}
        lockedDatasetName={editorState.selectedDataset.getValue()}
        lockedGraphUri={editorState.selectedGraph.getValue()}
        lockedPackage={editorState.selectedDiagram.getProperty("id")}
        onClose={() => contextMenus.close()}
    />
    <SvelteFlowClassContextMenu
        request={contextMenus.classRequest}
        disabled={!contextMenus.contextMenuClass}
        readOnly={isDatasetReadOnly}
        contextMenuClass={contextMenus.contextMenuClass}
        datasetName={editorState.selectedDataset.getValue()}
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
