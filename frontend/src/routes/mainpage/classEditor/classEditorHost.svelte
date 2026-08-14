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
<script module>
    let persisted = { classUuid: null, sourceUuid: null };
</script>

<script>
    import {
        faCaretDown,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";
    import { onMount, onDestroy } from "svelte";
    import { Fa } from "svelte-fa";

    import { getCrossProfileDiagram } from "$lib/api/apiDatasetUtils.js";
    import { DropdownMenu } from "$lib/components/bitsui/dropdown/index.js";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import {
        eventStack,
        EventType,
    } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { graphColors } from "$lib/graphColors.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import {
        ClassType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import ClassEditor from "./classEditor.svelte";

    let { datasetName, graphUri, classUuid, isMerged } = $props();

    let mergedClass = $state(null);
    let resolving = $state(false);

    let activeSourceUuid = $state(null);
    let resolvedGraphUri = $state(null);
    let resolvedClassUuid = $state(null);

    let activeSource = $derived(
        mergedClass?.sources?.find(s => s.classUUID === activeSourceUuid) ??
            null,
    );

    let hasSources = $derived(
        !!mergedClass && (mergedClass.sources?.length ?? 0) > 0,
    );

    let activeColor = $derived(sourceColor(activeSource));

    $effect(() => {
        if (
            isMerged &&
            activeSource?.classUUID &&
            mergedClass?.uuid === classUuid
        ) {
            persisted = {
                classUuid: classUuid,
                sourceUuid: activeSource.classUUID,
            };
        }
    });

    $effect(() => {
        if (isMerged && activeSource) {
            resolvedGraphUri = new URI(activeSource.graph.uri);
            resolvedClassUuid = activeSource.classUUID;
        }
    });

    $effect(() => {
        if (!datasetName || !classUuid) return;
        forceReloadTrigger.subscribe();

        if (!isMerged) {
            mergedClass = null;
            activeSourceUuid = null;
            resolvedGraphUri = graphUri;
            resolvedClassUuid = classUuid;
            return;
        }

        const savedSourceUuid =
            persisted.classUuid === classUuid ? persisted.sourceUuid : null;

        const cancellation = { cancelled: false };
        resolving = true;
        getCrossProfileDiagram(datasetName)
            .then(diagram => {
                if (cancellation.cancelled) return;
                const classes = diagram?.classes ?? [];

                let found = savedSourceUuid
                    ? classes.find(c =>
                          c.sources?.some(s => s.classUUID === savedSourceUuid),
                      )
                    : null;

                if (!found) {
                    found = classes.find(c => c.uuid === classUuid) ?? null;
                }

                if (!found) {
                    found =
                        classes.find(c =>
                            c.sources?.some(s => s.classUUID === classUuid),
                        ) ?? null;
                }

                mergedClass = found;

                if (found) {
                    const originGraph =
                        graphUri ??
                        editorState.mergedViewOriginGraph.getValue();
                    if (!graphUri) {
                        editorState.mergedViewOriginGraph.updateValue(null);
                    }
                    const originSource = originGraph
                        ? found.sources?.find(
                              s =>
                                  s.graph?.uri &&
                                  new URI(s.graph.uri).toString() ===
                                      String(originGraph),
                          )
                        : null;
                    const hasSaved = found.sources?.some(
                        s => s.classUUID === savedSourceUuid,
                    );
                    activeSourceUuid = hasSaved
                        ? savedSourceUuid
                        : (originSource?.classUUID ??
                          found.sources?.[0]?.classUUID ??
                          null);
                }

                if (found && found.uuid !== classUuid && !graphUri) {
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

    onMount(() =>
        eventStack.addEvent(closeClassEditorHost, EventType.CLASS_EDITOR),
    );
    onDestroy(() => eventStack.removeEvent(closeClassEditorHost));

    function sourceGraphUri(source) {
        return source?.graph?.uri ? new URI(source.graph.uri).toString() : null;
    }

    function sourceColor(source) {
        const uri = sourceGraphUri(source);
        return uri ? graphColors.get(datasetName, uri) : null;
    }

    function sourceLabel(source) {
        return source?.graph?.keyword ?? source?.graph?.uri?.suffix;
    }

    function closeClassEditorHost(
        {
            datasetName = null,
            graphUri = null,
            classUuid = null,
            classType = null,
        } = {
            datasetName: null,
            graphUri: null,
            classUuid: null,
            classType: null,
        },
    ) {
        editorState.selectedClassDataset.updateValue(datasetName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClass.updateValue({
            type: classType,
            id: classUuid,
        });
    }
</script>

<div class="relative h-full w-full">
    <div class="flex h-full flex-col">
        {#if isMerged && hasSources}
            <div class="border-border shrink-0 border-b px-2 py-1">
                <DropdownMenu.Root>
                    <DropdownMenu.Trigger class="w-full">
                        <div
                            class="bg-window-background text-default-text border-button-border flex h-8 w-full min-w-0 items-center gap-2 rounded border border-solid px-2 font-[350] shadow-xs"
                            title={sourceGraphUri(activeSource)}
                        >
                            <span
                                class="shrink-0"
                                style={activeColor
                                    ? `color: ${activeColor};`
                                    : ""}
                            >
                                <Fa icon={faDiagramProject} />
                            </span>
                            <span class="min-w-0 flex-1 truncate text-left">
                                {sourceLabel(activeSource)}
                            </span>
                            <Fa icon={faCaretDown} />
                        </div>
                    </DropdownMenu.Trigger>
                    <DropdownMenu.Content>
                        {#each mergedClass.sources as source (source.classUUID)}
                            <DropdownMenu.Item.Button
                                faIcon={faDiagramProject}
                                iconColor={sourceColor(source)}
                                onSelect={() =>
                                    (activeSourceUuid = source.classUUID)}
                            >
                                {sourceLabel(source)}
                            </DropdownMenu.Item.Button>
                        {/each}
                    </DropdownMenu.Content>
                </DropdownMenu.Root>
            </div>
        {/if}

        {#if isMerged && !resolving && !hasSources}
            <p class="text-default-text p-4 text-sm italic">
                No sources available for this class.
            </p>
        {:else if resolvedGraphUri && resolvedClassUuid}
            <div class="h-full overflow-auto">
                <ClassEditor
                    {datasetName}
                    graphUri={resolvedGraphUri}
                    classUuid={resolvedClassUuid}
                />
            </div>
        {/if}
    </div>

    {#if resolving}
        <div
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner />
        </div>
    {/if}
</div>
