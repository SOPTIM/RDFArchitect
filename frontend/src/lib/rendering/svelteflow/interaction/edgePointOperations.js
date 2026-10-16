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
 * Returns true if an edge point is a docking point (pinned to a class border). Points from the
 * backend get the flag when they are flattened, see `decorateEdges` in diagramElements.js.
 */
export function isDockingPoint(point) {
    return point?.isDockingPoint === true;
}

/**
 * Returns the full ordered edge point array of an edge, or an empty array if none exist. The
 * first/last element may be a docking point.
 */
export function getEdgePoints(edge) {
    return edge?.data?.edgePoints ?? [];
}

/**
 * Returns only the bend points, i.e. the edge points without a leading and/or trailing docking
 * point.
 */
export function getBendPoints(points) {
    return points.filter(point => !isDockingPoint(point));
}

/**
 * Returns the source docking point or null. A docking point is the source docking point if it
 * carries side "source", or, for a docking point without a side, if it is the first array
 * element.
 */
export function getSourceDockingPoint(points) {
    const first = points[0];
    if (first && isDockingPoint(first)) {
        return first.side === undefined || first.side === "source"
            ? first
            : null;
    }
    return null;
}

/**
 * Returns the target docking point or null. A docking point is the target docking point if it
 * carries side "target", or, for a docking point without a side, if it is the last array element
 * and distinct from the source docking point.
 */
export function getTargetDockingPoint(points) {
    if (points.length === 0) return null;
    const last = points[points.length - 1];
    if (!last || !isDockingPoint(last)) return null;
    if (last.side === "target") return last;
    if (last.side === "source") return null;
    const source = getSourceDockingPoint(points);
    if (source && source.id === last.id) return null;
    return last;
}

/**
 * Returns a new edge point array with a new bend point inserted at the given index. The index is
 * relative to the bend points (docking points excluded).
 */
export function insertBendPointAt(points, bendIndex, point) {
    const sourceOffset = getSourceDockingPoint(points) ? 1 : 0;
    const newPoints = [...points];
    newPoints.splice(sourceOffset + bendIndex, 0, point);
    return newPoints;
}

/**
 * Returns a new edge point array without the edge point with the given id.
 */
export function removeEdgePoint(points, pointId) {
    return points.filter(point => point.id !== pointId);
}

/**
 * Returns the edge points without the bend points, so only the docking points stay.
 */
export function removeBendPoints(points) {
    return points.filter(isDockingPoint);
}

/**
 * Returns the edge points without the docking points, so the edge docks on its own at both ends.
 */
export function removeDockingPoints(points) {
    return points.filter(point => !isDockingPoint(point));
}

/**
 * Creates a fresh bend point object at the given position.
 */
export function createBendPoint(x, y) {
    return { id: crypto.randomUUID(), x, y };
}

/**
 * Creates a fresh docking point object at the given position. The side ("source" or "target")
 * records which class border the docking point is pinned to, independent of its position in the
 * points array, so a lone docking point is never misclassified.
 */
export function createDockingPoint(x, y, side) {
    return { id: crypto.randomUUID(), x, y, isDockingPoint: true, side };
}

/**
 * Finds the first edge point within the given hit radius of a position. Returns the point (bend
 * or docking point) or null. Used to decide whether a click on an edge targets an existing point,
 * even when the points are not rendered.
 */
export function findEdgePointAtPosition(points, position, hitRadius) {
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
 * Maps raw layout routing points (each tagged with a role "source", "bend" or "target") to the
 * edge's ordered edge points. Source and target points become sided docking points, interior
 * points become bend points.
 */
export function toEdgePoints(routingPoints) {
    return routingPoints.map(routingPoint => {
        if (routingPoint.role === "source") {
            return createDockingPoint(routingPoint.x, routingPoint.y, "source");
        }
        if (routingPoint.role === "target") {
            return createDockingPoint(routingPoint.x, routingPoint.y, "target");
        }
        return createBendPoint(routingPoint.x, routingPoint.y);
    });
}

/**
 * Removes redundant (near-collinear) bend points from the ordered edge points. A bend point is
 * redundant when its perpendicular distance to the straight line between its two current
 * neighbours is within the given tolerance.
 *
 * Uses an iterative triplet scan: it walks the points, and whenever the middle of a triplet is
 * within tolerance it is dropped and scanning continues with the updated neighbourhood (the
 * just-kept point paired with the next candidate), rather than against the original neighbours.
 * This matches the robust behaviour known from polyline simplification (Douglas-Peucker family).
 *
 * Docking points are never removed and always act as line-defining anchors, so only bend points
 * can be dissolved.
 *
 * @param {Array<{x:number,y:number,id:string}>} points ordered edge points
 * @param {number} tolerance max perpendicular distance to count as collinear
 * @returns {Array} a new points array with redundant bend points removed
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
            !isDockingPoint(candidate) &&
            distanceToSegment(candidate, previous, next) <= tolerance;

        if (!isRedundant) {
            kept.push(candidate);
        }
    }
    kept.push(points[points.length - 1]);

    return kept.length === points.length ? points : kept;
}

/**
 * Replaces the docking points of all edges by what the given function returns for them, called
 * with the id of the class each docking point is pinned to.
 *
 * @param {Array} edges the diagram edges
 * @param {Function} updateDockingPoint returns the point itself to keep it unchanged
 * @returns {Array} the given edges if no point changed, otherwise new edges
 */
export function updateDockingPoints(edges, updateDockingPoint) {
    let changed = false;
    const nextEdges = edges.map(edge => {
        const points = edge.data?.edgePoints ?? [];
        const sourceDocking = getSourceDockingPoint(points);
        const targetDocking = getTargetDockingPoint(points);
        const nextPoints = points.map(point => {
            if (point === sourceDocking) {
                return updateDockingPoint(point, edge.source);
            }
            if (point === targetDocking) {
                return updateDockingPoint(point, edge.target);
            }
            return point;
        });
        if (nextPoints.every((point, index) => point === points[index])) {
            return edge;
        }
        changed = true;
        return { ...edge, data: { ...edge.data, edgePoints: nextPoints } };
    });
    return changed ? nextEdges : edges;
}

/**
 * Moves the edge points along with dragged classes: the docking points pinned to a dragged class
 * by the distance it moved since the last drag event, and the bend points of an edge whose classes
 * are both dragged by the distance of the drag.
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
        const points = edge.data?.edgePoints ?? [];
        let nextPoints = points;

        if (getSourceDockingPoint(points) || getTargetDockingPoint(points)) {
            for (const node of draggedNodes) {
                const previous = lastPositions.get(node.id);
                if (!previous) continue;
                const dx = node.position.x - previous.x;
                const dy = node.position.y - previous.y;
                if (dx === 0 && dy === 0) continue;
                nextPoints = shiftDockingPointsForNode(
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
            nextPoints = shiftBendPoints(
                nextPoints,
                dragDelta.dx,
                dragDelta.dy,
            );
        }

        if (nextPoints === points) {
            return edge;
        }
        anyPointMoved = true;
        return { ...edge, data: { ...edge.data, edgePoints: nextPoints } };
    });

    return anyPointMoved ? updatedEdges : edges;
}

/**
 * Takes over the routing ELK computed for the edges: source and target points become sided
 * docking points, interior points become bend points, and nearly collinear ones are dissolved.
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
                edgePoints: dissolveCollinearBendPoints(
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

function shiftBendPoints(points, dx, dy) {
    return points.map(point =>
        isDockingPoint(point)
            ? point
            : { ...point, x: point.x + dx, y: point.y + dy },
    );
}

function shiftDockingPointsForNode(points, edge, movedNodeId, dx, dy) {
    const sourceDocking = getSourceDockingPoint(points);
    const targetDocking = getTargetDockingPoint(points);
    const shiftSource = edge.source === movedNodeId && sourceDocking;
    const shiftTarget = edge.target === movedNodeId && targetDocking;
    if (!shiftSource && !shiftTarget) return points;

    return points.map(point => {
        if (shiftSource && point.id === sourceDocking.id) {
            return { ...point, x: point.x + dx, y: point.y + dy };
        }
        if (shiftTarget && point.id === targetDocking.id) {
            return { ...point, x: point.x + dx, y: point.y + dy };
        }
        return point;
    });
}
