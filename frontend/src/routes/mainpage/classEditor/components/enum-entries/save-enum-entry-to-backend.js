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
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { editorState } from "$lib/sharedState.svelte.js";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

export async function saveApiEnumEntryToBackend(
    dataset,
    graph,
    classUUID,
    enumEntry,
    isNewEnumEntry,
) {
    const saveEnumEntryCall = isNewEnumEntry
        ? bec.postEnumEntry(dataset, graph, classUUID, enumEntry)
        : bec.putEnumEntry(dataset, graph, classUUID, enumEntry);

    try {
        const res = await saveEnumEntryCall;
        if (res.ok) {
            const enumEntryUUID = await res.json();
            console.log("Successfully saved enum entry:", enumEntryUUID);
            return { ok: true, enumEntryUUID };
        }

        const errorText = await res.text();
        console.error("Could not save enum entry:", errorText);
        return { ok: false, errorText };
    } finally {
        editorState.selectedClassUUID.trigger();
        editorState.selectedPackageUUID.trigger();
    }
}
