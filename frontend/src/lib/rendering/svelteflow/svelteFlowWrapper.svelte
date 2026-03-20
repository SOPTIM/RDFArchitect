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
    import { onMount } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import AssociationEdge from "./components/AssociationEdge.svelte";
    import ClassNode from "./components/ClassNode.svelte";
    import EdgeMarkers from "./components/EdgeMarkers.svelte";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import SvelteFlowClassContextMenu from "./components/SvelteFlowClassContextMenu.svelte";
    import SvelteFlowPaneContextMenu from "./components/SvelteFlowPaneContextMenu.svelte";
    import DeleteClassConfirmDialog from "../../../routes/DeleteClassConfirmDialog.svelte";
    import NewClassDialog from "../../../routes/NewClassDialog.svelte";

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

    let nodes = $state.raw([...inputNodes]);
    let edges = $state.raw([...inputEdges]);
    let isDatasetReadOnly = $state();
    let contextMenuFlowPosition = $state({ x: 0, y: 0 });
    let paneContextMenuRequest = $state(null);
    let classContextMenuRequest = $state(null);
    let contextMenuClass = $state(null);
    let deleteClassTarget = $state(null);
    let showDeleteClassDialog = $state(false);
    let showNewClassDialog = $state(false);
    let pendingNewClassPlacement = null;

    let nodesInit = useNodesInitialized();
    let layouted = $state(false);
    let hasDefaultLayout = $derived(
        nodes.length > 0 &&
            nodes.every(node => node.position.x === 0 && node.position.y === 0),
    );
    let applyLayout = $derived(
        nodesInit.current && !layouted && hasDefaultLayout,
    );

    $effect(() => {
        syncDiagramElements();
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
        const dataset = editorState.selectedDataset.getValue();
        isDatasetReadOnly = dataset ? await isReadOnly(dataset) : false;
    });

    $effect(() => {
        editorState.focusedClassUUID.subscribe();
        focusRequestedClassInDiagram();
    });

    onMount(() => {
        svelteFlowAPI = {
            svelteFlow: useSvelteFlow(),
            nodes: useNodes(),
        };
    });

    function syncDiagramElements() {
        let nextNodes = [...inputNodes];
        const nextHasDefaultLayout =
            nextNodes.length > 0 &&
            nextNodes.every(
                node => node.position.x === 0 && node.position.y === 0,
            );

        if (
            pendingNewClassPlacement &&
            editorState.selectedPackageUUID.getValue() ===
                pendingNewClassPlacement.packageUUID
        ) {
            const addedNodes = nextNodes.filter(
                node => !pendingNewClassPlacement.existingNodeIds.has(node.id),
            );
            const addedNode =
                addedNodes.find(
                    node =>
                        node.data?.label ===
                            pendingNewClassPlacement.className &&
                        node.position.x === 0 &&
                        node.position.y === 0,
                ) ??
                addedNodes.find(
                    node =>
                        node.data?.label === pendingNewClassPlacement.className,
                ) ??
                (addedNodes.length === 1 ? addedNodes[0] : null);

            if (addedNode) {
                const { x, y } = pendingNewClassPlacement.position;
                nextNodes = nextNodes.map(node =>
                    node.id === addedNode.id
                        ? {
                              ...node,
                              position: { x, y },
                          }
                        : node,
                );

                bec.updateClassPositions(
                    pendingNewClassPlacement.datasetName,
                    pendingNewClassPlacement.graphURI,
                    pendingNewClassPlacement.packageUUID,
                    [
                        {
                            classUUID: addedNode.id,
                            xPosition: x,
                            yPosition: y,
                        },
                    ],
                ).catch(error => {
                    console.error(
                        "Could not persist newly created class position:",
                        error,
                    );
                });

                pendingNewClassPlacement = null;
            }
        }

        nodes = nextNodes;
        edges = inputEdges.map(edge => {
            //applies offset to inheritance edge if an association edge already exists between the same two nodes
            if (edge.type === "inheritance") {
                const hasAssociationEdgeBetweenSameNodes = inputEdges.some(
                    otherEdge => {
                        if (otherEdge.type !== "association") return false;

                        const sameDirection =
                            otherEdge.source === edge.source &&
                            otherEdge.target === edge.target;
                        const reverseDirection =
                            otherEdge.source === edge.target &&
                            otherEdge.target === edge.source;

                        return sameDirection || reverseDirection;
                    },
                );

                if (hasAssociationEdgeBetweenSameNodes) {
                    return {
                        ...edge,
                        data: {
                            ...(edge.data || {}),
                            offsetEdge: true,
                        },
                    };
                }
            }

            return edge;
        });
        layouted = false;

        // Keep the loading state active until persisted positions or ELK layout
        if (!nextHasDefaultLayout) {
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

    function handleNodeClick(nodeClickEvent) {
        closeContextMenus();
        if (nodeClickEvent.node.type === "class") {
            const id = nodeClickEvent.node.id;
            console.log("selecting class: ", id);

            if (!editorState.selectedClassUUID.getValue()) {
                eventStack.executeNewestEvent(id);
                editorState.selectedClassDataset.updateValue(
                    editorState.selectedDataset.getValue(),
                );
                editorState.selectedClassGraph.updateValue(
                    editorState.selectedGraph.getValue(),
                );
                editorState.selectedClassUUID.updateValue(id);
            } else {
                eventStack.executeNewestEvent({
                    datasetName: editorState.selectedDataset.getValue(),
                    graphUri: editorState.selectedGraph.getValue(),
                    classUuid: id,
                });
            }

            nodeClickEvent.event.stopPropagation();
        }
    }

    function handleNodeMove(nodeMoveEvent) {
        updateNodePositions(nodeMoveEvent.nodes);
    }

    function closeContextMenus() {
        paneContextMenuRequest = null;
        classContextMenuRequest = null;
    }

    function handlePaneContextMenu({ event }) {
        event.preventDefault();
        event.stopPropagation();
        closeContextMenus();
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
            contextMenuFlowPosition = { x: 0, y: 0 };
            paneContextMenuRequest = {
                x: event.clientX,
                y: event.clientY,
            };
            return;
        }

        contextMenuFlowPosition = svelteFlowAPI.svelteFlow.screenToFlowPosition(
            {
                x: event.clientX,
                y: event.clientY,
            },
            { snapToGrid: false },
        );
        paneContextMenuRequest = {
            x: event.clientX,
            y: event.clientY,
        };
    }

    function handleEdgeContextMenu({ event }) {
        event.preventDefault();
        event.stopPropagation();
        closeContextMenus();
    }

    function handleNodeContextMenu({ event, node }) {
        event.preventDefault();
        event.stopPropagation();
        closeContextMenus();
        if (isDatasetReadOnly) {
            return;
        }
        contextMenuClass = {
            uuid: node.id,
            label: node.data?.label ?? node.id,
        };
        classContextMenuRequest = {
            x: event.clientX,
            y: event.clientY,
        };
        editorState.selectedClassUUID.updateValue(node.id);
    }

    function openNewClassDialog() {
        showNewClassDialog = true;
        closeContextMenus();
    }

    function handleClassCreated({
        datasetName,
        graphURI,
        packageUUID,
        className,
    }) {
        pendingNewClassPlacement = {
            datasetName,
            graphURI,
            packageUUID,
            className,
            existingNodeIds: new Set(nodes.map(node => node.id)),
            position: {
                x: contextMenuFlowPosition.x,
                y: contextMenuFlowPosition.y,
            },
        };
    }

    function openDeleteClassDialog() {
        if (!contextMenuClass) {
            return;
        }
        deleteClassTarget = contextMenuClass;
        showDeleteClassDialog = true;
        closeContextMenus();
    }

    function updateNodePositions(movedNodes) {
        let classPositionDTOList = [];
        for (const node of movedNodes) {
            const classPositionDTO = {
                classUUID: node.id,
                xPosition: node.position.x,
                yPosition: node.position.y,
            };
            classPositionDTOList.push(classPositionDTO);
        }

        bec.updateClassPositions(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            editorState.selectedPackageUUID.getValue(),
            classPositionDTOList,
        );
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

<div class="relative h-full w-full">
    <SvelteFlow
        bind:nodes
        bind:edges
        {nodeTypes}
        {edgeTypes}
        nodesDraggable={!isDatasetReadOnly}
        fitView
        elementsSelectable={false}
        nodesFocusable={false}
        onnodeclick={handleNodeClick}
        onnodecontextmenu={handleNodeContextMenu}
        onpaneclick={closeContextMenus}
        onpanecontextmenu={handlePaneContextMenu}
        onedgecontextmenu={handleEdgeContextMenu}
        onnodedragstop={handleNodeMove}
        selectionMode={"full"}
        connectionMode={"loose"}
        multiSelectionKey={null}
        minZoom={0.1}
        maxZoom={5}
    >
        <EdgeMarkers />
        <Background patternColor="#aaa" gap={16} />
    </SvelteFlow>

    <SvelteFlowPaneContextMenu
        request={paneContextMenuRequest}
        disabled={isDatasetReadOnly}
        onAddClass={openNewClassDialog}
        onClose={closeContextMenus}
    />
    <SvelteFlowClassContextMenu
        request={classContextMenuRequest}
        disabled={isDatasetReadOnly || !contextMenuClass}
        onDeleteClass={openDeleteClassDialog}
        onClose={closeContextMenus}
    />
</div>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    lockedDatasetName={editorState.selectedDataset.getValue()}
    lockedGraphUri={editorState.selectedGraph.getValue()}
    onClassCreated={handleClassCreated}
/>

<DeleteClassConfirmDialog
    bind:showDialog={showDeleteClassDialog}
    datasetName={editorState.selectedDataset.getValue()}
    graphUri={editorState.selectedGraph.getValue()}
    classUuid={deleteClassTarget?.uuid}
    classLabel={deleteClassTarget?.label}
/>
