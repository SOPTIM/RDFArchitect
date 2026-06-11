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
import { classStore } from "$lib/stores/ClassStore.ts";

export async function saveApiEnumEntryToBackend(
    dataset,
    graph,
    classUUID,
    enumEntry,
    isNewEnumEntry,
) {
    const res = isNewEnumEntry
        ? await classStore.addEnumEntry(dataset, graph, classUUID, enumEntry)
        : await classStore.replaceEnumEntry(
              dataset,
              graph,
              classUUID,
              enumEntry,
          );

    try {
        if (!res.error) {
            const enumEntryUUID = res.data;
            console.log("Successfully saved enum entry:", enumEntryUUID);
            return { ok: true, enumEntryUUID };
        }

        const errorText = await res.error;
        console.error("Could not save enum entry:", errorText);
        return { ok: false, errorText };
    } finally {
        editorState.selectedClassUUID.trigger();
        editorState.selectedDiagram.trigger();
    }
}
