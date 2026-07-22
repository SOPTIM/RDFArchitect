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

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { migrationState } from "$lib/sharedState.svelte.js";
    import { isPrefixOnlyRename } from "$lib/utils/migrationUtils.js";

    import RenameTable from "./RenameTable.svelte";

    let hiddenRenames = $state([]);
    let visibleRenames = $state([]);
    let visibleAdded = $state([]);
    let isLoading = $state(true);
    let ignorePrefixes = $state(true);

    let renamedFrom = $state(new Map());
    let unlinkedAdded = $derived.by(() => {
        const linked = new Set(renamedFrom.keys());
        return visibleAdded.filter(c => !linked.has(c.label));
    });

    onMount(() => {
        const storedState = get(migrationState);
        ignorePrefixes = storedState.ignorePrefixes;
        fetch(PUBLIC_BACKEND_URL + "/migrations/class-renamings", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
        })
            .then(res => (res.ok ? res.json() : Promise.reject("Failed")))
            .then(data => {
                const deletedAndRenamed = data.deletedAndRenamed.sort((a, b) =>
                    a.oldResource.label.localeCompare(b.oldResource.label),
                );
                hiddenRenames = ignorePrefixes
                    ? deletedAndRenamed.filter(r =>
                          isPrefixOnlyRename(
                              r.oldResource.iri,
                              r.newResource?.iri,
                          ),
                      )
                    : [];
                visibleRenames = ignorePrefixes
                    ? deletedAndRenamed.filter(
                          r =>
                              !isPrefixOnlyRename(
                                  r.oldResource.iri,
                                  r.newResource?.iri,
                              ),
                      )
                    : deletedAndRenamed;
                const hiddenTargetIRIs = hiddenRenames
                    .map(r => r.newResource?.iri)
                    .filter(iri => iri != null);
                visibleAdded = data.added.filter(
                    addedClass => !hiddenTargetIRIs.includes(addedClass.iri),
                );

                for (let rename of deletedAndRenamed) {
                    if (rename.newResource) {
                        renamedFrom.set(
                            rename.newResource.label,
                            rename.oldResource.label,
                        );
                    }
                }
            })
            .catch(e => console.log("Failed to fetch class overview:", e))
            .finally(() => (isLoading = false));
    });

    function addRenameMapping(renameCandidate, newClass) {
        const newMap = new Map(renamedFrom);
        newMap.set(newClass.label, renameCandidate.oldResource.label);
        renamedFrom = newMap;
        renameCandidate.newResource = newClass;
        renameCandidate.confidenceScore = 1;
    }

    function dissolveRenameMapping(renameCandidate) {
        const newMap = new Map(renamedFrom);
        newMap.delete(renameCandidate.newResource.label);
        renamedFrom = newMap;
        renameCandidate.newResource = null;
    }

    export async function onNext() {
        let body = [...hiddenRenames, ...visibleRenames].filter(
            r => r.newResource != null,
        );
        try {
            const res = await fetch(
                PUBLIC_BACKEND_URL + "/migrations/class-renamings",
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    credentials: "include",
                    body: JSON.stringify(body),
                },
            );
            if (!res.ok) {
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

    {#if !isLoading && visibleRenames.length === 0 && visibleAdded.length === 0}
        <EmptyStateCard
            title="No Class Changes"
            description="There are no class changes to review in this migration."
        />
    {:else}
        {#if visibleRenames.length > 0}
            <RenameTable
                renameCandidates={visibleRenames}
                unlinkedNewItems={unlinkedAdded}
                allAddedItems={visibleAdded}
                onAddMapping={(rename, newItem) =>
                    addRenameMapping(rename, newItem)}
                onDissolveMapping={rename => dissolveRenameMapping(rename)}
                propertyType="Class"
            />
        {/if}

        {#if visibleAdded.length > 0}
            <div>
                <h3 class="mb-3 text-lg font-semibold">Added Classes</h3>
                <div class="space-y-1">
                    {#each visibleAdded as addedClass}
                        <div
                            class="bg-lightgray flex items-center justify-between px-3 py-1 text-sm"
                        >
                            <p>{addedClass.label}</p>
                            {#if renamedFrom.has(addedClass.label)}
                                <span
                                    class="text-soptim-dunkelgrau text-xs italic"
                                >
                                    renamed from {renamedFrom.get(
                                        addedClass.label,
                                    )}
                                </span>
                            {/if}
                        </div>
                    {/each}
                </div>
            </div>
        {/if}
    {/if}
</div>
