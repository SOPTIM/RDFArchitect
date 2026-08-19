<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import {
        faCaretDown,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";
    import { onMount, onDestroy } from "svelte";
    import { Fa } from "svelte-fa";

    import {
        extendToSchemaAndReveal,
        getClassSchemas,
        schemaLabel,
        schemaMarker,
        sourceCandidates,
        stubsDiffer,
    } from "$lib/actions/schemaExtensionActions.js";
    import Badge from "$lib/components/Badge.svelte";
    import { DropdownMenu } from "$lib/components/bitsui/dropdown/index.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import {
        eventStack,
        EventType,
    } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { askForExtendSource } from "$lib/extendSourceRequest.svelte.js";
    import { graphColors } from "$lib/graphColors.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import {
        ClassType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import ClassEditor from "./classEditor.svelte";

    let { workspaceName, graphUri, classUuid, isMerged } = $props();

    let mergedClass = $state(null);
    let resolving = $state(false);
    let loadingSchemas = $state(false);
    let creating = $state(false);
    let isWorkspaceReadOnly = $state(false);

    let sourceGraphUri = $state(null);
    let sourceClassUuid = $state(null);
    let occurrences = $state([]);

    let activeGraphUri = $derived.by(() => {
        if (occurrences.length === 0) {
            return null;
        }
        const known = uri => occurrences.some(entry => entry.graphUri === uri);
        const override = editorState.classEditorSchema.getValue();
        if (override?.classUuid === classUuid && known(override.graphUri)) {
            return override.graphUri;
        }
        if (graphUri && known(String(graphUri))) {
            return String(graphUri);
        }
        if (known(sourceGraphUri)) {
            return sourceGraphUri;
        }
        return occurrences[0].graphUri;
    });

    let activeOccurrence = $derived(
        occurrences.find(entry => entry.graphUri === activeGraphUri) ?? null,
    );

    let definedOccurrences = $derived(
        occurrences.filter(occurrence => occurrence.present),
    );

    /**
     * The schema a stub is copied from. The class does not have to be defined in
     * the schema it is opened from: it can be referenced there only.
     */
    let extendSource = $derived(
        definedOccurrences.find(
            occurrence => occurrence.graphUri === sourceGraphUri,
        ) ??
            definedOccurrences[0] ??
            null,
    );

    $effect(() => {
        if (!workspaceName || !classUuid) return;
        forceReloadTrigger.subscribe();

        if (!isMerged) {
            mergedClass = null;
            sourceGraphUri = graphUri ? String(graphUri) : null;
            sourceClassUuid = classUuid;
            return;
        }

        const cancellation = { cancelled: false };
        resolving = true;
        crossProfileStore
            .getDiagram(workspaceName)
            .then(diagram => {
                if (cancellation.cancelled) return;
                const classes = diagram?.classes ?? [];

                let found = classes.find(c => c.uuid === classUuid) ?? null;
                if (!found) {
                    found =
                        classes.find(c =>
                            c.sources?.some(s => s.classUUID === classUuid),
                        ) ?? null;
                }

                mergedClass = found;
                if (!found) {
                    sourceGraphUri = null;
                    sourceClassUuid = null;
                    return;
                }

                const source = preferredSource(found);
                sourceGraphUri = graphUriOfSource(source);
                sourceClassUuid = source?.classUUID ?? null;

                if (found.uuid !== classUuid && !graphUri) {
                    editorState.selectedClass.updateValue({
                        type: ClassType.MERGED_CLASS,
                        id: found.uuid,
                    });
                }
            })
            .finally(() => {
                if (!cancellation.cancelled) resolving = false;
            });

        return () => {
            cancellation.cancelled = true;
        };
    });

    $effect(() => {
        forceReloadTrigger.subscribe();
        const currentWorkspace = workspaceName;
        const currentGraph = sourceGraphUri;
        const currentClass = sourceClassUuid;
        if (!currentWorkspace || !currentGraph || !currentClass) {
            occurrences = [];
            return;
        }

        const cancellation = { cancelled: false };
        loadingSchemas = true;
        Promise.all([
            getClassSchemas(currentWorkspace, currentClass),
            workspaceStore.isReadOnly(currentWorkspace),
        ])
            .then(([schemas, readOnly]) => {
                if (cancellation.cancelled) return;
                occurrences = schemas;
                isWorkspaceReadOnly = readOnly;
            })
            .catch(e => {
                console.error("failed to load the schemas of a class:", e);
                if (cancellation.cancelled) return;
                // Without the schemas the editor falls back to the schema the
                // class was opened from, so it stays usable.
                occurrences = [];
                isWorkspaceReadOnly = true;
            })
            .finally(() => {
                if (!cancellation.cancelled) loadingSchemas = false;
            });

        return () => {
            cancellation.cancelled = true;
        };
    });

    // Lets the navigation highlight the open class in every schema that has it.
    $effect(() => {
        editorState.openClassOccurrences.updateValue({
            workspaceName,
            activeGraphUri,
            occurrences: definedOccurrences.map(occurrence => ({
                graphUri: occurrence.graphUri,
                classUUID: occurrence.classUUID,
            })),
        });
    });

    onMount(() =>
        eventStack.addEvent(closeClassEditorHost, EventType.CLASS_EDITOR),
    );
    onDestroy(() => {
        eventStack.removeEvent(closeClassEditorHost);
        editorState.clearOpenClassOccurrences();
    });

    /**
     * Picks the source the merged class is opened with: the graph the user came
     * from if it defines the class, the first one otherwise.
     */
    function preferredSource(merged) {
        const originGraph =
            (graphUri ? String(graphUri) : null) ??
            editorState.mergedViewOriginGraph.getValue();
        const originSource = originGraph
            ? merged.sources?.find(s => graphUriOfSource(s) === originGraph)
            : null;
        return originSource ?? merged.sources?.[0] ?? null;
    }

    function graphUriOfSource(source) {
        return source?.graph?.uri ? new URI(source.graph.uri).toString() : null;
    }

    function selectSchema(occurrence) {
        editorState.classEditorSchema.updateValue({
            classUuid: classUuid,
            graphUri: occurrence.graphUri,
        });
    }

    /**
     * Creates the class in the schema the dropdown points at. When the schemas
     * that could be copied from do not agree on the class, the user picks one.
     */
    function createClassInSchema() {
        if (!activeOccurrence || !extendSource || creating) {
            return;
        }
        const candidates = sourceCandidates(
            occurrences,
            activeOccurrence.graphUri,
        );
        if (stubsDiffer(candidates)) {
            askForExtendSource({
                workspaceName,
                candidates,
                targetLabel: schemaLabel(activeOccurrence),
                onPick: createFrom,
            });
            return;
        }
        createFrom(extendSource);
    }

    async function createFrom(occurrence) {
        creating = true;
        try {
            await extendToSchemaAndReveal({
                workspaceName,
                classUuids: [occurrence.classUUID],
                targetGraphUri: activeOccurrence.graphUri,
                targetLabel: schemaLabel(activeOccurrence),
                selectedClassUuid: classUuid,
            });
        } finally {
            creating = false;
        }
    }

    function closeClassEditorHost(
        {
            workspaceName = null,
            graphUri = null,
            classUuid = null,
            classType = null,
        } = {
            workspaceName: null,
            graphUri: null,
            classUuid: null,
            classType: null,
        },
    ) {
        // Opening a class always starts in its own schema again, even when the
        // editor was switched over to another one before.
        editorState.clearClassEditorSchema();
        editorState.selectedClassWorkspace.updateValue(workspaceName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClass.updateValue({
            type: classType,
            id: classUuid,
        });
    }
</script>

<div class="relative h-full w-full">
    <div class="flex h-full flex-col">
        {#if loadingSchemas && definedOccurrences.length === 0}
            <div class="border-border shrink-0 border-b px-2 py-1">
                <div
                    class="bg-window-background border-button-border h-8 w-full animate-pulse rounded border border-solid"
                ></div>
            </div>
        {:else if definedOccurrences.length > 0 && activeOccurrence}
            <div class="border-border shrink-0 border-b px-2 py-1">
                <DropdownMenu.Root>
                    <DropdownMenu.Trigger class="w-full">
                        <div
                            class="bg-window-background text-default-text border-button-border flex h-8 w-full min-w-0 items-center gap-2 rounded border border-solid px-2 font-[350] shadow-xs"
                            title={activeOccurrence.graphUri}
                        >
                            <span
                                class="shrink-0"
                                style={graphColors.get(
                                    workspaceName,
                                    activeOccurrence?.graphUri,
                                )
                                    ? `color: ${graphColors.get(workspaceName, activeOccurrence?.graphUri)};`
                                    : ""}
                            >
                                <Fa icon={faDiagramProject} />
                            </span>
                            <span class="min-w-0 flex-1 truncate text-left">
                                {schemaLabel(activeOccurrence)}
                            </span>
                            <Fa icon={faCaretDown} />
                        </div>
                    </DropdownMenu.Trigger>
                    <DropdownMenu.Content
                        style="min-width: var(--bits-floating-anchor-width);"
                    >
                        {#each occurrences as occurrence (occurrence.graphUri)}
                            <DropdownMenu.Item.Button
                                faIcon={faDiagramProject}
                                iconColor={graphColors.get(
                                    workspaceName,
                                    occurrence.graphUri,
                                )}
                                disabled={isWorkspaceReadOnly &&
                                    !occurrence.present}
                                onSelect={() => selectSchema(occurrence)}
                            >
                                <span
                                    class="flex w-full min-w-0 items-center gap-2"
                                >
                                    <span class="min-w-0 flex-1 truncate">
                                        {schemaLabel(occurrence)}
                                    </span>
                                    {#if !occurrence.present}
                                        <Badge
                                            text={schemaMarker(occurrence)}
                                            variant="external"
                                        />
                                    {/if}
                                </span>
                            </DropdownMenu.Item.Button>
                        {/each}
                    </DropdownMenu.Content>
                </DropdownMenu.Root>
            </div>
        {/if}

        {#if isMerged && !resolving && !mergedClass}
            <p class="text-default-text p-4 text-sm italic">
                No sources available for this class.
            </p>
        {:else if definedOccurrences.length === 0}
            <!-- No schema defines the class, so it is shown in the schema it
                 was opened from: the referenced only case, and the fallback
                 when the schemas could not be loaded. -->
            {#if sourceGraphUri && sourceClassUuid}
                <div class="h-full overflow-auto">
                    <ClassEditor
                        {workspaceName}
                        graphUri={sourceGraphUri}
                        classUuid={sourceClassUuid}
                    />
                </div>
            {/if}
        {:else if activeOccurrence && !activeOccurrence.present}
            <div
                class="text-default-text flex size-full flex-col items-center justify-center gap-3 p-4 text-center"
            >
                <span class="text-sm opacity-70">
                    This class does not exist in "{schemaLabel(
                        activeOccurrence,
                    )}" yet.
                </span>
                {#if !isWorkspaceReadOnly}
                    <ButtonControl
                        callOnClick={createClassInSchema}
                        disabled={creating}
                    >
                        Create Class in Schema
                    </ButtonControl>
                {/if}
            </div>
        {:else if activeOccurrence?.present}
            <div class="h-full overflow-auto">
                <ClassEditor
                    {workspaceName}
                    graphUri={activeOccurrence.graphUri}
                    classUuid={activeOccurrence.classUUID}
                />
            </div>
        {/if}
    </div>

    <!-- Loading the schema list only fills the dropdown, so it must not hold up
         the editor itself. -->
    {#if resolving}
        <div
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner />
        </div>
    {/if}
</div>
