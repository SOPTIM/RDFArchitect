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

export const DEFAULT_EDGE_Z_INDEX = 0;
export const DEFAULT_NODE_BASE_Z_INDEX = 1;

function normalizeNumber(value) {
    return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function normalizeId(value) {
    return String(value ?? "");
}

function compareByDeterministicLayerOrder(leftNode, rightNode) {
    const leftY = normalizeNumber(leftNode.position?.y);
    const rightY = normalizeNumber(rightNode.position?.y);
    if (leftY !== rightY) {
        return leftY - rightY;
    }

    const leftX = normalizeNumber(leftNode.position?.x);
    const rightX = normalizeNumber(rightNode.position?.x);
    if (leftX !== rightX) {
        return leftX - rightX;
    }

    return normalizeId(leftNode.id).localeCompare(normalizeId(rightNode.id));
}

/**
 * Assigns deterministic base z-indices to nodes without mutating input.
 * Order: y-position, then x-position, then id.
 */
export function assignDeterministicNodeZIndices(
    nodes,
    nodeBaseZIndex = DEFAULT_NODE_BASE_Z_INDEX,
) {
    const sortedNodes = [...nodes].sort(compareByDeterministicLayerOrder);

    const zIndexPerNodeId = new Map();
    sortedNodes.forEach((node, index) => {
        zIndexPerNodeId.set(normalizeId(node.id), nodeBaseZIndex + index);
    });

    return nodes.map(node => ({
        ...node,
        zIndex: zIndexPerNodeId.get(normalizeId(node.id)),
    }));
}

/**
 * Assigns a fixed base z-index to all edges without mutating input.
 */
export function assignEdgeZIndices(
    edges,
    edgeBaseZIndex = DEFAULT_EDGE_Z_INDEX,
) {
    return edges.map(edge => ({
        ...edge,
        zIndex: edgeBaseZIndex,
    }));
}

/**
 * Returns the next z-index to use for bringing nodes to the front.
 */
export function initializeNextFrontZIndex(
    nodes,
    minimumFrontZIndex = DEFAULT_NODE_BASE_Z_INDEX,
) {
    let maxZIndex = minimumFrontZIndex - 1;

    for (const node of nodes) {
        const nodeZIndex = normalizeNumber(node.zIndex);
        if (nodeZIndex > maxZIndex) {
            maxZIndex = nodeZIndex;
        }
    }

    return Math.max(minimumFrontZIndex, maxZIndex + 1);
}

/**
 * Promotes one node to the provided top z-index.
 */
export function bringNodeToFront(nodes, nodeId, nextFrontZIndex) {
    const normalizedNodeId = normalizeId(nodeId);
    let found = false;

    const updatedNodes = nodes.map(node => {
        if (normalizeId(node.id) !== normalizedNodeId) {
            return node;
        }

        found = true;
        return {
            ...node,
            zIndex: nextFrontZIndex,
        };
    });

    if (!found) {
        return {
            changed: false,
            nodes,
            nextFrontZIndex,
        };
    }

    return {
        changed: true,
        nodes: updatedNodes,
        nextFrontZIndex: nextFrontZIndex + 1,
    };
}
