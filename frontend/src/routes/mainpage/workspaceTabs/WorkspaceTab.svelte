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
    import {
        faDatabase,
        faLock,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { asyncValue } from "$lib/asyncValue.svelte.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { dragHasFiles, fileDragState } from "$lib/fileDragState.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import WorkspaceActionsMenu from "../workspaceActions/WorkspaceActionsMenu.svelte";

    let { name, active = false, onActivate } = $props();

    /**
     * How long files have to rest on the tab before it hands the editor to its workspace.
     * Switching at once would make every tab passed on the way flash by.
     */
    const SWITCH_AFTER_MS = 250;

    const tabClasses =
        "group relative flex h-[2.2rem] max-w-64 min-w-[9rem] items-center rounded-t-lg border border-b-0 pr-[0.15rem] transition-colors";
    const activeTabClasses =
        "border-button-default-background bg-nav-active-background text-nav-active-text";
    const inactiveTabClasses =
        "text-nav-text border-transparent hover:bg-nav-hover-background";
    // `:hover` does not apply while dragging, so the tab says for itself that it is being aimed at.
    const draggedOverTabClasses =
        "border-blue bg-lightblue text-nav-active-text";
    // An inactive tab is transparent, so it needs a surface of its own to stay out of the dimming
    // the bar behind it carries while files are dragged.
    const draggableTabClasses =
        "text-nav-text border-transparent bg-window-background";

    const readonlyValue = asyncValue(() => name, workspaceStore.isReadOnly);

    let showDeleteDialog = $state(false);
    let draggedOver = $state(false);

    let switchTimeout = null;
    const readonly = $derived(readonlyValue.current ?? false);
    const stateClasses = $derived(
        active
            ? activeTabClasses
            : draggedOver
              ? draggedOverTabClasses
              : fileDragState.active
                ? draggableTabClasses
                : inactiveTabClasses,
    );
    const dragLayerClasses = $derived(fileDragState.active ? "z-40" : "");

    $effect(() => cancelSwitch);

    function scheduleSwitch(event) {
        if (!dragHasFiles(event)) {
            return;
        }
        draggedOver = true;
        if (active || switchTimeout !== null) {
            return;
        }
        switchTimeout = setTimeout(() => {
            switchTimeout = null;
            onActivate?.();
        }, SWITCH_AFTER_MS);
    }

    /**
     * A tab hands the editor over, it does not take files itself. Saying so keeps a drop that
     * lands here from looking like an import that did nothing.
     */
    function refuseDrop(event) {
        cancelSwitch(event);
        if (!dragHasFiles(event)) {
            return;
        }
        // Answered here, so the window does not report it as a drop that landed nowhere.
        event.preventDefault();
        onActivate?.();
        toastStore.info(
            "Nothing imported",
            `Drop the schemas on the navigation to import them into "${name}".`,
        );
    }

    function cancelSwitch(event) {
        // Moving onto the button that fills the tab leaves it as far as the event is concerned;
        // restarting the countdown there would keep it from ever running out.
        if (event?.currentTarget?.contains(event.relatedTarget)) {
            return;
        }
        draggedOver = false;
        if (switchTimeout !== null) {
            clearTimeout(switchTimeout);
            switchTimeout = null;
        }
    }
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="contents">
        <div
            class={`${tabClasses} ${stateClasses} ${dragLayerClasses}`}
            role="presentation"
            ondragenter={scheduleSwitch}
            ondragover={scheduleSwitch}
            ondragleave={cancelSwitch}
            ondrop={refuseDrop}
        >
            <button
                type="button"
                role="tab"
                aria-selected={active}
                aria-label={name}
                class="focus-visible:outline-button-default-background absolute inset-0 cursor-pointer rounded-t-lg focus-visible:outline-2 focus-visible:-outline-offset-2"
                title={name}
                onclick={() => onActivate?.()}
            ></button>
            <span
                class="pointer-events-none relative inline-flex min-w-0 items-center gap-[0.4rem] pr-[0.35rem] pl-[0.6rem] text-[0.9rem] font-medium"
            >
                <span class="inline-flex">
                    <Fa icon={faDatabase} />
                </span>
                <span class="truncate">{name}</span>
                {#if readonly}
                    <span
                        class="text-nav-secondary-text inline-flex text-[0.72rem]"
                        title="Read-only"
                    >
                        <Fa icon={faLock} />
                    </span>
                {/if}
            </span>
            <button
                type="button"
                class="hover:bg-button-hover-background hover:text-button-hover-text focus-visible:outline-button-default-background invisible relative ml-auto h-[1.35rem] w-[1.35rem] shrink-0 cursor-pointer rounded-md text-[0.8rem] text-inherit transition-colors group-focus-within:visible group-hover:visible focus-visible:visible focus-visible:outline-2 focus-visible:-outline-offset-2"
                aria-label={`Delete workspace ${name}`}
                title="Delete Workspace"
                onclick={() => (showDeleteDialog = true)}
            >
                <Fa icon={faTrash} />
            </button>
        </div>
    </ContextMenu.TriggerArea>
    <WorkspaceActionsMenu
        workspaceName={name}
        {readonly}
        bind:showDeleteDialog
    />
</ContextMenu.Root>
