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

import { type InternalNode } from "@xyflow/svelte";

//TODO WICHTIG: AM ENDE HIER AUFRÄUMEN! einmal über alle funktionen gehen, zusammenfassen, aufräumen

//TODO UNBENUTZT! vllt entf?
export interface BendPoint {
    id: string;
    x: number;
    y: number;
}

export interface InactiveBendPoint {
    x: number;
    y: number;
    /** Index in the bendPoints array where this point would be inserted when activated. */
    insertionIndex: number;
}

/**
 * The corner rounding factor is exposed to the user as a percentage (0..100).
 * Even a small geometric factor already produces a strong visual rounding, so
 * 100 % maps to this maximum effective factor rather than to 1.
 */
const MAX_EFFECTIVE_ROUNDING_FACTOR = 0.3;

/**
 * Calculates the intersection point between a line (from the target to the node center)
 * and the border of the intersection node. Used to determine edge start and end points.
 * This and the following methods are adapted from the SvelteFlow "Easy Connect" example.
 * See: https://svelteflow.dev/examples/nodes/easy-connect
 */
function getNodeIntersection(
    intersectionNode: InternalNode,
    targetNode: InternalNode,
    offsetY: number = 0,
) {
    const intersectionPos = intersectionNode.internals.positionAbsolute || {
        x: 0,
        y: 0,
    };
    const targetPos = targetNode.internals.positionAbsolute || { x: 0, y: 0 };

    const w = (intersectionNode.measured.width ?? 0) / 2;
    const h = (intersectionNode.measured.height ?? 0) / 2;

    const x2 = intersectionPos.x + w;
    const y2 = intersectionPos.y + h;
    const x1 = targetPos.x + (targetNode.measured.width ?? 0) / 2;
    const y1 = targetPos.y + (targetNode.measured.height ?? 0) / 2 + offsetY;

    const xx1 = (x1 - x2) / (2 * w) - (y1 - y2) / (2 * h);
    const yy1 = (x1 - x2) / (2 * w) + (y1 - y2) / (2 * h);

    const a = 1 / (Math.abs(xx1) + Math.abs(yy1));
    const xx3 = a * xx1;
    const yy3 = a * yy1;

    return {
        x: w * (xx3 + yy3) + x2,
        y: h * (-xx3 + yy3) + y2,
    };
}

/**
 * Returns all parameters needed to render an edge and its labels.
 * Contains the start/end points of the edge as well as the calculated label positions.
 *
 * When bend points are provided, the source endpoint aims at the first bend point
 * and the target endpoint aims at the last bend point, so the edge docks correctly
 * on the node borders facing its actual routing instead of the opposite node.
 */
export function getEdgeParams(
    source: InternalNode,
    target: InternalNode,
    offsetY: number = 0,
    bendPoints: { x: number; y: number }[] = [],
    activeEndPoints: {
        source?: { x: number; y: number } | null;
        target?: { x: number; y: number } | null;
    } = {},
) {
    const sourceAim =
        bendPoints.length > 0 ? makePointNode(bendPoints[0]) : target;
    const targetAim =
        bendPoints.length > 0
            ? makePointNode(bendPoints[bendPoints.length - 1])
            : source;

    const sourceIntersection = getNodeIntersection(source, sourceAim);
    const targetIntersection = getNodeIntersection(target, targetAim, offsetY);

    // Active end points override the automatic border intersection for that side.
    const sourcePoint = activeEndPoints.source ?? sourceIntersection;
    const targetPoint = activeEndPoints.target ?? targetIntersection;

    const startAim =
        bendPoints.length > 0
            ? bendPoints[0]
            : { x: targetPoint.x, y: targetPoint.y };
    const endAim =
        bendPoints.length > 0
            ? bendPoints[bendPoints.length - 1]
            : { x: sourcePoint.x, y: sourcePoint.y };

    const startOffset = getSingleLabelOffset(
        sourcePoint.x,
        sourcePoint.y,
        startAim.x,
        startAim.y,
    );
    const endOffset = getSingleLabelOffset(
        targetPoint.x,
        targetPoint.y,
        endAim.x,
        endAim.y,
    );

    return {
        sx: sourcePoint.x,
        sy: sourcePoint.y,
        tx: targetPoint.x,
        ty: targetPoint.y,
        borderSx: sourceIntersection.x,
        borderSy: sourceIntersection.y,
        borderTx: targetIntersection.x,
        borderTy: targetIntersection.y,
        startX: startOffset.x,
        startY: startOffset.y,
        endX: endOffset.x,
        endY: endOffset.y,
    };
}

/**
 * Wraps a plain point as a minimal InternalNode-like object so it can be used
 * as an "aim" target in getNodeIntersection. The point acts as a zero-size node
 * centered exactly on the given coordinates.
 */
function makePointNode(point: { x: number; y: number }): InternalNode {
    return {
        internals: { positionAbsolute: { x: point.x, y: point.y } },
        measured: { width: 0, height: 0 },
    } as unknown as InternalNode;
}

/**
 * Builds a direct (diagonal) polyline SVG path through the given ordered points.
 * The points array must already include the source endpoint first and the
 * target endpoint last, with any bend points in between.
 */
export function getPolylinePath(points: { x: number; y: number }[]): string {
    if (points.length < 2) return "";
    const [start, ...rest] = points;
    const segments = rest.map(p => `L ${p.x} ${p.y}`).join(" ");
    return `M ${start.x} ${start.y} ${segments}`;
}

/**
 * Computes the midpoint of every segment in the given ordered point list.
 * These are the inactive bend points shown as insertion hints on a selected edge.
 * `points` must include source first and target last. Since corner rounding keeps
 * the segments straight, the plain segment midpoint always lies on the drawn line.
 */
export function getInactiveBendPoints(
    points: { x: number; y: number }[],
): InactiveBendPoint[] {
    const inactiveBendPoints: InactiveBendPoint[] = [];
    for (let i = 0; i < points.length - 1; i++) {
        inactiveBendPoints.push({
            x: (points[i].x + points[i + 1].x) / 2,
            y: (points[i].y + points[i + 1].y) / 2,
            insertionIndex: i,
        });
    }
    return inactiveBendPoints;
}

/**
 * Finds the index in the bend points array at which a new point should be
 * inserted so that it lands on the segment closest to the given position.
 *
 * `orderedPoints` must be the full ordered list [source, ...bendPoints, target].
 * The returned index is relative to the bendPoints array (source excluded),
 * i.e. inserting at that index in bendPoints places the new point on the
 * closest segment.
 */
export function getClosestSegmentInsertionIndex(
    orderedPoints: { x: number; y: number }[],
    position: { x: number; y: number },
): number {
    let bestIndex = 0;
    let bestDistance = Infinity;

    for (let i = 0; i < orderedPoints.length - 1; i++) {
        const distance = distanceToSegment(
            position,
            orderedPoints[i],
            orderedPoints[i + 1],
        );
        if (distance < bestDistance) {
            bestDistance = distance;
            bestIndex = i;
        }
    }

    return bestIndex;
}

/**
 * Returns the shortest distance from a point to a line segment.
 */
function distanceToSegment(
    point: { x: number; y: number },
    segmentStart: { x: number; y: number },
    segmentEnd: { x: number; y: number },
): number {
    const dx = segmentEnd.x - segmentStart.x;
    const dy = segmentEnd.y - segmentStart.y;
    const lengthSquared = dx * dx + dy * dy;

    if (lengthSquared === 0) {
        return Math.hypot(point.x - segmentStart.x, point.y - segmentStart.y);
    }

    let t =
        ((point.x - segmentStart.x) * dx + (point.y - segmentStart.y) * dy) /
        lengthSquared;
    t = Math.max(0, Math.min(1, t));

    const projX = segmentStart.x + t * dx;
    const projY = segmentStart.y + t * dy;
    return Math.hypot(point.x - projX, point.y - projY);
}

/**
 * Calculates the offset for a single label based on the direction of the
 * segment it belongs to. The label is pushed a bit along the segment (away
 * from the node) and a bit perpendicular to it, so it sits next to the line.
 */
function getSingleLabelOffset(
    fromX: number,
    fromY: number,
    towardX: number,
    towardY: number,
) {
    const ALONG_EDGE_DISTANCE = 20;
    const PERPENDICULAR_DISTANCE = 14;

    const dx = towardX - fromX;
    const dy = towardY - fromY;
    const length = Math.hypot(dx, dy) || 1;

    const normDx = dx / length;
    const normDy = dy / length;

    const angle = Math.atan2(-dy, dx) * (180 / Math.PI);
    const normalizedAngle = (angle + 360) % 360;

    const labelOnLeft =
        (normalizedAngle >= 90 && normalizedAngle < 180) ||
        normalizedAngle >= 270;
    const side = labelOnLeft ? 1 : -1;

    const alongX = normDx * ALONG_EDGE_DISTANCE;
    const alongY = normDy * ALONG_EDGE_DISTANCE;
    const perpX = -normDy * PERPENDICULAR_DISTANCE * side;
    const perpY = normDx * PERPENDICULAR_DISTANCE * side;

    return {
        x: alongX + perpX,
        y: alongY + perpY,
    };
}

/**
 * Builds a polyline whose corners at the bend points are rounded off.
 * The segments themselves stay straight, only each interior corner is replaced
 * by a quadratic Bézier arc. `points` must include the source endpoint first and
 * the target endpoint last.
 *
 * `roundingPercent` is a value in [0, 100] that controls how far the rounding
 * reaches into the two adjacent segments: 0 keeps the corner sharp, 100 applies
 * the maximum rounding. The percentage is mapped onto a small geometric factor
 * because even minor factors already produce a strong visual rounding.
 */
export function getRoundedCornerPolylinePath(
    points: { x: number; y: number }[],
    roundingPercent: number = 50,
): string {
    if (points.length < 2) return "";
    if (points.length === 2) {
        return `M ${points[0].x} ${points[0].y} L ${points[1].x} ${points[1].y}`;
    }

    const clampedPercent = Math.max(0, Math.min(100, roundingPercent));
    const clampedFactor =
        (clampedPercent / 100) * MAX_EFFECTIVE_ROUNDING_FACTOR;
    let path = `M ${points[0].x} ${points[0].y}`;

    for (let i = 1; i < points.length - 1; i++) {
        const previous = points[i - 1];
        const corner = points[i];
        const next = points[i + 1];

        const reachBefore = cornerReach(previous, corner, clampedFactor);
        const reachAfter = cornerReach(next, corner, clampedFactor);

        const entry = pointTowards(corner, previous, reachBefore);
        const exit = pointTowards(corner, next, reachAfter);

        path += ` L ${entry.x} ${entry.y}`;
        path += ` Q ${corner.x} ${corner.y}, ${exit.x} ${exit.y}`;
    }

    const last = points[points.length - 1];
    path += ` L ${last.x} ${last.y}`;
    return path;
}

/**
 * Returns how far the rounding may reach from `corner` towards `neighbour`:
 * half of the segment length scaled by the rounding factor.
 */
function cornerReach(
    neighbour: { x: number; y: number },
    corner: { x: number; y: number },
    roundingFactor: number,
): number {
    const segmentLength = Math.hypot(
        neighbour.x - corner.x,
        neighbour.y - corner.y,
    );
    return (segmentLength / 2) * roundingFactor;
}

/**
 * Returns the point that lies `distance` pixels away from `from` in the
 * direction of `to`.
 */
function pointTowards(
    from: { x: number; y: number },
    to: { x: number; y: number },
    distance: number,
): { x: number; y: number } {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const length = Math.hypot(dx, dy) || 1;
    return {
        x: from.x + (dx / length) * distance,
        y: from.y + (dy / length) * distance,
    };
}

/**
 * Projects an arbitrary point onto the border of a node's rectangle. Used to keep
 * end points sticky on the class border while they are dragged. Returns the point
 * on the rectangle outline closest to the given position.
 */
export function projectPointOntoNodeBorder(
    node: InternalNode,
    point: { x: number; y: number },
): { x: number; y: number } {
    const pos = node.internals.positionAbsolute ?? { x: 0, y: 0 };
    const width = node.measured.width ?? 0;
    const height = node.measured.height ?? 0;

    const left = pos.x;
    const right = pos.x + width;
    const top = pos.y;
    const bottom = pos.y + height;

    const clampedX = Math.max(left, Math.min(right, point.x));
    const clampedY = Math.max(top, Math.min(bottom, point.y));

    const distLeft = Math.abs(clampedX - left);
    const distRight = Math.abs(clampedX - right);
    const distTop = Math.abs(clampedY - top);
    const distBottom = Math.abs(clampedY - bottom);
    const minDist = Math.min(distLeft, distRight, distTop, distBottom);

    if (minDist === distLeft) return { x: left, y: clampedY };
    if (minDist === distRight) return { x: right, y: clampedY };
    if (minDist === distTop) return { x: clampedX, y: top };
    return { x: clampedX, y: bottom };
}
