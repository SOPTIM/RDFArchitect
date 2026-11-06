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
    import { editorState } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    let workspaces = $state([]);
    let workspace = $state("");

    $effect(() => {
        workspaceStore.getWorkspaces().then(list => {
            workspaces = (list ?? []).map(entry => entry.label);
            // A choice of one is not a choice, and leaving it unmade greys out the button for no
            // reason.
            if (workspaces.length === 1) {
                workspace = workspaces[0];
            }
        });
    });

    function open() {
        if (workspace) {
            editorState.selectWorkspace(workspace);
        }
    }
</script>

<!--
  @component
  Where a workbench opened without a workspace — a reload, a bookmark — lets the user pick one,
  rather than leaving them on a page whose only way out is the logo.
-->

<div class="flex w-72 flex-col gap-2 text-left">
    {#if workspaces.length > 0}
        <label class="text-text-subtle text-xs">
            Workspace
            <select
                class="border-border bg-input-default-background text-default-text h-9 w-full rounded border px-2 text-sm"
                bind:value={workspace}
            >
                <option value="" disabled>Pick a workspace</option>
                {#each workspaces as name (name)}
                    <option value={name}>{name}</option>
                {/each}
            </select>
        </label>
        <button
            class="bg-button-default-background text-button-default-text hover:bg-button-hover-background h-9 cursor-pointer rounded text-sm font-semibold disabled:cursor-default disabled:opacity-50"
            disabled={!workspace}
            onclick={open}
        >
            Open its constraints
        </button>
    {/if}
    <a
        class="text-blue self-center text-sm underline underline-offset-2"
        href="/mainpage"
    >
        Back to the main page
    </a>
</div>
