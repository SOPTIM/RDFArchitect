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
        editorState.selectedPackageUUID.getValue() === getPackageId(pack)
    );
}

export function isSelectedCustomDiagram(dataset, graph, diagram) {
    return graph
        ? isSelectedGraph(dataset, graph) &&
              editorState.selectedCustomDiagramUUID.getValue() ===
                  diagram.diagramId
        : isSelectedDataset(dataset) &&
              editorState.selectedCustomDiagramUUID.getValue() ===
                  diagram.diagramId;
}

export function isSelectedClass(dataset, graph, cls) {
    if (typeof cls === "string") {
        cls = { uuid: cls };
    }
    const datasetLabel = dataset?.label ?? dataset;
    const graphUri = graph ? getUri(graph) : null;
    return (
        editorState.selectedClassUUID.getValue() === cls.uuid &&
        editorState.selectedClassDataset.getValue() === datasetLabel &&
        editorState.selectedClassGraph.getValue() === graphUri
    );
}

export function getUri(resource) {
    if (typeof resource === "string") {
        return resource;
    }
    const uri = resource.uri ? resource.uri : resource;
    return uri.prefix ? uri.prefix + uri.suffix : uri.suffix;
}

export function getPackageId(pack) {
    if (typeof pack === "string") {
        return pack;
    }
    return pack?.uuid ?? "default";
}
