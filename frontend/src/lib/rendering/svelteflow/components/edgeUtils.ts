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

import { type InternalNode, type Node } from "@xyflow/svelte";

/**
 * Edge endpoints are read from the rendered internal nodes inside an edge component and from the
 * plain nodes when label nodes are laid out, which position themselves relative to an endpoint.
 */
type EdgeEndpointNode = Node & Partial<Pick<InternalNode, "internals">>;

function nodePosition(node: EdgeEndpointNode) {
    return node.internals?.positionAbsolute ?? node.position ?? { x: 0, y: 0 };
}

/**
 * Calculates the intersection point between a line (from the target to the node center)
 * and the border of the intersection node. Used to determine edge start and end points.
 * This and the following methods are adapted from the SvelteFlow "Easy Connect" example.
 * See: https://svelteflow.dev/examples/nodes/easy-connect
 */
function getNodeIntersection(
    intersectionNode: EdgeEndpointNode,
    targetNode: EdgeEndpointNode,
    offsetY: number = 0,
) {
    const intersectionPos = nodePosition(intersectionNode);
    const targetPos = nodePosition(targetNode);

    const w = (intersectionNode.measured.width ?? 0) / 2;
    const h = (intersectionNode.measured.height ?? 0) / 2;

    const x2 = intersectionPos.x + w;
    const y2 = intersectionPos.y + h;
    const x1 = targetPos.x + (targetNode.measured.width ?? 0) / 2;
    const y1 = targetPos.y + (targetNode.measured.height ?? 0) / 2 + offsetY;

    // Two classes share a centre for as long as a diagram has not been laid out, where every
    // class still sits at (0,0), and again whenever one is dropped on top of another. There is
    // no border intersection to compute then, and the formula below would turn 0 * Infinity
    // into NaN — which travels on into the edge path and the placement of its labels.
    if (w === 0 || h === 0 || (x1 === x2 && y1 === y2)) {
        return { x: x2, y: y2 };
    }

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

/**
 * Returns all parameters needed to render an edge and its labels.
 * Contains the start/end points of the edge as well as the calculated label positions.
 */
export function getEdgeParams(
    source: EdgeEndpointNode,
    target: EdgeEndpointNode,
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
