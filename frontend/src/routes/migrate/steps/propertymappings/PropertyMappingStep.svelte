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
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { URI } from "$lib/models/dto/index.ts";
    import { buildRenamedFromMap } from "$lib/utils/migrationUtils.js";

    import MappingSection from "../MappingSection.svelte";

    /**
     * One substep of the property mapping step, for one kind of property. `kind` picks the
     * overview that is mapped (`attributes`, `associations` or `enumEntries`); everything the
     * user reads about that kind - the explanation passed as children and the labels - comes
     * from the component rendering this one.
     */
    const {
        classes,
        isLoading,
        invalidMappingIds = new Set(),
        kind,
        resourceType,
        addedTitle,
        emptyStateDescription,
        children,
    } = $props();

    let renamedFrom = $derived(
        buildRenamedFromMap(
            classes?.flatMap(cls => cls[kind]?.deletedAndRemapped ?? []),
        ),
    );

    function candidateSubtitle(cls) {
        return candidate => {
            if (!candidate.domain) return null;
            const ownerLabel = new URI(candidate.domain).suffix;
            return ownerLabel === cls.classLabel ? null : `in ${ownerLabel}`;
        };
    }

    function hasChanges(cls) {
        const overview = cls[kind];
        return (
            overview &&
            (overview.addedInClass?.length ?? 0) +
                (overview.deletedAndRemapped?.length ?? 0) >
                0
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-8">
    <InfoBox type="info">
        {@render children?.()}
    </InfoBox>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasChanges))}
        <EmptyStateCard
            title={`No ${resourceType} Changes`}
            description={emptyStateDescription}
        />
    {:else}
        {#each classes as cls}
            {#if hasChanges(cls)}
                <MappingSection
                    title={cls.classLabel}
                    bind:deletedAndRemapped={cls[kind].deletedAndRemapped}
                    added={cls[kind].addedInClass ?? []}
                    {addedTitle}
                    candidates={cls[kind].allAdded ?? []}
                    getCandidateSubtitle={candidateSubtitle(cls)}
                    {resourceType}
                    {renamedFrom}
                    currentClassLabel={cls.classLabel}
                    {invalidMappingIds}
                />
            {/if}
        {/each}
    {/if}
</div>
