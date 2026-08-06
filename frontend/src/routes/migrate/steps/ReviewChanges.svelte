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
    import { onMount } from "svelte";
    import { get } from "svelte/store";

    import { BackendConnection } from "$lib/api/backend.js";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { migrationState } from "$lib/sharedState.svelte.js";

    import ResourceChangeCard from "./ResourceChangeCard.svelte";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let classes = $state([]);
    let isLoading = $state(true);
    let ignorePrefixes = $state(false);

    onMount(() => {
        let storedState = get(migrationState);
        ignorePrefixes = storedState.ignorePrefixes;

        bec.getChanges()
            .then(res => (res.ok ? res.json() : Promise.reject("Failed")))
            .then(data => {
                classes = data.sort((a, b) => {
                    return a.label.localeCompare(b.label);
                });
            })
            .catch(e => console.log("Failed to fetch changes:", e))
            .finally(() => {
                isLoading = false;
            });
    });

    export async function onNext() {
        try {
            const res = await bec.confirmChanges(classes);
            if (!res.ok) {
                toastStore.error(
                    "Save failed",
                    "Could not save changes for the migration.",
                );
            }
        } catch (e) {
            console.log("Failed to save changes:", e);
            toastStore.error(
                "Save failed",
                "Could not save changes for the migration.",
            );
        }
    }
</script>

<div class="flex h-full flex-col space-y-6 p-2">
    <InfoBox>
        This step lets you get an overview over all changes made in the schema
        update and lets you assign comments to those changes. These comments
        will be inserted in the migration script and the migration report you
        can download in the next step.
    </InfoBox>

    {#if isLoading}
        <p class="text-text-subtle text-sm">Loading changes…</p>
    {:else if classes.length === 0}
        <EmptyStateCard
            title="No Changes"
            description="There are no changes to review in this migration."
        />
    {:else}
        {#each classes as cls, i}
            <div class="space-y-3">
                <ResourceChangeCard
                    bind:resource={classes[i]}
                    title="Class"
                    {ignorePrefixes}
                />

                {#if cls.attributes?.length > 0}
                    <div class="border-l-blue ml-6 space-y-2 border-l-4 pl-4">
                        <h4 class="text-default-text text-sm font-semibold">
                            Attributes
                        </h4>
                        {#each cls.attributes as attribute (attribute.iri)}
                            {#if attribute.semanticResourceChangeType !== "DELETED_FROM_INHERITANCE" && attribute.semanticResourceChangeType !== "ADDED_FROM_INHERITANCE"}
                                <ResourceChangeCard
                                    bind:resource={
                                        cls.attributes[
                                            cls.attributes.indexOf(attribute)
                                        ]
                                    }
                                    title="Attribute"
                                    {ignorePrefixes}
                                />
                            {/if}
                        {/each}
                    </div>
                {/if}

                {#if cls.associations?.length > 0}
                    <div class="border-l-blue ml-6 space-y-2 border-l-4 pl-4">
                        <h4 class="text-default-text text-sm font-semibold">
                            Associations
                        </h4>
                        {#each cls.associations as association (association.iri)}
                            {#if association.semanticResourceChangeType !== "DELETED_FROM_INHERITANCE" && association.semanticResourceChangeType !== "ADDED_FROM_INHERITANCE"}
                                <ResourceChangeCard
                                    bind:resource={
                                        cls.associations[
                                            cls.associations.indexOf(
                                                association,
                                            )
                                        ]
                                    }
                                    title="Association"
                                    {ignorePrefixes}
                                />
                            {/if}
                        {/each}
                    </div>
                {/if}

                {#if cls.enumEntries?.length > 0}
                    <div class="border-l-blue ml-6 space-y-2 border-l-4 pl-4">
                        <h4 class="text-default-text text-sm font-semibold">
                            Enum Entries
                        </h4>
                        {#each cls.enumEntries as enumEntry (enumEntry.iri)}
                            <ResourceChangeCard
                                bind:resource={
                                    cls.enumEntries[
                                        cls.enumEntries.indexOf(enumEntry)
                                    ]
                                }
                                title="Enum Entry"
                                {ignorePrefixes}
                            />
                        {/each}
                    </div>
                {/if}
            </div>
        {/each}
    {/if}
</div>
