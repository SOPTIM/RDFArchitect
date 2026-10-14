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

import {
    distanceToPolyline,
    getClosestSegmentInsertionIndex,
    getEdgeParams,
} from "$lib/rendering/svelteflow/components/edge/edgeUtils.ts";

import {
    createBendPoint,
    findBendPointAtPosition,
    getBendPoints,
    getInnerBendPoints,
    getSourceEndPoint,
    getTargetEndPoint,
    insertBendPointAt,
    isEndPoint,
    removeBendPoint,
} from "./bendPointOperations.js";
import { EDGE_INTERACTION_CONFIG } from "./edgeInteractionConfig.js";

/**
 * Edits the bend and end points of the edges of a diagram: the actions of the edge context menu,
 * the hotkeys that add or delete a point at the cursor, and the context menu on the point handles.
 */
export class EdgePointController {
    #getEdges;
    #setEdges;
    #getSvelteFlow;
    #isReadOnly;
    #selectEdge;
    #contextMenus;

    #lastCursorPosition = null;
    #deleteBrushActive = false;

    constructor({
        getEdges,
        setEdges,
        getSvelteFlow,
        isReadOnly,
        selectEdge,
        contextMenus,
    }) {
        this.#getEdges = getEdges;
        this.#setEdges = setEdges;
        this.#getSvelteFlow = getSvelteFlow;
        this.#isReadOnly = isReadOnly;
        this.#selectEdge = selectEdge;
        this.#contextMenus = contextMenus;
    }

    /**
     * Remembers the cursor for the hotkeys and, while the delete brush is active, deletes the point
     * under the cursor.
     */
    handlePointerMove(event) {
        this.#lastCursorPosition = { x: event.clientX, y: event.clientY };
        if (!this.#deleteBrushActive) {
            return;
        }
        this.stopDeleteBrush(event);
        if (this.#deleteBrushActive && !this.#isReadOnly()) {
            this.#deleteBendPointUnderCursor();
        }
    }

    /** Opens the edge context menu on a right-click on one of the point handles of an edge. */
    routeBendPointContextMenu(event) {
        const target = event.target;
        if (!(target instanceof Element)) return;

        const bendPointCircle = target.closest("[data-edge-id]");
        if (!bendPointCircle) return;

        const edgeId = bendPointCircle.getAttribute("data-edge-id");
        const edge = this.#getEdges().find(e => e.id === edgeId);
        if (!edge) return;

        this.#contextMenus.handleEdgeContextMenu({ event, edge });
    }

    /** Inserts a bend point at the given position into the segment of the edge closest to it. */
    handleEdgeAddBendPoint = ({ edgeId, flowPosition }) => {
        const edge = this.#getEdges().find(e => e.id === edgeId);
        if (!edge) return;
        const allPoints = getBendPoints(edge);
        const innerBendPoints = getInnerBendPoints(allPoints);

        const endpoints = this.#edgeEndpoints(edge, innerBendPoints);
        let insertionIndex = innerBendPoints.length;
        if (endpoints) {
            const orderedPoints = [
                endpoints.source,
                ...innerBendPoints,
                endpoints.target,
            ];
            insertionIndex = getClosestSegmentInsertionIndex(
                orderedPoints,
                flowPosition,
            );
        }

        this.#patchBendPoints(
            edgeId,
            insertBendPointAt(
                allPoints,
                insertionIndex,
                createBendPoint(flowPosition.x, flowPosition.y),
            ),
        );
    };

    handleEdgeDeleteBendPoint = ({ edgeId, bendPointId }) => {
        const edge = this.#getEdges().find(e => e.id === edgeId);
        if (!edge) return;
        this.#patchBendPoints(
            edgeId,
            removeBendPoint(getBendPoints(edge), bendPointId),
        );
    };

    handleEdgeClearBendPoints = ({ edgeId }) => {
        this.#patchBendPoints(edgeId, []);
    };

    handleEdgeDeleteEndPoint = ({ edgeId, endPointId }) => {
        const edge = this.#getEdges().find(e => e.id === edgeId);
        if (!edge) return;
        this.#patchBendPoints(
            edgeId,
            getBendPoints(edge).filter(point => point.id !== endPointId),
        );
    };

    /**
     * Ctrl+Q: creates a bend point on the edge closest to the cursor. Selects that edge if it was
     * not selected yet. Holding the keys creates no further points, one press creates one point.
     */
    addBendPointAtCursor = event => {
        if (this.#isReadOnly() || event?.repeat) return;
        const context = this.#cursorFlowContext(
            EDGE_INTERACTION_CONFIG.edgeHitRadiusPx,
        );
        if (!context) return;
        const edge = this.#findEdgeAtFlowPosition(
            context.flowPosition,
            context.hitRadius,
        );
        if (!edge) return;

        if (!edge.selected) this.#selectEdge(edge.id);
        this.handleEdgeAddBendPoint({
            edgeId: edge.id,
            flowPosition: context.flowPosition,
        });
    };

    /**
     * Ctrl+Shift+Q: deletes the bend or end point under the cursor. While the keys are held it
     * works like a brush: every point the cursor passes over is deleted, until Q, Ctrl or Shift is
     * released.
     */
    deleteBendPointAtCursor = () => {
        if (this.#isReadOnly()) return;
        this.#deleteBrushActive = true;
        this.#deleteBendPointUnderCursor();
    };

    /** Ends the delete brush once Q, Ctrl or Shift is released or the window loses focus. */
    stopDeleteBrush = event => {
        if (
            event.type === "blur" ||
            event.code === "KeyQ" ||
            !event.shiftKey ||
            !(event.ctrlKey || event.metaKey)
        ) {
            this.#deleteBrushActive = false;
        }
    };

    /**
     * Deletes the bend or end point under the cursor, across all edges. Selects the affected edge
     * if it was not selected yet.
     */
    #deleteBendPointUnderCursor() {
        const context = this.#cursorFlowContext(
            EDGE_INTERACTION_CONFIG.pointHitRadiusPx,
        );
        if (!context) return;

        for (const edge of this.#getEdges()) {
            const hitPoint = findBendPointAtPosition(
                getBendPoints(edge),
                context.flowPosition,
                context.hitRadius,
            );
            if (!hitPoint) continue;

            if (!edge.selected) this.#selectEdge(edge.id);
            if (isEndPoint(hitPoint)) {
                this.handleEdgeDeleteEndPoint({
                    edgeId: edge.id,
                    endPointId: hitPoint.id,
                });
            } else {
                this.handleEdgeDeleteBendPoint({
                    edgeId: edge.id,
                    bendPointId: hitPoint.id,
                });
            }
            return;
        }
    }

    /**
     * Finds the edge whose drawn polyline is closest to the given flow position, within the given
     * hit radius. Self loops are skipped.
     *
     * @returns the edge, or null if none is close enough
     */
    #findEdgeAtFlowPosition(flowPosition, hitRadius) {
        let closestEdge = null;
        let closestDistance = Infinity;
        for (const edge of this.#getEdges()) {
            if (edge.source === edge.target) continue;
            const innerBendPoints = getInnerBendPoints(getBendPoints(edge));
            const endpoints = this.#edgeEndpoints(edge, innerBendPoints);
            if (!endpoints) continue;
            const distance = distanceToPolyline(flowPosition, [
                endpoints.source,
                ...innerBendPoints,
                endpoints.target,
            ]);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEdge = edge;
            }
        }
        return closestDistance <= hitRadius ? closestEdge : null;
    }

    /**
     * Resolves the current cursor position to a flow position and a zoom-corrected hit radius.
     *
     * @returns the flow position and hit radius, or null if the cursor position or SvelteFlow is
     *     unavailable
     */
    #cursorFlowContext(baseHitRadiusPx) {
        const svelteFlow = this.#getSvelteFlow();
        if (!svelteFlow || !this.#lastCursorPosition) return null;
        const flowPosition = svelteFlow.screenToFlowPosition(
            { x: this.#lastCursorPosition.x, y: this.#lastCursorPosition.y },
            { snapToGrid: false },
        );
        const zoom = svelteFlow.getViewport?.().zoom ?? 1;
        return { flowPosition, hitRadius: baseHitRadiusPx / (zoom || 1) };
    }

    /**
     * The start and end point the edge is drawn with, see {@link getEdgeParams}.
     *
     * @returns the source and target point, or null if a class of the edge is not rendered
     */
    #edgeEndpoints(edge, innerBendPoints) {
        const svelteFlow = this.#getSvelteFlow();
        if (!svelteFlow?.getInternalNode) return null;
        const sourceNode = svelteFlow.getInternalNode(edge.source);
        const targetNode = svelteFlow.getInternalNode(edge.target);
        if (!sourceNode || !targetNode) return null;
        const allPoints = getBendPoints(edge);
        const params = getEdgeParams(sourceNode, targetNode, innerBendPoints, {
            source: getSourceEndPoint(allPoints),
            target: getTargetEndPoint(allPoints),
        });
        return {
            source: { x: params.sx, y: params.sy },
            target: { x: params.tx, y: params.ty },
        };
    }

    #patchBendPoints(edgeId, bendPoints) {
        this.#setEdges(
            this.#getEdges().map(edge =>
                edge.id === edgeId
                    ? { ...edge, data: { ...edge.data, bendPoints } }
                    : edge,
            ),
        );
    }
}
