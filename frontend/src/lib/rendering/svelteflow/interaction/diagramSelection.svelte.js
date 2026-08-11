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
import { renderOptions } from "$lib/renderOptions.svelte.js";
import {
    ClassType,
    DiagramType,
    editorState,
    isMergedDiagramType,
    mergeSelections,
    multiSelectState,
    SelectionLevel,
    SINGLE_SCHEMA_DIAGRAM_TYPES,
    toggleSelections,
} from "$lib/sharedState.svelte.js";

import { propertySelection } from "./propertyInteraction.svelte.js";

export const DIAGRAM_SELECTION_CONTEXT = "diagramSelection";

export class DiagramSelectionController {
    #getNodes;
    #setNodes;
    #getEdges;
    #setEdges;
    #pan;
    #contextMenus;
    #nodeOrder;

    #nodeDragActive = false;
    #suppressClickOpen = false;

    constructor({
        getNodes,
        setNodes,
        getEdges,
        setEdges,
        pan,
        contextMenus,
        nodeOrder,
    }) {
        this.#getNodes = getNodes;
        this.#setNodes = setNodes;
        this.#getEdges = getEdges;
        this.#setEdges = setEdges;
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

    // SvelteFlow drags every selected node along with a label and clears that selection too late,
    // so it is taken off the nodes while a label is pressed and put back on release.
    notifyLabelPress() {
        this.#nodeDragActive = true;
        this.#applyNodeSelection(() => false);
    }

    notifyLabelRelease() {
        this.#nodeDragActive = false;
        this.reflectSelectionToNodes();
    }

    #selectionGraphUri(node) {
        return isMergedDiagramType(
            editorState.selectedDiagram.getProperty("type"),
        )
            ? null
            : node.data?.graphUri;
    }

    buildEntry(node) {
        return {
            workspaceName: editorState.selectedWorkspace.getValue(),
            graphUri: this.#selectionGraphUri(node),
            classUuid: node.id,
            classLabel: node.data?.label ?? node.id,
            packageId: editorState.selectedDiagram.getProperty("id"),
            classNavEntry: { id: node.id, label: node.data?.label ?? node.id },
        };
    }

    handleSelectionChange(
        { nodes: selectedNodes, edges: selectedEdges },
        isBoxSelection,
    ) {
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

        const selectedClassIds = new Set(entries.map(entry => entry.classUuid));
        this.#reconcileEdgeSelection(
            selectedClassIds,
            isBoxSelection ? selectedEdges : null,
        );
    }

    #reconcileEdgeSelection(selectedClassIds, autoSelectedEdges) {
        const autoSelectedIds = new Set(
            (autoSelectedEdges ?? []).map(edge => edge.id),
        );
        let changed = false;
        const next = this.#getEdges().map(edge => {
            const shouldSelect =
                selectedClassIds.has(edge.source) &&
                selectedClassIds.has(edge.target);
            if (!!edge.selected === shouldSelect) {
                return edge;
            }
            if (!shouldSelect && !autoSelectedIds.has(edge.id)) {
                return edge;
            }
            changed = true;
            return { ...edge, selected: shouldSelect };
        });
        if (changed) {
            this.#setEdges(next);
        }
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
        this.#reconcileEdgeSelection(selectedIds, null);
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
            workspaceName: editorState.selectedWorkspace.getValue(),
            graphUri,
            classUuid,
            classType,
        });
    }

    handleNodeClick(nodeClickEvent) {
        this.#contextMenus.close();
        propertySelection.clear();
        if (this.#suppressClickOpen) {
            this.#suppressClickOpen = false;
            return;
        }
        if (nodeClickEvent.node.type !== "class") {
            return;
        }
        const event = nodeClickEvent.event;

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

        this.openClass(nodeClickEvent.node);
        event.stopPropagation();
    }

    openClass(node) {
        const id = node.id;
        const graphUri = this.#selectionGraphUri(node);
        const classType = this.#classTypeForDiagram();

        multiSelectState.setSelection([this.buildEntry(node)]);
        this.reflectSelectionToNodes();

        this.#nodeOrder.bringToFrontTemporarily(id);

        if (!editorState.selectedClass.getProperty("id")) {
            eventStack.executeNewestEvent(id);
            editorState.selectedClassWorkspace.updateValue(
                editorState.selectedWorkspace.getValue(),
            );
            editorState.selectedClassGraph.updateValue(graphUri);
            editorState.selectedClass.updateValue({
                type: classType,
                id: id,
            });
        } else {
            this.#routeClassEditor(graphUri, id, classType);
        }
    }

    #classTypeForDiagram() {
        const diagramType = editorState.selectedDiagram.getProperty("type");
        const isMergedContext =
            isMergedDiagramType(diagramType) ||
            (SINGLE_SCHEMA_DIAGRAM_TYPES.includes(diagramType) &&
                renderOptions.get("includePropertiesFromOtherProfiles"));
        return isMergedContext
            ? ClassType.MERGED_CLASS
            : ClassType.SINGLE_CLASS;
    }

    hasClearableSelection() {
        return (
            multiSelectState.getSelected().length > 0 ||
            !!propertySelection.current
        );
    }

    escapeClearSelection = (...args) => {
        if (args.length > 0) {
            eventStack.removeEvent(this.escapeClearSelection);
            eventStack.executeNewestEvent(...args);
            if (this.hasClearableSelection()) {
                eventStack.addEvent(this.escapeClearSelection);
            }
            return;
        }
        this.#contextMenus.close();
        if (propertySelection.clear()) {
            return;
        }
        const hadOpenClass = !!editorState.selectedClass.getProperty("id");
        this.#pan.resetBox();
        multiSelectState.clear();
        this.reflectSelectionToNodes();
        eventStack.removeEvent(this.escapeClearSelection);
        if (hadOpenClass) {
            eventStack.executeNewestEvent();
        }
    };
}
