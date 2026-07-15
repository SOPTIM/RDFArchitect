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
) {
    const sourceAim =
        bendPoints.length > 0 ? makePointNode(bendPoints[0]) : target;
    const targetAim =
        bendPoints.length > 0
            ? makePointNode(bendPoints[bendPoints.length - 1])
            : source;

    const sourceIntersection = getNodeIntersection(source, sourceAim);
    const targetIntersection = getNodeIntersection(target, targetAim, offsetY);

    const startAim =
        bendPoints.length > 0
            ? bendPoints[0]
            : { x: targetIntersection.x, y: targetIntersection.y };
    const endAim =
        bendPoints.length > 0
            ? bendPoints[bendPoints.length - 1]
            : { x: sourceIntersection.x, y: sourceIntersection.y };

    const startOffset = getSingleLabelOffset(
        sourceIntersection.x,
        sourceIntersection.y,
        startAim.x,
        startAim.y,
    );
    const endOffset = getSingleLabelOffset(
        targetIntersection.x,
        targetIntersection.y,
        endAim.x,
        endAim.y,
    );

    return {
        sx: sourceIntersection.x,
        sy: sourceIntersection.y,
        tx: targetIntersection.x,
        ty: targetIntersection.y,
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

/*TODO WICHTIG: DIESE FUNKTION IST EIGENTLICH ÜBERFLÜßIG GEWORDEN
 *  ganz einfach, eine smooth edge der getSmoothEdge methode mit tension=1 verhält sich gleich zu ner PolyLine
 *  also wir könnten für später komplett auf die getSmoothEdge methode umstellen
 *  nur halt dass wenn man keine smoothEdges will, dass in diesem fall tension=1 gesetzt wird
 *  ABER ich behalte den code fürs erste mal, vielleicht sind die smooth edges ja inperformant*/
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
 * Computes the inactive bend points shown as insertion hints on a selected edge.
 * For straight (polyline) edges the point sits on the linear midpoint of each
 * segment. For smooth edges the point is sampled on the actual Catmull-Rom curve
 * at the segment's midpoint parameter, so the hint stays visually on the line.
 * `points` must include source first and target last.
 */
export function getInactiveBendPoints(
    points: { x: number; y: number }[],
    smooth: boolean = false,
    tension: number = 0.5,
): InactiveBendPoint[] {
    const inactiveBendPoints: InactiveBendPoint[] = [];
    for (let i = 0; i < points.length - 1; i++) {
        const position =
            smooth && points.length > 2
                ? sampleCurveMidpoint(points, i, tension)
                : {
                      x: (points[i].x + points[i + 1].x) / 2,
                      y: (points[i].y + points[i + 1].y) / 2,
                  };
        inactiveBendPoints.push({
            x: position.x,
            y: position.y,
            insertionIndex: i,
        });
    }
    return inactiveBendPoints;
}

/**
 * Samples the point at parameter t = 0.5 on the Catmull-Rom curve segment
 * between points[segmentIndex] and points[segmentIndex + 1]. Uses the same
 * control-point math as getSmoothPath, so the sampled point lies exactly on
 * the rendered curve.
 */
function sampleCurveMidpoint(
    points: { x: number; y: number }[],
    segmentIndex: number,
    tension: number,
): { x: number; y: number } {
    const factor = (1 - tension) / 6;

    const p0 = points[segmentIndex - 1] ?? points[segmentIndex];
    const p1 = points[segmentIndex];
    const p2 = points[segmentIndex + 1];
    const p3 = points[segmentIndex + 2] ?? points[segmentIndex + 1];

    const cp1x = p1.x + (p2.x - p0.x) * factor;
    const cp1y = p1.y + (p2.y - p0.y) * factor;
    const cp2x = p2.x - (p3.x - p1.x) * factor;
    const cp2y = p2.y - (p3.y - p1.y) * factor;

    return cubicBezierAt(
        p1,
        { x: cp1x, y: cp1y },
        { x: cp2x, y: cp2y },
        p2,
        0.5,
    );
}

/**
 * Evaluates a cubic Bézier curve at parameter t.
 */
function cubicBezierAt(
    p0: { x: number; y: number },
    cp1: { x: number; y: number },
    cp2: { x: number; y: number },
    p1: { x: number; y: number },
    t: number,
): { x: number; y: number } {
    const mt = 1 - t;
    const a = mt * mt * mt;
    const b = 3 * mt * mt * t;
    const c = 3 * mt * t * t;
    const d = t * t * t;
    return {
        x: a * p0.x + b * cp1.x + c * cp2.x + d * p1.x,
        y: a * p0.y + b * cp1.y + c * cp2.y + d * p1.y,
    };
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
    const length = Math.sqrt(dx * dx + dy * dy) || 1;

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
 * Builds a smooth (rounded) SVG path through the given ordered points using a
 * Catmull-Rom spline converted to cubic Bézier segments. The curve passes
 * through every point, so bend points stay visually on the line.
 * `points` must include the source endpoint first and the target endpoint last.
 * `tension` controls roundness (0 = very round, 1 = almost straight).
 */
export function getSmoothPath(
    points: { x: number; y: number }[],
    tension: number = 0.5,
): string {
    if (points.length < 2) return "";
    if (points.length === 2) {
        return `M ${points[0].x} ${points[0].y} L ${points[1].x} ${points[1].y}`;
    }

    const factor = (1 - tension) / 6;
    let path = `M ${points[0].x} ${points[0].y}`;

    for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[i - 1] ?? points[i];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = points[i + 2] ?? points[i + 1];

        const cp1x = p1.x + (p2.x - p0.x) * factor;
        const cp1y = p1.y + (p2.y - p0.y) * factor;
        const cp2x = p2.x - (p3.x - p1.x) * factor;
        const cp2y = p2.y - (p3.y - p1.y) * factor;

        path += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
    }

    return path;
}
