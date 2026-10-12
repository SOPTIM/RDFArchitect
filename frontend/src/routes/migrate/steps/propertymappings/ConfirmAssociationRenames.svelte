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
    import { faCircleInfo } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import { URI } from "$lib/models/dto/index.ts";
    import { buildRenamedFromMap } from "$lib/utils/migrationUtils.js";

    import MappingSection from "../MappingSection.svelte";

    const { classes, isLoading, invalidMappingIds = new Set() } = $props();

    let renamedFrom = $derived(
        buildRenamedFromMap(
            classes,
            cls => cls.associations?.deletedAndRemapped,
        ),
    );

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
                <MappingSection
                    title={cls.classLabel}
                    bind:deletedAndRemapped={
                        cls.associations.deletedAndRemapped
                    }
                    added={cls.associations.addedInClass ?? []}
                    addedTitle="Added Associations"
                    candidates={cls.associations.allAdded ?? []}
                    getCandidateSubtitle={candidateSubtitle(cls)}
                    resourceType="Association"
                    {renamedFrom}
                    currentClassLabel={cls.classLabel}
                    {invalidMappingIds}
                />
            {/if}
        {/each}
    {/if}
</div>
