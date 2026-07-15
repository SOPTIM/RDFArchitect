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

export const MAX_BEND_POINTS_PER_EDGE = 20;

/**
 * Returns the bend points of an edge, or an empty array if none exist.
 */
export function getBendPoints(edge) {
    return edge?.data?.bendPoints ?? [];
}

/**
 * Returns a new bend points array with a new point inserted at the given index.
 */
export function insertBendPointAt(bendPoints, index, point) {
    const newBendPoints = [...bendPoints];
    newBendPoints.splice(index, 0, point);
    return newBendPoints;
}

/**
 * Returns a new bend points array without the point with the given id.
 */
export function removeBendPoint(bendPoints, pointId) {
    return bendPoints.filter(bp => bp.id !== pointId);
}

/**
 * Creates a fresh bend point object at the given position.
 */
export function createBendPoint(x, y) {
    return { id: crypto.randomUUID(), x, y };
}

/**
 * Finds the first active bend point within the given hit radius of a position.
 * Returns the bend point or null. Used to decide whether a click on an edge
 * targets an existing bend point, even when the bend points are not rendered.
 */
export function findBendPointAtPosition(bendPoints, position, hitRadius) {
    for (const bp of bendPoints) {
        const dx = bp.x - position.x;
        const dy = bp.y - position.y;
        if (Math.hypot(dx, dy) <= hitRadius) {
            return bp;
        }
    }
    return null;
}

/**
 * Returns the active end points of an edge, defaulting to an empty object.
 * Shape: { source?: {id,x,y}|null, target?: {id,x,y}|null }.
 */
export function getEndPoints(edge) {
    return edge?.data?.endPoints ?? {};
}

/**
 * Creates a fresh end point object at the given position.
 */
export function createEndPoint(x, y) {
    return { id: crypto.randomUUID(), x, y };
}

/**
 * Finds an active end point ("source"/"target") within the given hit radius of a
 * position. Returns the side or null. Mirrors findBendPointAtPosition, but for the
 * end points held in edge.data.endPoints.
 */
export function findEndPointSideAtPosition(endPoints, position, hitRadius) {
    for (const side of ["source", "target"]) {
        const endPoint = endPoints?.[side];
        if (!endPoint) continue;
        const dx = endPoint.x - position.x;
        const dy = endPoint.y - position.y;
        if (Math.hypot(dx, dy) <= hitRadius) {
            return side;
        }
    }
    return null;
}
