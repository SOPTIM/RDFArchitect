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

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";

    let { classes, disableNext = $bindable(), isLoading = false } = $props();

    $effect(() => {
        let allReplacementsSet = classes.every(
            enumClass =>
                enumClass.enumEntries
                    ?.filter(e => e.semanticResourceChangeType === "DELETE")
                    .every(
                        entry =>
                            entry.replacementValue &&
                            entry.replacementValue.trim().length > 0,
                    ) ?? true,
        );
        disableNext = !allReplacementsSet;
    });

    function hasDeletedEntries(enumClass) {
        return (
            enumClass.enumEntries &&
            enumClass.enumEntries.some(
                e => e.semanticResourceChangeType === "DELETE",
            )
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-10">
    <InfoBox>
        This step lets you specify <span class="font-semibold">
            replacement values for deleted enum entries
        </span>
        .
        <br />
        All deleted enum entries require a replacement value from the remaining valid
        entries. This replacement will be applied to all instances using the deleted
        enum value.
    </InfoBox>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasDeletedEntries))}
        <EmptyStateCard
            title="No Deleted Enum Entries"
            description="There are no deleted enum entries that require replacement values."
        />
    {:else}
        {#each classes as enumClass}
            {#if hasDeletedEntries(enumClass)}
                <div class="space-y-4">
                    <CollapsibleCard>
                        <h2 slot="header" class="mb-3 text-lg font-semibold">
                            {enumClass.classLabel}
                            <Fa icon={faCaretDown} />
                        </h2>
                        <div
                            class="border-l-blue flex-col space-y-4 border-l-4 pl-4"
                            slot="body"
                        >
                            <div>
                                <div
                                    class="border-border bg-window-background overflow-x-auto rounded-xl border shadow-sm"
                                >
                                    <table
                                        class="w-full border-collapse text-left text-sm"
                                    >
                                        <thead class="bg-lightgray">
                                            <tr>
                                                <th
                                                    class="w-1/2 px-4 py-2 font-medium"
                                                >
                                                    Deleted Enum Entry
                                                </th>
                                                <th
                                                    class="w-1/2 px-4 py-2 font-medium"
                                                >
                                                    Replacement Value
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each enumClass.enumEntries as deletedEntry}
                                                {#if deletedEntry.semanticResourceChangeType === "DELETE"}
                                                    <tr>
                                                        <td class="px-3 py-2">
                                                            {deletedEntry.label}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            <select
                                                                class="border-border text-default-text focus:border-orange w-full rounded-md border bg-white px-2 py-1 text-sm outline-none focus:border-2"
                                                                bind:value={
                                                                    deletedEntry.replacementValue
                                                                }
                                                            >
                                                                {#each deletedEntry.allowedValues as entry}
                                                                    <option
                                                                        value={entry}
                                                                    >
                                                                        {entry}
                                                                    </option>
                                                                {/each}
                                                            </select>
                                                        </td>
                                                    </tr>
                                                {/if}
                                            {/each}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                    </CollapsibleCard>
                </div>
            {/if}
        {/each}
    {/if}
</div>
