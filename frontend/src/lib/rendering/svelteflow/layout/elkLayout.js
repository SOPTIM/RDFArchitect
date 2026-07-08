/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

import ElkWorkerURL from "elkjs/lib/elk-worker.js?url";
import ELK from "elkjs/lib/elk.bundled.js"; //keep this import! the 'elkjs' import has a bug

const LAYOUT_OPTIONS = {
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
    "elk.layered.crossingMinimization.greedySwitchType": "TWO_SIDED",
    "elk.layered.greedySwitch.activationThreshold": "40",

    //NODE PROMOTION
    "elk.layered.layering.nodePromotion.strategy": "NIKOLOV_IMPROVED",
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
};

let elk = null;

function initializeELK() {
    if (typeof Worker === "undefined") {
        return null;
    }

    if (!elk) {
        elk = new ELK({
            workerFactory: () => new Worker(ElkWorkerURL, { type: "classic" }),
        });
    }
    return elk;
}

export async function getLayoutedNodes(nodes, edges) {
    const elkInstance = initializeELK();

    if (!elkInstance) {
        console.warn("ELK layout not available in this environment");
        return nodes;
    }

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
        layoutOptions: LAYOUT_OPTIONS,
    };

    const elkGraph = await elkInstance.layout(graph);

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
