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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";

    import ButtonControl from "../lib/components/ButtonControl.svelte";
    import { editorState } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedDatasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let datasetName = $state();
    let datasetNames = $state([]);
    let base64Token = $state();

    let copySuccess = $state(false);

    const datasetSelectionLocked = $derived(!!lockedDatasetName);

    function onOpen() {
        datasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        if (!datasetSelectionLocked) {
            getDatasetNames();
        }
    }

    async function snapshotDataset() {
        const res = await bec.createSnapshot(datasetName);
        if (res.ok) {
            base64Token = await res.text();
            console.log(
                "Successfully created snapshot for dataset",
                datasetName,
            );
        } else {
            console.error(
                "Error creating snapshot for dataset:",
                res.statusText,
            );
        }
    }

    async function getDatasetNames() {
        const res = await bec.getDatasetNames();
        datasetNames = await res.json();
    }

    async function copyToClipboard() {
        try {
            await navigator.clipboard.writeText(
                `${window.location.origin}/?snapshot=${base64Token}`,
            );
            copySuccess = true;
            setTimeout(() => {
                copySuccess = false;
            }, 2000);
        } catch (err) {
            console.error("Failed to copy: ", err);
        }
    }
</script>

<Dialog bind:showDialog {onOpen}>
    <div class="mx-2 flex h-full flex-col">
        {#if !datasetSelectionLocked}
            <label for="datasetNameDelete" class="mb-1">Dataset</label>
            {#key datasetNames}
                <select
                    class="border-border bg-window-background focus:border-orange h-9 w-full rounded border-2 p-2"
                    id="datasetNameDelete"
                    bind:value={datasetName}
                >
                    {#each datasetNames as availableDatasetName}
                        <option
                            value={availableDatasetName}
                            selected={availableDatasetName ===
                                editorState.selectedDataset.getValue()}
                        >
                            {availableDatasetName}
                        </option>
                    {/each}
                </select>
            {/key}
        {:else}
            <p class="mb-1 font-semibold">Dataset</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {lockedDatasetName}
            </div>
        {/if}

        <div class="mt-4 flex h-full flex-col">
            <p class="mb-1">Snapshot Link</p>
            <div class="flex items-center gap-2">
                <div
                    class="border-border bg-window-background focus:border-orange h-9 w-full rounded border-2 p-2"
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
                        >
                            <Fa icon={faClipboardList} />
                        </ButtonControl>
                    </div>
                {/if}
            </div>
            {#if copySuccess}
                <p class="text-green-text mt-1 text-sm">
                    Link copied to clipboard!
                </p>
            {/if}
        </div>
    </div>
    <DialogLeaveButtons
        submitLabel="Share Snapshot"
        onSubmit={snapshotDataset}
        onCancel={() => (showDialog = false)}
        cancelLabel="Close"
    />
</Dialog>
