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
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";

/**
 * Puts a class in front of the user: its package's diagram, the class selected in the editor, and
 * the diagram scrolled to it.
 *
 * Opening the class editor alone left the user looking at whatever diagram they had been on, with
 * the class they asked for nowhere in sight, so all three are the one request. Used wherever a term
 * in a constraint is followed to the class it belongs to — the workbench, and the class and
 * property constraint dialogs.
 */
export function showClass(workspaceName, graphUri, classUUID, packageUUID) {
    editorState.selectPackage(
        workspaceName,
        graphUri,
        packageUUID ?? "default",
    );
    editorState.selectedClassWorkspace.updateValue(workspaceName);
    editorState.selectedClassGraph.updateValue(graphUri);
    editorState.selectedClass.updateValue({
        type: ClassType.SINGLE_CLASS,
        id: classUUID,
    });
    editorState.focusedClassUUID.updateValue(classUUID);
    editorState.selectedWorkspace.trigger();
    editorState.selectedGraph.trigger();
    editorState.selectedDiagram.trigger();
    forceReloadTrigger.trigger();
}
