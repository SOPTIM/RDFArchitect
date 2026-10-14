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

import { distanceToSegment } from "$lib/rendering/svelteflow/components/edge/edgeUtils.ts";

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
 * Returns the source end point or null. An end point is the source end point if it
 * carries side "source", or (for backend data without a side) if it is the first
 * array element and flagged as an end point.
 */
export function getSourceEndPoint(points) {
    const first = points[0];
    if (first && isEndPoint(first)) {
        return first.side === undefined || first.side === "source"
            ? first
            : null;
    }
    return null;
}

/**
 * Returns the target end point or null. An end point is the target end point if it
 * carries side "target", or (for backend data without a side) if it is the last
 * array element, flagged as an end point, and distinct from the source end point.
 */
export function getTargetEndPoint(points) {
    if (points.length === 0) return null;
    const last = points[points.length - 1];
    if (!last || !isEndPoint(last)) return null;
    if (last.side === "target") return last;
    if (last.side === "source") return null;
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
 * Creates a fresh end point object at the given position. The side ("source" or
 * "target") records which class border the end point docks to, independent of its
 * position in the points array, so a lone end point is never misclassified.
 */
export function createEndPoint(x, y, side) {
    return { id: crypto.randomUUID(), x, y, isEndPoint: true, side };
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

/**
 * Maps raw layout routing points (each tagged with a role "source", "bend" or
 * "target") to the edge's ordered point objects. Source and target points become
 * sided end points, interior points become regular bend points.
 */
export function toEdgePoints(routingPoints) {
    return routingPoints.map(routingPoint => {
        if (routingPoint.role === "source") {
            return createEndPoint(routingPoint.x, routingPoint.y, "source");
        }
        if (routingPoint.role === "target") {
            return createEndPoint(routingPoint.x, routingPoint.y, "target");
        }
        return createBendPoint(routingPoint.x, routingPoint.y);
    });
}

/**
 * Removes redundant (near-collinear) inner bend points from the ordered points
 * array. An inner bend point is redundant when its perpendicular distance to the
 * straight line between its two current neighbours is within the given tolerance.
 *
 * Uses an iterative triplet scan: it walks the points, and whenever the middle of
 * a triplet is within tolerance it is dropped and scanning continues with the
 * updated neighbourhood (the just-kept point paired with the next candidate),
 * rather than against the original neighbours. This matches the robust behaviour
 * known from polyline simplification (Douglas-Peucker family).
 *
 * End points (first/last when flagged) are never removed and always act as
 * line-defining anchors, so only interior bend points can be dissolved.
 *
 * @param {Array<{x:number,y:number,id:string}>} points ordered edge points
 * @param {number} tolerance max perpendicular distance to count as collinear
 * @returns {Array} a new points array with redundant inner points removed
 */
export function dissolveCollinearBendPoints(points, tolerance) {
    if (points.length < 3) {
        return points;
    }

    const kept = [points[0]];
    for (let i = 1; i < points.length - 1; i++) {
        const previous = kept[kept.length - 1];
        const candidate = points[i];
        const next = points[i + 1];

        const isRedundant =
            !isEndPoint(candidate) &&
            distanceToSegment(candidate, previous, next) <= tolerance;

        if (!isRedundant) {
            kept.push(candidate);
        }
    }
    kept.push(points[points.length - 1]);

    return kept.length === points.length ? points : kept;
}

/**
 * Replaces the end points of all edges by what the given function returns for them, called with
 * the id of the class each end point is docked to.
 *
 * @param {Array} edges the diagram edges
 * @param {Function} updateEndPoint returns the point itself to keep it unchanged
 * @returns {Array} the given edges if no point changed, otherwise new edges
 */
export function updateEndPoints(edges, updateEndPoint) {
    let changed = false;
    const nextEdges = edges.map(edge => {
        const points = edge.data?.bendPoints ?? [];
        const sourceEnd = getSourceEndPoint(points);
        const targetEnd = getTargetEndPoint(points);
        const nextPoints = points.map(point => {
            if (point === sourceEnd) {
                return updateEndPoint(point, edge.source);
            }
            if (point === targetEnd) {
                return updateEndPoint(point, edge.target);
            }
            return point;
        });
        if (nextPoints.every((point, index) => point === points[index])) {
            return edge;
        }
        changed = true;
        return { ...edge, data: { ...edge.data, bendPoints: nextPoints } };
    });
    return changed ? nextEdges : edges;
}

/**
 * Moves the points of the edges along with dragged classes: the end points docked to a dragged
 * class by the distance it moved since the last drag event, and the inner bend points of an edge
 * whose classes are both dragged by the distance of the drag.
 *
 * @param {Array} edges the diagram edges
 * @param {Array} draggedNodes the dragged classes at their current positions
 * @param {Map<string, {x: number, y: number}>} lastPositions the positions of the dragged classes
 *     at the last drag event, by id
 * @returns {Array} the given edges if no point moved, otherwise new edges
 */
export function moveEdgePointsWithDraggedClasses(
    edges,
    draggedNodes,
    lastPositions,
) {
    const draggedNodeIds = new Set(draggedNodes.map(node => node.id));
    const dragDelta = computeDragDelta(draggedNodes, lastPositions);
    let anyPointMoved = false;

    const updatedEdges = edges.map(edge => {
        const points = edge.data?.bendPoints ?? [];
        let nextPoints = points;

        if (getSourceEndPoint(points) || getTargetEndPoint(points)) {
            for (const node of draggedNodes) {
                const previous = lastPositions.get(node.id);
                if (!previous) continue;
                const dx = node.position.x - previous.x;
                const dy = node.position.y - previous.y;
                if (dx === 0 && dy === 0) continue;
                nextPoints = shiftEndPointsForNode(
                    nextPoints,
                    edge,
                    node.id,
                    dx,
                    dy,
                );
            }
        }

        const bothClassesDragged =
            draggedNodeIds.has(edge.source) && draggedNodeIds.has(edge.target);
        if (bothClassesDragged && dragDelta) {
            nextPoints = shiftInnerBendPoints(
                nextPoints,
                dragDelta.dx,
                dragDelta.dy,
            );
        }

        if (nextPoints === points) {
            return edge;
        }
        anyPointMoved = true;
        return { ...edge, data: { ...edge.data, bendPoints: nextPoints } };
    });

    return anyPointMoved ? updatedEdges : edges;
}

/**
 * Takes over the routing ELK computed for the edges: source and target points become sided end
 * points, interior points become bend points, and nearly collinear ones are dissolved.
 *
 * @param {Array} edges the diagram edges
 * @param {Map<string, Array>} layoutedEdges the routing points ELK computed, by edge id
 * @param {number} tolerance see {@link dissolveCollinearBendPoints}
 * @returns {Array} the edges with the computed points
 */
export function applyLayoutedEdges(edges, layoutedEdges, tolerance) {
    return edges.map(edge => {
        const routingPoints = layoutedEdges.get(edge.id);
        if (!routingPoints || routingPoints.length === 0) {
            return edge;
        }
        return {
            ...edge,
            data: {
                ...edge.data,
                bendPoints: dissolveCollinearBendPoints(
                    toEdgePoints(routingPoints),
                    tolerance,
                ),
            },
        };
    });
}

function computeDragDelta(draggedNodes, lastPositions) {
    for (const node of draggedNodes) {
        const previous = lastPositions.get(node.id);
        if (!previous) continue;
        const dx = node.position.x - previous.x;
        const dy = node.position.y - previous.y;
        if (dx === 0 && dy === 0) continue;
        return { dx, dy };
    }
    return null;
}

function shiftInnerBendPoints(points, dx, dy) {
    return points.map(point =>
        isEndPoint(point)
            ? point
            : { ...point, x: point.x + dx, y: point.y + dy },
    );
}

function shiftEndPointsForNode(points, edge, movedNodeId, dx, dy) {
    const sourceEnd = getSourceEndPoint(points);
    const targetEnd = getTargetEndPoint(points);
    const shiftSource = edge.source === movedNodeId && sourceEnd;
    const shiftTarget = edge.target === movedNodeId && targetEnd;
    if (!shiftSource && !shiftTarget) return points;

    return points.map(point => {
        if (shiftSource && point.id === sourceEnd.id) {
            return { ...point, x: point.x + dx, y: point.y + dy };
        }
        if (shiftTarget && point.id === targetEnd.id) {
            return { ...point, x: point.x + dx, y: point.y + dy };
        }
        return point;
    });
}
