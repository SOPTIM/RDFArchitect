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
    import { v4 as uuidv4 } from "uuid";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    let { showDialog = $bindable(), workspaceName } = $props();

    const workspaceInputId = `renameWorkspaceName-${uuidv4()}`;
    const invalidCharacters = /[/\\?#]/;

    let workspaceNameUserInput = $state("");

    const trimmedName = $derived(workspaceNameUserInput.trim());
    const nameExists = $derived(
        trimmedName !== workspaceName &&
            workspaceState.getNames().includes(trimmedName),
    );
    const nameHasInvalidCharacters = $derived(
        invalidCharacters.test(trimmedName),
    );
    const disableSubmit = $derived(
        !trimmedName ||
            trimmedName === workspaceName ||
            nameExists ||
            nameHasInvalidCharacters,
    );

    function onOpen() {
        workspaceNameUserInput = workspaceName ?? "";
    }

    function onClose() {
        workspaceNameUserInput = "";
    }

    async function renameWorkspace() {
        const oldName = workspaceName;
        const newName = trimmedName;
        try {
            if (!(await workspaceState.rename(oldName, newName))) {
                return;
            }
            toastStore.success(
                "Workspace renamed",
                `"${oldName}" is now "${newName}".`,
            );
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Rename Workspace"
    onPrimary={renameWorkspace}
    title={workspaceName
        ? `Rename Workspace "${workspaceName}"`
        : "Rename Workspace"}
    disablePrimary={disableSubmit}
>
    <div class="mx-2 flex h-full flex-col">
        <label for={workspaceInputId} class="mb-1">Name</label>
        <input
            class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={workspaceInputId}
            placeholder="Workspace name"
            autocomplete="off"
            bind:value={workspaceNameUserInput}
        />
        {#if nameExists}
            <div class="mt-1 mb-1 h-6 text-sm">Workspace already exists</div>
        {:else if nameHasInvalidCharacters}
            <div class="mt-1 mb-1 h-6 text-sm">
                Name must not contain / \ ? or #
            </div>
        {/if}
    </div>
</ActionDialog>
