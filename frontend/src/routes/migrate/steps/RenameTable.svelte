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
    let {
        renameCandidates = $bindable([]),
        unlinkedNewItems = $bindable([]),
        allAddedItems = $bindable([]),
        onAddMapping,
        onDissolveMapping,
        propertyType = "Item",
    } = $props();

    function handleSelectionChange(rename, event) {
        const value = event.target.value;

        const selectedItem = unlinkedNewItems.find(
            item => item.label === value,
        );

        if (rename.newResource) {
            onDissolveMapping(rename);
        }

        if (selectedItem) {
            onAddMapping(rename, selectedItem);
        }
    }
</script>

<div>
    <h3 class="mb-3 font-semibold">
        Renamed and Deleted {propertyType}s
    </h3>
    <div
        class="border-border bg-window-background overflow-x-auto rounded-xl border shadow-sm"
    >
        <table class="w-full border-collapse text-left text-sm">
            <thead class="bg-lightgray">
                <tr>
                    <th class="w-5/11 px-3 py-2 font-medium">Old Name</th>
                    <th class="w-5/11 px-3 py-2 font-medium">New Name</th>
                    <th class="w-1/11 px-3 py-2 font-medium">Confidence</th>
                </tr>
            </thead>
            <tbody>
                {#each renameCandidates as rename (rename.oldResource.label)}
                    <tr>
                        <td class="px-3 py-2">
                            {rename.oldResource.label}
                        </td>
                        <td class="px-3 py-2">
                            <select
                                class="border-border bg-input-default-background text-default-text focus:ring-border-select rounded-md border px-2 py-1 text-sm focus:ring-2 focus:outline-none"
                                onchange={e => handleSelectionChange(rename, e)}
                                value={rename.newResource?.label ?? ""}
                            >
                                <option value="">
                                    —
                                </option>
                                {#if rename.newResource && allAddedItems.some(a => a.label === rename.newResource.label)}
                                    <option value={rename.newResource.label}>
                                        {rename.newResource.label}
                                    </option>
                                {/if}
                                {#each unlinkedNewItems as item (item.label)}
                                    <option
                                        value={item.label}
                                    >
                                        {item.label}
                                    </option>
                                {/each}
                            </select>
                        </td>
                        <td class="px-3 py-2">
                            {#if rename.confidenceScore == null || rename.newResource == null}
                                —
                            {:else}
                                {Math.round(rename.confidenceScore * 10000) /
                                    100}%
                            {/if}
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
</div>
