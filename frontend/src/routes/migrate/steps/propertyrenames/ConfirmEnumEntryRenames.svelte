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
    import {
        faCaretDown,
        faCircleInfo,
    } from "@fortawesome/free-solid-svg-icons";
    import { CollapsibleCard } from "svelte-collapsible";
    import { Fa } from "svelte-fa";

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";

    import RenameTable from "../RenameTable.svelte";

    const { classes, isLoading } = $props();

    let unlinkedAdded = $derived.by(() => {
        const result = new Map();
        for (let cls of classes) {
            const linked = renamedFrom.has(cls.label)
                ? new Set(renamedFrom.get(cls.label).keys())
                : new Set();

            const unlinked = cls.enumEntries.added.filter(
                attr => !linked.has(attr.label),
            );
            result.set(cls.label, unlinked);
        }
        return result;
    });

    let renamedFrom = $derived.by(() => {
        if (!classes || classes.length === 0) return new Map();

        const map = new Map();

        for (const cls of classes) {
            const renameMap = buildEnumEntryRenameMapForClass(cls);
            if (renameMap && renameMap.size > 0) {
                map.set(cls.label, renameMap);
            }
        }
        return map;
    });

    function buildEnumEntryRenameMapForClass(cls) {
        const map = new Map();
        const renameCandidates = cls.enumEntries.deletedAndRenamed || [];
        if (renameCandidates.length > 0) {
            for (const rename of renameCandidates) {
                if (rename.newResource) {
                    map.set(rename.newResource.label, rename.oldResource.label);
                }
            }
        }
        return map;
    }

    function addRenameMapping(classLabel, renameCandidate, newEnumEntry) {
        renamedFrom
            .get(classLabel)
            .set(newEnumEntry, renameCandidate.oldResource.label);
        renameCandidate.newResource = newEnumEntry;
        renameCandidate.confidenceScore = 1;
    }

    function dissolveRenameMapping(classLabel, renameCandidate) {
        renamedFrom.get(classLabel).delete(renameCandidate.newResource.label);
        renameCandidate.newResource = null;
    }

    function hasEnumEntries(cls) {
        return (
            cls.enumEntries &&
            cls.enumEntries.added.length +
                cls.enumEntries.modified.length +
                cls.enumEntries.deletedAndRenamed.length >
                0
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-8">
    <div class="text-dark flex items-start space-x-2 rounded-lg border p-4">
        <Fa icon={faCircleInfo} class="text-blue mt-0.5" />
        <p class="text-sm leading-relaxed">
            This step lets you review potential enum entries renames for all
            classes.
            <br />
            Please verify that the detected renames are correct, or adjust them as
            necessary.
        </p>
    </div>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasEnumEntries))}
        <EmptyStateCard
            title="No Enum Entry Changes"
            description="There are no enum entry changes to review in this migration."
        />
    {:else}
        {#each classes as cls}
            {#if hasEnumEntries(cls)}
                <div class="space-y-4">
                    <CollapsibleCard>
                        <h2 slot="header" class="mb-3 text-lg font-semibold">
                            {cls.label}
                            <Fa icon={faCaretDown} />
                        </h2>
                        <div
                            class="border-l-blue flex-col space-y-4 border-l-4 pl-4"
                            slot="body"
                        >
                            {#if cls.enumEntries.deletedAndRenamed.length > 0}
                                <RenameTable
                                    renameCandidates={cls.enumEntries
                                        .deletedAndRenamed}
                                    unlinkedNewItems={unlinkedAdded.get(
                                        cls.label,
                                    )}
                                    allAddedItems={cls.enumEntries.added}
                                    onAddMapping={(rename, newItem) =>
                                        addRenameMapping(
                                            cls.label,
                                            rename,
                                            newItem,
                                        )}
                                    onDissolveMapping={rename =>
                                        dissolveRenameMapping(
                                            cls.label,
                                            rename,
                                        )}
                                    propertyType="Enum Entry"
                                />
                            {/if}

                            {#if cls.enumEntries.added.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Added Enum Entries
                                    </h3>
                                    <div class="space-y-1">
                                        {#each cls.enumEntries.added as addedEnumEntry}
                                            <div
                                                class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                                            >
                                                <p>{addedEnumEntry.label}</p>
                                                {#if renamedFrom
                                                    .get(cls.label)
                                                    ?.has(addedEnumEntry.label)}
                                                    <span
                                                        class="text-soptim-dunkelgrau text-xs italic"
                                                    >
                                                        renamed from {renamedFrom
                                                            .get(cls.label)
                                                            .get(
                                                                addedEnumEntry.label,
                                                            )}
                                                    </span>
                                                {/if}
                                            </div>
                                        {/each}
                                    </div>
                                </div>
                            {/if}

                            {#if cls.enumEntries.modified.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Modified Enum Entries
                                    </h3>
                                    <ul
                                        class="list-inside list-disc space-y-1 text-sm"
                                    >
                                        {#each cls.enumEntries.modified as modifiedEnumEntry}
                                            <li>{modifiedEnumEntry.label}</li>
                                        {/each}
                                    </ul>
                                </div>
                            {/if}
                        </div>
                    </CollapsibleCard>
                </div>
            {/if}
        {/each}
    {/if}
</div>
