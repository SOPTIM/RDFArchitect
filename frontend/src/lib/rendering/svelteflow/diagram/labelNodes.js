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

import { getEdgeParams } from "../components/edgeUtils.ts";

export const LABEL_NODE_TYPE = "label";

/**
 * Labels are rendered above every class, including a selected one, so that a label stays readable
 * where it overlaps a class. See NodeOrderController for the z indices classes are given.
 */
const LABEL_Z_INDEX = 2_000_000;

/** Radius of the invisible circle a label may be dragged within, around its anchor point. */
const LABEL_MAX_DISTANCE = 120;

const SELF_LOOP_LABEL_OFFSET = { x: 12, y: -30 };

const SOURCE_ANCHOR = "SOURCE";

export function labelNodeId(label) {
    return `${label.identifiedObjectUUID}:${label.kind}`;
}

/** All labels of a diagram together with the class each of them is anchored to. */
export function collectLabels(edges) {
    const labels = [];
    for (const edge of edges) {
        for (const label of edge.data?.labels ?? []) {
            labels.push({ label, anchorClassId: anchorClassId(edge, label) });
        }
    }
    return labels;
}

function anchorClassId(edge, label) {
    return label.anchor === SOURCE_ANCHOR ? edge.source : edge.target;
}

/**
 * Builds the nodes for all movable labels. A label that has been placed manually sits at its
 * stored offset relative to its class, so it follows the class but neither the edge routing nor
 * the anchor point wandering along the class border. Labels without a stored offset fall back to
 * their default placement next to the anchor point.
 *
 * @param nodes the current nodes, used for the class positions and the measured label sizes
 * @param edges the current edges, carrying the labels of both of their ends
 * @param offsetOverrides offsets of labels moved in this session, keyed by label node id; an entry
 *     holding null resets that label to its default placement
 */
export function buildLabelNodes(nodes, edges, offsetOverrides = new Map()) {
    const classNodes = new Map();
    const labelSizes = new Map();
    for (const node of nodes) {
        if (node.type === LABEL_NODE_TYPE) {
            labelSizes.set(node.id, node.measured);
        } else {
            classNodes.set(node.id, node);
        }
    }

    const labelNodes = [];
    for (const edge of edges) {
        const source = classNodes.get(edge.source);
        const target = classNodes.get(edge.target);
        if (!source?.measured || !target?.measured) {
            continue;
        }

        const placements = edgePlacements(source, target);
        for (const label of edge.data?.labels ?? []) {
            const atSource = label.anchor === SOURCE_ANCHOR;
            labelNodes.push(
                buildLabelNode(
                    label,
                    atSource ? source : target,
                    atSource ? placements.source : placements.target,
                    offsetOverrides,
                    labelSizes,
                ),
            );
        }
    }
    return labelNodes;
}

/** The offset a label at the given position has relative to the class it is anchored to. */
export function offsetFromClass(labelNode, anchorClass) {
    return {
        x: labelNode.position.x - anchorClass.position.x,
        y: labelNode.position.y - anchorClass.position.y,
    };
}

export function effectiveOffset(label, offsetOverrides) {
    const id = labelNodeId(label);
    return offsetOverrides.has(id) ? offsetOverrides.get(id) : label.offset;
}

function buildLabelNode(
    label,
    anchorClass,
    placement,
    offsetOverrides,
    labelSizes,
) {
    const id = labelNodeId(label);
    const offset = effectiveOffset(label, offsetOverrides);
    const position = offset
        ? {
              x: anchorClass.position.x + offset.x,
              y: anchorClass.position.y + offset.y,
          }
        : placement.defaultCenter;

    return {
        id,
        type: LABEL_NODE_TYPE,
        position,
        // A label is positioned by its centre, which is what both the default placement and the
        // stored offset describe.
        origin: [0.5, 0.5],
        zIndex: LABEL_Z_INDEX,
        selectable: false,
        // Carried over because SvelteFlow takes a node's dimensions from the node object it is
        // given: rebuilding without them hides the label until it has been measured again.
        measured: labelSizes.get(labelNodeId(label)),
        data: {
            text: label.text,
            identifiedObjectUUID: label.identifiedObjectUUID,
            kind: label.kind,
            anchorClassId: anchorClass.id,
            anchorPoint: placement.anchor,
        },
    };
}

/**
 * Holds a label within its maximum distance from the anchor point. SvelteFlow can only constrain a
 * node to a rectangle, so a dragged label is clamped here instead of through its extent.
 */
export function clampToAnchor(position, anchorPoint) {
    const dx = position.x - anchorPoint.x;
    const dy = position.y - anchorPoint.y;
    const distance = Math.hypot(dx, dy);
    if (distance <= LABEL_MAX_DISTANCE) {
        return position;
    }
    const scale = LABEL_MAX_DISTANCE / distance;
    return {
        x: anchorPoint.x + dx * scale,
        y: anchorPoint.y + dy * scale,
    };
}

/**
 * Anchor points and default label placements of both edge ends. The anchor point is where the
 * edge meets the class; the default placement is the one the labels had before they became
 * movable.
 */
function edgePlacements(source, target) {
    if (source.id === target.id) {
        const position = source.position;
        const width = source.measured.width ?? 100;
        return {
            source: placement(
                position.x + width * 0.25,
                position.y,
                -SELF_LOOP_LABEL_OFFSET.x,
                SELF_LOOP_LABEL_OFFSET.y,
            ),
            target: placement(
                position.x + width * 0.75,
                position.y,
                SELF_LOOP_LABEL_OFFSET.x,
                SELF_LOOP_LABEL_OFFSET.y,
            ),
        };
    }

    const edgeParams = getEdgeParams(source, target);
    return {
        source: placement(
            edgeParams.sx,
            edgeParams.sy,
            edgeParams.startX,
            edgeParams.startY,
        ),
        target: placement(
            edgeParams.tx,
            edgeParams.ty,
            edgeParams.endX,
            edgeParams.endY,
        ),
    };
}

function placement(anchorX, anchorY, offsetX, offsetY) {
    return {
        anchor: { x: anchorX, y: anchorY },
        defaultCenter: { x: anchorX + offsetX, y: anchorY + offsetY },
    };
}
