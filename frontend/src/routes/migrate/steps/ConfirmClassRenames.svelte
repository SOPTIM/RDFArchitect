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

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

    import RenameTable from "./RenameTable.svelte";

    let deletedAndRenamed = $state([]);
    let added = $state([]);
    let modified = $state([]);
    let isLoading = $state(true);

    let renamedFrom = $state(new Map());
    let unlinkedAdded = $derived.by(() => {
        const linked = new Set(renamedFrom.keys());
        return added.filter(c => !linked.has(c.label));
    });

    onMount(() => {
        fetch(PUBLIC_BACKEND_URL + "/migrations/class-renamings", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
        })
            .then(res => (res.ok ? res.json() : Promise.reject("Failed")))
            .then(data => {
                deletedAndRenamed = data.deletedAndRenamed.sort((a, b) =>
                    a.oldResource.label.localeCompare(b.oldResource.label),
                );
                added = data.added;
                modified = data.modified;

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
        let body = deletedAndRenamed.filter(r => r.newResource != null);
        await fetch(PUBLIC_BACKEND_URL + "/migrations/class-renamings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify(body),
        });
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

    {#if !isLoading && deletedAndRenamed.length === 0 && added.length === 0 && modified.length === 0}
        <EmptyStateCard
            title="No Class Changes"
            description="There are no class changes to review in this migration."
        />
    {:else}
        {#if deletedAndRenamed.length > 0}
            <RenameTable
                renameCandidates={deletedAndRenamed}
                unlinkedNewItems={unlinkedAdded}
                allAddedItems={added}
                onAddMapping={(rename, newItem) =>
                    addRenameMapping(rename, newItem)}
                onDissolveMapping={rename => dissolveRenameMapping(rename)}
                propertyType="Class"
            />
        {/if}

        {#if added.length > 0}
            <div>
                <h3 class="mb-3 text-lg font-semibold">Added Classes</h3>
                <div class="space-y-1">
                    {#each added as addedClass}
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

        {#if modified.length > 0}
            <div>
                <h3 class="mb-3] text-lg font-semibold">Modified Classes</h3>
                <ul class="list-inside list-disc space-y-1 text-sm">
                    {#each modified as modifiedClass}
                        <li>{modifiedClass.label}</li>
                    {/each}
                </ul>
            </div>
        {/if}
    {/if}
</div>
