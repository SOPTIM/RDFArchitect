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
        updateDatasetLabelPositions,
        updateLabelPositions,
    } from "$lib/api/generated/index.ts";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        propertyEditorRequest,
        superClassFocusRequest,
    } from "$lib/propertyEditorRequest.svelte.js";
    import SvelteFlowEdgeContextMenu from "$lib/rendering/svelteflow/components/contextmenu/SvelteFlowEdgeContextMenu.svelte";
    import InheritanceEdge from "$lib/rendering/svelteflow/components/edge/InheritanceEdge.svelte";
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";
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
        moveDockingPointWithBorder,
        projectPointOntoRectBorder,
    } from "./components/edge/edgeUtils.ts";
    import SvelteFlowPropertyContextMenu from "./components/SvelteFlowPropertyContextMenu.svelte";
    import {
        decorateEdges,
        elevateEdges,
        getDiagramBounds,
        hasDefaultNodeLayout,
        onlySelectedEdgeId,
    } from "./diagram/diagramElements.js";
    import {
        buildLabelNodes,
        clampToAnchor,
        collectLabels,
        hasManualPlacement,
        LABEL_NODE_TYPE,
        labelNodeId,
        labelNodesChanged,
        labelsOf,
    } from "./diagram/labelNodes.js";
    import { ContextMenuController } from "./interaction/contextMenus.svelte.js";
    import {
        DIAGRAM_SELECTION_CONTEXT,
        DiagramSelectionController,
    } from "./interaction/diagramSelection.svelte.js";
    import {
        associationEditTarget,
        isEditableEdge,
    } from "./interaction/edgeEditing.js";
    import { EdgeLayoutPersistence } from "./interaction/edgeLayoutPersistence.js";
    import {
        applyLayoutedEdges,
        moveEdgePointsWithDraggedClasses,
        updateDockingPoints,
    } from "./interaction/edgePointOperations.js";
    import { EdgePointController } from "./interaction/edgePoints.svelte.js";
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
    import { layoutDiagram } from "./layout/elkLayout.js";

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

    const edgePoints = new EdgePointController({
        getEdges: () => edges,
        setEdges: value => (edges = value),
        getSvelteFlow: () => svelteFlowAPI?.svelteFlow,
        isReadOnly: () => isWorkspaceReadOnly ?? false,
        selectEdge: edgeId => selectOnlyEdge(edgeId),
        contextMenus,
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

    const edgeLayouts = new EdgeLayoutPersistence({
        getEdges: () => edges,
        setEdges: value => (edges = value),
        getTarget: () => ({
            datasetName: editorState.selectedWorkspace.getValue(),
            graphURI: editorState.selectedGraph.getValue(),
            diagramUUID: editorState.selectedDiagram.getProperty("id"),
        }),
        isReadOnly: () => isWorkspaceReadOnly ?? false,
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
    let boxSelecting = $state(false);
    /**
     * The last seen position per dragged node id, to compute the delta for moving attached end
     * points live during a class drag.
     */
    let lastDragPositions = new Map();
    let classBounds = new Map();
    let classBoundsDiagramId = null;
    let dockingPointsCheckPending = false;

    let hasFittedInitially = false;
    let labelPositions = new SvelteMap();
    // Memorizes edge-intersection geometry per class pair, so dragging one class does not
    // recompute the placement of every other edge in the diagram.
    let labelPlacementCache = new Map();
    let labelDragActive = false;
    let contextMenuEdge = $derived(
        contextMenus.edgeRequest
            ? edges.find(edge => edge.id === contextMenus.edgeRequest.edgeId)
            : null,
    );
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
        const currentEdges = edges;
        untrack(() => edgeLayouts.track(currentEdges));
    });

    $effect(() => {
        const currentNodes = nodes;
        untrack(() => {
            moveDockingPointsOfResizedClasses(currentNodes);
            redockDetachedDockingPoints(currentNodes);
        });
    });

    $effect(() => {
        forceReloadTrigger.subscribe();
        editorState.selectedWorkspace.subscribe();
        editorState.selectedDiagram.subscribe();
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

    $effect(() => {
        const readOnly = isWorkspaceReadOnly ?? false;
        const currentEdges = edges;
        untrack(() => {
            let changed = false;
            const next = currentEdges.map(edge => {
                if ((edge.data?.readOnly ?? false) === readOnly) {
                    return edge;
                }
                changed = true;
                return { ...edge, data: { ...edge.data, readOnly } };
            });
            if (changed) {
                edges = next;
            }
        });
    });

    $effect(() => {
        const currentEdges = edges;
        const editedEdgeId = onlySelectedEdgeId(currentEdges);
        const next = elevateEdges(
            currentEdges,
            edge =>
                edge.id === editedEdgeId ||
                labelHighlight.isHeld(labelsOf(edge.data)),
        );
        if (next !== currentEdges) {
            edges = next;
        }
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
        window.addEventListener("pagehide", saveEdgeLayoutsOnPageHide);
        window.addEventListener("keyup", edgePoints.stopDeleteBrush);
        window.addEventListener("blur", edgePoints.stopDeleteBrush);

        const unregisterAddBendPoint = shortcutStore.register(
            "diagram-add-bend-point-at-cursor",
            ["ctrl", "q"],
            edgePoints.addBendPointAtCursor,
        );
        const unregisterDeleteEdgePoint = shortcutStore.register(
            "diagram-delete-edge-point-at-cursor",
            ["ctrl", "shift", "q"],
            edgePoints.deleteEdgePointAtCursor,
        );

        return () => {
            el.removeEventListener("pointerdown", onContainerPointerDown, true);
            el.removeEventListener("pointermove", onContainerPointerMove, true);
            el.removeEventListener("click", onContainerClick, true);
            el.removeEventListener("contextmenu", onContainerContextMenu, true);
            window.removeEventListener("pagehide", saveEdgeLayoutsOnPageHide);
            window.removeEventListener("keyup", edgePoints.stopDeleteBrush);
            window.removeEventListener("blur", edgePoints.stopDeleteBrush);
            unregisterAddBendPoint();
            unregisterDeleteEdgePoint();
        };
    });

    onDestroy(() => {
        edgeLayouts.dispose();
        eventStack.removeEvent(selection.escapeClearSelection);
        if (selectionZFrame !== null) {
            cancelAnimationFrame(selectionZFrame);
        }
    });

    function saveEdgeLayoutsOnPageHide() {
        edgeLayouts.saveAll({ keepalive: true });
    }

    function onContainerPointerDown(event) {
        selection.notifyPointerDown();
        pan.handleContainerPointerDown(event);
    }

    function onContainerPointerMove(event) {
        edgePoints.handlePointerMove(event);
    }

    function onContainerClick(event) {
        pan.handleContainerClickCapture(event);
    }

    function onContainerContextMenu(event) {
        pan.handleContainerContextMenuCapture(event);
        if (event.defaultPrevented) {
            return;
        }
        edgePoints.routeEdgePointContextMenu(event);
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
        untrack(() => fitViewIncludingEdgePoints({ duration: 0 }));
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
        const diagramId = editorState.selectedDiagram.getProperty("id");
        if (diagramId !== classBoundsDiagramId) {
            classBoundsDiagramId = diagramId;
            classBounds = new Map();
        }
        labelPositions = new SvelteMap();
        labelPlacementCache = new Map();
        const nextNodes = [...inputNodes];
        const nextHasDefaultLayout = hasDefaultNodeLayout(nextNodes);

        nodeOrderCtrl.sync(nextNodes);
        nodes = nodeOrderCtrl.applyZIndices(nextNodes);
        selectionZKey = selectionContentKey(selectedNodeIdSet());
        const nextEdges = decorateEdges(inputEdges);
        edgeLayouts.load(nextEdges);
        edges = nextEdges;
        dockingPointsCheckPending = true;
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

    /**
     * Keeps the docking points on the border of classes whose size changed, e.g. a class that shrank
     * after its attributes were deleted. The bounds of each class are remembered per diagram, also
     * across a reload of the diagram, so a class that is rendered smaller than before is noticed.
     * The moved docking points are not saved on their own, see
     * {@link EdgeLayoutPersistence#acceptWithoutSaving}.
     */
    function moveDockingPointsOfResizedClasses(currentNodes) {
        const resizedClasses = new Map();
        for (const node of currentNodes) {
            if (node.type === LABEL_NODE_TYPE || !node.measured?.width) {
                continue;
            }
            const bounds = {
                x: node.position.x,
                y: node.position.y,
                width: node.measured.width,
                height: node.measured.height,
            };
            const previous = classBounds.get(node.id);
            if (
                previous &&
                (previous.width !== bounds.width ||
                    previous.height !== bounds.height)
            ) {
                resizedClasses.set(node.id, { previous, bounds });
            }
            classBounds.set(node.id, bounds);
        }
        if (resizedClasses.size === 0) {
            return;
        }
        edges = updateDockingPoints(edges, (point, classId) => {
            const resized = resizedClasses.get(classId);
            if (!resized) {
                return point;
            }
            return {
                ...point,
                ...moveDockingPointWithBorder(
                    point,
                    resized.previous,
                    resized.bounds,
                ),
            };
        });
        edgeLayouts.acceptWithoutSaving(edges);
    }

    /**
     * Puts stored docking points that are off the border of their class back onto it, once per
     * loaded diagram as soon as its classes are measured. A docking point comes off the border when
     * its class changed while the edge was not shown, e.g. the class grew while a filter hid the
     * edge, or when the layout data was changed outside of this diagram. The redocked docking
     * points are not saved on their own, see {@link EdgeLayoutPersistence#acceptWithoutSaving}.
     */
    function redockDetachedDockingPoints(currentNodes) {
        if (!dockingPointsCheckPending) {
            return;
        }
        const classRects = new Map();
        for (const node of currentNodes) {
            if (node.type === LABEL_NODE_TYPE) {
                continue;
            }
            if (!node.measured?.width) {
                return;
            }
            classRects.set(node.id, {
                x: node.position.x,
                y: node.position.y,
                width: node.measured.width,
                height: node.measured.height,
            });
        }
        dockingPointsCheckPending = false;
        edges = updateDockingPoints(edges, (point, classId) => {
            const rect = classRects.get(classId);
            if (!rect) {
                return point;
            }
            const docked = projectPointOntoRectBorder(rect, point);
            const distance = Math.hypot(docked.x - point.x, docked.y - point.y);
            if (
                distance <=
                EDGE_INTERACTION_CONFIG.dockingPointBorderTolerancePx
            ) {
                return point;
            }
            return { ...point, ...docked };
        });
        edgeLayouts.acceptWithoutSaving(edges);
    }

    function handleNodeDragStart({ nodes: draggedNodes }) {
        edgeLayouts.beginNodeDrag();
        lastDragPositions.clear();
        for (const node of draggedNodes) {
            lastDragPositions.set(node.id, {
                x: node.position.x,
                y: node.position.y,
            });
        }
    }

    function handleNodeDrag({ nodes: draggedNodes }) {
        edges = moveEdgePointsWithDraggedClasses(
            edges,
            draggedNodes,
            lastDragPositions,
        );
        for (const node of draggedNodes) {
            lastDragPositions.set(node.id, {
                x: node.position.x,
                y: node.position.y,
            });
        }
    }

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

    /**
     * Drops the manual placement of every label, so they return to their default placement.
     *
     * @returns the label positions to save for the labels that were placed manually
     */
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
        return resetLabels;
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
        edgeLayouts.saveDiagramLayout({
            classes: toClassPositionDTOs(movedClasses),
            labels: manuallyPlacedLabelsOf(movedClasses),
        });
    }

    /**
     * The positions of the manually placed labels of the edges of the moved classes, which move
     * along with their anchor.
     */
    function manuallyPlacedLabelsOf(movedClassNodes) {
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
        return affectedLabels;
    }

    function toClassPositionDTOs(classNodesToSave) {
        return classNodesToSave.map(node => ({
            classUUID: node.id,
            xPosition: node.position.x,
            yPosition: node.position.y,
            zPosition: nodeOrderCtrl.rankOf(node.id),
        }));
    }
    function selectOnlyEdge(edgeId) {
        edges = edges.map(edge => ({
            ...edge,
            selected: edge.id === edgeId,
        }));
    }
    /**
     * Selects only the clicked edge on a plain left click. Independent of the class-based auto
     * selection: clicking an edge always selects just that edge.
     */
    function handleEdgeClick({ edge, event }) {
        if (!edge?.id) return;
        selectOnlyEdge(edge.id);
        if (event?.detail === 2) {
            openEdgeEditor(edge);
        }
    }

    /**
     * Opens what an edge stands for: the association editor with the association end focused, or
     * the class editor of the sub class with its super class focused.
     */
    function openEdgeEditor(edge) {
        if (edge?.type === "inheritance") {
            superClassFocusRequest.open(edge.source);
            openClassFromMenu(edge.source);
            return;
        }
        const target = associationEditTarget(edge);
        if (target) {
            propertyEditorRequest.open(target);
        }
    }

    function handleEditEdge({ edgeId }) {
        openEdgeEditor(edges.find(edge => edge.id === edgeId));
    }

    /**
     * Lays out the classes and saves the result. A layout that finishes after another diagram was
     * loaded is dropped, otherwise it would be saved into that diagram.
     */
    export async function applyELKLayout() {
        if (!isLoading) isLoading = true;
        layouted = true;
        const laidOutInput = inputNodes;
        try {
            const [{ nodes: layoutedNodes, layoutedEdges }] = await Promise.all(
                [layoutDiagram(classNodes, edges), refreshReadOnlyState()],
            );
            if (inputNodes !== laidOutInput) {
                return;
            }
            nodes = [...layoutedNodes];
            edges = applyLayoutedEdges(
                edges,
                layoutedEdges,
                EDGE_INTERACTION_CONFIG.collinearBendPointTolerancePx,
            );
            edgeLayouts.saveDiagramLayout({
                classes: toClassPositionDTOs(classNodes),
                labels: resetLabelPositions(),
                allEdges: true,
            });
            syncLabelNodes(nodes, edges);
            await tick();
            await fitViewIncludingEdgePoints();
        } catch (error) {
            // The diagram keeps the positions it has; leaving the spinner up would only look
            // like a layout that never finishes.
            console.error("Laying out the diagram failed:", error);
            toastStore.error(
                "Layout failed",
                "The diagram could not be laid out automatically.",
            );
        } finally {
            if (inputNodes === laidOutInput) {
                isLoading = false;
            }
        }
    }

    /**
     * Fits the view to the diagram including the edge points of its edges, which may lie outside of
     * the classes.
     */
    export async function fitViewIncludingEdgePoints({ duration = 400 } = {}) {
        const bounds = getDiagramBounds(nodes, edges, id =>
            svelteFlowAPI.svelteFlow.getInternalNode(id),
        );

        if (!bounds) {
            await svelteFlowAPI.svelteFlow.fitView({ duration });
            return;
        }

        return svelteFlowAPI.svelteFlow.fitBounds(bounds, {
            padding: 0.1,
            duration,
        });
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
            } else {
                handleNodeDrag(e);
            }
        }}
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
        disabled={!contextMenus.edgeRequest}
        readOnly={isWorkspaceReadOnly}
        edgeKind={contextMenuEdge?.type ?? null}
        canEdit={isEditableEdge(contextMenuEdge)}
        onClose={() => contextMenus.close()}
        onEditEdge={handleEditEdge}
        onAddBendPoint={edgePoints.handleEdgeAddBendPoint}
        onDeleteEdgePoint={edgePoints.handleEdgeDeleteEdgePoint}
        onRemoveBendPoints={edgePoints.handleEdgeRemoveBendPoints}
        onRemoveDockingPoints={edgePoints.handleEdgeRemoveDockingPoints}
        onRemoveAllEdgePoints={edgePoints.handleEdgeRemoveAllEdgePoints}
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
