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

import { LABEL_NODE_TYPE } from "./labelNodes.js";

export const EDGE_Z_INDEX = -1;
export const ELEVATED_EDGE_Z_INDEX = 1_500_000;

export function hasDefaultNodeLayout(diagramNodes) {
    const classNodes = diagramNodes.filter(
        node => node.type !== LABEL_NODE_TYPE,
    );
    return (
        classNodes.length > 0 &&
        classNodes.every(node => node.position.x === 0 && node.position.y === 0)
    );
}

export function decorateEdges(edges) {
    return edges.map(decorateEdge);
}

/**
 * Raises the given edges above every class, including selected ones, so their bend
 * points can be grabbed where other edges or classes cover them. Labels stay on top.
 * Returns the given array when no z-index changes.
 *
 * @param edges the diagram edges
 * @param isElevated whether an edge is raised, e.g. while selected or one of its labels is held
 */
export function elevateEdges(edges, isElevated) {
    let changed = false;
    const next = edges.map(edge => {
        const zIndex = isElevated(edge) ? ELEVATED_EDGE_Z_INDEX : EDGE_Z_INDEX;
        if (edge.zIndex === zIndex) {
            return edge;
        }
        changed = true;
        return { ...edge, zIndex };
    });
    return changed ? next : edges;
}

/**
 * The id of the only selected edge, or null when no or several edges are selected.
 *
 * @param edges the diagram edges
 */
export function onlySelectedEdgeId(edges) {
    let selectedId = null;
    for (const edge of edges) {
        if (!edge.selected) {
            continue;
        }
        if (selectedId !== null) {
            return null;
        }
        selectedId = edge.id;
    }
    return selectedId;
}

/**
 * The bounds of a diagram including the bend and end points of its edges, which may lie outside
 * of the classes.
 *
 * @param nodes the diagram nodes
 * @param edges the diagram edges
 * @param getInternalNode returns the measured SvelteFlow node of an id
 * @returns the bounds, or null for an empty diagram
 */
export function getDiagramBounds(nodes, edges, getInternalNode) {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const node of nodes) {
        const internalNode = getInternalNode(node.id);
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

function decorateEdge(edge) {
    return {
        ...edge,
        zIndex: EDGE_Z_INDEX,
        data: normalizeEdgeData(edge.data),
    };
}

/**
 * Normalizes edge data coming from the backend. Points arrive with a nested
 * position ({ id, position: { x, y, z }, side }) and are flattened to the shape
 * used throughout the frontend, see {@link flattenBendPoint}.
 */
function normalizeEdgeData(data) {
    if (!data) {
        return data;
    }
    if (!Array.isArray(data.bendPoints)) {
        return data;
    }
    return {
        ...data,
        bendPoints: data.bendPoints.map(flattenBendPoint),
    };
}

/**
 * Flattens a single point from the backend's nested position shape to the shape
 * the frontend creates points in: { id, x, y } for a bend point and
 * { id, x, y, isEndPoint, side } for an end point, which the backend marks with
 * the side of the class it is glued to. Points that are already flat (e.g.
 * created in the frontend during interaction) are returned unchanged.
 */
function flattenBendPoint(point) {
    if (!point || !point.position) {
        return point;
    }
    const flatPoint = {
        id: point.id,
        x: point.position.x,
        y: point.position.y,
    };
    if (!point.side) {
        return flatPoint;
    }
    return { ...flatPoint, isEndPoint: true, side: point.side };
}
