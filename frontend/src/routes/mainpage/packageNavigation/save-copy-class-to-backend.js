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
import {
    copyState,
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

export async function saveCopyClass(
    datasetName,
    graphURI,
    packageDTO,
    copyAbstract,
) {
    if (!copyState.classUUID || !copyState.graphURI || !copyState.datasetName)
        return false;
    const payload = {
        targetDatasetName: datasetName,
        targetGraphURI: graphURI,
        targetPackage: packageDTO,
        copyAsAbstract: copyAbstract,
    };
    try {
        const res = await bec.postCopyClass(
            copyState.datasetName.getValue(),
            copyState.graphURI.getValue(),
            copyState.classUUID.getValue(),
            payload,
        );
        if (res.ok) {
            const uuid = await res.text();
            editorState.selectedClassDataset.updateValue(datasetName);
            editorState.selectedClassGraph.updateValue(graphURI);
            editorState.selectedPackageUUID.updateValue(packageDTO.uuid);
            editorState.selectedClassUUID.updateValue(uuid);
        } else {
            const errorText = await res.text();
            console.error("Could not copy class:", errorText);
        }
    } finally {
        forceReloadTrigger.trigger();
    }
}
