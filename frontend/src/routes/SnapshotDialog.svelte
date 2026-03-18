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

    import ButtonControl from "../lib/components/ButtonControl.svelte";
    import { editorState } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedDatasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const datasetSelectId = `datasetSelect-${uuidv4()}`;

    let datasetName = $state(null);
    let datasets = $state([]);
    let base64Token = $state();

    let copySuccess = $state(false);

    const datasetSelectionLocked = $derived(!!lockedDatasetName);

    function onOpen() {
        datasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        if (datasetSelectionLocked) {
            datasets = [{ label: lockedDatasetName }];
        } else {
            loadDatasets();
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

    async function loadDatasets() {
        const res = await bec.getDatasetNames();
        const datasetNames = await res.json();
        datasets = datasetNames.map(name => ({ label: name }));
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
            options={datasets}
            getOptionValue={dataset => dataset.label}
            getOptionLabel={dataset => dataset.label}
            disabled={datasetSelectionLocked || datasets.length === 0}
            placeholder="Select dataset"
        />

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
                            height={9}
                        >
                            <Fa icon={faClipboardList} />
                        </ButtonControl>
                    </div>
                {/if}
            </div>
            <div class="h-6">
                {#if copySuccess}
                    <p class="text-green-text text-sm">
                        Link copied to clipboard!
                    </p>
                {/if}
            </div>
        </div>
    </div>
</ActionDialog>
