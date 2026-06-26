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
    import ElkWorkerURL from "elkjs/lib/elk-worker.js?url";
    import ELK from "elkjs/lib/elk.bundled.js"; //keep this import! the 'elkjs' import has a bug
    import { onDestroy, onMount, tick, untrack } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import {
        ClassType,
        DiagramType,
        editorState,
        forceReloadTrigger,
        mergeSelections,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";

    import AssociationEdge from "./components/AssociationEdge.svelte";
    import ClassNode from "./components/ClassNode.svelte";
    import EdgeMarkers from "./components/EdgeMarkers.svelte";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import SvelteFlowClassContextMenu from "./components/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/SvelteFlowPaneContextMenu.svelte";

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

    const EDGE_Z_INDEX = -1;

    // Right-click is used both for panning (drag) and the context menu (click).
    // We remember where the right button went down so a context-menu event that
    // follows an actual drag (> threshold) can be suppressed as "this was a pan".
    const RIGHT_DRAG_THRESHOLD_PX = 5;

    let nodes = $state.raw([...inputNodes]);
    let edges = $state.raw([...inputEdges]);
    let isDatasetReadOnly = $state();
    let paneContextMenuRequest = $state(null);
    let classContextMenuRequest = $state(null);
    let contextMenuClass = $state(null);
    // When a box selection is started with Shift held on the pane, it extends the
    // existing selection instead of replacing it. boxPriorSelection snapshots the
    // selection at drag start so the boxed classes can be merged into it.
    let boxAdditive = false;
    let boxPriorSelection = [];

    // Ordered list of node IDs from back (index 0) to front (index n-1).
    // Each node's zIndex equals its position in this array.
    let nodeOrder = $state([]);

    // Temporarily elevated node (not persisted) — shown in front during click/drag/search
    let temporaryFrontNodeId = $state(null);

    // Whether Control is currently held - the pan modifier. Used to show the grab
    // cursor on the pane immediately, before the user starts dragging.
    let ctrlHeld = $state(false);
    // Whether Shift is currently held. While held, nodes are not draggable so a
    // Shift+drag that starts on a node draws the (additive) selection box instead
    // of moving the node.
    let shiftHeld = $state(false);
    let panningActive = $state(false);
    let containerEl;
    // Active Ctrl+left pan session: d3-zoom ignores ctrl-modified mouse events,
    // so panning with Ctrl is driven manually via setViewport.
    let manualPan = null;
    // Suppresses the click that follows a Ctrl+pan so SvelteFlow's pane click
    // handler does not clear the selection.
    let suppressClick = false;
    // Suppresses the contextmenu that follows a right-drag pan so no menu opens.
    let suppressContextMenu = false;

    // Tracks the diagram a selection belongs to, so switching diagrams clears the
    // multi-selection (its class ids would otherwise refer to the previous diagram).
    let lastSelectedDiagramId = null;

    let nodesInit = useNodesInitialized();
    let layouted = $state(false);
    let hasDefaultLayout = $derived(hasDefaultNodeLayout(nodes));
    let applyLayout = $derived(
        nodesInit.current && !layouted && hasDefaultLayout,
    );

    $effect(() => {
        // Track only the inputs
        if (!inputNodes || !inputEdges) {
            return;
        }
        // Don't track the internal state writes
        untrack(() => {
            syncDiagramElements();
        });
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        if (applyLayout) {
            await applyELKLayout();
        } else if (!hasDefaultLayout) {
            isLoading = false;
        }
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        editorState.selectedDataset.subscribe();
        const dataset = editorState.selectedDataset.getValue();
        isDatasetReadOnly = dataset ? await isReadOnly(dataset) : false;
    });

    $effect(() => {
        editorState.focusedClassUUID.subscribe();
        focusRequestedClassInDiagram();
    });

    $effect(() => {
        editorState.selectedClass.subscribe();
        const selectedUUID = editorState.selectedClass.getProperty("id");
        untrack(() => {
            if (!selectedUUID) {
                resetTemporaryFront();
            }
        });
    });

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        const diagramId = editorState.selectedDiagram.getProperty("id");
        untrack(() => {
            if (diagramId !== lastSelectedDiagramId) {
                lastSelectedDiagramId = diagramId;
                boxAdditive = false;
                multiSelectState.clear();
            }
        });
    });

    // Keep the Escape handler on top of the eventStack while a selection exists,
    // so a single Escape clears it (and then chains to the class editor's close).
    // We also depend on selectedClass: opening or switching the open class
    // destroys and remounts the keyed ClassEditor, which pushes a fresh
    // closeClassEditor onto the stack. That mount happens in the same flush as
    // this effect, so we re-assert on top again after tick() (i.e. once the
    // remount and its onMount have run) - otherwise the first Escape would hit
    // closeClassEditor and only close the editor instead of clearing everything.
    $effect(() => {
        multiSelectState.subscribe();
        editorState.selectedClass.subscribe();
        const hasSelection = multiSelectState.getSelected().length > 0;
        untrack(() => {
            eventStack.removeEvent(escapeClearSelection);
            if (!hasSelection) {
                return;
            }
            eventStack.addEvent(escapeClearSelection);
            tick().then(() => {
                if (multiSelectState.getSelected().length === 0) {
                    return;
                }
                eventStack.removeEvent(escapeClearSelection);
                eventStack.addEvent(escapeClearSelection);
            });
        });
    });

    onMount(() => {
        svelteFlowAPI = {
            svelteFlow: useSvelteFlow(),
            nodes: useNodes(),
        };
    });

    onDestroy(() => {
        eventStack.removeEvent(escapeClearSelection);
    });

    function hasDefaultNodeLayout(diagramNodes) {
        return (
            diagramNodes.length > 0 &&
            diagramNodes.every(
                node => node.position.x === 0 && node.position.y === 0,
            )
        );
    }

    /**
     * Synchronizes nodeOrder with the current set of nodes.
     *
     * - On first load, initializes the order from the backend's persisted
     *   position.z so the saved stacking order is restored.
     * - On subsequent updates, removes nodes that no longer exist and appends
     *   newly added nodes at the end so they appear in front (highest zIndex).
     */
    function syncNodeOrder(nextNodes) {
        const nextIds = new Set(nextNodes.map(n => n.id));

        if (nodeOrder.length === 0) {
            // Initial load: sort by persisted z-position
            nodeOrder = [...nextNodes]
                .sort((a, b) => (a.position?.z ?? 0) - (b.position?.z ?? 0))
                .map(n => n.id);
            return;
        }

        // Drop nodes that no longer exist from the order
        const existingOrder = nodeOrder.filter(id => nextIds.has(id));

        // Append new nodes at the end -> they appear in front (highest zIndex)
        const knownIds = new Set(existingOrder);
        const newIds = nextNodes
            .filter(n => !knownIds.has(n.id))
            .map(n => n.id);

        nodeOrder = [...existingOrder, ...newIds];
    }

    function applyZIndicesFromOrder(diagramNodes) {
        const zIndexLookup = new Map();
        for (let i = 0; i < nodeOrder.length; i++) {
            zIndexLookup.set(nodeOrder[i], i);
        }
        // Temporary front node gets a zIndex above all other nodes
        const tempFrontZ = nodeOrder.length + 1;
        return diagramNodes.map(node => ({
            ...node,
            zIndex:
                node.id === temporaryFrontNodeId
                    ? tempFrontZ
                    : (zIndexLookup.get(node.id) ?? 0),
        }));
    }

    function bringToFrontTemporarily(nodeId) {
        if (temporaryFrontNodeId === nodeId) return;
        temporaryFrontNodeId = nodeId;
        nodes = applyZIndicesFromOrder(nodes);
    }

    function resetTemporaryFront() {
        if (temporaryFrontNodeId === null) return;
        temporaryFrontNodeId = null;
        nodes = applyZIndicesFromOrder(nodes);
    }

    function syncDiagramElements() {
        const nextNodes = [...inputNodes];
        const nextHasDefaultLayout = hasDefaultNodeLayout(nextNodes);

        syncNodeOrder(nextNodes);
        nodes = applyZIndicesFromOrder(nextNodes);
        edges = buildDiagramEdges();
        resetDiagramSyncState(nextHasDefaultLayout);
    }

    function buildDiagramEdges() {
        return inputEdges.map(decorateEdgeForDiagram);
    }

    function decorateEdgeForDiagram(edge) {
        const decorated = { ...edge, zIndex: EDGE_Z_INDEX };

        if (!shouldOffsetInheritanceEdge(edge)) {
            return decorated;
        }

        return {
            ...decorated,
            data: {
                ...(edge.data || {}),
                offsetEdge: true,
            },
        };
    }

    function shouldOffsetInheritanceEdge(edge) {
        return (
            edge.type === "inheritance" &&
            inputEdges.some(otherEdge =>
                isAssociationEdgeBetweenSameNodes(edge, otherEdge),
            )
        );
    }

    function isAssociationEdgeBetweenSameNodes(edge, otherEdge) {
        if (otherEdge.type !== "association") {
            return false;
        }

        const sameDirection =
            otherEdge.source === edge.source &&
            otherEdge.target === edge.target;
        const reverseDirection =
            otherEdge.source === edge.target &&
            otherEdge.target === edge.source;

        return sameDirection || reverseDirection;
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
            bringToFrontTemporarily(focusNode.id);
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

    /**
     * Maps a SvelteFlow class node to a multiSelectState entry. All nodes in a
     * diagram share the currently selected dataset/graph, so the selection can
     * never span multiple graphs here.
     */
    function buildDiagramSelectionEntry(node) {
        return {
            datasetName: editorState.selectedDataset.getValue(),
            graphUri: node.data?.graphUri,
            classUuid: node.id,
            classLabel: node.data?.label ?? node.id,
            packageId: editorState.selectedDiagram.getProperty("id"),
            classNavEntry: { id: node.id, label: node.data?.label ?? node.id },
        };
    }

    /**
     * Mirrors SvelteFlow's own node selection into the shared multiSelectState.
     * A Shift+box drag (boxAdditive) extends the prior selection instead of
     * replacing it. setSelection only writes on an actual change - otherwise the
     * new array reference would retrigger a render that re-emits
     * onselectionchange, creating an infinite update loop.
     */
    function handleSelectionChange({ nodes: selectedNodes }) {
        const boxed = (selectedNodes ?? [])
            .filter(node => node.type === "class")
            .map(buildDiagramSelectionEntry);
        const entries = boxAdditive
            ? mergeSelections(boxPriorSelection, boxed)
            : boxed;
        untrack(() => multiSelectState.setSelection(entries));
    }

    /** Re-evaluates each node's `selected` flag via the predicate, writing once. */
    function applyNodeSelection(shouldSelect) {
        let changed = false;
        const next = nodes.map(node => {
            const selected = shouldSelect(node);
            if (!!node.selected !== selected) {
                changed = true;
                return { ...node, selected };
            }
            return node;
        });
        if (changed) {
            nodes = next;
        }
    }

    /** Sets node.selected to match a single id (used to force-deselect a node). */
    function setNodeSelected(nodeId, selected) {
        applyNodeSelection(node =>
            node.id === nodeId ? selected : !!node.selected,
        );
    }

    /**
     * Pushes the multiSelectState membership onto the diagram nodes' `selected`
     * flag so group dragging moves every selected class - SvelteFlow clears its
     * own selection at the start of a box drag, which would otherwise drop the
     * classes that were only added via Shift+box.
     */
    function reflectSelectionToNodes() {
        const selectedIds = new Set(
            multiSelectState.getSelected().map(entry => entry.classUuid),
        );
        applyNodeSelection(node => selectedIds.has(node.id));
    }

    function handleSelectionEnd() {
        const wasAdditive = boxAdditive;
        boxAdditive = false;
        reflectSelectionToNodes();
        // A replacing (non-additive) box selection drops the open class from the
        // editor unless it is part of the new selection.
        if (!wasAdditive) {
            closeEditorIfClassDeselected();
        }
    }

    /**
     * Closes the class editor when the currently open class is no longer part of
     * the multi-selection. Routed through the eventStack so the editor's
     * unsaved-changes guard still applies.
     */
    function closeEditorIfClassDeselected() {
        const openUuid = editorState.selectedClass.getProperty("id");
        if (!openUuid) {
            return;
        }
        const stillSelected = multiSelectState
            .getSelected()
            .some(entry => entry.classUuid === openUuid);
        if (stillSelected) {
            return;
        }
        eventStack.executeNewestEvent({
            datasetName: editorState.selectedClassDataset.getValue(),
            graphUri: editorState.selectedClassGraph.getValue(),
            classUuid: null,
        });
    }

    /**
     * Routes an open/switch/close of the class editor through the eventStack so
     * the editor's unsaved-changes guard applies. classUuid=null closes it.
     */
    function routeClassEditor(graphUri, classUuid, classType) {
        eventStack.executeNewestEvent({
            datasetName: editorState.selectedDataset.getValue(),
            graphUri,
            classUuid,
            classType,
        });
    }

    function handleNodeClick(nodeClickEvent) {
        closeContextMenus();
        if (nodeClickEvent.node.type !== "class") {
            return;
        }
        const id = nodeClickEvent.node.id;
        const event = nodeClickEvent.event;
        const graphUri = nodeClickEvent.node.data.graphUri;

        // Ctrl is the pan modifier - it must not open or select a class.
        if (event?.ctrlKey || event?.metaKey) {
            return;
        }

        const classType =
            editorState.selectedDiagram.getProperty("type") ===
            DiagramType.CROSS_PROFILE
                ? ClassType.MERGED_CLASS
                : ClassType.SINGLE_CLASS;

        // Shift toggles the class in the selection (handled by SvelteFlow). If
        // the toggled class is the one currently open in the editor, also remove
        // it from the selection and close the editor.
        if (event?.shiftKey) {
            if (editorState.selectedClass.getProperty("id") === id) {
                routeClassEditor(graphUri, null, classType);
                queueMicrotask(() => setNodeSelected(id, false));
            }
            return;
        }

        // A plain click reduces the selection to just this class. SvelteFlow
        // keeps the multi-selection when an already-selected node is clicked
        // (so the group stays draggable), so we collapse it explicitly here.
        multiSelectState.setSelection([
            buildDiagramSelectionEntry(nodeClickEvent.node),
        ]);
        reflectSelectionToNodes();

        bringToFrontTemporarily(id);

        if (!editorState.selectedClass.getProperty("id")) {
            eventStack.executeNewestEvent(id);
            editorState.selectedClassDataset.updateValue(
                editorState.selectedDataset.getValue(),
            );
            editorState.selectedClassGraph.updateValue(graphUri);
            editorState.selectedClass.updateValue({
                type: classType,
                id: id,
            });
        } else {
            routeClassEditor(graphUri, id, classType);
        }

        event.stopPropagation();
    }

    function handleNodeMove(nodeMoveEvent) {
        updateNodePositions(nodeMoveEvent.nodes);
    }

    function closeContextMenus() {
        paneContextMenuRequest = null;
        classContextMenuRequest = null;
    }

    function syncModifierKeys(event) {
        ctrlHeld = event.ctrlKey;
        shiftHeld = event.shiftKey;
    }

    /**
     * Escape handler registered on the shared eventStack (the same stack the
     * global Escape handler in +layout.svelte drives via executeNewestEvent). It
     * is kept on top of the stack while a selection exists (see the effect below),
     * so a single Escape clears the whole diagram selection and then chains to the
     * next handler - the class editor's close - so the open class closes in the
     * same press.
     */
    function escapeClearSelection(...args) {
        // The shared eventStack's executeNewestEvent is also used to route class
        // open/switch through the unsaved-changes guard, and those calls always
        // pass arguments (a uuid or a {datasetName, graphUri, classUuid} object).
        // Escape is the only caller that passes none. So when we are invoked with
        // arguments we are not the intended handler: delegate to the entry beneath
        // us and restore ourselves on top.
        if (args.length > 0) {
            eventStack.removeEvent(escapeClearSelection);
            eventStack.executeNewestEvent(...args);
            if (multiSelectState.getSelected().length > 0) {
                eventStack.addEvent(escapeClearSelection);
            }
            return;
        }
        const hadOpenClass = !!editorState.selectedClass.getProperty("id");
        closeContextMenus();
        // Reset the additive-box state first: clearing the selection calls
        // reflectSelectionToNodes(), which makes SvelteFlow emit an (empty)
        // onselectionchange. If boxAdditive were still set, handleSelectionChange
        // would merge boxPriorSelection back in and resurrect the selection.
        boxAdditive = false;
        boxPriorSelection = [];
        multiSelectState.clear();
        reflectSelectionToNodes();
        eventStack.removeEvent(escapeClearSelection);
        if (hadOpenClass) {
            eventStack.executeNewestEvent();
        }
    }

    /**
     * Manually pans the canvas. Used for both Ctrl+left and the right mouse
     * button, because d3-zoom rejects ctrl-modified mouse events and we want
     * right-drag panning to work even when the gesture starts on a node.
     * Ctrl+left hijacks the gesture (so nothing underneath reacts); the right
     * button stays passive until it is recognised as a drag, so a plain
     * right-click still opens the context menu.
     */
    function startManualPan(event) {
        if (!svelteFlowAPI?.svelteFlow) {
            return;
        }
        const isCtrlLeft = event.button === 0;
        const viewport = svelteFlowAPI.svelteFlow.getViewport();
        manualPan = {
            startX: event.clientX,
            startY: event.clientY,
            vpX: viewport.x,
            vpY: viewport.y,
            zoom: viewport.zoom,
            pointerId: event.pointerId,
            button: event.button,
            moved: false,
        };

        if (isCtrlLeft) {
            // Take over the gesture entirely so neither node dragging nor
            // selection nor the trailing click reacts, and show the grab cursor
            // synchronously (before any drag).
            event.preventDefault();
            event.stopPropagation();
            suppressClick = true;
            panningActive = true;
            if (containerEl) {
                containerEl.style.cursor = "grabbing";
                containerEl.setPointerCapture?.(event.pointerId);
            }
        }

        window.addEventListener("pointermove", onManualPanMove);
        window.addEventListener("pointerup", endManualPan, { once: true });
    }

    function onManualPanMove(event) {
        if (!manualPan) {
            return;
        }
        const dx = event.clientX - manualPan.startX;
        const dy = event.clientY - manualPan.startY;

        if (!manualPan.moved && Math.hypot(dx, dy) > RIGHT_DRAG_THRESHOLD_PX) {
            manualPan.moved = true;
            if (manualPan.button === 2) {
                // The right button turned into a pan: show the grabbing cursor
                // and suppress the contextmenu that fires on release.
                panningActive = true;
                suppressContextMenu = true;
                if (containerEl) {
                    containerEl.style.cursor = "grabbing";
                }
            }
        }

        // Ctrl+left pans from the first pixel; the right button only once the
        // movement is recognised as a drag (so a plain click stays a click).
        if (manualPan.button === 0 || manualPan.moved) {
            svelteFlowAPI.svelteFlow.setViewport({
                x: manualPan.vpX + dx,
                y: manualPan.vpY + dy,
                zoom: manualPan.zoom,
            });
        }
    }

    function endManualPan() {
        if (containerEl) {
            containerEl.style.cursor = "";
            if (manualPan?.button === 0 && manualPan?.pointerId !== undefined) {
                containerEl.releasePointerCapture?.(manualPan.pointerId);
            }
        }
        manualPan = null;
        panningActive = false;
        window.removeEventListener("pointermove", onManualPanMove);
    }

    function handleContainerClickCapture(event) {
        if (suppressClick) {
            // Swallow the click after a Ctrl+pan so the pane's click handler does
            // not clear the selection.
            event.stopPropagation();
            event.preventDefault();
            suppressClick = false;
        }
    }

    function handleContainerContextMenuCapture(event) {
        if (suppressContextMenu) {
            // Swallow the contextmenu that follows a right-drag pan before it
            // reaches SvelteFlow's pane/node handlers.
            event.preventDefault();
            event.stopPropagation();
            suppressContextMenu = false;
        }
    }

    function handleContainerPointerDown(event) {
        suppressClick = false;
        suppressContextMenu = false;
        // Ctrl+left and the right mouse button pan the canvas (handled manually).
        if ((event.button === 0 && event.ctrlKey) || event.button === 2) {
            startManualPan(event);
            return;
        }
        // A Shift+drag (anywhere, including over a node) extends the current
        // selection; a Shift+click (no drag) stays a plain toggle. Additive mode
        // is only armed once the pointer actually moves - otherwise a toggle-off
        // would be merged straight back in from boxPriorSelection.
        boxAdditive = false;
        if (event.button === 0 && event.shiftKey) {
            boxPriorSelection = [...multiSelectState.getSelected()];
            armAdditiveBoxOnDrag(event);
        }
    }

    /**
     * Switches the upcoming box selection to additive (extend) mode as soon as
     * the pointer moves, so a stationary Shift+click stays a toggle. The capture
     * listener runs before SvelteFlow's pane handler, so boxAdditive is already
     * set when the box-start selection change arrives.
     */
    function armAdditiveBoxOnDrag(event) {
        const { clientX: startX, clientY: startY } = event;
        const onMove = moveEvent => {
            if (moveEvent.clientX !== startX || moveEvent.clientY !== startY) {
                boxAdditive = true;
                stop();
            }
        };
        const stop = () => {
            window.removeEventListener("pointermove", onMove, true);
            window.removeEventListener("pointerup", stop, true);
        };
        window.addEventListener("pointermove", onMove, true);
        window.addEventListener("pointerup", stop, true);
    }

    /** Cancels the native context menu and closes any open custom menu. */
    function consumeContextMenuEvent(event) {
        event.preventDefault();
        event.stopPropagation();
        closeContextMenus();
    }

    function handlePaneContextMenu({ event }) {
        consumeContextMenuEvent(event);
        if (
            event.target instanceof Element &&
            event.target.closest(".svelte-flow__node")
        ) {
            return;
        }
        if (isDatasetReadOnly) {
            return;
        }

        contextMenuClass = null;
        if (!svelteFlowAPI?.svelteFlow) {
            paneContextMenuRequest = {
                x: event.clientX,
                y: event.clientY,
                flowPosition: { x: 0, y: 0 },
            };
            return;
        }

        const flowPosition = svelteFlowAPI.svelteFlow.screenToFlowPosition(
            {
                x: event.clientX,
                y: event.clientY,
            },
            { snapToGrid: false },
        );
        paneContextMenuRequest = {
            x: event.clientX,
            y: event.clientY,
            flowPosition,
        };
    }

    function handleEdgeContextMenu({ event }) {
        consumeContextMenuEvent(event);
    }

    function handleNodeContextMenu({ event, node }) {
        consumeContextMenuEvent(event);
        // Explorer behaviour: right-clicking a node that is not part of the
        // current multi-selection reduces the selection to that single node so
        // the menu matches what the user sees.
        if (
            !multiSelectState.isSelected(
                editorState.selectedDataset.getValue(),
                node.data?.graphUri,
                node.id,
            )
        ) {
            multiSelectState.setSelection([buildDiagramSelectionEntry(node)]);
        }
        contextMenuClass = {
            uuid: node.id,
            label: node.data?.label ?? node.id,
        };
        classContextMenuRequest = {
            x: event.clientX,
            y: event.clientY,
        };
    }

    function handleMoveClass({ classUuid, direction }) {
        const idx = nodeOrder.indexOf(classUuid);
        if (idx === -1) return;

        const next = [...nodeOrder];
        let changedIds;

        if (direction === "up") {
            // Swap with the one above (higher zIndex)
            if (idx >= next.length - 1) return;
            [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]];
            changedIds = [next[idx], next[idx + 1]];
        } else if (direction === "down") {
            // Swap with the one below (lower zIndex)
            if (idx <= 0) return;
            [next[idx], next[idx - 1]] = [next[idx - 1], next[idx]];
            changedIds = [next[idx], next[idx - 1]];
        } else if (direction === "top") {
            // Move to front: remove and append at end
            if (idx >= next.length - 1) return;
            const [removed] = next.splice(idx, 1);
            next.push(removed);
            // All indices from idx onward shifted
            changedIds = next.slice(idx);
        } else if (direction === "bottom") {
            // Move to back: remove and prepend at start
            if (idx <= 0) return;
            const [removed] = next.splice(idx, 1);
            next.unshift(removed);
            // All indices up to and including original idx shifted
            changedIds = next.slice(0, idx + 1);
        }
        nodeOrder = next;
        nodes = applyZIndicesFromOrder(nodes);

        persistNodeOrder(nodeOrder, changedIds);
    }

    function persistNodeOrder(order, changedIds) {
        const classPositionDTOList = changedIds.map(id => {
            const node = nodes.find(n => n.id === id);
            return {
                classUUID: id,
                xPosition: node?.position.x ?? 0,
                yPosition: node?.position.y ?? 0,
                zPosition: order.indexOf(id),
            };
        });

        if (editorState.selectedGraph.getValue()) {
            bec.updateClassPositions(
                editorState.selectedDataset.getValue(),
                editorState.selectedGraph.getValue(),
                editorState.selectedDiagram.getProperty("id"),
                classPositionDTOList,
            );
        } else {
            bec.updateGlobalClassPositions(
                editorState.selectedDataset.getValue(),
                editorState.selectedDiagram.getProperty("id"),
                classPositionDTOList,
            );
        }
    }

    function moveToLayer(classUuid, layer) {
        const currentIdx = nodeOrder.indexOf(classUuid);
        if (currentIdx === -1 || currentIdx === layer) return;

        const next = [...nodeOrder];
        const [removed] = next.splice(currentIdx, 1);
        next.splice(layer, 0, removed);

        nodeOrder = next;
        nodes = applyZIndicesFromOrder(nodes);
    }

    function handleSetLayer({ classUuid, layer }) {
        moveToLayer(classUuid, layer);
    }

    function handlePersistLayer({ classUuid }) {
        // nodeOrder is already updated by handleSetLayer
        const currentIdx = nodeOrder.indexOf(classUuid);
        if (currentIdx === -1) return;

        // Persist all nodes since we don't know intermediate states
        persistNodeOrder(nodeOrder, [...nodeOrder]);
    }

    function updateNodePositions(movedNodes) {
        let classPositionDTOList = [];
        for (const node of movedNodes) {
            const classPositionDTO = {
                classUUID: node.id,
                xPosition: node.position.x,
                yPosition: node.position.y,
                zPosition: node.zIndex ?? nodeOrder.indexOf(node.id),
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

    async function getLayoutedNodes(nodes, edges) {
        const elk = new ELK({
            //WEB WORKER: layouting is executed in a separate thread, preventing the frontend from blocking
            workerFactory: () => new Worker(ElkWorkerURL, { type: "classic" }),
        });
        const graph = {
            id: "root",
            children: nodes.map(node => ({
                id: node.id,
                width: node.measured.width,
                height: node.measured.height,
            })),
            edges: edges.map(edge => ({
                id: edge.id,
                source: edge.source,
                target: edge.target,
            })),
            layoutOptions: {
                //BASE
                "elk.algorithm": "layered",
                "elk.aspectRatio": "1.78f", //1.6f = 16:10, 1.78f = 16:9, which is more common for monitors
                "elk.edge.thickness": "2.0", //matches the 2px width of SvelteFlow edges
                "elk.direction": "RIGHT", //horizontal as it suits monitor layouts, right because the ClassEditor is more likely to be closed than the PackageNav
                "elk.layered.thoroughness": "150",
                "elk.edgeRouting": "POLYLINE",
                "elk.layered.slopedEdgeZoneWidth": "0.0",
                "elk.separateConnectedComponents": "false",
                "elk.layered.mergeHierarchyEdges": "false",

                //NODE PLACEMENT
                "elk.layered.nodePlacement.strategy": "NETWORK_SIMPLEX",
                "elk.layered.nodePlacement.favorStraightEdges": "false",

                //CROSSING MINIMIZATION
                "elk.layered.crossingMinimization.greedySwitchType":
                    "TWO_SIDED",
                "elk.layered.greedySwitch.activationThreshold": "40",

                //NODE PROMOTION
                "elk.layered.layering.nodePromotion.strategy":
                    "NIKOLOV_IMPROVED",
                "elk.layered.layering.nodePromotion.maxIterations": "20",

                //NODE LAYERING
                "elk.layered.layering.strategy": "STRETCH_WIDTH",

                //HIGH DEGREE NODES
                "elk.layered.highDegreeNodes.treatment": "true",
                "elk.layered.highDegreeNodes.threshold": "10",
                "elk.layered.highDegreeNodes.treeHeight": "5",

                //SPACING
                "elk.layered.spacing.edgeEdgeBetweenLayers": "20",
                "elk.layered.spacing.edgeNodeBetweenLayers": "40",
                "elk.spacing.edgeNode": "30",
                "elk.spacing.edgeEdge": "15",
                "elk.layered.spacing.nodeNodeBetweenLayers": "80",
                "elk.spacing.nodeNode": "60",
            },
        };

        const elkGraph = await elk.layout(graph);

        return nodes.map(node => {
            const elkNode = elkGraph.children.find(n => n.id === node.id);

            if (elkNode) {
                return {
                    ...node,
                    position: {
                        x: elkNode.x,
                        y: elkNode.y,
                    },
                };
            }
            return node;
        });
    }

    export async function applyELKLayout() {
        if (!isLoading) isLoading = true;
        layouted = true;
        const layoutedNodes = await getLayoutedNodes(nodes, edges);
        nodes = [...layoutedNodes];
        await updateNodePositions(nodes);
        await svelteFlowAPI.svelteFlow.fitView();
        isLoading = false;
    }
</script>

<svelte:window
    onkeydown={syncModifierKeys}
    onkeyup={syncModifierKeys}
    onblur={() => {
        ctrlHeld = false;
        shiftHeld = false;
    }}
/>

<div
    bind:this={containerEl}
    class={`relative h-full w-full ${ctrlHeld ? "ctrl-pan" : ""} ${panningActive ? "ctrl-panning" : ""}`}
    onpointerdowncapture={handleContainerPointerDown}
    onclickcapture={handleContainerClickCapture}
    oncontextmenucapture={handleContainerContextMenuCapture}
>
    <SvelteFlow
        bind:nodes
        bind:edges
        {nodeTypes}
        {edgeTypes}
        nodesDraggable={!isDatasetReadOnly && !shiftHeld}
        fitView
        elementsSelectable={!isDatasetReadOnly}
        nodesFocusable={false}
        onnodeclick={handleNodeClick}
        onnodecontextmenu={handleNodeContextMenu}
        onpaneclick={closeContextMenus}
        onpanecontextmenu={handlePaneContextMenu}
        onedgecontextmenu={handleEdgeContextMenu}
        onselectionchange={handleSelectionChange}
        onselectionend={handleSelectionEnd}
        onnodedragstart={({ node }) => bringToFrontTemporarily(node?.id)}
        onnodedragstop={handleNodeMove}
        selectionMode={"partial"}
        selectionOnDrag={true}
        panOnDrag={false}
        panActivationKey={"Control"}
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
        request={paneContextMenuRequest}
        disabled={isDatasetReadOnly}
        lockedDatasetName={editorState.selectedDataset.getValue()}
        lockedGraphUri={editorState.selectedGraph.getValue()}
        onClose={closeContextMenus}
    />
    <SvelteFlowClassContextMenu
        request={classContextMenuRequest}
        disabled={!contextMenuClass}
        readOnly={isDatasetReadOnly}
        {contextMenuClass}
        datasetName={editorState.selectedDataset.getValue()}
        graphUri={editorState.selectedGraph.getValue()}
        {nodeOrder}
        nodeCount={nodes.length}
        onClose={closeContextMenus}
        onMoveClass={handleMoveClass}
        onSetLayer={handleSetLayer}
        onPersistLayer={handlePersistLayer}
    />
</div>

<style>
    /* Hide SvelteFlow's persistent multi-selection bounding box. Selected classes
       are shown via the node highlight (multiSelectState) instead, and group
       dragging still works by dragging any selected node. The active drag
       rectangle (rendered separately as .svelte-flow__selection at the container
       level) stays visible while the box is being drawn. */
    :global(.svelte-flow__selection-wrapper) {
        display: none;
    }

    /* While Control is held the pane pans (panActivationKey="Control"), so show
       the grab cursor immediately - before the drag starts - and the grabbing
       cursor while panning. */
    .ctrl-pan :global(.svelte-flow__pane) {
        cursor: grab;
    }

    /* While actively panning (Ctrl+left or right-drag), show the grabbing cursor
       across the diagram. */
    .ctrl-panning :global(.svelte-flow__pane),
    .ctrl-panning :global(.svelte-flow__node) {
        cursor: grabbing;
    }

    /* Selection drag box: a solid, slightly thicker border instead of the dotted
       default. */
    :global(.svelte-flow__selection) {
        border: 2px solid var(--color-border-select);
        background: rgba(31, 117, 203, 0.08);
    }
</style>
