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

const ANY_GRAPH = Symbol("anyGraph");

function normContext(dataset, graph) {
    return {
        datasetLabel: dataset?.label ?? dataset,
        graphUri: graph ? getUri(graph) : null,
    };
}

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
    const { datasetLabel, graphUri } = normContext(dataset, graph);
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

export function inferSelectionLevel() {
    if (editorState.selectedClass.getProperty("id")) {
        return SelectionLevel.CLASS;
    }
    return inferContextLevel();
}

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

function someOpenClass(datasetLabel, graphUri = ANY_GRAPH, matchUuid = null) {
    return getSelectedClasses().some(
        c =>
            c.datasetName === datasetLabel &&
            (graphUri === ANY_GRAPH || c.graphUri === graphUri) &&
            (!matchUuid || matchUuid(c.classUuid)),
    );
}

export function classHighlight(dataset, graph, classUuid) {
    const { datasetLabel, graphUri } = normContext(dataset, graph);
    const uuid = classUuid?.uuid ?? classUuid;
    const inSelection = someOpenClass(datasetLabel, graphUri, u => u === uuid);
    if (inSelection && deepestSelectedLevel() === SelectionLevel.CLASS) {
        return "active";
    }
    if (inSelection || isSelectedClass(dataset, graph, uuid)) {
        return "secondary";
    }
    return null;
}

export function datasetHighlight(dataset) {
    const { datasetLabel } = normContext(dataset);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        return someOpenClass(datasetLabel) ? "ancestor" : null;
    }
    if (!isSelectedDataset(datasetLabel)) {
        return null;
    }
    return level === SelectionLevel.DATASET ? "active" : "ancestor";
}

/** Highlight state of a graph entry (see {@link datasetHighlight}). */
export function graphHighlight(dataset, graph) {
    const { datasetLabel, graphUri } = normContext(dataset, graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        return someOpenClass(datasetLabel, graphUri) ? "ancestor" : null;
    }
    if (!isSelectedGraph(datasetLabel, graph)) {
        return null;
    }
    return level === SelectionLevel.GRAPH ? "active" : "ancestor";
}

export function packageHighlight(dataset, graph, pack, classEntries = []) {
    const { datasetLabel, graphUri } = normContext(dataset, graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        const childUuids = new Set(
            (classEntries ?? []).map(c => c?.id ?? c?.uuid ?? c),
        );
        return someOpenClass(datasetLabel, graphUri, u => childUuids.has(u))
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
