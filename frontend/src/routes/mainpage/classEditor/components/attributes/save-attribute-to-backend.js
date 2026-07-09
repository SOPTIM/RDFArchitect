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

export async function saveApiAttributeToBackend(
    dataset,
    graph,
    classUUID,
    attribute,
    isNewAttribute,
) {
    const res = isNewAttribute
        ? await classStore.addAttribute(dataset, graph, classUUID, attribute)
        : await classStore.replaceAttribute(
              dataset,
              graph,
              classUUID,
              attribute,
          );

    try {
        if (!res.error) {
            const attributeUUID = res.data;
            console.log("Successfully saved attribute:", attributeUUID);
            return { ok: true, attributeUUID };
        }

        console.error("Could not save attribute:", res.error);
        return { ok: false, error: res.error };
    } finally {
        editorState.selectedClass.trigger();
        editorState.selectedDiagram.trigger();
    }
}
