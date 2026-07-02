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

import { editorState } from "$lib/sharedState.svelte.js";

const NODE_SELECTED_Z_OFFSET = 1_000_000;

export class NodeOrderController {
    #nodeOrder = $state([]);
    #temporaryFrontNodeId = $state(null);

    #getNodes;
    #setNodes;
    #getSelectedIds;
    #bec;

    constructor({ getNodes, setNodes, getSelectedIds, bec }) {
        this.#getNodes = getNodes;
        this.#setNodes = setNodes;
        this.#getSelectedIds = getSelectedIds;
        this.#bec = bec;
    }

    get nodeOrder() {
        return this.#nodeOrder;
    }

    rankOf(nodeId) {
        const idx = this.#nodeOrder.indexOf(nodeId);
        return idx === -1 ? 0 : idx;
    }

    sync(nextNodes) {
        const nextIds = new Set(nextNodes.map(n => n.id));

        if (this.#nodeOrder.length === 0) {
            this.#nodeOrder = [...nextNodes]
                .sort((a, b) => (a.position?.z ?? 0) - (b.position?.z ?? 0))
                .map(n => n.id);
            return;
        }

        const existingOrder = this.#nodeOrder.filter(id => nextIds.has(id));

        const knownIds = new Set(existingOrder);
        const newIds = nextNodes
            .filter(n => !knownIds.has(n.id))
            .map(n => n.id);

        this.#nodeOrder = [...existingOrder, ...newIds];
    }

    applyZIndices(diagramNodes) {
        const zIndexLookup = new Map();
        for (let i = 0; i < this.#nodeOrder.length; i++) {
            zIndexLookup.set(this.#nodeOrder[i], i);
        }
        const tempFrontRank = this.#nodeOrder.length + 1;
        const selectedIds = this.#getSelectedIds?.() ?? new Set();
        return diagramNodes.map(node => {
            const rank =
                node.id === this.#temporaryFrontNodeId
                    ? tempFrontRank
                    : (zIndexLookup.get(node.id) ?? 0);
            const zIndex = selectedIds.has(node.id)
                ? rank + NODE_SELECTED_Z_OFFSET
                : rank;
            return node.zIndex === zIndex ? node : { ...node, zIndex };
        });
    }

    bringToFrontTemporarily(nodeId) {
        if (this.#temporaryFrontNodeId === nodeId) return;
        this.#temporaryFrontNodeId = nodeId;
        this.#setNodes(this.applyZIndices(this.#getNodes()));
    }

    resetTemporaryFront() {
        if (this.#temporaryFrontNodeId === null) return;
        this.#temporaryFrontNodeId = null;
        this.#setNodes(this.applyZIndices(this.#getNodes()));
    }

    moveClass({ classUuid, direction }) {
        const idx = this.#nodeOrder.indexOf(classUuid);
        if (idx === -1) return;

        const next = [...this.#nodeOrder];
        let changedIds;

        if (direction === "up") {
            if (idx >= next.length - 1) return;
            [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]];
            changedIds = [next[idx], next[idx + 1]];
        } else if (direction === "down") {
            if (idx <= 0) return;
            [next[idx], next[idx - 1]] = [next[idx - 1], next[idx]];
            changedIds = [next[idx], next[idx - 1]];
        } else if (direction === "top") {
            if (idx >= next.length - 1) return;
            const [removed] = next.splice(idx, 1);
            next.push(removed);
            changedIds = next.slice(idx);
        } else if (direction === "bottom") {
            if (idx <= 0) return;
            const [removed] = next.splice(idx, 1);
            next.unshift(removed);
            changedIds = next.slice(0, idx + 1);
        }
        this.#nodeOrder = next;
        this.#setNodes(this.applyZIndices(this.#getNodes()));

        this.#persistNodeOrder(this.#nodeOrder, changedIds);
    }

    setLayer({ classUuid, layer }) {
        const currentIdx = this.#nodeOrder.indexOf(classUuid);
        if (currentIdx === -1 || currentIdx === layer) return;

        const next = [...this.#nodeOrder];
        const [removed] = next.splice(currentIdx, 1);
        next.splice(layer, 0, removed);

        this.#nodeOrder = next;
        this.#setNodes(this.applyZIndices(this.#getNodes()));
    }

    persistLayer({ classUuid }) {
        const currentIdx = this.#nodeOrder.indexOf(classUuid);
        if (currentIdx === -1) return;

        this.#persistNodeOrder(this.#nodeOrder, [...this.#nodeOrder]);
    }

    #persistNodeOrder(order, changedIds) {
        const nodes = this.#getNodes();
        const classPositionDTOList = changedIds.map(id => {
            const node = nodes.find(n => n.id === id);
            return {
                classUUID: id,
                xPosition: node?.position.x ?? 0,
                yPosition: node?.position.y ?? 0,
                zPosition: order.indexOf(id),
            };
        });

        if (editorState.selectedGraph.getValue()) {
            this.#bec.updateClassPositions(
                editorState.selectedDataset.getValue(),
                editorState.selectedGraph.getValue(),
                editorState.selectedDiagram.getProperty("id"),
                classPositionDTOList,
            );
        } else {
            this.#bec.updateGlobalClassPositions(
                editorState.selectedDataset.getValue(),
                editorState.selectedDiagram.getProperty("id"),
                classPositionDTOList,
            );
        }
    }
}
