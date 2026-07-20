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
/**
 * Returns true if a point is an end point (docked to a class border). The flag may
 * arrive as `isEndPoint` or, depending on Jackson serialization, as `endPoint`.
 */
export function isEndPoint(point) {
    return point?.isEndPoint === true || point?.endPoint === true;
}

/**
 * Returns the full ordered point array of an edge (end points included), or an
 * empty array if none exist. The first/last element may be an end point.
 */
export function getBendPoints(edge) {
    return edge?.data?.bendPoints ?? [];
}

/**
 * Returns only the regular (non end point) bend points, i.e. the array without a
 * leading and/or trailing end point element.
 */
export function getInnerBendPoints(points) {
    return points.filter(point => !isEndPoint(point));
}

/**
 * Returns the source end point (first element, if it is an end point) or null.
 */
export function getSourceEndPoint(points) {
    const first = points[0];
    return first && isEndPoint(first) ? first : null;
}

/**
 * Returns the target end point (last element, if it is an end point) or null.
 * When the array holds a single end point, it is treated as the source end point,
 * so it is only a target end point if it differs from the detected source.
 */
export function getTargetEndPoint(points) {
    if (points.length === 0) return null;
    const last = points[points.length - 1];
    if (!last || !isEndPoint(last)) return null;
    const source = getSourceEndPoint(points);
    if (source && source.id === last.id) return null;
    return last;
}

/**
 * Returns a new points array with a new middle bend point inserted at the given
 * index. The index is relative to the middle bend points (end points excluded).
 */
export function insertBendPointAt(points, middleIndex, point) {
    const sourceOffset = getSourceEndPoint(points) ? 1 : 0;
    const newPoints = [...points];
    newPoints.splice(sourceOffset + middleIndex, 0, point);
    return newPoints;
}

/**
 * Returns a new points array without the point with the given id.
 */
export function removeBendPoint(points, pointId) {
    return points.filter(point => point.id !== pointId);
}

/**
 * Creates a fresh regular bend point object at the given position.
 */
export function createBendPoint(x, y) {
    return { id: crypto.randomUUID(), x, y };
}

/**
 * Creates a fresh end point object at the given position.
 */
export function createEndPoint(x, y) {
    return { id: crypto.randomUUID(), x, y, isEndPoint: true };
}

/**
 * Finds the first point within the given hit radius of a position. Returns the
 * point (bend or end point) or null. Used to decide whether a click on an edge
 * targets an existing point, even when the points are not rendered.
 */
export function findBendPointAtPosition(points, position, hitRadius) {
    for (const point of points) {
        const dx = point.x - position.x;
        const dy = point.y - position.y;
        if (Math.hypot(dx, dy) <= hitRadius) {
            return point;
        }
    }
    return null;
}
