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
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { isReadOnly } from "$lib/api/apiWorkspaceUtils.js";
    import { asyncValue } from "$lib/asyncValue.svelte.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";

    import WorkspaceActionsMenu from "../workspaceActions/WorkspaceActionsMenu.svelte";

    let { name, active = false, onActivate } = $props();

    const tabClasses =
        "flex h-[2.2rem] max-w-64 items-center rounded-t-lg border border-b-0 pr-[0.15rem] transition-colors";
    const activeTabClasses =
        "border-button-default-background bg-nav-active-background text-nav-active-text";
    const inactiveTabClasses =
        "text-nav-text border-transparent hover:bg-nav-hover-background";

    const readonlyValue = asyncValue(() => name, isReadOnly);

    let showDeleteDialog = $state(false);
    const readonly = $derived(readonlyValue.current ?? false);
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="contents">
        <div
            class={`${tabClasses} ${active ? activeTabClasses : inactiveTabClasses}`}
        >
            <button
                type="button"
                role="tab"
                aria-selected={active}
                class="focus-visible:outline-button-default-background inline-flex h-full max-w-52 min-w-0 cursor-pointer items-center gap-[0.4rem] pr-[0.35rem] pl-[0.6rem] text-[0.9rem] font-medium text-inherit focus-visible:outline-2 focus-visible:-outline-offset-2"
                title={name}
                onclick={() => onActivate?.()}
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
            </button>
            <button
                type="button"
                class="hover:bg-button-hover-background hover:text-button-hover-text focus-visible:outline-button-default-background h-[1.35rem] w-[1.35rem] cursor-pointer rounded-md text-[0.8rem] text-inherit transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2"
                aria-label={`Delete workspace ${name}`}
                title="Delete Workspace"
                onclick={() => (showDeleteDialog = true)}
            >
                <Fa icon={faXmark} />
            </button>
        </div>
    </ContextMenu.TriggerArea>
    <WorkspaceActionsMenu
        workspaceName={name}
        {readonly}
        bind:showDeleteDialog
    />
</ContextMenu.Root>
