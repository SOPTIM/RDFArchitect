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
    import { onMount } from "svelte";
    import { get } from "svelte/store";

    import {
        confirmRenamedClasses,
        getClassMappings,
    } from "$lib/api/generated/index.ts";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { migrationState } from "$lib/sharedState.svelte.js";
    import {
        groupMappingsForSubmission,
        isPrefixOnlyRename,
    } from "$lib/utils/migrationUtils.js";

    import MappingSection from "./MappingSection.svelte";

    let hiddenMappings = $state([]);
    let visibleMappings = $state([]);
    let visibleAdded = $state([]);
    let isLoading = $state(true);
    let ignorePrefixes = $state(true);

    let renamedFrom = $derived.by(() => {
        const map = new Map();
        for (const mapping of [...hiddenMappings, ...visibleMappings]) {
            for (const newClass of mapping.newResources) {
                const froms = mapping.oldResources.map(r => r.label);
                map.set(newClass.iri, [
                    ...(map.get(newClass.iri) ?? []),
                    ...froms,
                ]);
            }
        }
        return map;
    });

    onMount(() => {
        const storedState = get(migrationState);
        ignorePrefixes = storedState.ignorePrefixes;
        getClassMappings()
            .then(res => (res.error ? Promise.reject("Failed") : res.data))
            .then(data => {
                const deletedAndRemapped = [...data.deletedAndRemapped].sort(
                    (a, b) =>
                        a.oldResources[0].label.localeCompare(
                            b.oldResources[0].label,
                        ),
                );
                const isPrefixOnly = mapping =>
                    isPrefixOnlyRename(
                        mapping.oldResources[0].iri,
                        mapping.newResources[0]?.iri,
                    );
                hiddenMappings = ignorePrefixes
                    ? deletedAndRemapped.filter(isPrefixOnly)
                    : [];
                visibleMappings = ignorePrefixes
                    ? deletedAndRemapped.filter(m => !isPrefixOnly(m))
                    : deletedAndRemapped;
                const hiddenTargetIRIs = hiddenMappings
                    .map(m => m.newResources[0]?.iri)
                    .filter(iri => iri != null);
                visibleAdded = data.added
                    .filter(
                        addedClass =>
                            !hiddenTargetIRIs.includes(addedClass.iri),
                    )
                    .sort((a, b) => a.label.localeCompare(b.label));
            })
            .catch(e => console.log("Failed to fetch class overview:", e))
            .finally(() => (isLoading = false));
    });

    export async function onNext() {
        const { mappings, invalidMappingIds } = groupMappingsForSubmission([
            ...hiddenMappings,
            ...visibleMappings,
        ]);

        if (invalidMappingIds.size > 0) {
            toastStore.warning(
                "Some mappings were skipped",
                "A class can't be linked as both a merge target and a split source at once. Fix those rows before continuing.",
            );
        }

        try {
            const { error } = await confirmRenamedClasses({
                body: mappings,
            });

            if (error) {
                toastStore.error(
                    "Save failed",
                    "Could not save class renames for the migration.",
                );
            }
        } catch (e) {
            console.log("Failed to save class renames:", e);
            toastStore.error(
                "Save failed",
                "Could not save class renames for the migration.",
            );
        }
    }
</script>

<div class="text-default-text flex flex-col space-y-8 p-2 pr-4">
    <InfoBox type="info">
        <p>
            This step gives an overview of all added and deleted classes, as
            well as possible class renames that were detected. <br />
            Please verify that the detected renames are correct, or adjust them as
            necessary.
        </p>
    </InfoBox>

    {#if !isLoading && visibleMappings.length === 0 && visibleAdded.length === 0}
        <EmptyStateCard
            title="No Class Changes"
            description="There are no class changes to review in this migration."
        />
    {:else}
        <MappingSection
            bind:deletedAndRemapped={visibleMappings}
            added={visibleAdded}
            addedTitle="Added Classes"
            candidates={visibleAdded}
            resourceType="Class"
            {renamedFrom}
        />
    {/if}
</div>
