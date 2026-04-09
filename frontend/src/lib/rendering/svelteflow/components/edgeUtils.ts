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

type Point = {
    x: number;
    y: number;
};

const LABEL_ALIGNMENT_THRESHOLD = 8;

function getNodeCenter(node: InternalNode): Point {
    const positionAbsolute = node.internals.positionAbsolute || { x: 0, y: 0 };

    return {
        x: positionAbsolute.x + (node.measured.width ?? 0) / 2,
        y: positionAbsolute.y + (node.measured.height ?? 0) / 2,
    };
}

function getAxisAlignment(value: number, center: number) {
    if (value < center - LABEL_ALIGNMENT_THRESHOLD) {
        return "min";
    }

    if (value > center + LABEL_ALIGNMENT_THRESHOLD) {
        return "max";
    }

    return "center";
}

function getTranslateValue(alignment: "min" | "center" | "max") {
    if (alignment === "min") {
        return "-100%";
    }

    if (alignment === "max") {
        return "0%";
    }

    return "-50%";
}

export function getAssociationLabelTransform(
    position: Point,
    node: InternalNode,
) {
    const nodeCenter = getNodeCenter(node);
    const horizontalAlignment = getAxisAlignment(position.x, nodeCenter.x);
    const verticalAlignment = getAxisAlignment(position.y, nodeCenter.y);

    return `translate(${getTranslateValue(horizontalAlignment)}, ${getTranslateValue(verticalAlignment)})`;
}

/**
 * Calculates the intersection point between a line (from the target to the node center)
 * and the border of the intersection node. Used to determine edge start and end points.
 * This and the following methods are adapted from the SvelteFlow "Easy Connect" example.
 * See: https://svelteflow.dev/examples/nodes/easy-connect
 */
function getNodeIntersectionForPoint(
    intersectionNode: InternalNode,
    targetPoint: Point,
) {
    const intersectionPos = intersectionNode.internals.positionAbsolute || {
        x: 0,
        y: 0,
    };
    const w = (intersectionNode.measured.width ?? 0) / 2;
    const h = (intersectionNode.measured.height ?? 0) / 2;

    if (w === 0 || h === 0) {
        return {
            x: intersectionPos.x + w,
            y: intersectionPos.y + h,
        };
    }

    const x2 = intersectionPos.x + w;
    const y2 = intersectionPos.y + h;
    const x1 = targetPoint.x;
    const y1 = targetPoint.y;

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

function getNodeIntersection(
    intersectionNode: InternalNode,
    targetNode: InternalNode,
    offsetY: number = 0,
) {
    const targetCenter = getNodeCenter(targetNode);

    return getNodeIntersectionForPoint(intersectionNode, {
        x: targetCenter.x,
        y: targetCenter.y + offsetY,
    });
}

/**
 * Calculates the label offsets along an edge based on the angle of the connection line.
 * Used to position start and end labels.
 */
function getLabelOffsets(sx: number, sy: number, tx: number, ty: number) {
    const ALONG_EDGE_DISTANCE = 20;
    const PERPENDICULAR_DISTANCE = 14;

    const dx = tx - sx;
    const dy = ty - sy;
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
        startX: alongX + perpX,
        startY: alongY + perpY,
        endX: -alongX + perpX,
        endY: -alongY + perpY,
    };
}

function getVectorData(sx: number, sy: number, tx: number, ty: number) {
    const dx = tx - sx;
    const dy = ty - sy;
    const length = Math.sqrt(dx * dx + dy * dy) || 1;

    return {
        dx,
        dy,
        length,
        unitX: dx / length,
        unitY: dy / length,
        normalX: -dy / length,
        normalY: dx / length,
    };
}

function getPreferredLabelSide(sx: number, sy: number, tx: number, ty: number) {
    const { dx, dy } = getVectorData(sx, sy, tx, ty);
    const angle = Math.atan2(-dy, dx) * (180 / Math.PI);
    const normalizedAngle = (angle + 360) % 360;
    const labelOnLeft =
        (normalizedAngle >= 90 && normalizedAngle < 180) ||
        normalizedAngle >= 270;

    return labelOnLeft ? 1 : -1;
}

function getSign(value: number) {
    if (value === 0) {
        return 0;
    }

    return value > 0 ? 1 : -1;
}

function getQuadraticBezierTangent(
    sx: number,
    sy: number,
    cx: number,
    cy: number,
    tx: number,
    ty: number,
    t: number,
) {
    return {
        x: 2 * (1 - t) * (cx - sx) + 2 * t * (tx - cx),
        y: 2 * (1 - t) * (cy - sy) + 2 * t * (ty - cy),
    };
}

function getNormalizedVector(x: number, y: number) {
    const length = Math.sqrt(x * x + y * y) || 1;

    return {
        x: x / length,
        y: y / length,
    };
}

function getAssociationEndpointDirections(
    sx: number,
    sy: number,
    cx: number,
    cy: number,
    tx: number,
    ty: number,
) {
    const startTangentAtSource = getQuadraticBezierTangent(
        sx,
        sy,
        cx,
        cy,
        tx,
        ty,
        0.08,
    );
    const endTangentAtTarget = getQuadraticBezierTangent(
        sx,
        sy,
        cx,
        cy,
        tx,
        ty,
        0.92,
    );
    const startTangent = getNormalizedVector(
        startTangentAtSource.x,
        startTangentAtSource.y,
    );
    const endTangent = getNormalizedVector(
        endTangentAtTarget.x,
        endTangentAtTarget.y,
    );
    const startNormal = { x: -startTangent.y, y: startTangent.x };
    const endInward = { x: -endTangent.x, y: -endTangent.y };
    const endNormal = { x: -endInward.y, y: endInward.x };

    return {
        startInward: startTangent,
        startNormal,
        endInward,
        endNormal,
    };
}

function getAssociationDecorationAnchor(
    point: Point,
    inward: Point,
    normal: Point,
    alongDistance: number,
    perpendicularDistance: number,
    side: number,
) {
    return {
        x:
            point.x +
            inward.x * alongDistance +
            normal.x * perpendicularDistance * side,
        y:
            point.y +
            inward.y * alongDistance +
            normal.y * perpendicularDistance * side,
    };
}

/**
 * Returns all parameters needed to render an edge and its labels.
 * Contains the start/end points of the edge as well as the calculated label positions.
 */
export function getEdgeParams(
    source: InternalNode,
    target: InternalNode,
    offsetY: number = 0,
) {
    const sourceIntersection = getNodeIntersection(source, target);
    const targetIntersection = getNodeIntersection(target, source, offsetY);

    const labelOffsets = getLabelOffsets(
        sourceIntersection.x,
        sourceIntersection.y,
        targetIntersection.x,
        targetIntersection.y,
    );

    return {
        sx: sourceIntersection.x,
        sy: sourceIntersection.y,
        tx: targetIntersection.x,
        ty: targetIntersection.y,
        startX: labelOffsets.startX,
        startY: labelOffsets.startY,
        endX: labelOffsets.endX,
        endY: labelOffsets.endY,
    };
}

export function getAssociationEdgeGeometry(
    source: InternalNode,
    target: InternalNode,
    parallelOffset: number = 0,
) {
    const sourceCenter = getNodeCenter(source);
    const targetCenter = getNodeCenter(target);
    const centerVector = getVectorData(
        sourceCenter.x,
        sourceCenter.y,
        targetCenter.x,
        targetCenter.y,
    );
    const endpointOffset = parallelOffset * 0.32;
    const sourceIntersection = getNodeIntersectionForPoint(source, {
        x: targetCenter.x + centerVector.normalX * endpointOffset,
        y: targetCenter.y + centerVector.normalY * endpointOffset,
    });
    const targetIntersection = getNodeIntersectionForPoint(target, {
        x: sourceCenter.x + centerVector.normalX * endpointOffset,
        y: sourceCenter.y + centerVector.normalY * endpointOffset,
    });
    const { normalX, normalY } = getVectorData(
        sourceIntersection.x,
        sourceIntersection.y,
        targetIntersection.x,
        targetIntersection.y,
    );
    const controlPoint = {
        x:
            (sourceIntersection.x + targetIntersection.x) / 2 +
            normalX * parallelOffset,
        y:
            (sourceIntersection.y + targetIntersection.y) / 2 +
            normalY * parallelOffset,
    };
    const labelSide =
        parallelOffset === 0
            ? getPreferredLabelSide(
                  sourceIntersection.x,
                  sourceIntersection.y,
                  targetIntersection.x,
                  targetIntersection.y,
              )
            : getSign(parallelOffset);
    const endpointDirections = getAssociationEndpointDirections(
        sourceIntersection.x,
        sourceIntersection.y,
        controlPoint.x,
        controlPoint.y,
        targetIntersection.x,
        targetIntersection.y,
    );

    return {
        sx: sourceIntersection.x,
        sy: sourceIntersection.y,
        tx: targetIntersection.x,
        ty: targetIntersection.y,
        cx: controlPoint.x,
        cy: controlPoint.y,
        path: `M ${sourceIntersection.x},${sourceIntersection.y} Q ${controlPoint.x},${controlPoint.y} ${targetIntersection.x},${targetIntersection.y}`,
        startMultiplicityAnchor: getAssociationDecorationAnchor(
            sourceIntersection,
            endpointDirections.startInward,
            endpointDirections.startNormal,
            16,
            14,
            labelSide,
        ),
        endMultiplicityAnchor: getAssociationDecorationAnchor(
            targetIntersection,
            endpointDirections.endInward,
            endpointDirections.endNormal,
            16,
            14,
            labelSide,
        ),
        startAssociationLabelAnchor: getAssociationDecorationAnchor(
            sourceIntersection,
            endpointDirections.startInward,
            endpointDirections.startNormal,
            42,
            24,
            -labelSide,
        ),
        endAssociationLabelAnchor: getAssociationDecorationAnchor(
            targetIntersection,
            endpointDirections.endInward,
            endpointDirections.endNormal,
            42,
            24,
            -labelSide,
        ),
    };
}
