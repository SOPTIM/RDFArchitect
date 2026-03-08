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
    import { onMount, untrack } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import AssociationEdge from "./components/AssociationEdge.svelte";
    import ClassNode from "./components/ClassNode.svelte";
    import EdgeMarkers from "./components/EdgeMarkers.svelte";
    import InheritanceEdge from "./components/InheritanceEdge.svelte";
    import {
        assignDeterministicNodeZIndices,
        assignEdgeZIndices,
        bringNodeToFront as promoteNodeToFront,
        initializeNextFrontZIndex,
    } from "./layering.ts";

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
    let nextFrontZIndex = $state(1);
    let isDatasetReadOnly = $state();

    let nodesInit = useNodesInitialized();
    let layouted = $state(false);
    let hasDefaultLayout = $derived(
        nodes.every(node => node.position.x === 0 && node.position.y === 0),
    );
    let applyLayout = $derived(
        nodesInit.current && !layouted && hasDefaultLayout,
    );

    $effect(() => {
        const preprocessedEdges = inputEdges.map(edge => {
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

        let layeredNodes = assignDeterministicNodeZIndices(inputNodes);
        let currentNextFrontZIndex = initializeNextFrontZIndex(layeredNodes);
        const selectedClassUUID = editorState.selectedClassUUID.getValue();

        if (selectedClassUUID) {
            const promotedSelection = promoteNodeToFront(
                layeredNodes,
                selectedClassUUID,
                currentNextFrontZIndex,
            );
            layeredNodes = promotedSelection.nodes;
            currentNextFrontZIndex = promotedSelection.nextFrontZIndex;
        }

        nodes = layeredNodes;
        edges = assignEdgeZIndices(preprocessedEdges);
        nextFrontZIndex = currentNextFrontZIndex;
        layouted = false;
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
        editorState.selectedClassUUID.subscribe();
        const selectedClassUUID = editorState.selectedClassUUID.getValue();
        if (!selectedClassUUID) return;

        // Avoid tracking node/z-index state in this effect to prevent self-triggering loops.
        untrack(() => bringNodeToFront(selectedClassUUID));
    });

    onMount(() => {
        svelteFlowAPI = {
            svelteFlow: useSvelteFlow(),
            nodes: useNodes(),
        };
    });

    async function isReadOnly(datasetName) {
        const res = await bec.isReadOnly(datasetName);
        return await res.json();
    }

    function bringNodeToFront(nodeId) {
        const promotedNode = promoteNodeToFront(nodes, nodeId, nextFrontZIndex);
        if (!promotedNode.changed) return;
        nodes = promotedNode.nodes;
        nextFrontZIndex = promotedNode.nextFrontZIndex;
    }

    function handleNodeClick(nodeClickEvent) {
        if (nodeClickEvent.node.type === "class") {
            const id = nodeClickEvent.node.id;
            bringNodeToFront(id);
            console.log("selecting class: ", id);
            editorState.selectedClassUUID.updateValue(id);
            nodeClickEvent.event.stopPropagation();
        }
    }

    function handleNodeDragStart(nodeDragEvent) {
        if (nodeDragEvent.targetNode?.id) {
            bringNodeToFront(nodeDragEvent.targetNode.id);
        }
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

<SvelteFlow
    bind:nodes
    bind:edges
    {nodeTypes}
    {edgeTypes}
    nodesDraggable={!isDatasetReadOnly}
    fitView
    elementsSelectable={false}
    nodesFocusable={false}
    zIndexMode={"manual"}
    elevateNodesOnSelect={false}
    elevateEdgesOnSelect={false}
    onnodeclick={handleNodeClick}
    onnodedragstart={handleNodeDragStart}
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
