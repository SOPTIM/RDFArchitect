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
    import { onMount, onDestroy } from "svelte";

    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import {
        ClassType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/CrossProfileStore.ts";

    import ClassEditor from "../../mainpage/classEditor/classEditor.svelte";

    let { datasetName, classUuid } = $props();

    let mergedClass = $state(null);
    let loading = $state(true);

    let activeSourceUuid = $state(null);
    let activeSource = $derived(
        mergedClass?.sources?.find(s => s.classUUID === activeSourceUuid) ??
            null,
    );

    $effect(() => {
        if (activeSource?.classUUID) {
            persisted = {
                classUuid: classUuid,
                sourceUuid: activeSource.classUUID,
            };
        }
    });

    $effect(() => {
        if (!datasetName || !classUuid) return;
        forceReloadTrigger.subscribe();

        const savedSourceUuid =
            persisted.classUuid === classUuid ? persisted.sourceUuid : null;

        loading = true;

        (async () => {
            await crossProfileStore.loadDiagram(datasetName);
            const diagram = crossProfileStore.getDiagram(datasetName);

            const classes = diagram?.classes ?? [];

            let found = savedSourceUuid
                ? classes.find(c =>
                      c.sources?.some(s => s.classUUID === savedSourceUuid),
                  )
                : null;

            if (!found) {
                found = classes.find(c => c.uuid === classUuid) ?? null;
            }

            mergedClass = found;

            if (found) {
                const hasSaved = found.sources?.some(
                    s => s.classUUID === savedSourceUuid,
                );
                activeSourceUuid = hasSaved
                    ? savedSourceUuid
                    : (found.sources?.[0]?.classUUID ?? null);
            }

            if (found && found.uuid !== classUuid) {
                editorState.selectedClass.updateValue({
                    type: ClassType.MERGED_CLASS,
                    id: found.uuid,
                });
            }

            loading = false;
        })();
    });

    onMount(() => eventStack.addEvent(closeMergedClassEditor));
    onDestroy(() => eventStack.removeEvent(closeMergedClassEditor));

    function extractGraphLabel(graphUri) {
        try {
            return new URI(graphUri).suffix;
        } catch {
            return graphUri;
        }
    }

    function closeMergedClassEditor() {
        editorState.selectedClassDataset.updateValue(null);
        editorState.selectedClassGraph.updateValue(null);
        editorState.selectedClass.updateValue({ type: null, id: null });
    }
</script>

{#if loading}
    <div class="relative h-full w-full">
        <div
            class="absolute inset-0 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner />
        </div>
    </div>
{:else if mergedClass && mergedClass.sources?.length > 0}
    <div class="flex h-full flex-col">
        <div class="border-border border-b px-2 py-1 shrink-0">
            <SelectEditControl
                bind:value={activeSourceUuid}
                options={mergedClass.sources}
                getOptionValue={source => source.classUUID}
                getOptionLabel={source => extractGraphLabel(source.graphUri)}
                height={8}
            />
        </div>

        {#if activeSource}
            {#key activeSource.classUUID + activeSource.graphUri}
                <div class="h-full overflow-auto">
                    <ClassEditor
                        {datasetName}
                        graphUri={activeSource.graphUri}
                        classUuid={activeSource.classUUID}
                    />
                </div>
            {/key}
        {/if}
    </div>
{:else}
    <p class="text-default-text p-4 text-sm italic">
        No sources available for this class.
    </p>
{/if}
