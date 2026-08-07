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

import { BackendConnection } from "$lib/api/backend.js";
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
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

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

function sourcesOf(entries) {
    return entries.map(e => ({
        sourceDatasetName: e.datasetName,
        sourceGraphURI: e.graphURI,
        classUUID: e.classUUID,
    }));
}

/**
 * What the paste would carry along, or null when it needs no decision and can just run. Only what
 * the chosen paste variant copies can make the paste incomplete, so only that decides. A failing
 * preview reports null too: the paste still works and skips whatever the target schema already
 * contains.
 */
export async function loadPastePreview(datasetName, graphURI, options) {
    const entries = copyState.getEntries();
    const copiesAnything = PASTE_REFERENCE_KINDS.some(
        ({ option }) => options[option],
    );
    if (entries.length === 0 || !copiesAnything) return null;
    try {
        const res = await bec.postPastePreview(datasetName, graphURI, {
            sources: sourcesOf(entries),
        });
        if (!res.ok) return null;
        const preview = await res.json();
        const missing = PASTE_REFERENCE_KINDS.some(
            ({ kind, option }) =>
                options[option] && preview.missing?.[kind]?.length > 0,
        );
        return missing ? preview : null;
    } catch (e) {
        console.error("Could not preview paste:", e);
        return null;
    }
}

export async function saveCopyClass(
    datasetName,
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
        const res = await bec.postPasteClasses(datasetName, graphURI, payload);
        if (res.ok) {
            const pasted = await res.json();
            editorState.selectedDataset.updateValue(datasetName);
            editorState.selectedClassDataset.updateValue(datasetName);
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
            if (pasted.length === 1) {
                toastStore.success(
                    "Class pasted",
                    `"${pasted[0].name}" was pasted.`,
                );
            } else {
                toastStore.success(
                    "Classes pasted",
                    `${pasted.length} classes were pasted.`,
                );
            }
            return true;
        } else {
            const errorText = await res.text();
            console.error("Could not paste classes:", errorText);
            toastStore.error("Paste failed", `Could not paste classes.`);
            return false;
        }
    } finally {
        forceReloadTrigger.trigger();
    }
}
