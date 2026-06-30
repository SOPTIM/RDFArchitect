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

    import { createSnapshot } from "$lib/api/generated/index.ts";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";

    import ButtonControl from "../lib/components/ButtonControl.svelte";
    import { editorState } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedDatasetName } = $props();

    const datasetSelectId = `datasetSelect-${uuidv4()}`;

    let datasetName = $state(null);
    let base64Token = $state();

    const datasetSelectionLocked = $derived(!!lockedDatasetName);

    function onOpen() {
        datasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
    }

    async function snapshotDataset() {
        const { data, error } = await createSnapshot({
            path: { datasetName: datasetName },
        });
        if (!error) {
            base64Token = data;
            console.log(
                "Successfully created snapshot for dataset",
                datasetName,
            );
            toastStore.success(
                "Snapshot ready",
                `Share link created for "${datasetName}".`,
            );
        } else {
            console.error("Error creating snapshot for dataset:", error);
            toastStore.error(
                "Snapshot failed",
                `Could not create a snapshot for "${datasetName}".`,
            );
        }
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
    onPrimary={snapshotDataset}
    closeOnPrimary={false}
    title="Share Snapshot"
    disablePrimary={!datasetName}
>
    <div class="mx-2 flex h-full flex-col">
        <label for={datasetSelectId} class="mb-1">Dataset</label>
        <SelectEditControl
            id={datasetSelectId}
            bind:value={datasetName}
            options={$datasetStore.data}
            getOptionValue={dataset => dataset.label}
            getOptionLabel={dataset => dataset.label}
            disabled={datasetSelectionLocked || datasetStore.data?.length === 0}
            placeholder="Select dataset"
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
