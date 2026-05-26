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
    import { editorState } from "$lib/sharedState.svelte.js";

    import ClassEditor from "../../mainpage/classEditor/classEditor.svelte";

    let { mergedClass } = $props();

    let activeTabIndex = $state(0);

    let activeSource = $derived(mergedClass?.sources?.[activeTabIndex] ?? null);

    let datasetName = $derived(editorState.selectedDataset.getValue());

    function extractGraphLabel(graphUri) {
        const hash = graphUri.lastIndexOf("#");
        const slash = graphUri.lastIndexOf("/");
        const idx = Math.max(hash, slash);
        return idx >= 0 ? graphUri.substring(idx + 1) : graphUri;
    }
</script>

{#if mergedClass && mergedClass.sources?.length > 0}
    <div class="border-border flex border-b">
        {#each mergedClass.sources as source, i}
            <button
                type="button"
                class="px-4 py-2 text-sm font-medium transition-colors
                    {activeTabIndex === i
                    ? 'border-b-2 border-orange-500 text-orange-500'
                    : 'text-default-text hover:text-orange-400'}"
                onclick={() => (activeTabIndex = i)}
                title={source.graphUri}
            >
                {extractGraphLabel(source.graphUri)}
            </button>
        {/each}
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
