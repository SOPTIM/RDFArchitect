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
import { classStore } from "$lib/stores/classStore.ts";

export async function saveApiAssociationToBackend(
    workspace,
    graph,
    classUUID,
    associationDTO,
    isNewAssociation,
) {
    const res = isNewAssociation
        ? await classStore.addAssociationPair(
            workspace,
              graph,
              classUUID,
              associationDTO,
          )
        : await classStore.replaceAssociationPair(
            workspace,
              graph,
              classUUID,
              associationDTO,
          );

    try {
        if (!res.error) {
            const associationUUIDs = res.data;
            return { ok: true, associationUUIDs };
        }

        return { ok: false };
    } finally {
        editorState.selectedClass.trigger();
        editorState.selectedDiagram.trigger();
    }
}
