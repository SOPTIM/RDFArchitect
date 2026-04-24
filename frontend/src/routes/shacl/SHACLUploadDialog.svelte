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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();
    const fileInputId = `actual-file-input-shacl-upload-${crypto.randomUUID()}`;
    let datasetName = $state("");
    let graphURI = $state("");
    let file = $state(null);

    const lockedDatasetNameValue = $derived(lockedDatasetName);
    const lockedGraphUriValue = $derived(lockedGraphUri);
    let disableSubmit = $derived(!file || !datasetName || !graphURI);

    async function onOpen() {
        if (showDialog) {
            datasetName =
                lockedDatasetNameValue ??
                editorState.selectedDataset.getValue();
            graphURI =
                lockedGraphUriValue ?? editorState.selectedGraph.getValue();
            file = null;
        }
    }

    async function importGraph() {
        let formData = new FormData();
        formData.append("file", file);
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetName) +
                "/graphs/" +
                encodeURIComponent(graphURI) +
                "/shacl/custom/file",
            {
                method: "PUT",
                body: formData,
                credentials: "include",
            },
        )
            .then(res => {
                if (res.ok) {
                    console.log("successfully inserted data");
                } else {
                    console.log("failed to insert SHACL file");
                }
            })
            .catch(e => {
                console.log("failed to insert SHACL file:");
                console.log(e);
            })
            .finally(() => {
                forceReloadTrigger.trigger();
            });
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    primaryLabel="Import"
    disablePrimary={disableSubmit}
    onPrimary={importGraph}
    title="Import Constrains (SHACL)"
>
    <div class="mx-2 flex h-full flex-col">
        <DatasetAndGraphSelection
            bind:dataset={datasetName}
            bind:graph={graphURI}
            {lockedDatasetName}
            {lockedGraphUri}
            displayAsCard={false}
        />
        <input
            class="hidden"
            type="file"
            id={fileInputId}
            onchange={event => {
                file = event.target.files[0];
            }}
        />
        <div class="mt-4 flex h-9 w-full space-x-4">
            <div class="h-9 w-24">
                <ButtonControl
                    height={9}
                    callOnClick={() => {
                        document.getElementById(fileInputId).click();
                    }}
                >
                    select file
                </ButtonControl>
            </div>
            <div class="h-9 w-full content-center">
                <p class="break-all">
                    {#if file}
                        {file.name}
                    {:else}
                        no file selected
                    {/if}
                </p>
            </div>
        </div>
    </div>
</ActionDialog>
