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
    import { Fa } from "svelte-fa";

    import {
        dragHasFiles,
        endFileDrag,
        extractDroppedItems,
        fileDragState,
    } from "$lib/fileDragState.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    import WorkspaceTab from "./WorkspaceTab.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import NewWorkspaceDialog from "../../NewWorkspaceDialog.svelte";
    import { acceptSchemaDrop } from "../schemaDrop.js";

    let showNewWorkspaceDialog = $state(false);
    let showImportDialog = $state(false);
    let droppedItems = $state(null);
    let draggedOver = $state(false);

    let workspaces = $state([]);
    const activeWorkspace = $derived(editorState.selectedWorkspace.getValue());
    const newWorkspaceClasses = $derived(
        !fileDragState.active
            ? ""
            : draggedOver
              ? "relative z-40 bg-lightblue text-blue"
              : "relative z-40 bg-window-background",
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        workspaces = await workspaceStore.getWorkspaces();
        await workspaceState.load();
    });

    function handleDragOver(event) {
        if (!dragHasFiles(event)) {
            return;
        }
        event.preventDefault();
        draggedOver = true;
    }

    function handleDragLeave(event) {
        if (event.currentTarget.contains(event.relatedTarget)) {
            return;
        }
        draggedOver = false;
    }

    /** Imports what was dropped into a workspace that the import itself brings into existence. */
    function handleDrop(event) {
        if (!dragHasFiles(event)) {
            return;
        }
        event.preventDefault();
        draggedOver = false;
        endFileDrag();

        const accepted = acceptSchemaDrop(
            extractDroppedItems(event.dataTransfer),
        );
        if (!accepted) {
            return;
        }
        droppedItems = accepted;
        showImportDialog = true;
    }
</script>

<div
    class="border-border-strong bg-default-background flex h-[2.65rem] min-h-[2.65rem] items-end gap-1 overflow-x-auto overflow-y-hidden border-b px-[0.4rem]"
    role="tablist"
    aria-label="Workspaces"
>
    {#each workspaces as workspace (workspace.label)}
        <WorkspaceTab
            name={workspace.label}
            active={workspace.label === activeWorkspace}
            onActivate={() => workspaceState.activate(workspace.label)}
        />
    {/each}
    <button
        type="button"
        class={`hover:bg-nav-hover-background focus-visible:outline-button-default-background text-nav-text mb-[0.1rem] ml-1 h-[2rem] w-[2rem] cursor-pointer rounded-lg text-[0.8rem] transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2 ${newWorkspaceClasses}`}
        aria-label="New Workspace"
        title={fileDragState.active
            ? "Import into a new workspace"
            : "New Workspace"}
        onclick={() => (showNewWorkspaceDialog = true)}
        ondragover={handleDragOver}
        ondragleave={handleDragLeave}
        ondrop={handleDrop}
    >
        <Fa icon={faPlus} />
    </button>
</div>

<NewWorkspaceDialog
    bind:showDialog={showNewWorkspaceDialog}
    existingNames={workspaces.map(ws => ws.label)}
/>

<ImportDialog
    bind:showDialog={showImportDialog}
    {droppedItems}
    forNewWorkspace
/>
