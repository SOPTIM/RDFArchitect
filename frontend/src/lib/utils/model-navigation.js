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
    ClassType,
    DiagramType,
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";

/**
 * Opens the package diagram containing a class and selects that class in it.
 * Used by the search bar and by `class` deep links on /mainpage.
 *
 * With `propertyUUID`, the class editor additionally reveals that attribute, association or enum
 * entry — how a search hit or a deep link on a property lands on the row the user asked for.
 *
 * @param {{datasetName: string, graphUri: string, packageUUID: string | null | undefined, classUUID: string, propertyUUID?: string | null}} target
 */
export function navigateToClass({
    datasetName,
    graphUri,
    packageUUID,
    classUUID,
    propertyUUID = null,
}) {
    editorState.selectedDataset.updateValue(datasetName);
    editorState.selectedGraph.updateValue(graphUri);
    editorState.selectedDiagram.updateValue({
        type: DiagramType.PACKAGE,
        id: packageUUID ?? "default",
    });
    editorState.selectedClassDataset.updateValue(datasetName);
    editorState.selectedClassGraph.updateValue(graphUri);
    editorState.selectedClass.updateValue({
        type: ClassType.SINGLE_CLASS,
        id: classUUID,
    });
    editorState.focusedClassUUID.updateValue(classUUID);
    editorState.focusedPropertyUUID.updateValue(propertyUUID);
    triggerNavigationReload();
}

/**
 * Opens a package diagram without selecting a class.
 *
 * @param {{datasetName: string, graphUri: string, packageUUID: string | null | undefined}} target
 */
export function navigateToPackage({ datasetName, graphUri, packageUUID }) {
    editorState.selectedDataset.updateValue(datasetName);
    editorState.selectedGraph.updateValue(graphUri);
    editorState.selectedClass.updateValue({ type: null, id: null });
    editorState.focusedClassUUID.updateValue(null);
    editorState.focusedPropertyUUID.updateValue(null);
    editorState.selectedDiagram.updateValue({
        type: DiagramType.PACKAGE,
        id: packageUUID ?? "default",
    });
    triggerNavigationReload();
}

function triggerNavigationReload() {
    editorState.selectedDataset.trigger();
    editorState.selectedGraph.trigger();
    editorState.selectedDiagram.trigger();
    forceReloadTrigger.trigger();
}
