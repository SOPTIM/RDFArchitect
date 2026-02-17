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
    import { isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    import ChangesRow from "./ChangesRow.svelte";

    const { getExpanded, setExpanded, cleanExpandedStateMap } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let changelog = $state();

    let readonlyDataset = $state(true);

    let selectedDatasetName = $derived(editorState.selectedDataset.getValue());
    let selectedGraphUri = $derived(editorState.selectedGraph.getValue());

    $effect(async () => {
        forceReloadTrigger.subscribe();
        if (selectedDatasetName) {
            readonlyDataset = await isReadOnly(selectedDatasetName);
        }
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        if (selectedDatasetName && selectedGraphUri) {
            await getChangelog();
        }
    });

    async function getChangelog() {
        if (!selectedDatasetName || !selectedGraphUri) {
            return;
        }
        const res = await bec.getChangelog(
            selectedDatasetName,
            selectedGraphUri,
        );
        if (res.ok) {
            changelog = await res.json();
            changelog.reverse();
            cleanExpandedStateMap(changelog);
        } else {
            console.error("Failed to fetch changelog:", res.statusText);
        }
    }
</script>

{#if changelog && changelog.length > 0}
    <div class="no-scrollbar h-full overflow-auto pb-10">
        <div
            class="border-border bg-window-background m-4 rounded border p-6 shadow"
        >
            <table class="w-full table-auto text-left">
                <thead class="border-border border-b-4">
                    <tr>
                        <th class="p-4">Change</th>
                        <th class="p-4">Timestamp</th>
                        <th class="w-0 p-4">Details</th>
                        <th class="w-0 p-4"></th>
                    </tr>
                </thead>
                <tbody class="divide-border divide-y text-sm">
                    {#each changelog as change, i (change.changeId)}
                        <ChangesRow
                            {change}
                            {getExpanded}
                            {setExpanded}
                            newest={i === 0}
                            readonly={readonlyDataset}
                        />
                    {/each}
                </tbody>
            </table>
        </div>
    </div>
{:else if selectedGraphUri}
    <div class="flex h-full items-center justify-center">
        <p class="text-default-text text-lg">No changes in current session</p>
    </div>
{/if}
