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

import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
import { graphStore } from "$lib/stores/graphStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

import { editorState, forceReloadTrigger } from "../sharedState.svelte.js";

/**
 * Opens what an import wrote and has everything reading the workspace load it again.
 *
 * Shared by the two import dialogs rather than written out in each: they differ in what they
 * import and not at all in what happens afterwards, and a selection field added to one of them
 * has to be cleared by both.
 *
 * @param {string[]} importedGraphUris the schemas the import wrote, the first of which is opened
 * @param {string} workspaceName the workspace it wrote them into
 */
export function applyImportResult(importedGraphUris, workspaceName) {
    editorState.selectedWorkspace.updateValue(workspaceName);
    editorState.selectedGraph.updateValue(importedGraphUris[0] || null);
    editorState.selectedDiagram.updateValue({ type: null, id: null });
    editorState.selectedClassWorkspace.updateValue(null);
    editorState.selectedClassGraph.updateValue(null);
    editorState.selectedClass.updateValue({ type: null, id: null });

    graphStore.invalidateWorkspace(workspaceName);
    workspaceStore.invalidate();
    crossProfileStore.invalidateWorkspace(workspaceName);
    forceReloadTrigger.trigger();
}
