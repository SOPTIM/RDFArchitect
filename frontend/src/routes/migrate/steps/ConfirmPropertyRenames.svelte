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

    import { PUBLIC_BACKEND_URL } from "$env/static/public";

    let { substeps = [], currentSubstepIndex = 0 } = $props();

    let classes = $state([]);
    let isLoading = $state(true);

    let currentSubstep = $derived(substeps[currentSubstepIndex]);

    onMount(() => {
        fetch(PUBLIC_BACKEND_URL + "/migrations/property-renames", {
            method: "GET",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
        })
            .then(res => (res.ok ? res.json() : Promise.reject("Failed")))
            .then(data => {
                classes = data;
            })
            .catch(e => console.log("Failed to fetch property overview:", e))
            .finally(() => (isLoading = false));
    });

    export async function onNext() {
        let body = buildPropertyRenameList();
        await fetch(PUBLIC_BACKEND_URL + "/migrations/property-renames", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify(body),
        });
    }

    function buildPropertyRenameList() {
        return classes
            .map(cls => {
                const attributeRenames = (
                    cls.attributes?.deletedAndRenamed ?? []
                ).filter(r => r.newResource != null);

                const associationRenames = (
                    cls.associations?.deletedAndRenamed ?? []
                ).filter(r => r.newResource != null);

                const enumEntryRenames = (
                    cls.enumEntries?.deletedAndRenamed ?? []
                ).filter(r => r.newResource != null);

                return {
                    classLabel: cls.label,
                    attributeRenames,
                    associationRenames,
                    enumEntryRenames,
                };
            })
            .filter(
                cls =>
                    cls.attributeRenames.length > 0 ||
                    cls.associationRenames.length > 0 ||
                    cls.enumEntryRenames.length > 0,
            );
    }
</script>

<div class="flex h-full flex-col space-y-4">
    <div class="flex justify-center">
        <div class="flex justify-center space-x-4">
            {#each substeps as substep, i}
                <span
                    class={`px-2 text-sm ${i === currentSubstepIndex ? "border-b-2 font-bold text-blue-700" : "text-button-disabled-background"}`}
                >
                    {substep.title}
                </span>
            {/each}
        </div>
    </div>

    <div class="no-scrollbar flex-1 overflow-y-auto p-2">
        <currentSubstep.component {classes} {isLoading} />
    </div>
</div>
