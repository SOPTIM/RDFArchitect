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

export const EDGE_Z_INDEX = -1;
export const EDGE_SELECTED_Z = 500_000;

export function hasDefaultNodeLayout(diagramNodes) {
    return (
        diagramNodes.length > 0 &&
        diagramNodes.every(
            node => node.position.x === 0 && node.position.y === 0,
        )
    );
}

const offsetEdgeIdCache = new WeakMap();

function offsetEdgeIds(edges) {
    let ids = offsetEdgeIdCache.get(edges);
    if (!ids) {
        ids = new Set();
        for (const edge of edges) {
            if (shouldOffsetInheritanceEdge(edge, edges)) {
                ids.add(edge.id);
            }
        }
        offsetEdgeIdCache.set(edges, ids);
    }
    return ids;
}

export function decorateEdges(edges, { selectedNodeIds, previous } = {}) {
    const selected = selectedNodeIds ?? new Set();
    const offsetIds = offsetEdgeIds(edges);
    const prevById = previous
        ? new Map(previous.map(edge => [edge.id, edge]))
        : null;
    return edges.map(edge =>
        decorateEdge(edge, offsetIds, selected, prevById?.get(edge.id)),
    );
}

function decorateEdge(edge, offsetIds, selectedNodeIds, previous) {
    const elevated =
        selectedNodeIds.has(edge.source) || selectedNodeIds.has(edge.target);
    const zIndex = elevated ? EDGE_SELECTED_Z : EDGE_Z_INDEX;
    const needsOffset = offsetIds.has(edge.id);

    if (
        previous &&
        previous.zIndex === zIndex &&
        (previous.data?.offsetEdge === true) === needsOffset
    ) {
        return previous;
    }

    const decorated = { ...edge, zIndex };

    if (!needsOffset) {
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

function shouldOffsetInheritanceEdge(edge, edges) {
    return (
        edge.type === "inheritance" &&
        edges.some(otherEdge =>
            isAssociationEdgeBetweenSameNodes(edge, otherEdge),
        )
    );
}

function isAssociationEdgeBetweenSameNodes(edge, otherEdge) {
    if (otherEdge.type !== "association") {
        return false;
    }

    const sameDirection =
        otherEdge.source === edge.source && otherEdge.target === edge.target;
    const reverseDirection =
        otherEdge.source === edge.target && otherEdge.target === edge.source;

    return sameDirection || reverseDirection;
}
