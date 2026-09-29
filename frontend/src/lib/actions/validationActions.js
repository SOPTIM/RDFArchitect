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

import {
    validateSchemaInWorkspace,
    validateWorkspace,
} from "$lib/api/generated/index.ts";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import {
    ClassType,
    editorState,
    forceReloadTrigger,
    validationState,
    validationContext,
    ValidationKind,
} from "$lib/sharedState.svelte.js";
import { workspaceState } from "$lib/workspaceState.svelte.js";

import { goto } from "$app/navigation";

/**
 * Validates a whole workspace, or a single schema against the other schemas of
 * its workspace when a graph is given, and shows the result page. Reading every
 * schema of the workspace takes a moment, so the run is announced and a second
 * run is refused while the first is still going.
 */
export async function validateWorkspaceAndShowResult(
    workspaceName,
    graphUri = null,
    schemaLabel = null,
) {
    if (validationState.running.getValue()) {
        return;
    }
    validationState.running.updateValue(true);
    const pending = toastStore.info(
        graphUri
            ? `Validating ${schemaLabel ?? "schema"} against its workspace…`
            : `Validating workspace ${workspaceName}…`,
        undefined,
        { duration: 0 },
    );
    try {
        const response = graphUri
            ? await validateSchemaInWorkspace({
                  path: { datasetName: workspaceName, graphURI: graphUri },
              })
            : await validateWorkspace({ path: { datasetName: workspaceName } });

        if (response.error) {
            console.error(response.error);
            toastStore.error(
                graphUri
                    ? "Something went wrong while validating the schema in its workspace."
                    : "Something went wrong while validating the workspace.",
            );
            return;
        }

        validationState.context.updateValue(
            validationContext(ValidationKind.WORKSPACE, {
                workspace: workspaceName,
                graph: graphUri,
                schemaLabel,
            }),
        );
        validationState.result.updateValue(response.data);
        await goto("/validate");
    } finally {
        toastStore.dismiss(pending);
        validationState.running.updateValue(false);
    }
}

export function canOpenOccurrence(workspaceName, occurrence) {
    return !!workspaceName && !!occurrence?.graphUri && !!occurrence?.classUUID;
}

/**
 * Opens the class a validation finding points to in the class editor of its
 * schema, and shows the package the class belongs to.
 */
export async function openOccurrence(workspaceName, occurrence) {
    workspaceState.activate(workspaceName);
    editorState.selectPackage(
        workspaceName,
        occurrence.graphUri,
        occurrence.packageUUID ?? "default",
    );
    editorState.selectedClassWorkspace.updateValue(workspaceName);
    editorState.selectedClassGraph.updateValue(occurrence.graphUri);
    editorState.selectedClass.updateValue({
        type: ClassType.SINGLE_CLASS,
        id: occurrence.classUUID,
    });
    editorState.focusedClassUUID.updateValue(occurrence.classUUID);
    editorState.markClassActive();
    forceReloadTrigger.trigger();
    await goto("/mainpage");
}
