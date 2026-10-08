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
