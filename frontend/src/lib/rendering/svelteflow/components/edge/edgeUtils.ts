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

export interface Point {
    x: number;
    y: number;
}

export interface InactiveBendPoint extends Point {
    /** Index in the bendPoints array where this point would be inserted when activated. */
    insertionIndex: number;
}

export interface Rect extends Point {
    width: number;
    height: number;
}

/**
 * The corner rounding factor is exposed to the user as a percentage (0..100),
 * mapped onto this maximum corner radius in pixels (flow units).
 */
const MAX_CORNER_RADIUS_PX = 30;

/**
 * Caps how far the rounding may reach relative to the radius itself, as a
 * multiple of the radius. Without this, a very sharp corner would need a
 * tangent length approaching infinity for a mathematically exact fillet,
 * pulling the drawn curve's peak far away from the actual bend point the
 * sharper (and longer) the surrounding segments get. This keeps the rounding
 * visually anchored near the bend point at the cost of a not-quite-circular
 * arc on extremely sharp corners.
 */
const MAX_TANGENT_TO_RADIUS_RATIO = 3;

/** How far an end label is pushed along its segment, away from the class. */
const LABEL_ALONG_EDGE_DISTANCE = 20;

/** How far an end label is pushed perpendicular to its segment, next to the line. */
const LABEL_PERPENDICULAR_DISTANCE = 14;

/**
 * Returns all parameters needed to render an edge and its labels: the two ends
 * of the edge, the points where it crosses the class borders, and the offsets
 * of the labels at both ends.
 *
 * When bend points are provided, the source end aims at the first bend point
 * and the target end aims at the last bend point, so the edge docks correctly
 * on the node borders facing its actual routing instead of the opposite node.
 * Docking points override the automatic border intersection of their side; the
 * border points stay available for inactive docking points.
 */
export function getEdgeParams(
    source: InternalNode,
    target: InternalNode,
    bendPoints: Point[] = [],
    dockingPoints: {
        source?: Point | null;
        target?: Point | null;
    } = {},
) {
    const firstBendPoint = bendPoints[0];
    const lastBendPoint = bendPoints[bendPoints.length - 1];

    const sourceIntersection = getNodeIntersection(
        source,
        firstBendPoint ? makePointNode(firstBendPoint) : target,
    );
    const targetIntersection = getNodeIntersection(
        target,
        lastBendPoint ? makePointNode(lastBendPoint) : source,
    );

    const sourcePoint = dockingPoints.source ?? sourceIntersection;
    const targetPoint = dockingPoints.target ?? targetIntersection;

    const startOffset = getSingleLabelOffset(
        sourcePoint,
        firstBendPoint ?? targetPoint,
    );
    const endOffset = getSingleLabelOffset(
        targetPoint,
        lastBendPoint ?? sourcePoint,
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
 * Calculates the intersection point between a line (from the target to the node center)
 * and the border of the intersection node. Used to determine where an edge docks on its own.
 * If both node centres coincide, there is no unique border intersection and the shared
 * centre is returned, which is what the edge visibly collapses to.
 * Adapted from the SvelteFlow "Easy Connect" example.
 * See: https://svelteflow.dev/examples/nodes/easy-connect
 */
function getNodeIntersection(
    intersectionNode: InternalNode,
    targetNode: InternalNode,
): Point {
    const intersectionPos = intersectionNode.internals?.positionAbsolute ??
        intersectionNode.position ?? { x: 0, y: 0 };
    const targetPos = targetNode.internals?.positionAbsolute ??
        targetNode.position ?? { x: 0, y: 0 };

    const w = (intersectionNode.measured.width ?? 0) / 2;
    const h = (intersectionNode.measured.height ?? 0) / 2;

    const x2 = intersectionPos.x + w;
    const y2 = intersectionPos.y + h;
    const x1 = targetPos.x + (targetNode.measured.width ?? 0) / 2;
    const y1 = targetPos.y + (targetNode.measured.height ?? 0) / 2;

    const xx1 = (x1 - x2) / (2 * w) - (y1 - y2) / (2 * h);
    const yy1 = (x1 - x2) / (2 * w) + (y1 - y2) / (2 * h);

    const denominator = Math.abs(xx1) + Math.abs(yy1);
    if (denominator === 0) {
        return { x: x2, y: y2 };
    }
    const a = 1 / denominator;
    const xx3 = a * xx1;
    const yy3 = a * yy1;

    return {
        x: w * (xx3 + yy3) + x2,
        y: h * (-xx3 + yy3) + y2,
    };
}

/**
 * Wraps a plain point as a minimal InternalNode-like object so it can be used
 * as an "aim" target in getNodeIntersection. The point acts as a zero-size node
 * centered exactly on the given coordinates.
 */
function makePointNode(point: Point): InternalNode {
    return {
        internals: { positionAbsolute: { x: point.x, y: point.y } },
        measured: { width: 0, height: 0 },
    } as unknown as InternalNode;
}

/**
 * Calculates the offset for a single label based on the direction of the
 * segment it belongs to. The label is pushed a bit along the segment (away
 * from the node) and a bit perpendicular to it, so it sits next to the line.
 */
function getSingleLabelOffset(from: Point, toward: Point): Point {
    const dx = toward.x - from.x;
    const dy = toward.y - from.y;
    const length = Math.hypot(dx, dy) || 1;

    const normDx = dx / length;
    const normDy = dy / length;

    const angle = Math.atan2(-dy, dx) * (180 / Math.PI);
    const normalizedAngle = (angle + 360) % 360;

    const labelOnLeft =
        (normalizedAngle >= 90 && normalizedAngle < 180) ||
        normalizedAngle >= 270;
    const side = labelOnLeft ? 1 : -1;

    return {
        x:
            normDx * LABEL_ALONG_EDGE_DISTANCE -
            normDy * LABEL_PERPENDICULAR_DISTANCE * side,
        y:
            normDy * LABEL_ALONG_EDGE_DISTANCE +
            normDx * LABEL_PERPENDICULAR_DISTANCE * side,
    };
}

/**
 * Builds a direct (diagonal) polyline SVG path through the given ordered points.
 * The points array must already include the source end first and the target
 * end last, with any bend points in between.
 */
export function getPolylinePath(points: Point[]): string {
    if (points.length < 2) return "";
    const [start, ...rest] = points;
    const segments = rest.map(p => `L ${p.x} ${p.y}`).join(" ");
    return `M ${start.x} ${start.y} ${segments}`;
}

/**
 * Builds a polyline whose corners at the bend points are rounded off.
 * The segments themselves stay straight, only each interior corner is replaced
 * by a quadratic Bézier arc. `points` must include the source end first and the
 * target end last.
 *
 * `roundingPercent` is a value in [0, 100] that controls how far the rounding
 * reaches into the two adjacent segments: 0 keeps the corner sharp, 100 applies
 * the maximum rounding. The percentage is mapped onto a small geometric factor
 * because even minor factors already produce a strong visual rounding.
 */
export function getRoundedCornerPolylinePath(
    points: Point[],
    roundingPercent: number = 50,
): string {
    if (points.length < 3) return getPolylinePath(points);

    const clampedPercent = Math.max(0, Math.min(100, roundingPercent));
    const radius = (clampedPercent / 100) * MAX_CORNER_RADIUS_PX;
    let path = `M ${points[0].x} ${points[0].y}`;

    for (let i = 1; i < points.length - 1; i++) {
        const previous = points[i - 1];
        const corner = points[i];
        const next = points[i + 1];

        const tangentLength = cornerTangentLength(
            previous,
            corner,
            next,
            radius,
        );

        const entry = pointTowards(corner, previous, tangentLength);
        const exit = pointTowards(corner, next, tangentLength);

        path += ` L ${entry.x} ${entry.y}`;
        path += ` Q ${corner.x} ${corner.y}, ${exit.x} ${exit.y}`;
    }

    const last = points[points.length - 1];
    path += ` L ${last.x} ${last.y}`;
    return path;
}

/**
 * Returns the tangent length (distance along each adjacent segment from the
 * corner) needed to inscribe a circular fillet of the given radius at the
 * corner, based on the interior angle between the two segments. Sharp corners
 * (small angle) need a longer tangent for the same visual radius than shallow
 * corners, unlike a fixed fraction of the segment length. The result is
 * clamped so it never eats more than half of either adjacent segment, which
 * keeps neighboring corners' fillets from overlapping.
 */
function cornerTangentLength(
    previous: Point,
    corner: Point,
    next: Point,
    radius: number,
): number {
    if (radius <= 0) return 0;

    const toPrevious = { x: previous.x - corner.x, y: previous.y - corner.y };
    const toNext = { x: next.x - corner.x, y: next.y - corner.y };
    const lengthToPrevious = Math.hypot(toPrevious.x, toPrevious.y);
    const lengthToNext = Math.hypot(toNext.x, toNext.y);
    if (lengthToPrevious === 0 || lengthToNext === 0) return 0;

    const cosAngle = Math.max(
        -1,
        Math.min(
            1,
            (toPrevious.x * toNext.x + toPrevious.y * toNext.y) /
                (lengthToPrevious * lengthToNext),
        ),
    );
    const interiorAngle = Math.acos(cosAngle);
    const tangentLength = radius / Math.tan(interiorAngle / 2);

    const maxReach = Math.min(lengthToPrevious, lengthToNext) / 2;
    const maxReachFromRadius = radius * MAX_TANGENT_TO_RADIUS_RATIO;
    return Math.min(tangentLength, maxReach, maxReachFromRadius);
}

/**
 * Returns the point that lies `distance` pixels away from `from` in the
 * direction of `to`.
 */
function pointTowards(from: Point, to: Point, distance: number): Point {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const length = Math.hypot(dx, dy) || 1;
    return {
        x: from.x + (dx / length) * distance,
        y: from.y + (dy / length) * distance,
    };
}

/**
 * Computes the midpoint of every segment in the given ordered point list.
 * These are the inactive bend points shown as insertion hints on a selected edge.
 * `points` must include source first and target last. Since corner rounding keeps
 * the segments straight, the plain segment midpoint always lies on the drawn line.
 */
export function getInactiveBendPoints(points: Point[]): InactiveBendPoint[] {
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
    orderedPoints: Point[],
    position: Point,
): number {
    return closestSegment(orderedPoints, position).index;
}

/**
 * Returns the shortest distance from a point to a polyline described by the given
 * ordered points. The polyline must contain at least two points. Used to find the
 * edge closest to the cursor for the on-cursor bend point hotkey.
 */
export function distanceToPolyline(
    point: Point,
    orderedPoints: Point[],
): number {
    return closestSegment(orderedPoints, point).distance;
}

/**
 * The segment of a polyline closest to the given point: its index, counted from
 * the first point, and its distance to the point.
 */
function closestSegment(
    orderedPoints: Point[],
    point: Point,
): { index: number; distance: number } {
    let index = 0;
    let distance = Infinity;
    for (let i = 0; i < orderedPoints.length - 1; i++) {
        const segmentDistance = distanceToSegment(
            point,
            orderedPoints[i],
            orderedPoints[i + 1],
        );
        if (segmentDistance < distance) {
            distance = segmentDistance;
            index = i;
        }
    }
    return { index, distance };
}

/**
 * Returns the shortest distance from a point to a line segment.
 */
export function distanceToSegment(
    point: Point,
    segmentStart: Point,
    segmentEnd: Point,
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
 * Projects an arbitrary point onto the border of a node's rectangle. Used to keep
 * docking points sticky on the class border while they are dragged. Returns the
 * point on the rectangle outline closest to the given position.
 */
export function projectPointOntoNodeBorder(
    node: InternalNode,
    point: Point,
): Point {
    const pos = node.internals.positionAbsolute ?? { x: 0, y: 0 };
    return projectPointOntoRectBorder(
        {
            x: pos.x,
            y: pos.y,
            width: node.measured.width ?? 0,
            height: node.measured.height ?? 0,
        },
        point,
    );
}

/**
 * Projects an arbitrary point onto the outline of a rectangle, see
 * {@link projectPointOntoNodeBorder}.
 */
export function projectPointOntoRectBorder(rect: Rect, point: Point): Point {
    const left = rect.x;
    const right = rect.x + rect.width;
    const top = rect.y;
    const bottom = rect.y + rect.height;

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

/**
 * Moves a docking point along with the border of a class whose bounds changed, e.g. because it
 * shrank after its attributes were deleted. The point keeps the side of the class it is pinned to
 * and its relative position along that side.
 */
export function moveDockingPointWithBorder(
    point: Point,
    oldRect: Rect,
    newRect: Rect,
): Point {
    const left = Math.abs(point.x - oldRect.x);
    const right = Math.abs(point.x - (oldRect.x + oldRect.width));
    const top = Math.abs(point.y - oldRect.y);
    const bottom = Math.abs(point.y - (oldRect.y + oldRect.height));
    const nearest = Math.min(left, right, top, bottom);
    const alongY = ratio(point.y - oldRect.y, oldRect.height);
    const alongX = ratio(point.x - oldRect.x, oldRect.width);

    if (nearest === left || nearest === right) {
        return {
            x: nearest === left ? newRect.x : newRect.x + newRect.width,
            y: newRect.y + alongY * newRect.height,
        };
    }
    return {
        x: newRect.x + alongX * newRect.width,
        y: nearest === top ? newRect.y : newRect.y + newRect.height,
    };
}

function ratio(offset: number, length: number): number {
    return length > 0 ? Math.max(0, Math.min(1, offset / length)) : 0;
}
