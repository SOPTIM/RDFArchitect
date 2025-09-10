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

            const unlinked = cls.attributes.added.filter(
                attr => !linked.has(attr.label),
            );
            result.set(cls.label, unlinked);
        }
        return result;
    });

    let renamedFrom = $derived.by(() => {
        const map = new Map();
        if (!classes || classes.length === 0) return map;

        for (const cls of classes) {
            const renameMap = buildAttributeRenameMapForClass(cls);
            if (renameMap && renameMap.size > 0) {
                map.set(cls.label, renameMap);
            }
        }
        return map;
    });

    function buildAttributeRenameMapForClass(cls) {
        const map = new Map();
        const renameCandidates = cls.attributes.deletedAndRenamed || [];
        if (renameCandidates.length > 0) {
            for (const rename of renameCandidates) {
                if (rename.newResource) {
                    map.set(rename.newResource.label, rename.oldResource.label);
                }
            }
        }
        return map;
    }

    function addRenameMapping(classLabel, renameCandidate, newAttribute) {
        renamedFrom
            .get(classLabel)
            .set(newAttribute, renameCandidate.oldResource.label);
        renameCandidate.newResource = newAttribute;
        renameCandidate.confidenceScore = 1;
    }

    function dissolveRenameMapping(classLabel, renameCandidate) {
        renamedFrom.get(classLabel).delete(renameCandidate.newResource.label);
        renameCandidate.newResource = null;
    }

    function hasAttributes(cls) {
        return (
            cls.attributes &&
            cls.attributes.added.length +
                cls.attributes.modified.length +
                cls.attributes.deletedAndRenamed.length >
                0
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-10">
    <div
        class="text-default-text flex items-start space-x-2 rounded-lg border p-4"
    >
        <Fa icon={faCircleInfo} class="text-blue mt-0.5" />
        <p class="text-sm leading-relaxed">
            This step lets you review potential attribute renames for all
            classes.
            <br />
            Please verify that the detected renames are correct, or adjust them as
            necessary.
        </p>
    </div>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasAttributes))}
        <EmptyStateCard
            title="No Attribute Changes"
            description="There are no attribute changes to review in this migration."
        />
    {:else}
        {#each classes as cls}
            {#if hasAttributes(cls)}
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
                            {#if cls.attributes.deletedAndRenamed.length > 0}
                                <RenameTable
                                    renameCandidates={cls.attributes
                                        .deletedAndRenamed}
                                    unlinkedNewItems={unlinkedAdded.get(
                                        cls.label,
                                    )}
                                    allAddedItems={cls.attributes.added}
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
                                    propertyType="Attribute"
                                />
                            {/if}

                            {#if cls.attributes.added.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Added Attributes
                                    </h3>
                                    <div class="space-y-1">
                                        {#each cls.attributes.added as addedAttribute}
                                            <div
                                                class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                                            >
                                                <p>{addedAttribute.label}</p>
                                                {#if renamedFrom
                                                    .get(cls.label)
                                                    ?.has(addedAttribute.label)}
                                                    <span
                                                        class="text-soptim-dunkelgrau text-xs italic"
                                                    >
                                                        renamed from {renamedFrom
                                                            .get(cls.label)
                                                            .get(
                                                                addedAttribute.label,
                                                            )}
                                                    </span>
                                                {/if}
                                            </div>
                                        {/each}
                                    </div>
                                </div>
                            {/if}

                            {#if cls.attributes.modified.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Modified Attributes
                                    </h3>
                                    <ul
                                        class="list-inside list-disc space-y-1 text-sm"
                                    >
                                        {#each cls.attributes.modified as modifiedAttribute}
                                            <li>{modifiedAttribute.label}</li>
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
