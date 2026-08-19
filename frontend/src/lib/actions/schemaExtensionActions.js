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
 */

import { extendToSchema, listSchemas } from "$lib/api/generated/index";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { URI } from "$lib/models/dto/index.ts";
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
import { uriSuffix } from "$lib/utils/iri.js";

import {
    mergeSchemaOccurrences,
    sortByGraphOrder,
    sortSchemaOccurrences,
} from "./schemaOccurrences.js";

export {
    groupCandidatesByStub,
    mergeSchemaOccurrences,
    schemaLabel,
    schemaMarker,
    sourceCandidates,
    sourceOfOccurrence,
    stubsDiffer,
} from "./schemaOccurrences.js";

/**
 * Lists every schema of the workspace together with the state the class has in
 * it. The class has to exist in the given graph.
 *
 * @returns {Promise<Array<Object>>} one entry per schema, empty on failure
 */
export async function getClassSchemas(workspaceName, graphUri, classUuid) {
    if (!workspaceName || !graphUri || !classUuid) {
        return [];
    }
    try {
        const { data, error } = await listSchemas({
            path: {
                datasetName: workspaceName,
                graphURI: String(graphUri),
                classUUID: classUuid,
            },
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
 * Lists the schemas of the workspace for a whole selection of classes.
 *
 * @param {Array<{uuid: string, graphUri: string|null}>} classes
 * @returns {Promise<Array<Object>>} one entry per schema, empty on failure
 */
export async function loadClassSchemas(workspaceName, classes) {
    const sources = await resolveClassSources(workspaceName, classes);
    const lists = await Promise.all(
        sources.flatMap(source =>
            source.classUuids.map(classUuid =>
                getClassSchemas(workspaceName, source.graphUri, classUuid),
            ),
        ),
    );
    return sortSchemaOccurrences(mergeSchemaOccurrences(lists));
}

/**
 * Resolves the classes a diagram selection stands for into groups of classes
 * that share one graph. Classes of the cross-profile diagram carry a merged
 * uuid instead of a graph, so they are looked up in the merged diagram first.
 *
 * @param {Array<{uuid: string, graphUri: string|null}>} classes
 * @returns {Promise<Array<{graphUri: string, classUuids: Array<string>}>>}
 */
export async function resolveClassSources(workspaceName, classes) {
    const byGraph = new Map();
    const mergedOnes = [];
    for (const entry of classes) {
        if (entry?.graphUri) {
            addToGroup(byGraph, String(entry.graphUri), entry.uuid);
        } else if (entry?.uuid) {
            mergedOnes.push(entry.uuid);
        }
    }
    if (mergedOnes.length > 0) {
        const diagram = await crossProfileStore.getDiagram(workspaceName);
        for (const mergedUuid of mergedOnes) {
            const source = firstSourceOf(diagram, mergedUuid);
            if (source) {
                addToGroup(byGraph, source.graphUri, source.classUuid);
            }
        }
    }
    return [...byGraph.entries()].map(([graphUri, classUuids]) => ({
        graphUri,
        classUuids,
    }));
}

function addToGroup(byGraph, graphUri, classUuid) {
    const group = byGraph.get(graphUri) ?? [];
    if (!group.includes(classUuid)) {
        group.push(classUuid);
    }
    byGraph.set(graphUri, group);
}

/**
 * The schema a merged class is copied from when the user is not asked: the
 * first one in the order the navigation lists the schemas in, so that it does
 * not depend on how the graphs happen to be stored.
 */
function firstSourceOf(diagram, mergedUuid) {
    const merged = (diagram?.classes ?? []).find(
        candidate =>
            candidate.uuid === mergedUuid ||
            candidate.sources?.some(source => source.classUUID === mergedUuid),
    );
    const sources = sortByGraphOrder(
        (merged?.sources ?? []).filter(source => source.graph?.uri),
        source => source.graph.keyword || uriSuffix(graphUriOf(source)),
        graphUriOf,
    );
    const source = sources[0];
    if (!source) {
        return null;
    }
    return { graphUri: graphUriOf(source), classUuid: source.classUUID };
}

function graphUriOf(source) {
    return new URI(source.graph.uri).toString();
}

/**
 * Creates the stubs of the given classes in another schema. Classes that the
 * target schema already defines are kept as they are.
 *
 * @param {Array<{graphUri: string, classUuids: Array<string>}>} sources
 * @returns {Promise<Array<Object>|null>} one result per class, null on failure
 */
export async function extendClassesToSchema(
    workspaceName,
    sources,
    targetGraphUri,
    withInheritance = false,
) {
    const results = [];
    let failed = 0;
    for (const source of sources) {
        try {
            const { data, error } = await extendToSchema({
                path: {
                    datasetName: workspaceName,
                    graphURI: String(source.graphUri),
                },
                body: {
                    graphUri: String(targetGraphUri),
                    classUUIDs: source.classUuids,
                    withInheritance,
                },
            });
            if (error) {
                console.error(
                    "failed to extend classes into another schema:",
                    error,
                );
                failed += 1;
                continue;
            }
            results.push(...(data ?? []));
        } catch (e) {
            console.error("failed to extend classes into another schema:", e);
            failed += 1;
        }
    }
    if (results.length > 0) {
        classStore.invalidateGraph(workspaceName, String(targetGraphUri));
        packageStore.invalidateGraph(workspaceName, String(targetGraphUri));
        crossProfileStore.invalidateWorkspace(workspaceName);
    }
    if (failed > 0) {
        toastStore.error(
            "Could not extend class",
            failed === sources.length
                ? "The class could not be created in the selected schema."
                : "Some classes could not be created in the selected schema.",
        );
    }
    // Whatever was created is reported back, so that it is revealed and
    // reloaded even when another source schema failed.
    return failed === sources.length ? null : results;
}

/** Extends the classes into the target schema and reports the outcome. */
async function extendToSchemaAndReport({
    workspaceName,
    sources,
    targetGraphUri,
    targetLabel,
    withInheritance = false,
}) {
    const results = await extendClassesToSchema(
        workspaceName,
        sources,
        targetGraphUri,
        withInheritance,
    );
    if (!results) {
        return null;
    }
    reportExtensionResults(results, targetLabel);
    return results;
}

/**
 * Extends the classes into the target schema and moves the selection there. In
 * the cross-profile diagram the diagram stays as it is and only the class
 * editor switches over to the target schema.
 */
export async function extendToSchemaAndReveal({
    workspaceName,
    sources,
    targetGraphUri,
    targetLabel,
    selectedClassUuid = null,
    withInheritance = false,
}) {
    const results = await extendToSchemaAndReport({
        workspaceName,
        sources,
        targetGraphUri,
        targetLabel,
        withInheritance,
    });
    if (!results) {
        return null;
    }

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
