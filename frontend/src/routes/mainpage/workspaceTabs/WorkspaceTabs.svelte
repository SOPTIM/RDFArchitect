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
    // A "+" alone reads as "new workspace", not as "new workspace from these files", so while
    // files are dragged the button opens up and says so.
    const newWorkspaceClasses = $derived(
        !fileDragState.active
            ? "px-[0.6rem]"
            : draggedOver
              ? "border-blue bg-lightblue text-blue z-40 border-2 border-dashed px-3"
              : "border-blue/70 bg-window-background text-blue z-40 border-2 border-dashed px-3",
    );
    // The label is given its width by a grid column growing from nothing, which is what the
    // animation runs along; a width of its own would have to be guessed.
    const newWorkspaceLabelClasses = $derived(
        fileDragState.active ? "ml-2 grid-cols-[1fr]" : "ml-0 grid-cols-[0fr]",
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        workspaces = await workspaceStore.getWorkspaces();
        await workspaceState.load();
    });

    /**
     * Lets the wheel scroll the tabs without holding shift: the bar scrolls sideways only, so a
     * wheel that is not answered here scrolls nothing at all.
     */
    function scrollTabsWithWheel(event) {
        const tabs = event.currentTarget;
        if (
            event.deltaY === 0 ||
            event.shiftKey ||
            tabs.scrollWidth <= tabs.clientWidth
        ) {
            return;
        }
        // Firefox reports lines rather than pixels.
        tabs.scrollLeft +=
            event.deltaMode === 1 ? event.deltaY * 16 : event.deltaY;
        event.preventDefault();
    }

    function handleDragOver(event) {
        if (!dragHasFiles(event) || !fileDragState.active) {
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
        if (!dragHasFiles(event) || !fileDragState.active) {
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
    class="border-border-strong bg-default-background flex h-[2.65rem] min-h-[2.65rem] items-end border-b px-[0.4rem]"
>
    <!-- Takes no more room than the tabs need, so the button stays beside them while they fit and
         gives way to them once they do not. -->
    <div
        class="flex min-w-0 items-end gap-1 overflow-x-auto overflow-y-hidden"
        role="tablist"
        aria-label="Workspaces"
        onwheel={scrollTabsWithWheel}
    >
        {#each workspaces as workspace (workspace.label)}
            <WorkspaceTab
                name={workspace.label}
                active={workspace.label === activeWorkspace}
                onActivate={() => workspaceState.activate(workspace.label)}
            />
        {/each}
    </div>
    <button
        type="button"
        class={`hover:bg-nav-hover-background focus-visible:outline-button-default-background text-nav-text mb-[0.1rem] ml-1 flex h-[2rem] min-w-[2rem] shrink-0 cursor-pointer items-center rounded-lg text-[0.8rem] transition-all duration-200 ease-out focus-visible:outline-2 focus-visible:-outline-offset-2 ${newWorkspaceClasses}`}
        aria-label="New Workspace"
        title={fileDragState.active
            ? "Import into a new workspace"
            : "New Workspace"}
        onclick={() => (showNewWorkspaceDialog = true)}
        ondragover={handleDragOver}
        ondragleave={handleDragLeave}
        ondrop={handleDrop}
    >
        <span class="shrink-0"><Fa icon={faPlus} /></span>
        <span
            class={`grid transition-all duration-200 ease-out ${newWorkspaceLabelClasses}`}
        >
            <span class="overflow-hidden font-medium whitespace-nowrap">
                Import files into new workspace
            </span>
        </span>
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
