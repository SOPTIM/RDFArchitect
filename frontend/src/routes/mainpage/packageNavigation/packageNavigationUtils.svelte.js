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

function normContext(workspace, graph) {
    return {
        workspaceLabel: workspace?.label ?? workspace,
        graphUri: graph ? getUri(graph) : null,
    };
}

export function isSelectedWorkspace(workspace) {
    if (workspace.label !== undefined) {
        workspace = workspace.label;
    }
    return editorState.selectedWorkspace.getValue() === workspace;
}

export function isSelectedGraph(workspace, graph) {
    return (
        isSelectedWorkspace(workspace) &&
        editorState.selectedGraph.getValue() === getUri(graph)
    );
}

export function isSelectedPackage(workspace, graph, pack) {
    return (
        isSelectedGraph(workspace, graph) &&
        editorState.selectedDiagram.getProperty("id") === getPackageId(pack)
    );
}

export function isSelectedCustomDiagram(workspace, graph, diagram) {
    return graph
        ? isSelectedGraph(workspace, graph) &&
              editorState.selectedDiagram.getProperty("id") ===
                  diagram.diagramId
        : isSelectedWorkspace(workspace) &&
              editorState.selectedDiagram.getProperty("id") ===
                  diagram.diagramId;
}

export function isSelectedClass(workspace, graph, cls) {
    if (typeof cls === "string") {
        cls = { uuid: cls };
    }
    const { workspaceLabel, graphUri } = normContext(workspace, graph);
    return (
        editorState.selectedClass.getProperty("id") === cls.uuid &&
        editorState.selectedClassWorkspace.getValue() === workspaceLabel &&
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
        type === DiagramType.CUSTOM_WORKSPACE_DIAGRAM
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
    if (editorState.selectedWorkspace.getValue()) {
        return SelectionLevel.WORKSPACE;
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
        case SelectionLevel.WORKSPACE:
            if (editorState.selectedWorkspace.getValue())
                return SelectionLevel.WORKSPACE;
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
            workspaceName: editorState.selectedClassWorkspace.getValue(),
            graphUri: editorState.selectedClassGraph.getValue(),
            classUuid: uuid,
            packageId: editorState.selectedDiagram.getProperty("id"),
        },
    ];
}

function someOpenClass(workspaceLabel, graphUri = ANY_GRAPH, matchUuid = null) {
    return getSelectedClasses().some(
        c =>
            c.workspaceName === workspaceLabel &&
            (graphUri === ANY_GRAPH || c.graphUri === graphUri) &&
            (!matchUuid || matchUuid(c.classUuid)),
    );
}

export function classHighlight(workspace, graph, classUuid) {
    const { workspaceLabel, graphUri } = normContext(workspace, graph);
    const uuid = classUuid?.uuid ?? classUuid;
    const inSelection = someOpenClass(
        workspaceLabel,
        graphUri,
        u => u === uuid,
    );
    if (inSelection && deepestSelectedLevel() === SelectionLevel.CLASS) {
        return "active";
    }
    if (inSelection || isSelectedClass(workspace, graph, uuid)) {
        return "secondary";
    }
    return null;
}

/**
 * Highlight state of a graph entry: "active" when the graph itself is the most
 * specific selection, "ancestor" when something inside it is.
 */
export function graphHighlight(workspace, graph) {
    const { workspaceLabel, graphUri } = normContext(workspace, graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        return someOpenClass(workspaceLabel, graphUri) ? "ancestor" : null;
    }
    if (!isSelectedGraph(workspaceLabel, graph)) {
        return null;
    }
    return level === SelectionLevel.GRAPH ? "active" : "ancestor";
}

export function packageHighlight(workspace, graph, pack, classEntries = []) {
    const { workspaceLabel, graphUri } = normContext(workspace, graph);
    const level = deepestSelectedLevel();
    if (level === SelectionLevel.CLASS) {
        const childUuids = new Set(
            (classEntries ?? []).map(c => c?.id ?? c?.uuid ?? c),
        );
        return someOpenClass(workspaceLabel, graphUri, u => childUuids.has(u))
            ? "ancestor"
            : null;
    }
    if (
        level === SelectionLevel.PACKAGE &&
        isSelectedPackage(workspace, graph, pack)
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
