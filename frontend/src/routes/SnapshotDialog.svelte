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
    import { faClipboardList } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";
    import { v4 as uuidv4 } from "uuid";

    import { BackendConnection } from "$lib/api/backend.js";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

    import ButtonControl from "../lib/components/ButtonControl.svelte";
    import { editorState } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedWorkspaceName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const workspaceSelectId = `workspaceSelect-${uuidv4()}`;

    let workspaceName = $state(null);
    let workspaces = $state([]);
    let base64Token = $state();

    const workspaceSelectionLocked = $derived(!!lockedWorkspaceName);

    function onOpen() {
        workspaceName =
            lockedWorkspaceName ?? editorState.selectedWorkspace.getValue();
        if (workspaceSelectionLocked) {
            workspaces = [{ label: lockedWorkspaceName }];
        } else {
            loadWorkspaces();
        }
    }

    async function snapshotWorkspace() {
        const res = await bec.createSnapshot(workspaceName);
        if (res.ok) {
            base64Token = await res.text();
            console.log(
                "Successfully created snapshot for workspace",
                workspaceName,
            );
            toastStore.success(
                "Snapshot ready",
                `Share link created for "${workspaceName}".`,
            );
        } else {
            console.error(
                "Error creating snapshot for workspace:",
                res.statusText,
            );
            toastStore.error(
                "Snapshot failed",
                `Could not create a snapshot for "${workspaceName}".`,
            );
        }
    }

    async function loadWorkspaces() {
        const res = await bec.getWorkspaceNames();
        const workspaceNames = await res.json();
        workspaces = workspaceNames.map(name => ({ label: name }));
    }

    async function copyToClipboard() {
        try {
            await navigator.clipboard.writeText(
                `${window.location.origin}/?snapshot=${base64Token}`,
            );
            toastStore.success("Snapshot link copied to clipboard");
        } catch (err) {
            console.error("Failed to copy: ", err);
            toastStore.error(
                "Copy failed",
                "Could not write the snapshot link to the clipboard.",
            );
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    primaryLabel="Share Snapshot"
    onPrimary={snapshotWorkspace}
    closeOnPrimary={false}
    title="Share Snapshot"
    disablePrimary={!workspaceName}
>
    <div class="mx-2 flex h-full flex-col">
        <label for={workspaceSelectId} class="mb-1">Workspace</label>
        <SelectEditControl
            id={workspaceSelectId}
            bind:value={workspaceName}
            options={workspaces}
            getOptionValue={workspace => workspace.label}
            getOptionLabel={workspace => workspace.label}
            disabled={workspaceSelectionLocked || workspaces.length === 0}
            placeholder="Select workspace"
        />

        <div class="mt-4 flex h-full flex-col">
            <p class="mb-1">Snapshot Link</p>
            <div class="flex items-center gap-2">
                <div
                    class="border-border bg-window-background focus:border-blue h-9 w-full rounded border-2 p-2"
                >
                    {base64Token
                        ? `${window.location.origin}/?snapshot=${base64Token}`
                        : ""}
                </div>
                {#if base64Token}
                    <div>
                        <ButtonControl
                            callOnClick={copyToClipboard}
                            title="Copy to clipboard"
                            height={9}
                        >
                            <Fa icon={faClipboardList} />
                        </ButtonControl>
                    </div>
                {/if}
            </div>
        </div>
    </div>
</ActionDialog>
