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
            for (const mapping of cls.associations?.deletedAndRemapped ?? []) {
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

    function hasAssociations(cls) {
        return (
            cls.associations &&
            (cls.associations.addedInClass?.length ?? 0) +
                (cls.associations.deletedAndRemapped?.length ?? 0) >
                0
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-8">
    <div class="text-dark flex items-start space-x-2 rounded-lg border p-4">
        <Fa icon={faCircleInfo} class="text-blue mt-0.5" />
        <p class="text-sm leading-relaxed">
            This step lets you review potential association renames and moves
            for all classes. A candidate from elsewhere in the inheritance chain
            is labeled with its class; link a deleted association to more than
            one target to split it, or link two deleted associations to the same
            target to merge them.
            <br />
            Please verify that the detected mappings are correct, or adjust them as
            necessary.
            <br />
            Associations that cannot be instantiated (AssociationUsed = "No") are
            not listed: they hold no instance data, so there is nothing to map.
        </p>
    </div>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasAssociations))}
        <EmptyStateCard
            title="No Association Changes"
            description="There are no association changes to review in this migration."
        />
    {:else}
        {#each classes as cls}
            {#if hasAssociations(cls)}
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
                            {#if cls.associations.deletedAndRemapped.length > 0}
                                <RenameTable
                                    bind:mappings={
                                        cls.associations.deletedAndRemapped
                                    }
                                    candidates={cls.associations.allAdded ?? []}
                                    getCandidateSubtitle={candidateSubtitle(
                                        cls,
                                    )}
                                    propertyType="Association"
                                />
                            {/if}

                            {#if cls.associations.addedInClass?.length > 0}
                                <div>
                                    <h3 class="mb-3 font-semibold">
                                        Added Associations
                                    </h3>
                                    <div class="space-y-1">
                                        {#each [...cls.associations.addedInClass].sort( (a, b) => a.label.localeCompare(b.label) ) as addedAssociation}
                                            <div
                                                class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                                            >
                                                <p>{addedAssociation.label}</p>
                                                {#if renamedFrom.has(addedAssociation.iri)}
                                                    <span
                                                        class="text-soptim-dunkelgrau text-xs italic"
                                                    >
                                                        renamed from {renamedFrom
                                                            .get(
                                                                addedAssociation.iri,
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
