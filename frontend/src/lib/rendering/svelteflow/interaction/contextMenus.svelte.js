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

import { editorState, multiSelectState } from "$lib/sharedState.svelte.js";

import {
    findBendPointAtPosition,
    getBendPoints,
    getInnerBendPoints,
    isEndPoint,
} from "./bendPointOperations.js";
import { EDGE_INTERACTION_CONFIG } from "./edgeInteractionConfig.js";

export class ContextMenuController {
    #paneRequest = $state(null);
    #classRequest = $state(null);
    #edgeRequest = $state(null);
    #contextMenuClass = $state(null);

    #getSvelteFlow;
    #getIsReadOnly;
    #getEdges;
    #selectEdge;

    constructor({ getSvelteFlow, getIsReadOnly, getEdges, selectEdge }) {
        this.#getSvelteFlow = getSvelteFlow;
        this.#getIsReadOnly = getIsReadOnly;
        this.#getEdges = getEdges ?? (() => []);
        this.#selectEdge = selectEdge ?? (() => {});
    }

    get paneRequest() {
        return this.#paneRequest;
    }

    get classRequest() {
        return this.#classRequest;
    }

    get edgeRequest() {
        return this.#edgeRequest;
    }

    get contextMenuClass() {
        return this.#contextMenuClass;
    }

    close() {
        this.#paneRequest = null;
        this.#classRequest = null;
        this.#edgeRequest = null;
    }

    #consumeEvent(event) {
        event.preventDefault();
        event.stopPropagation();
        this.close();
    }

    handlePaneContextMenu({ event }) {
        this.#consumeEvent(event);
        if (
            event.target instanceof Element &&
            event.target.closest(".svelte-flow__node")
        ) {
            return;
        }
        if (this.#getIsReadOnly()) {
            return;
        }

        this.#contextMenuClass = null;
        const svelteFlow = this.#getSvelteFlow();
        if (!svelteFlow) {
            this.#paneRequest = {
                x: event.clientX,
                y: event.clientY,
                flowPosition: { x: 0, y: 0 },
            };
            return;
        }

        const flowPosition = svelteFlow.screenToFlowPosition(
            {
                x: event.clientX,
                y: event.clientY,
            },
            { snapToGrid: false },
        );
        this.#paneRequest = {
            x: event.clientX,
            y: event.clientY,
            flowPosition,
        };
    }

    handleEdgeContextMenu({ event, edge }) {
        this.#consumeEvent(event);
        if (this.#getIsReadOnly()) {
            return;
        }

        const edgeId = edge?.id;
        if (!edgeId) {
            return;
        }

        this.#selectEdge(edgeId);

        const svelteFlow = this.#getSvelteFlow();
        const flowPosition = svelteFlow
            ? svelteFlow.screenToFlowPosition(
                  { x: event.clientX, y: event.clientY },
                  { snapToGrid: false },
              )
            : { x: 0, y: 0 };

        const currentEdge = this.#getEdges().find(e => e.id === edgeId) ?? edge;
        const allPoints = getBendPoints(currentEdge);
        const middleBendPoints = getInnerBendPoints(allPoints);

        const zoom = svelteFlow?.getViewport?.().zoom ?? 1;
        const hitRadius =
            EDGE_INTERACTION_CONFIG.pointHitRadiusPx / (zoom || 1);
        const hitPoint = findBendPointAtPosition(
            allPoints,
            flowPosition,
            hitRadius,
        );

        const hitIsEndPoint = hitPoint ? isEndPoint(hitPoint) : false;

        this.#edgeRequest = {
            x: event.clientX,
            y: event.clientY,
            edgeId,
            flowPosition,
            hitBendPointId: hitIsEndPoint ? null : (hitPoint?.id ?? null),
            hitEndPointId: hitIsEndPoint ? hitPoint.id : null,
            bendPointCount: middleBendPoints.length,
        };
    }

    handleNodeContextMenu({ event, node }) {
        this.#consumeEvent(event);
        const isInSelection = multiSelectState.isSelected(
            editorState.selectedDataset.getValue(),
            node.data?.graphUri,
            node.id,
        );
        if (!isInSelection && multiSelectState.count > 0) {
            multiSelectState.clear();
        }
        this.#contextMenuClass = {
            uuid: node.id,
            label: node.data?.label ?? node.id,
            graphUri: node.data?.graphUri ?? null,
        };
        this.#classRequest = {
            x: event.clientX,
            y: event.clientY,
        };
    }
}
