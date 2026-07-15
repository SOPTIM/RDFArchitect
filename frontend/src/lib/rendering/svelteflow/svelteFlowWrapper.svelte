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
    import { onDestroy, onMount, setContext, tick, untrack } from "svelte";
    import { SvelteMap } from "svelte/reactivity";

    import {
        updateClassPositions,
        updateDatasetClassPositions,
        updateDatasetLabelPositions,
        updateLabelPositions,
    } from "$lib/api/generated/index.ts";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import SvelteFlowEdgeContextMenu from "$lib/rendering/svelteflow/components/contextmenu/SvelteFlowEdgeContextMenu.svelte";
    import { renderOptions } from "$lib/renderOptions.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import ClassNode from "./components/ClassNode.svelte";
    import SvelteFlowClassContextMenu from "./components/contextmenu/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/contextmenu/SvelteFlowPaneContextMenu.svelte";
    import DiagramLabelNode from "./components/DiagramLabelNode.svelte";
    import AssociationEdge from "./components/edge/AssociationEdge.svelte";
    import EdgeMarkers from "./components/edge/EdgeMarkers.svelte";
    import {
        getEdgeParams,
        getClosestSegmentInsertionIndex,
    } from "./components/edge/edgeUtils.ts";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import SvelteFlowPropertyContextMenu from "./components/SvelteFlowPropertyContextMenu.svelte";
    import {
        decorateEdges,
        hasDefaultNodeLayout,
    } from "./diagram/diagramElements.js";
    import {
        buildLabelNodes,
        clampToAnchor,
        collectLabels,
        hasManualPlacement,
        LABEL_NODE_TYPE,
        labelNodeId,
        labelNodesChanged,
    } from "./diagram/labelNodes.js";
    import {
        createBendPoint,
        insertBendPointAt,
        removeBendPoint,
        getBendPoints,
        getEndPoints,
        MAX_BEND_POINTS_PER_EDGE,
    } from "./interaction/bendPointOperations.js";
    import { ContextMenuController } from "./interaction/contextMenus.svelte.js";
    import {
        DIAGRAM_SELECTION_CONTEXT,
        DiagramSelectionController,
    } from "./interaction/diagramSelection.svelte.js";
    import { labelHighlight } from "./interaction/labelHighlight.svelte.js";
    import {
        clearHeldModifiers,
        heldModifiers,
        syncHeldModifiers,
    } from "./interaction/modifierKeys.svelte.js";
    import { NodeOrderController } from "./interaction/nodeOrder.svelte.js";
    import { PanController } from "./interaction/panController.svelte.js";
    import {
        propertyContextMenu,
        propertySelection,
    } from "./interaction/propertyInteraction.svelte.js";
    import { getLayoutedNodes } from "./layout/elkLayout.js";

    let {
        nodes: inputNodes,
        edges: inputEdges,
        svelteFlowAPI = $bindable({}),
        isLoading = $bindable(false),
    } = $props();

    const nodeTypes = {
        class: ClassNode,
        label: DiagramLabelNode,
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
    let labelPositions = new SvelteMap();
    // Memorizes edge-intersection geometry per class pair, so dragging one class does not
    // recompute the placement of every other edge in the diagram.
    let labelPlacementCache = new Map();
    let labelDragActive = false;
    let classNodes = $derived(
        nodes.filter(node => node.type !== LABEL_NODE_TYPE),
    );
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
        syncLabelNodes(nodes, edges);
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
        selection.hasClearableSelection();
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
        labelHighlight.clear();
        propertySelection.clear();
        contextMenus.close();
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
        if (!selection.hasClearableSelection()) {
            return;
        }
        eventStack.addEvent(selection.escapeClearSelection);
        tick().then(() => {
            if (!selection.hasClearableSelection()) {
                return;
            }
            eventStack.removeEvent(selection.escapeClearSelection);
            eventStack.addEvent(selection.escapeClearSelection);
        });
    }

    function openClassFromMenu(classUuid) {
        const node = classNodes.find(candidate => candidate.id === classUuid);
        if (node) {
            selection.openClass(node);
        }
    }

    function syncDiagramElements() {
        labelPositions = new SvelteMap();
        labelPlacementCache = new Map();
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
    /**
     * Rebuilds the label nodes whenever the classes they are anchored to move. Skipped while a
     * label itself is being dragged, which would otherwise pull it back to its stored offset.
     */
    function syncLabelNodes(currentNodes, currentEdges) {
        if (labelDragActive) {
            return;
        }
        const nextLabelNodes = buildLabelNodes(
            currentNodes,
            currentEdges,
            labelPositions,
            labelPlacementCache,
            renderOptions.get("showAssociationLabels"),
        );
        if (!labelNodesChanged(currentNodes, nextLabelNodes)) {
            return;
        }
        nodes = [
            ...currentNodes.filter(node => node.type !== LABEL_NODE_TYPE),
            ...nextLabelNodes,
        ];
    }

    /**
     * Holds a dragged label within its maximum distance from the anchor point. SvelteFlow can only
     * constrain a node to a rectangle, so the radial limit is applied per drag event instead.
     *
     * This relies on the drag event firing after SvelteFlow has written its own position, so that
     * the clamped one is what the frame ends on. The drag itself keeps following the pointer
     * unclamped, which is what lets the label pick it up again on the way back in.
     */
    function clampDraggedLabels(draggedNodes) {
        const clampedById = new Map();
        for (const dragged of draggedNodes) {
            if (dragged.type !== LABEL_NODE_TYPE) {
                continue;
            }
            const clamped = clampToAnchor(
                dragged.position,
                dragged.data.anchorPoint,
            );
            if (clamped !== dragged.position) {
                clampedById.set(dragged.id, clamped);
            }
        }
        if (clampedById.size === 0) {
            return;
        }
        nodes = nodes.map(node =>
            clampedById.has(node.id)
                ? { ...node, position: clampedById.get(node.id) }
                : node,
        );
    }

    function toLabelPositionDTO(identifiedObjectUUID, kind, position) {
        return {
            identifiedObjectUUID,
            kind,
            x: position?.x ?? null,
            y: position?.y ?? null,
        };
    }

    function handleLabelMove(movedLabelNodes) {
        const movedLabels = [];
        for (const labelNode of movedLabelNodes) {
            const position = clampToAnchor(
                labelNode.position,
                labelNode.data.anchorPoint,
            );
            const delta = {
                x: position.x - labelNode.data.anchorPoint.x,
                y: position.y - labelNode.data.anchorPoint.y,
            };
            labelPositions.set(labelNode.id, delta);
            movedLabels.push(
                toLabelPositionDTO(
                    labelNode.data.identifiedObjectUUID,
                    labelNode.data.kind,
                    position,
                ),
            );
        }
        persistLabelPositions(movedLabels);
    }

    /** Drops the manual placement of every label, so they return to their default placement. */
    function resetLabelPositions() {
        const resetLabels = [];
        for (const { label } of collectLabels(edges)) {
            if (!hasManualPlacement(label, labelPositions)) {
                continue;
            }
            labelPositions.set(labelNodeId(label), null);
            resetLabels.push(
                toLabelPositionDTO(
                    label.identifiedObjectUUID,
                    label.kind,
                    null,
                ),
            );
        }
        persistLabelPositions(resetLabels);
    }

    function persistLabelPositions(labelPositionDTOList) {
        const diagramUUID = editorState.selectedDiagram.getProperty("id");
        if (!diagramUUID || labelPositionDTOList.length === 0) {
            return;
        }

        if (editorState.selectedGraph.getValue()) {
            updateLabelPositions({
                path: {
                    datasetName: editorState.selectedWorkspace.getValue(),
                    graphURI: editorState.selectedGraph.getValue(),
                    diagramUUID: diagramUUID,
                },
                body: labelPositionDTOList,
            });
        } else {
            updateDatasetLabelPositions({
                path: {
                    datasetName: editorState.selectedWorkspace.getValue(),
                    diagramUUID: diagramUUID,
                },
                body: labelPositionDTOList,
            });
        }
    }

    function handleNodeMove(nodeMoveEvent, isLabelDrag) {
        const movedNodes = nodeMoveEvent.nodes ?? [];
        if (isLabelDrag) {
            const movedLabels = movedNodes.filter(
                node => node.type === LABEL_NODE_TYPE,
            );
            if (movedLabels.length > 0) {
                handleLabelMove(movedLabels);
            }
            return;
        }
        const movedClasses = movedNodes.filter(
            node => node.type !== LABEL_NODE_TYPE,
        );
        if (movedClasses.length === 0) {
            return;
        }
        updateNodePositions(movedClasses);
        persistManuallyPlacedLabelsOf(movedClasses);
    }

    function persistManuallyPlacedLabelsOf(movedClassNodes) {
        const movedClassIds = new Set(movedClassNodes.map(node => node.id));
        const rebuilt = buildLabelNodes(
            nodes,
            edges,
            labelPositions,
            labelPlacementCache,
        );
        const rebuiltById = new Map(rebuilt.map(node => [node.id, node]));

        const affectedLabels = [];
        for (const { label, sourceId, targetId } of collectLabels(edges)) {
            const edgeAffected =
                movedClassIds.has(sourceId) || movedClassIds.has(targetId);
            if (!edgeAffected || !hasManualPlacement(label, labelPositions)) {
                continue;
            }
            const labelNode = rebuiltById.get(labelNodeId(label));
            if (!labelNode) {
                continue;
            }
            affectedLabels.push(
                toLabelPositionDTO(
                    label.identifiedObjectUUID,
                    label.kind,
                    labelNode.position,
                ),
            );
        }
        if (affectedLabels.length > 0) {
            persistLabelPositions(affectedLabels);
        }
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
        if (!diagramUUID || classPositionDTOList.length === 0) return;

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
        if (bendPoints.length >= MAX_BEND_POINTS_PER_EDGE) return;

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
        try {
            const layoutedNodes = await getLayoutedNodes(nodes, edges);
            nodes = [...layoutedNodes];
            updateNodePositions(nodes);
            resetLabelPositions();
            syncLabelNodes(nodes, edges);
            await fitViewIncludingBendPoints();
        } catch (error) {
            // The diagram keeps the positions it has; leaving the spinner up would only look
            // like a layout that never finishes.
            console.error("Laying out the diagram failed:", error);
            toastStore.error(
                "Layout failed",
                "The diagram could not be laid out automatically.",
            );
        } finally {
            isLoading = false;
        }
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

    setContext(DIAGRAM_SELECTION_CONTEXT, selection);
</script>

<svelte:window
    onkeydown={syncHeldModifiers}
    onkeyup={syncHeldModifiers}
    onblur={clearHeldModifiers}
/>

<div
    bind:this={containerEl}
    class="diagram-canvas relative h-full w-full"
    class:ctrl-panning={pan.panningActive}
    class:box-selecting={boxSelecting}
>
    <SvelteFlow
        bind:nodes
        bind:edges
        {nodeTypes}
        {edgeTypes}
        nodesDraggable={!isWorkspaceReadOnly &&
            !heldModifiers.shiftKey &&
            !heldModifiers.ctrlKey &&
            !heldModifiers.metaKey}
        elementsSelectable={true}
        nodesFocusable={false}
        zIndexMode={"manual"}
        onnodeclick={e => {
            if (e.node?.type === LABEL_NODE_TYPE) {
                return;
            }
            selection.handleNodeClick(e);
        }}
        onnodecontextmenu={e => {
            if (e.node?.type !== LABEL_NODE_TYPE) {
                contextMenus.handleNodeContextMenu(e);
            }
        }}
        onpaneclick={() => {
            contextMenus.close();
            propertySelection.clear();
        }}
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
            // Dragging a label makes SvelteFlow clear the selection, because label nodes are not
            // selectable. Announcing the drag keeps the class selection from following along.
            selection.notifyNodeDragStart();
            if (e.targetNode?.type === LABEL_NODE_TYPE) {
                labelDragActive = true;
                return;
            }
            nodeOrderCtrl.bringToFrontTemporarily(e.node?.id);
            handleNodeDragStart(e);
        }}
        onnodedrag={e => {
            if (labelDragActive) {
                clampDraggedLabels(e.nodes ?? []);
            }
        }}
        onnodedrag={e => handleNodeDrag(e)}
        onnodedragstop={e => {
            const wasLabelDrag = labelDragActive;
            labelDragActive = false;
            selection.notifyNodeDragStop();
            handleNodeMove(e, wasLabelDrag);
            if (wasLabelDrag) {
                syncLabelNodes(nodes, edges);
            }
        }}
        selectionMode={"partial"}
        selectionOnDrag={true}
        panOnDrag={false}
        selectionKey={"Shift"}
        connectionMode={"loose"}
        multiSelectionKey={"Shift"}
        deleteKey={null}
        zoomOnDoubleClick={false}
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
        classes={classNodes.map(node => ({
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
        nodeCount={classNodes.length}
        onClose={() => contextMenus.close()}
        onOpenClass={openClassFromMenu}
        onMoveClass={e => nodeOrderCtrl.moveClass(e)}
        onSetLayer={e => nodeOrderCtrl.setLayer(e)}
        onPersistLayer={e => nodeOrderCtrl.persistLayer(e)}
    />
    <SvelteFlowPropertyContextMenu
        request={propertyContextMenu.request}
        readOnly={isWorkspaceReadOnly}
        onClose={() => propertyContextMenu.close()}
    />
    <SvelteFlowEdgeContextMenu
        request={contextMenus.edgeRequest}
        disabled={isWorkspaceReadOnly || !contextMenus.edgeRequest}
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

    .diagram-canvas :global(.svelte-flow__pane.selection) {
        cursor: default;
    }

    .diagram-canvas.ctrl-panning :global(.svelte-flow__pane),
    .diagram-canvas.ctrl-panning :global(.svelte-flow__node),
    .diagram-canvas.ctrl-panning :global(.svelte-flow__node *) {
        cursor: grabbing;
    }

    .diagram-canvas.box-selecting :global(.svelte-flow__pane),
    .diagram-canvas.box-selecting :global(.svelte-flow__node),
    .diagram-canvas.box-selecting :global(.svelte-flow__node *) {
        cursor: crosshair;
    }

    :global(.svelte-flow__selection) {
        border: 2px solid var(--color-border-select);
        background: var(--color-background-select);
    }
</style>
