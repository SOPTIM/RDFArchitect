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
    import { faDatabase } from "@fortawesome/free-solid-svg-icons";

    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    let workspaceList = $state([]);
    let selectedWorkspaceName = $derived(
        editorState.selectedWorkspace.getValue(),
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchNavigationObject();
    });

    async function fetchNavigationObject() {
        workspaceList = (await workspaceStore.getWorkspaces()) ?? [];
    }
</script>

<div class="nav-sidebar h-full w-full">
    <div class="nav-sidebar__scroll no-scrollbar">
        {#if workspaceList && workspaceList.length > 0}
            <div class="flex flex-col gap-1 pr-2">
                {#each workspaceList as workspace}
                    <NavigationEntry
                        level={1}
                        label={workspace.label}
                        icon={faDatabase}
                        isSelected={workspace.label === selectedWorkspaceName}
                        title={workspace.label}
                        onclick={() => {
                            editorState.selectedWorkspace.updateValue(
                                workspace.label,
                            );
                        }}
                    />
                {/each}
            </div>
        {:else}
            <div class="p-4 text-left">No data available</div>
        {/if}
    </div>
</div>
