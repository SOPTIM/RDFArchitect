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

import { previewPaste } from "$lib/api/generated/index.ts";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import {
    DEFAULT_PASTE_OPTIONS,
    PASTE_REFERENCE_KINDS,
} from "$lib/pasteOptions.js";
import {
    ClassType,
    copyState,
    DiagramType,
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";
import { classStore } from "$lib/stores/classStore.ts";
import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";

export const PASTE_PREVIEW_FAILED = Symbol("pastePreviewFailed");

function sourcesOf(entries) {
    return entries.map(e => ({
        sourceDatasetName: e.workspaceName,
        sourceGraphURI: e.graphURI,
        classUUID: e.classUUID,
    }));
}

/**
 * What the paste would carry along, or null when it needs no decision and can just run. Only what
 * the chosen paste variant copies can make the paste incomplete, so only that decides. A preview
 * that fails reports PASTE_PREVIEW_FAILED and has already told the user that the paste failed.
 */
export async function loadPastePreview(workspaceName, graphURI, options) {
    const entries = copyState.getEntries();
    const copiesAnything = PASTE_REFERENCE_KINDS.some(
        ({ option }) => options[option],
    );
    if (entries.length === 0 || !copiesAnything) return null;
    try {
        const { data, error } = await previewPaste({
            path: { workspaceName, graphURI },
            body: {
                sources: sourcesOf(entries),
            },
        });
        if (error) {
            return reportPreviewFailure(error);
        }
        const preview = data;
        const missing = PASTE_REFERENCE_KINDS.some(
            ({ kind, option }) =>
                options[option] && preview.missing?.[kind]?.length > 0,
        );
        return missing ? preview : null;
    } catch (e) {
        return reportPreviewFailure(e);
    }
}

function reportPreviewFailure(reason) {
    console.error("Could not preview paste:", reason);
    toastStore.error("Paste failed", "Could not paste classes.");
    return PASTE_PREVIEW_FAILED;
}

export async function saveCopyClass(
    workspaceName,
    graphURI,
    targetPackageUUID,
    options,
) {
    const entries = copyState.getEntries();
    if (entries.length === 0) return false;
    const payload = {
        targetPackageUUID:
            targetPackageUUID === "default"
                ? null
                : (targetPackageUUID ?? null),
        ...DEFAULT_PASTE_OPTIONS,
        ...options,
        sources: sourcesOf(entries),
    };
    try {
        const { data, error } = await classStore.saveCopyClass(
            workspaceName,
            graphURI,
            payload,
        );
        if (!error) {
            const pasted = data;
            crossProfileStore.invalidateWorkspace(workspaceName);
            editorState.selectedWorkspace.updateValue(workspaceName);
            editorState.selectedClassWorkspace.updateValue(workspaceName);
            editorState.selectedGraph.updateValue(graphURI);
            editorState.selectedClassGraph.updateValue(graphURI);
            editorState.selectedDiagram.updateValue({
                type: DiagramType.PACKAGE,
                id: targetPackageUUID ?? "default",
            });
            const last = pasted[pasted.length - 1];
            if (last) {
                editorState.selectedClass.updateValue({
                    type: ClassType.SINGLE_CLASS,
                    id: last.uuid,
                });
            }
            return true;
        }
    } finally {
        forceReloadTrigger.trigger();
    }
}
