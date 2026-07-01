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
    if (!copyState.classUUID || !copyState.graphURI || !copyState.datasetName)
        return false;
    const payload = {
        targetDatasetName: datasetName,
        targetGraphURI: graphURI,
        targetPackage: packageDTO?.uuid === "default" ? null : packageDTO,
        copyAsAbstract: copyAbstract,
        copyAttributes: copyAttributes,
        copyAssociations: copyAssociations,
    };
    try {
        const res = await bec.postCopyClass(
            copyState.datasetName.getValue(),
            copyState.graphURI.getValue(),
            copyState.classUUID.getValue(),
            payload,
        );
        if (res.ok) {
            const json = await res.json();
            const uuid = json.uuid;
            const name = json.name;
            editorState.selectedDataset.updateValue(datasetName);
            editorState.selectedClassDataset.updateValue(datasetName);
            editorState.selectedGraph.updateValue(graphURI);
            editorState.selectedClassGraph.updateValue(graphURI);
            editorState.selectedDiagram.updateValue({
                type: DiagramType.PACKAGE,
                id: packageDTO?.uuid ?? "default",
            });
            editorState.selectedClass.updateValue({
                type: ClassType.SINGLE_CLASS,
                id: uuid,
            });
            toastStore.success("Class pasted", `"${name}" was pasted.`);
        } else {
            const errorText = await res.text();
            console.error("Could not copy class:", errorText);
            toastStore.error("Paste failed", `Could not paste class.`);
        }
    } finally {
        forceReloadTrigger.trigger();
    }
}
