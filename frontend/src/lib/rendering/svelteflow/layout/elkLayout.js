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

/**
 * Runs the ELK layout for the given SvelteFlow nodes and edges and returns the
 * layouted result. The nodes come back in SvelteFlow format with updated
 * positions. The layouted edges are returned separately as raw geometric points
 * (tagged source/bend/target) per edge id, so that the rendering layer can turn
 * them into the actual end and bend point objects. This file stays purely
 * geometric and does not deal with the frontend's point representation.
 *
 * @param {Array} nodes SvelteFlow nodes
 * @param {Array} edges SvelteFlow edges
 * @returns {Promise<{nodes: Array, layoutedEdges: Map<string, {x: number, y: number, role: "source"|"bend"|"target"}[]>}>}
 */
export async function layoutDiagram(nodes, edges) {
    const elkInstance = initializeELK();

    if (!elkInstance) {
        console.warn("ELK layout not available in this environment");
        return { nodes, layoutedEdges: new Map() };
    }

    const elkGraph = await elkInstance.layout(buildElkGraph(nodes, edges));

    return {
        nodes: nodes.map(node => toSvelteFlowNode(node, elkGraph)),
        layoutedEdges: extractLayoutedEdges(elkGraph),
    };
}

/**
 * Extracts the routing ELK computed for every edge. For each edge the first
 * section is used, since UML association edges are simple one-to-one edges whose
 * full path is described by a single section. The result maps an edge id to an
 * ordered list of raw points, each tagged with a role: "source" for the start
 * point at the source class border, "bend" for the interior bend points, and
 * "target" for the end point at the target class border.
 *
 * @param {object} elkGraph the graph returned by ELK after layout
 * @returns {Map<string, {x: number, y: number, role: "source"|"bend"|"target"}[]>}
 */
function extractLayoutedEdges(elkGraph) {
    const layoutedEdges = new Map();
    for (const elkEdge of elkGraph.edges ?? []) {
        const section = elkEdge.sections?.[0];
        if (!section) {
            continue;
        }

        const orderedPoints = [
            {
                x: section.startPoint.x,
                y: section.startPoint.y,
                role: "source",
            },
        ];
        for (const bendPoint of section.bendPoints ?? []) {
            orderedPoints.push({
                x: bendPoint.x,
                y: bendPoint.y,
                role: "bend",
            });
        }
        orderedPoints.push({
            x: section.endPoint.x,
            y: section.endPoint.y,
            role: "target",
        });

        layoutedEdges.set(elkEdge.id, orderedPoints);
    }
    return layoutedEdges;
}

/**
 * Builds the ELK graph input structure from the SvelteFlow nodes and edges.
 * A pure SvelteFlow-to-ELK transformation.
 *
 * @param {Array} nodes SvelteFlow nodes
 * @param {Array} edges SvelteFlow edges
 * @returns {object} the ELK graph input
 */
function buildElkGraph(nodes, edges) {
    return {
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
}

/**
 * Returns the given SvelteFlow node with the position ELK computed for it. If ELK
 * did not lay out the node, it is returned unchanged.
 *
 * @param {object} node the original SvelteFlow node
 * @param {object} elkGraph the graph returned by ELK after layout
 * @returns {object} the positioned SvelteFlow node
 */
function toSvelteFlowNode(node, elkGraph) {
    const elkNode = elkGraph.children?.find(child => child.id === node.id);
    if (!elkNode) {
        return node;
    }
    return {
        ...node,
        position: { x: elkNode.x, y: elkNode.y },
    };
}
