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

/**
 * Shared actions for maintaining one class in several schemas of a workspace.
 *
 * A class is identified across schemas by its uri, so every schema either
 * defines it or does not know it yet. Extending a class into a schema creates
 * the stub it needs to be edited there.
 *
 * Classes are addressed by uuid alone: the backend accepts both the uuid a
 * class carries in a schema and the uuid of its merged class in the
 * cross-profile view, and picks the schema to copy from itself.
 */

import { extendToSchema, listSchemas } from "$lib/api/generated/index";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import {
    ClassType,
    DiagramType,
    editorState,
    forceReloadTrigger,
    multiSelectState,
} from "$lib/sharedState.svelte.js";
import { classStore } from "$lib/stores/classStore.ts";
import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
import { packageStore } from "$lib/stores/packageStore.ts";

import {
    mergeSchemaOccurrences,
    sortSchemaOccurrences,
} from "./schemaOccurrences.js";

export {
    groupCandidatesByStub,
    mergeSchemaOccurrences,
    schemaLabel,
    schemaMarker,
    sourceCandidates,
    stubsDiffer,
} from "./schemaOccurrences.js";

/**
 * Lists every schema of the workspace together with the state the class has in
 * it.
 *
 * @returns {Promise<Array<Object>>} one entry per schema, empty on failure
 */
export async function getClassSchemas(workspaceName, classUuid) {
    if (!workspaceName || !classUuid) {
        return [];
    }
    try {
        const { data, error } = await listSchemas({
            path: { datasetName: workspaceName, classUUID: classUuid },
        });
        if (error) {
            console.error("failed to load the schemas of a class:", error);
            return [];
        }
        return sortSchemaOccurrences(data ?? []);
    } catch (e) {
        console.error("failed to load the schemas of a class:", e);
        return [];
    }
}

/**
 * Lists the schemas for a whole selection of classes: a schema counts as having
 * the class only when it defines every one of them, so a selection can still be
 * extended into a schema that misses one of its classes.
 *
 * @returns {Promise<Array<Object>>} one entry per schema, empty on failure
 */
export async function loadClassSchemas(workspaceName, classUuids) {
    const lists = await Promise.all(
        (classUuids ?? []).map(classUuid =>
            getClassSchemas(workspaceName, classUuid),
        ),
    );
    return sortSchemaOccurrences(mergeSchemaOccurrences(lists));
}

/**
 * Creates the stubs of the given classes in another schema. Classes that the
 * target schema already defines are kept as they are.
 *
 * @returns {Promise<Array<Object>|null>} one result per class, null on failure
 */
export async function extendClassesToSchema(
    workspaceName,
    classUuids,
    targetGraphUri,
    withInheritance = false,
) {
    try {
        const { data, error } = await extendToSchema({
            path: { datasetName: workspaceName },
            body: {
                graphUri: String(targetGraphUri),
                classUUIDs: classUuids,
                withInheritance,
            },
        });
        if (error) {
            console.error(
                "failed to extend classes into another schema:",
                error,
            );
            toastStore.error(
                "Could not extend class",
                "The class could not be created in the selected schema.",
            );
            return null;
        }
        const results = data ?? [];
        if (results.length > 0) {
            classStore.invalidateGraph(workspaceName, String(targetGraphUri));
            packageStore.invalidateGraph(workspaceName, String(targetGraphUri));
            crossProfileStore.invalidateWorkspace(workspaceName);
        }
        return results;
    } catch (e) {
        console.error("failed to extend classes into another schema:", e);
        toastStore.error(
            "Could not extend class",
            "An unexpected error occurred. Please try again.",
        );
        return null;
    }
}

/**
 * Extends the classes into the target schema and moves the selection there. In
 * the cross-profile diagram the diagram stays as it is and only the class
 * editor switches over to the target schema.
 */
export async function extendToSchemaAndReveal({
    workspaceName,
    classUuids,
    targetGraphUri,
    targetLabel,
    selectedClassUuid = null,
    withInheritance = false,
}) {
    const results = await extendClassesToSchema(
        workspaceName,
        classUuids,
        targetGraphUri,
        withInheritance,
    );
    if (!results) {
        return null;
    }

    reportExtensionResults(results, targetLabel);
    if (
        editorState.selectedDiagram.getProperty("type") ===
        DiagramType.CROSS_PROFILE
    ) {
        revealInCrossProfileDiagram(
            workspaceName,
            selectedClassUuid,
            targetGraphUri,
        );
    } else {
        revealInSchemaDiagram(workspaceName, targetGraphUri, results);
    }
    forceReloadTrigger.trigger();
    return results;
}

function reportExtensionResults(results, targetLabel) {
    const created = results.filter(result => result.created).length;
    const schema = targetLabel ?? "the selected schema";
    if (created === 0) {
        toastStore.info(
            "Nothing to create",
            results.length === 1
                ? `The class already exists in "${schema}".`
                : `All selected classes already exist in "${schema}".`,
        );
        return;
    }
    toastStore.success(
        "Class extended",
        created === 1
            ? `The class was created in "${schema}".`
            : `${created} classes were created in "${schema}".`,
    );
}

function revealInCrossProfileDiagram(
    workspaceName,
    selectedClassUuid,
    targetGraphUri,
) {
    if (!selectedClassUuid) {
        return;
    }
    editorState.classEditorSchema.updateValue({
        classUuid: selectedClassUuid,
        graphUri: String(targetGraphUri),
    });
    editorState.selectedClassWorkspace.updateValue(workspaceName);
    editorState.selectedClassGraph.updateValue(null);
    editorState.selectedClass.updateValue({
        type: ClassType.MERGED_CLASS,
        id: selectedClassUuid,
    });
    editorState.focusedClassUUID.updateValue(selectedClassUuid);
}

function revealInSchemaDiagram(workspaceName, targetGraphUri, results) {
    const target = results[0];
    multiSelectState.clear();
    editorState.selectPackage(
        workspaceName,
        String(targetGraphUri),
        target?.packageUUID ?? "default",
    );
    if (results.length > 1 || !target?.classUUID) {
        editorState.selectedClass.updateValue({ type: null, id: null });
        editorState.focusedClassUUID.updateValue(null);
        return;
    }
    editorState.selectedClassWorkspace.updateValue(workspaceName);
    editorState.selectedClassGraph.updateValue(String(targetGraphUri));
    editorState.selectedClass.updateValue({
        type: ClassType.SINGLE_CLASS,
        id: target.classUUID,
    });
    editorState.focusedClassUUID.updateValue(target.classUUID);
}
