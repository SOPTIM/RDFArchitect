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
    import { onMount, onDestroy } from "svelte";

    import { getCrossProfileDiagram } from "$lib/api/apiDatasetUtils.js";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import ClassEditor from "../../mainpage/classEditor/classEditor.svelte";

    let { datasetName, classUuid } = $props();

    let mergedClass = $state(null);
    let loading = $state(true);

    let activeTabIndex = $state(0);
    let activeSource = $derived(mergedClass?.sources?.[activeTabIndex] ?? null);

    $effect(() => {
        if (!datasetName || !classUuid) return;
        loading = true;
        getCrossProfileDiagram(datasetName)
            .then(diagram => {
                mergedClass =
                    diagram?.classes?.find(c => c.uuid === classUuid) ?? null;
            })
            .finally(() => (loading = false));
    });

    onMount(() => eventStack.addEvent(closeMergedClassEditor));
    onDestroy(() => eventStack.removeEvent(closeMergedClassEditor));

    function extractGraphLabel(graphUri) {
        const hash = graphUri.lastIndexOf("#");
        const slash = graphUri.lastIndexOf("/");
        const idx = Math.max(hash, slash);
        return idx >= 0 ? graphUri.substring(idx + 1) : graphUri;
    }

    function closeMergedClassEditor() {
        editorState.selectedClassDataset.updateValue(null);
        editorState.selectedClassGraph.updateValue(null);
        editorState.selectedClassType.updateValue(null);
        editorState.selectedClassUUID.updateValue(null);
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
    <div class="border-border border-b px-2 py-1">
        <select
            class="border-button-border bg-window-background text-default-text w-full rounded border border-solid px-2 py-1 text-sm outline-none"
            onchange={e => (activeTabIndex = Number(e.target.value))}
            value={activeTabIndex}
        >
            {#each mergedClass.sources as source, i}
                <option value={i} title={source.graphUri}>
                    {extractGraphLabel(source.graphUri)}
                </option>
            {/each}
        </select>
    </div>

    {#if activeSource}
        {#key activeSource.classUuid + activeSource.graphUri}
            <div class="h-full overflow-auto">
                <ClassEditor
                    {datasetName}
                    graphUri={activeSource.graphUri}
                    classUuid={activeSource.classUuid}
                />
            </div>
        {/key}
    {/if}
{:else}
    <p class="text-default-text p-4 text-sm italic">
        No sources available for this class.
    </p>
{/if}
