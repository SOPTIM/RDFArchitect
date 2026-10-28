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

import { SvelteMap } from "svelte/reactivity";

import {
    updateDatasetLabelPositions,
    updateLabelPositions,
} from "$lib/api/generated/index.ts";
import { renderOptions } from "$lib/renderOptions.svelte.js";

import {
    buildLabelNodes,
    clampToAnchor,
    collectLabels,
    hasManualPlacement,
    LABEL_NODE_TYPE,
    labelNodeId,
    labelNodesChanged,
} from "../diagram/labelNodes.js";

function toLabelPositionDTO(identifiedObjectUUID, kind, position) {
    return {
        identifiedObjectUUID,
        kind,
        x: position?.x ?? null,
        y: position?.y ?? null,
    };
}

/**
 * The label nodes of a diagram: builds them from the edges, holds a dragged label near its anchor
 * and keeps and saves the placement of the labels the user placed manually.
 */
export class LabelLayoutController {
    /** Whether a label is being dragged right now. */
    dragActive = false;

    /**
     * The offsets of the manually placed labels from their anchor point, by label node id. Null
     * marks a label that was reset to its default placement.
     */
    #positions = new SvelteMap();

    /**
     * Memorizes edge-intersection geometry per class pair, so dragging one class does not
     * recompute the placement of every other edge in the diagram.
     */
    #placementCache = new Map();

    #getNodes;
    #setNodes;
    #getEdges;
    #getTarget;

    constructor({ getNodes, setNodes, getEdges, getTarget }) {
        this.#getNodes = getNodes;
        this.#setNodes = setNodes;
        this.#getEdges = getEdges;
        this.#getTarget = getTarget;
    }

    /** Forgets the label placements of the diagram shown before. */
    reset() {
        this.#positions = new SvelteMap();
        this.#placementCache = new Map();
    }

    /**
     * Rebuilds the label nodes whenever the classes they are anchored to move. Skipped while a
     * label itself is being dragged, which would otherwise pull it back to its stored offset.
     */
    sync(currentNodes, currentEdges) {
        if (this.dragActive) {
            return;
        }
        const nextLabelNodes = buildLabelNodes(
            currentNodes,
            currentEdges,
            this.#positions,
            this.#placementCache,
            renderOptions.get("showAssociationLabels"),
        );
        if (!labelNodesChanged(currentNodes, nextLabelNodes)) {
            return;
        }
        this.#setNodes([
            ...currentNodes.filter(node => node.type !== LABEL_NODE_TYPE),
            ...nextLabelNodes,
        ]);
    }

    /**
     * Holds a dragged label within its maximum distance from the anchor point. SvelteFlow can only
     * constrain a node to a rectangle, so the radial limit is applied per drag event instead.
     *
     * This relies on the drag event firing after SvelteFlow has written its own position, so that
     * the clamped one is what the frame ends on. The drag itself keeps following the pointer
     * unclamped, which is what lets the label pick it up again on the way back in.
     */
    clampDragged(draggedNodes) {
        const clampedById = new Map();
        for (const dragged of draggedNodes) {
            if (dragged.type !== LABEL_NODE_TYPE) {
                continue;
            }
            const clamped = clampToAnchor(
                dragged.position,
                dragged.data.anchorPoint,
            );
            if (clamped !== dragged.position) {
                clampedById.set(dragged.id, clamped);
            }
        }
        if (clampedById.size === 0) {
            return;
        }
        this.#setNodes(
            this.#getNodes().map(node =>
                clampedById.has(node.id)
                    ? { ...node, position: clampedById.get(node.id) }
                    : node,
            ),
        );
    }

    /** Keeps and saves the placement of labels the user dropped somewhere. */
    handleMove(movedLabelNodes) {
        const movedLabels = [];
        for (const labelNode of movedLabelNodes) {
            const position = clampToAnchor(
                labelNode.position,
                labelNode.data.anchorPoint,
            );
            const delta = {
                x: position.x - labelNode.data.anchorPoint.x,
                y: position.y - labelNode.data.anchorPoint.y,
            };
            this.#positions.set(labelNode.id, delta);
            movedLabels.push(
                toLabelPositionDTO(
                    labelNode.data.identifiedObjectUUID,
                    labelNode.data.kind,
                    position,
                ),
            );
        }
        this.#persist(movedLabels);
    }

    /**
     * Drops the manual placement of every label, so they return to their default placement.
     *
     * @returns the label positions to save for the labels that were placed manually
     */
    resetPositions() {
        const resetLabels = [];
        for (const { label } of collectLabels(this.#getEdges())) {
            if (!hasManualPlacement(label, this.#positions)) {
                continue;
            }
            this.#positions.set(labelNodeId(label), null);
            resetLabels.push(
                toLabelPositionDTO(
                    label.identifiedObjectUUID,
                    label.kind,
                    null,
                ),
            );
        }
        return resetLabels;
    }

    /** The positions of the manually placed labels of the edges of the moved classes. */
    manuallyPlacedLabelsOf(movedClassNodes) {
        const movedClassIds = new Set(movedClassNodes.map(node => node.id));
        return this.#manuallyPlacedLabelsWhere(
            ({ sourceId, targetId }) =>
                movedClassIds.has(sourceId) || movedClassIds.has(targetId),
        );
    }

    /** The positions of the manually placed labels of the given edges, e.g. after an edit. */
    manuallyPlacedLabelsOfEdges(savedEdges) {
        const edgeIds = new Set(savedEdges.map(edge => edge.id));
        return this.#manuallyPlacedLabelsWhere(({ edgeId }) =>
            edgeIds.has(edgeId),
        );
    }

    /**
     * The positions of the manually placed labels of the edges that match, which move along with
     * their anchor, the point where the edge meets the class.
     */
    #manuallyPlacedLabelsWhere(isAffectedEdge) {
        const edges = this.#getEdges();
        const rebuilt = buildLabelNodes(
            this.#getNodes(),
            edges,
            this.#positions,
            this.#placementCache,
        );
        const rebuiltById = new Map(rebuilt.map(node => [node.id, node]));

        const affectedLabels = [];
        for (const entry of collectLabels(edges)) {
            const { label } = entry;
            if (
                !isAffectedEdge(entry) ||
                !hasManualPlacement(label, this.#positions)
            ) {
                continue;
            }
            const labelNode = rebuiltById.get(labelNodeId(label));
            if (!labelNode) {
                continue;
            }
            affectedLabels.push(
                toLabelPositionDTO(
                    label.identifiedObjectUUID,
                    label.kind,
                    labelNode.position,
                ),
            );
        }
        return affectedLabels;
    }

    #persist(labelPositionDTOList) {
        const { datasetName, graphURI, diagramUUID } = this.#getTarget();
        if (!diagramUUID || labelPositionDTOList.length === 0) {
            return;
        }

        if (graphURI) {
            updateLabelPositions({
                path: { datasetName, graphURI, diagramUUID },
                body: labelPositionDTOList,
            });
        } else {
            updateDatasetLabelPositions({
                path: { datasetName, diagramUUID },
                body: labelPositionDTOList,
            });
        }
    }
}
