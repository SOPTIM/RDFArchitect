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

/** Deepest level derived purely from the editor state, ignoring recency. */
function inferDeepestLevel() {
    if (editorState.selectedClass.getProperty("id")) {
        return "class";
    }
    if (isPackageSelectedAsDiagram()) {
        return "package";
    }
    if (isCustomDiagramSelected()) {
        return "diagram";
    }
    if (editorState.selectedGraph.getValue()) {
        return "graph";
    }
    if (editorState.selectedDataset.getValue()) {
        return "dataset";
    }
    return "none";
}

/**
 * The deepest (most specific) level that currently holds a selection. Honors the
 * resource the user picked last (editorState.activeSelectionKind) so that, e.g.,
 * clicking a package while a class is still selected highlights the package - as
 * long as that selection still exists; otherwise falls back to inference.
 */
function deepestSelectedLevel() {
    if (multiSelectState.getSelected().length > 0) {
        return "class";
    }
    switch (editorState.activeSelectionKind.getValue()) {
        case "class":
            if (editorState.selectedClass.getProperty("id")) return "class";
            break;
        case "package":
            if (isPackageSelectedAsDiagram()) return "package";
            break;
        case "diagram":
            if (isCustomDiagramSelected()) return "diagram";
            break;
        case "graph":
            if (editorState.selectedGraph.getValue()) return "graph";
            break;
        case "dataset":
            if (editorState.selectedDataset.getValue()) return "dataset";
            break;
    }
    return inferDeepestLevel();
}

/**
 * The classes that are currently "open"/selected: the multi-selection set, or
 * the single selected class. These always stay highlighted (they indicate which
 * class is shown), independent of the last-clicked navigation resource. Each
 * entry has datasetName/graphUri/classUuid/packageId.
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
 * Highlight state of a class entry: "active" (blue) when the class is the
 * most-specific active selection, "secondary" (light blue) when it is open but
 * a different resource is the active selection, otherwise null.
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
    return deepestSelectedLevel() === "class" ? "active" : "secondary";
}

/**
 * Highlight state of a dataset entry: "active" (most specific, blue),
 * "ancestor" (a less-specific level in the selection path, grey) or null.
 */
export function datasetHighlight(dataset) {
    const datasetLabel = dataset?.label ?? dataset;
    const level = deepestSelectedLevel();
    if (level === "class") {
        return getSelectedClasses().some(c => c.datasetName === datasetLabel)
            ? "ancestor"
            : null;
    }
    if (!isSelectedDataset(datasetLabel)) {
        return null;
    }
    return level === "dataset" ? "active" : "ancestor";
}

/** Highlight state of a graph entry (see {@link datasetHighlight}). */
export function graphHighlight(dataset, graph) {
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = getUri(graph);
    const level = deepestSelectedLevel();
    if (level === "class") {
        return getSelectedClasses().some(
            c => c.datasetName === datasetLabel && c.graphUri === graphUri,
        )
            ? "ancestor"
            : null;
    }
    if (!isSelectedGraph(datasetLabel, graph)) {
        return null;
    }
    return level === "graph" ? "active" : "ancestor";
}

/**
 * Highlight state of a package entry (see {@link datasetHighlight}). The grey
 * "ancestor" state is decided by membership: the package is greyed when it
 * actually contains an open class. This is more reliable than deriving the
 * class's package from the editor state, which is only correct when the class
 * was opened from its package view. `classEntries` are the package's child class
 * nav entries (objects with an `id`, or plain uuid strings).
 */
export function packageHighlight(dataset, graph, pack, classEntries = []) {
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = getUri(graph);
    const level = deepestSelectedLevel();
    if (level === "class") {
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
    if (level === "package" && isSelectedPackage(dataset, graph, pack)) {
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
