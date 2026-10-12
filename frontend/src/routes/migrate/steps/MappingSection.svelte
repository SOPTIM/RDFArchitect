<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { faCaretDown } from "@fortawesome/free-solid-svg-icons";
    import { CollapsibleCard } from "svelte-collapsible";
    import { Fa } from "svelte-fa";

    import RenameTable from "./RenameTable.svelte";

    let {
        title = null,
        deletedAndRemapped = $bindable([]),
        added = [],
        addedTitle = "Added",
        candidates = [],
        getCandidateSubtitle = () => null,
        resourceType = "Item",
        renamedFrom = new Map(),
        currentClassLabel = null,
    } = $props();

    let sortedAdded = $derived(
        [...added].sort((a, b) => a.label.localeCompare(b.label)),
    );
</script>

{#snippet content()}
    {#if deletedAndRemapped.length > 0}
        <RenameTable
            bind:mappings={deletedAndRemapped}
            {candidates}
            {getCandidateSubtitle}
            {resourceType}
            {currentClassLabel}
        />
    {/if}

    {#if sortedAdded.length > 0}
        <div>
            <h3 class="mb-3 font-semibold">{addedTitle}</h3>
            <div class="space-y-1">
                {#each sortedAdded as item (item.iri)}
                    <div
                        class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                    >
                        <p>{item.label}</p>
                        {#if renamedFrom.has(item.iri)}
                            <span class="text-soptim-dunkelgrau text-xs italic">
                                {renamedFrom.get(item.iri)}
                            </span>
                        {/if}
                    </div>
                {/each}
            </div>
        </div>
    {/if}
{/snippet}

{#if title}
    <div class="space-y-4">
        <CollapsibleCard>
            <h2 slot="header" class="mb-3 text-lg font-semibold">
                {title}
                <Fa icon={faCaretDown} />
            </h2>
            <div
                class="border-l-blue flex-col space-y-4 border-l-4 pl-4"
                slot="body"
            >
                {@render content()}
            </div>
        </CollapsibleCard>
    </div>
{:else}
    {@render content()}
{/if}
