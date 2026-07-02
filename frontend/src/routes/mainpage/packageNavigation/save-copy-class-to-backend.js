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
    ClassType,
    copyState,
    DiagramType,
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

export async function saveCopyClass(
    datasetName,
    graphURI,
    packageDTO,
    copyAbstract,
    copyAttributes,
    copyAssociations,
) {
    const entries = copyState.getEntries();
    if (entries.length === 0) return false;
    const payload = {
        targetPackage: packageDTO?.uuid === "default" ? null : packageDTO,
        copyAsAbstract: copyAbstract,
        copyAttributes: copyAttributes,
        copyAssociations: copyAssociations,
        sources: entries.map(e => ({
            sourceDatasetName: e.datasetName,
            sourceGraphURI: e.graphURI,
            classUUID: e.classUUID,
        })),
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
                id: packageDTO?.uuid ?? "default",
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
