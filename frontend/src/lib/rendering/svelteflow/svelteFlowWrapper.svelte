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
        useNodes,
        useNodesInitialized,
        useSvelteFlow,
    } from "@xyflow/svelte";
    import { onDestroy, onMount, tick, untrack } from "svelte";
    import { SvelteMap } from "svelte/reactivity";

    import {
        updateClassPositions,
        updateDatasetClassPositions,
        updateDatasetLabelPositions,
        updateLabelPositions,
    } from "$lib/api/generated/index.ts";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import AssociationEdge from "./components/AssociationEdge.svelte";
    import ClassNode from "./components/ClassNode.svelte";
    import DiagramLabelNode from "./components/DiagramLabelNode.svelte";
    import EdgeMarkers from "./components/EdgeMarkers.svelte";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import SvelteFlowClassContextMenu from "./components/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/SvelteFlowPaneContextMenu.svelte";
    import {
        decorateEdges,
        hasDefaultNodeLayout,
    } from "./diagram/diagramElements.js";
    import {
        buildLabelNodes,
        clampToAnchor,
        collectLabels,
        effectiveOffset,
        LABEL_NODE_TYPE,
        labelNodeId,
        offsetFromClass,
    } from "./diagram/labelNodes.js";
    import { ContextMenuController } from "./interaction/contextMenus.svelte.js";
    import { DiagramSelectionController } from "./interaction/diagramSelection.svelte.js";
    import { labelHighlight } from "./interaction/labelHighlight.svelte.js";
    import { NodeOrderController } from "./interaction/nodeOrder.svelte.js";
    import { PanController } from "./interaction/panController.svelte.js";
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

    let nodesInit = useNodesInitialized();
    let layouted = $state(false);

    let selectionZFrame = null;
    let boxSelecting = false;
    // Offsets of labels moved in this session, so a drag takes effect without refetching the
    // diagram. An entry holding null resets that label to its default placement. A SvelteMap so
    // mutating it alone is enough to re-trigger syncLabelNodes below.
    let labelOffsets = new SvelteMap();
    // Memoizes edge-intersection geometry per class pair, so dragging one class does not
    // recompute the placement of every other edge in the diagram.
    let labelPlacementCache = new Map();
    let labelDragActive = false;
    let classNodes = $derived(
        nodes.filter(node => node.type !== LABEL_NODE_TYPE),
    );
    let hasDefaultLayout = $derived(hasDefaultNodeLayout(nodes));
    let applyLayout = $derived(
        nodesInit.current && !layouted && hasDefaultLayout,
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
        untrack(keepEscapeHandlerOnTop);
    });

    onMount(() => {
        svelteFlowAPI = {
            svelteFlow: useSvelteFlow(),
            nodes: useNodes(),
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

    function onContainerPointerDown(event) {
        selection.notifyPointerDown();
        pan.handleContainerPointerDown(event);
    }

    function onContainerClick(event) {
        pan.handleContainerClickCapture(event);
    }

    function onContainerContextMenu(event) {
        pan.handleContainerContextMenuCapture(event);
    }

    function applyAutoLayoutIfNeeded() {
        if (applyLayout) {
            applyELKLayout();
        } else if (!hasDefaultLayout) {
            isLoading = false;
        }
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
        labelOffsets = new SvelteMap();
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

        // Keep the loading state active until persisted positions or ELK layout
        if (!hasDefaultLayoutAfterSync) {
            isLoading = false;
        }
    }

    function focusRequestedClassInDiagram() {
        const focusClassUUID = editorState.focusedClassUUID.getValue();
        if (!focusClassUUID || !nodesInit.current) {
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
            labelOffsets,
            labelPlacementCache,
        );
        if (!labelNodesChanged(currentNodes, nextLabelNodes)) {
            return;
        }
        nodes = [
            ...currentNodes.filter(node => node.type !== LABEL_NODE_TYPE),
            ...nextLabelNodes,
        ];
    }

    function labelNodesChanged(currentNodes, nextLabelNodes) {
        const current = currentNodes.filter(
            node => node.type === LABEL_NODE_TYPE,
        );
        if (current.length !== nextLabelNodes.length) {
            return true;
        }
        const currentById = new Map(current.map(node => [node.id, node]));
        return nextLabelNodes.some(next => {
            const node = currentById.get(next.id);
            return (
                !node ||
                node.data.text !== next.data.text ||
                node.position.x !== next.position.x ||
                node.position.y !== next.position.y ||
                node.data.anchorPoint.x !== next.data.anchorPoint.x ||
                node.data.anchorPoint.y !== next.data.anchorPoint.y
            );
        });
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

    function toLabelPositionDTO(identifiedObjectUUID, kind, offset) {
        return {
            identifiedObjectUUID,
            kind,
            xOffset: offset?.x ?? null,
            yOffset: offset?.y ?? null,
        };
    }

    function handleLabelMove(movedLabelNodes) {
        const nodesById = new Map(nodes.map(node => [node.id, node]));
        const movedLabels = [];
        for (const labelNode of movedLabelNodes) {
            const anchorClass = nodesById.get(labelNode.data.anchorClassId);
            if (!anchorClass) {
                continue;
            }
            const offset = offsetFromClass(
                {
                    position: clampToAnchor(
                        labelNode.position,
                        labelNode.data.anchorPoint,
                    ),
                },
                anchorClass,
            );
            labelOffsets.set(labelNode.id, offset);
            movedLabels.push(
                toLabelPositionDTO(
                    labelNode.data.identifiedObjectUUID,
                    labelNode.data.kind,
                    offset,
                ),
            );
        }
        persistLabelPositions(movedLabels);
    }

    /** Drops the manual placement of every label, so they return to their default placement. */
    function resetLabelPositions() {
        const resetLabels = [];
        for (const { label } of collectLabels(edges)) {
            if (!effectiveOffset(label, labelOffsets)) {
                continue;
            }
            labelOffsets.set(labelNodeId(label), null);
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

    function handleNodeMove(nodeMoveEvent) {
        const movedNodes = nodeMoveEvent.nodes ?? [];
        const movedLabels = movedNodes.filter(
            node => node.type === LABEL_NODE_TYPE,
        );
        if (movedLabels.length > 0) {
            handleLabelMove(movedLabels);
        }
        updateNodePositions(
            movedNodes.filter(node => node.type !== LABEL_NODE_TYPE),
        );
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

    export async function applyELKLayout() {
        if (!isLoading) isLoading = true;
        layouted = true;
        const layoutedNodes = await getLayoutedNodes(classNodes, edges);
        nodes = [...layoutedNodes];
        updateNodePositions(nodes);
        resetLabelPositions();
        syncLabelNodes(nodes, edges);
        await svelteFlowAPI.svelteFlow.fitView();
        isLoading = false;
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
        fitView
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
            nodeOrderCtrl.bringToFrontTemporarily(e.targetNode?.id);
        }}
        onnodedrag={e => {
            if (labelDragActive) {
                clampDraggedLabels(e.nodes ?? []);
            }
        }}
        onnodedragstop={e => {
            if (labelDragActive) {
                labelDragActive = false;
                selection.notifyNodeDragStop();
                handleNodeMove(e);
                syncLabelNodes(nodes, edges);
                return;
            }
            selection.notifyNodeDragStop();
            handleNodeMove(e);
        }}
        selectionMode={"partial"}
        selectionOnDrag={true}
        panOnDrag={false}
        selectionKey={"Shift"}
        connectionMode={"loose"}
        multiSelectionKey={"Shift"}
        deleteKeyCode={null}
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
        onMoveClass={e => nodeOrderCtrl.moveClass(e)}
        onSetLayer={e => nodeOrderCtrl.setLayer(e)}
        onPersistLayer={e => nodeOrderCtrl.persistLayer(e)}
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
