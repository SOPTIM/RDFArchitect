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
    DiagramType,
    editorState,
    multiSelectState,
    SelectionLevel,
} from "$lib/sharedState.svelte.js";

export function isSelectedDataset(dataset) {
    if (dataset.label !== undefined) {
        dataset = dataset.label;
    }
    return editorState.selectedDataset.getValue() === dataset;
}

export function isSelectedGraph(dataset, graph) {
    return (
        isSelectedDataset(dataset) &&
        editorState.selectedGraph.getValue() === getUri(graph)
    );
}

export function isSelectedPackage(dataset, graph, pack) {
    return (
        isSelectedGraph(dataset, graph) &&
        editorState.selectedDiagram.getProperty("id") === getPackageId(pack)
    );
}

export function isSelectedCustomDiagram(dataset, graph, diagram) {
    return graph
        ? isSelectedGraph(dataset, graph) &&
              editorState.selectedDiagram.getProperty("id") ===
                  diagram.diagramId
        : isSelectedDataset(dataset) &&
              editorState.selectedDiagram.getProperty("id") ===
                  diagram.diagramId;
}

export function isSelectedClass(dataset, graph, cls) {
    if (typeof cls === "string") {
        cls = { uuid: cls };
    }
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = graph ? getUri(graph) : null;
    return (
        editorState.selectedClass.getProperty("id") === cls.uuid &&
        editorState.selectedClassDataset.getValue() === datasetLabel &&
        editorState.selectedClassGraph.getValue() === graphUri
    );
}

function isPackageSelectedAsDiagram() {
    return (
        editorState.selectedDiagram.getProperty("type") ===
            DiagramType.PACKAGE &&
        !!editorState.selectedDiagram.getProperty("id")
    );
}

function isCustomDiagramSelected() {
    const type = editorState.selectedDiagram.getProperty("type");
    return (
        type === DiagramType.CUSTOM_GRAPH_DIAGRAM ||
        type === DiagramType.CUSTOM_DATASET_DIAGRAM
    );
}

/** Most-specific context level (package/diagram/graph/dataset), ignoring class. */
function inferContextLevel() {
    if (isPackageSelectedAsDiagram()) {
        return SelectionLevel.PACKAGE;
    }
    if (isCustomDiagramSelected()) {
        return SelectionLevel.DIAGRAM;
    }
    if (editorState.selectedGraph.getValue()) {
        return SelectionLevel.GRAPH;
    }
    if (editorState.selectedDataset.getValue()) {
        return SelectionLevel.DATASET;
    }
    return SelectionLevel.NONE;
}

/** Deepest level from the editor state alone, ignoring recency. */
export function inferSelectionLevel() {
    if (editorState.selectedClass.getProperty("id")) {
        return SelectionLevel.CLASS;
    }
    return inferContextLevel();
}

/**
 * Most-specific level holding a selection. Prefers the last-clicked resource
 * (activeSelectionKind) while it still exists, else falls back to inference.
 */
function deepestSelectedLevel() {
    if (multiSelectState.getSelected().length > 0) {
        return SelectionLevel.CLASS;
    }
    switch (editorState.activeSelectionKind.getValue()) {
        case SelectionLevel.CLASS:
            if (editorState.selectedClass.getProperty("id"))
                return SelectionLevel.CLASS;
            break;
        case SelectionLevel.PACKAGE:
            if (isPackageSelectedAsDiagram()) return SelectionLevel.PACKAGE;
            break;
        case SelectionLevel.DIAGRAM:
            if (isCustomDiagramSelected()) return SelectionLevel.DIAGRAM;
            break;
        case SelectionLevel.GRAPH:
            if (editorState.selectedGraph.getValue())
                return SelectionLevel.GRAPH;
            break;
        case SelectionLevel.DATASET:
            if (editorState.selectedDataset.getValue())
                return SelectionLevel.DATASET;
            break;
    }
    return inferSelectionLevel();
}

/**
 * The open/selected classes: the multi-selection set, or the single selected
 * class. Always stay highlighted, independent of the last-clicked resource.
 */
function getSelectedClasses() {
    const multi = multiSelectState.getSelected();
    if (multi.length > 0) {
        return multi;
    }
    const uuid = editorState.selectedClass.getProperty("id");
    if (!uuid) {
        return [];
    }
    return [
        {
            datasetName: editorState.selectedClassDataset.getValue(),
            graphUri: editorState.selectedClassGraph.getValue(),
            classUuid: uuid,
            packageId: editorState.selectedDiagram.getProperty("id"),
        },
    ];
}

/**
 * Class highlight: "active" (blue) when it is the most-specific selection,
 * "secondary" (light blue) when open but another resource is active, else null.
 */
export function classHighlight(dataset, graph, classUuid) {
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = graph ? getUri(graph) : null;
    const uuid = classUuid?.uuid ?? classUuid;
    const isOpen = getSelectedClasses().some(
        c =>
            c.datasetName === datasetLabel &&
            c.graphUri === graphUri &&
            c.classUuid === uuid,
    );
    if (!isOpen) {
        return null;
    }
    return deepestSelectedLevel() === SelectionLevel.CLASS
        ? "active"
        : "secondary";
}

/**
 * Dataset highlight: "active" (most specific, blue), "ancestor" (a less-specific
 * level in the path, grey) or null.
 */
export function datasetHighlight(dataset) {
    const datasetLabel = dataset?.label ?? dataset;
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        return getSelectedClasses().some(c => c.datasetName === datasetLabel)
            ? "ancestor"
            : null;
    }
    if (!isSelectedDataset(datasetLabel)) {
        return null;
    }
    return level === SelectionLevel.DATASET ? "active" : "ancestor";
}

/** Highlight state of a graph entry (see {@link datasetHighlight}). */
export function graphHighlight(dataset, graph) {
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = getUri(graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        return getSelectedClasses().some(
            c => c.datasetName === datasetLabel && c.graphUri === graphUri,
        )
            ? "ancestor"
            : null;
    }
    if (!isSelectedGraph(datasetLabel, graph)) {
        return null;
    }
    return level === SelectionLevel.GRAPH ? "active" : "ancestor";
}

/**
 * Package highlight (see {@link datasetHighlight}). The grey "ancestor" state is
 * decided by membership - greyed when the package contains an open class - which
 * is more reliable than deriving the class's package from the editor state.
 * `classEntries` are the package's child class nav entries (objects with `id`,
 * or plain uuid strings).
 */
export function packageHighlight(dataset, graph, pack, classEntries = []) {
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = getUri(graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        const childUuids = new Set(
            (classEntries ?? []).map(c => c?.id ?? c?.uuid ?? c),
        );
        return getSelectedClasses().some(
            c =>
                c.datasetName === datasetLabel &&
                c.graphUri === graphUri &&
                childUuids.has(c.classUuid),
        )
            ? "ancestor"
            : null;
    }
    if (
        level === SelectionLevel.PACKAGE &&
        isSelectedPackage(dataset, graph, pack)
    ) {
        return "active";
    }
    return null;
}

export function getUri(resource) {
    if (typeof resource === "string") {
        return resource;
    }
    const uri = resource.uri ? resource.uri : resource;
    return (uri.prefix ?? "") + (uri.suffix ?? "");
}

export function getPackageId(pack) {
    if (typeof pack === "string") {
        return pack;
    }
    return pack?.uuid ?? "default";
}
