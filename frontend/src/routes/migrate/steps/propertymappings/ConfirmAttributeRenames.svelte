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
    import { URI } from "$lib/models/dto/index.ts";

    import RenameTable from "../RenameTable.svelte";

    const { classes, isLoading } = $props();

    let renamedFrom = $derived.by(() => {
        const map = new Map();
        if (!classes || classes.length === 0) return map;

        for (const cls of classes) {
            for (const mapping of cls.attributes?.deletedAndRemapped ?? []) {
                if (mapping.newResources.length === 0) continue;
                for (const target of mapping.newResources) {
                    const froms = mapping.oldResources.map(oldLabelWithClass);
                    map.set(target.iri, [
                        ...(map.get(target.iri) ?? []),
                        ...froms,
                    ]);
                }
            }
        }
        return map;
    });

    function oldLabelWithClass(resource) {
        if (!resource.domain) return resource.label;
        return `${resource.label} (in ${new URI(resource.domain).suffix})`;
    }

    function candidateSubtitle(cls) {
        return candidate => {
            if (!candidate.domain) return null;
            const ownerLabel = new URI(candidate.domain).suffix;
            return ownerLabel === cls.classLabel ? null : `in ${ownerLabel}`;
        };
    }

    function hasAttributes(cls) {
        return (
            cls.attributes &&
            (cls.attributes.addedInClass?.length ?? 0) +
                (cls.attributes.deletedAndRemapped?.length ?? 0) >
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
            This step lets you review potential attribute renames and moves for
            all classes. A candidate from elsewhere in the inheritance chain is
            labeled with its class; link a deleted attribute to more than one
            target to split it, or link two deleted attributes to the same
            target to merge them.
            <br />
            Please verify that the detected mappings are correct, or adjust them as
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
                            {cls.classLabel}
                            <Fa icon={faCaretDown} />
                        </h2>
                        <div
                            class="border-l-blue flex-col space-y-4 border-l-4 pl-4"
                            slot="body"
                        >
                            {#if cls.attributes.deletedAndRemapped.length > 0}
                                <RenameTable
                                    bind:mappings={
                                        cls.attributes.deletedAndRemapped
                                    }
                                    candidates={cls.attributes.allAdded ?? []}
                                    getCandidateSubtitle={candidateSubtitle(
                                        cls,
                                    )}
                                    propertyType="Attribute"
                                />
                            {/if}

                            {#if cls.attributes.addedInClass?.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Added Attributes
                                    </h3>
                                    <div class="space-y-1">
                                        {#each [...cls.attributes.addedInClass].sort( (a, b) => a.label.localeCompare(b.label) ) as addedAttribute}
                                            <div
                                                class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                                            >
                                                <p>{addedAttribute.label}</p>
                                                {#if renamedFrom.has(addedAttribute.iri)}
                                                    <span
                                                        class="text-soptim-dunkelgrau text-xs italic"
                                                    >
                                                        renamed from {renamedFrom
                                                            .get(
                                                                addedAttribute.iri,
                                                            )
                                                            .join(", ")}
                                                    </span>
                                                {/if}
                                            </div>
                                        {/each}
                                    </div>
                                </div>
                            {/if}
                        </div>
                    </CollapsibleCard>
                </div>
            {/if}
        {/each}
    {/if}
</div>
