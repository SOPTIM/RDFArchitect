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

export async function saveApiAssociationToBackend(
    dataset,
    graph,
    classUUID,
    associationDTO,
    isNewAssociation,
) {
    const res = isNewAssociation
        ? await classStore.addAssociationPair(
              dataset,
              graph,
              classUUID,
              associationDTO,
          )
        : await classStore.replaceAssociationPair(
              dataset,
              graph,
              classUUID,
              associationDTO,
          );

    try {
        if (!res.error) {
            const associationUUIDs = res.data;
            console.log(
                "Successfully saved association:",
                associationUUIDs.fromUUID,
                associationUUIDs.toUUID,
            );
            return { ok: true, associationUUIDs };
        }

        const errorText = await res.error;
        console.error("Could not save association:", errorText);
        return { ok: false, errorText };
    } finally {
        editorState.selectedClassUUID.trigger();
        editorState.selectedDiagram.trigger();
    }
}
