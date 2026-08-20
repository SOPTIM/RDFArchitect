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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";
    import { untrack } from "svelte";
    import { Fa } from "svelte-fa";

    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    import WorkspaceTab from "./WorkspaceTab.svelte";
    import NewWorkspaceDialog from "../../NewWorkspaceDialog.svelte";

    let showNewWorkspaceDialog = $state(false);

    const workspaces = $derived(workspaceState.getNames());
    const activeWorkspace = $derived(editorState.selectedWorkspace.getValue());

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await untrack(async () => await workspaceState.load());
    });
</script>

<div
    class="border-border-strong bg-default-background flex h-[2.65rem] min-h-[2.65rem] items-end gap-1 overflow-x-auto overflow-y-hidden border-b px-[0.4rem]"
    role="tablist"
    aria-label="Workspaces"
>
    {#each workspaces as name (name)}
        <WorkspaceTab
            {name}
            active={name === activeWorkspace}
            onActivate={() => workspaceState.activate(name)}
        />
    {/each}
    <button
        type="button"
        class="hover:bg-nav-hover-background focus-visible:outline-button-default-background text-nav-text mb-[0.1rem] ml-1 h-[2rem] w-[2rem] cursor-pointer rounded-lg text-[0.8rem] transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2"
        aria-label="New Workspace"
        title="New Workspace"
        onclick={() => (showNewWorkspaceDialog = true)}
    >
        <Fa icon={faPlus} />
    </button>
</div>

<NewWorkspaceDialog
    bind:showDialog={showNewWorkspaceDialog}
    existingNames={workspaces}
/>
