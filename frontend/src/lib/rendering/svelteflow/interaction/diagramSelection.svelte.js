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

import { untrack } from "svelte";

import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
import {
    ClassType,
    DiagramType,
    editorState,
    mergeSelections,
    multiSelectState,
    SelectionLevel,
    toggleSelections,
} from "$lib/sharedState.svelte.js";

export class DiagramSelectionController {
    #getNodes;
    #setNodes;
    #pan;
    #contextMenus;
    #nodeOrder;

    #nodeDragActive = false;
    #suppressClickOpen = false;

    constructor({ getNodes, setNodes, pan, contextMenus, nodeOrder }) {
        this.#getNodes = getNodes;
        this.#setNodes = setNodes;
        this.#pan = pan;
        this.#contextMenus = contextMenus;
        this.#nodeOrder = nodeOrder;
    }

    notifyNodeDragStart() {
        this.#nodeDragActive = true;
    }

    notifyNodeDragStop() {
        this.#nodeDragActive = false;
        this.#suppressClickOpen = true;
        this.reflectSelectionToNodes();
    }

    notifyPointerDown() {
        this.#suppressClickOpen = false;
    }

    #selectionGraphUri(node) {
        return editorState.selectedDiagram.getProperty("type") ===
            DiagramType.CROSS_PROFILE
            ? null
            : node.data?.graphUri;
    }

    buildEntry(node) {
        return {
            datasetName: editorState.selectedDataset.getValue(),
            graphUri: this.#selectionGraphUri(node),
            classUuid: node.id,
            classLabel: node.data?.label ?? node.id,
            packageId: editorState.selectedDiagram.getProperty("id"),
            classNavEntry: { id: node.id, label: node.data?.label ?? node.id },
        };
    }

    handleSelectionChange({ nodes: selectedNodes }) {
        if (this.#nodeDragActive) {
            return;
        }
        const boxed = (selectedNodes ?? [])
            .filter(node => node.type === "class")
            .map(node => this.buildEntry(node));
        const entries = this.#pan.boxToggle
            ? toggleSelections(this.#pan.boxPriorSelection, boxed)
            : this.#pan.boxAdditive
              ? mergeSelections(this.#pan.boxPriorSelection, boxed)
              : boxed;
        untrack(() => multiSelectState.setSelection(entries));
    }

    handleSelectionEnd() {
        this.#pan.clearBoxMode();
        this.reflectSelectionToNodes();
        this.#dimOpenClassWhenSelectionEmpty();
    }

    #applyNodeSelection(shouldSelect) {
        let changed = false;
        const next = this.#getNodes().map(node => {
            const selected = shouldSelect(node);
            if (!!node.selected !== selected) {
                changed = true;
                return { ...node, selected };
            }
            return node;
        });
        if (changed) {
            this.#setNodes(next);
        }
    }

    reflectSelectionToNodes() {
        const selectedIds = new Set(
            multiSelectState.getSelected().map(entry => entry.classUuid),
        );
        this.#applyNodeSelection(node => selectedIds.has(node.id));
    }

    #dimOpenClassWhenSelectionEmpty() {
        if (multiSelectState.getSelected().length > 0) {
            return;
        }
        if (!editorState.selectedClass.getProperty("id")) {
            return;
        }
        const isPackage =
            editorState.selectedDiagram.getProperty("type") ===
            DiagramType.PACKAGE;
        editorState.activeSelectionKind.updateValue(
            isPackage ? SelectionLevel.PACKAGE : SelectionLevel.DIAGRAM,
        );
    }

    #routeClassEditor(graphUri, classUuid, classType) {
        eventStack.executeNewestEvent({
            datasetName: editorState.selectedDataset.getValue(),
            graphUri,
            classUuid,
            classType,
        });
    }

    handleNodeClick(nodeClickEvent) {
        this.#contextMenus.close();
        if (this.#suppressClickOpen) {
            this.#suppressClickOpen = false;
            return;
        }
        if (nodeClickEvent.node.type !== "class") {
            return;
        }
        const id = nodeClickEvent.node.id;
        const event = nodeClickEvent.event;
        const graphUri = this.#selectionGraphUri(nodeClickEvent.node);

        if (event?.ctrlKey || event?.metaKey) {
            const entry = this.buildEntry(nodeClickEvent.node);
            multiSelectState.setSelection(
                toggleSelections(this.#pan.boxPriorSelection, [entry]),
            );
            multiSelectState.anchor = entry;
            this.reflectSelectionToNodes();
            this.#dimOpenClassWhenSelectionEmpty();
            event.stopPropagation();
            return;
        }

        const classType =
            editorState.selectedDiagram.getProperty("type") ===
            DiagramType.CROSS_PROFILE
                ? ClassType.MERGED_CLASS
                : ClassType.SINGLE_CLASS;

        if (event?.shiftKey) {
            const entry = this.buildEntry(nodeClickEvent.node);
            multiSelectState.setSelection(
                mergeSelections(this.#pan.boxPriorSelection, [entry]),
            );
            multiSelectState.anchor = entry;
            this.reflectSelectionToNodes();
            event.stopPropagation();
            return;
        }

        multiSelectState.setSelection([this.buildEntry(nodeClickEvent.node)]);
        this.reflectSelectionToNodes();

        this.#nodeOrder.bringToFrontTemporarily(id);

        if (!editorState.selectedClass.getProperty("id")) {
            eventStack.executeNewestEvent(id);
            editorState.selectedClassDataset.updateValue(
                editorState.selectedDataset.getValue(),
            );
            editorState.selectedClassGraph.updateValue(graphUri);
            editorState.selectedClass.updateValue({
                type: classType,
                id: id,
            });
        } else {
            this.#routeClassEditor(graphUri, id, classType);
        }

        event.stopPropagation();
    }

    escapeClearSelection = (...args) => {
        if (args.length > 0) {
            eventStack.removeEvent(this.escapeClearSelection);
            eventStack.executeNewestEvent(...args);
            if (multiSelectState.getSelected().length > 0) {
                eventStack.addEvent(this.escapeClearSelection);
            }
            return;
        }
        const hadOpenClass = !!editorState.selectedClass.getProperty("id");
        this.#contextMenus.close();
        this.#pan.resetBox();
        multiSelectState.clear();
        this.reflectSelectionToNodes();
        eventStack.removeEvent(this.escapeClearSelection);
        if (hadOpenClass) {
            eventStack.executeNewestEvent();
        }
    };
}
