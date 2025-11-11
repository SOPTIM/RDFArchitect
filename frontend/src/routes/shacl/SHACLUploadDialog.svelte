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
    import { getDatasetNames } from "$lib/api/apiDatasetUtils.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    const defaultDatasetName = "default";
    const defaultGraphUri = "default";
    const fileInputId = `actual-file-input-shacl-upload-${crypto.randomUUID()}`;
    let datasetName = $state("");
    let graphURI = $state("");
    let file = $state(null);
    let readOnlyDatasets = $state([]);

    let datasetNames = $state([]);
    let graphNames = $state([]);

    const lockedDatasetNameValue = $derived(lockedDatasetName);
    const lockedGraphUriValue = $derived(lockedGraphUri);
    const isReadOnlySelected = $derived(
        readOnlyDatasets.includes(datasetName || defaultDatasetName),
    );
    let disableSubmit = $derived(!file || isReadOnlySelected);

    const datasetSelectionLocked = $derived(!!lockedDatasetNameValue);
    const graphSelectionLocked = $derived(!!lockedGraphUriValue);

    async function onOpen() {
        if (showDialog) {
            datasetName =
                lockedDatasetNameValue ??
                editorState.selectedDataset.getValue();
            graphURI =
                lockedGraphUriValue ?? editorState.selectedGraph.getValue();
            file = null;
            await loadDatasets();
            if (graphSelectionLocked) {
                graphNames = [];
            } else {
                await getGraphNames(datasetName);
            }
        }
    }

    function setDefaultIfNotSet() {
        if (datasetName === "") {
            datasetName = defaultDatasetName;
        }
        if (graphURI === "") {
            graphURI = defaultGraphUri;
        }
    }

    async function importGraph() {
        setDefaultIfNotSet();
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

    async function loadDatasets() {
        const datasets = await getDatasetNames();
        readOnlyDatasets = datasets.readonly;
        const all = [...datasets.modifiable, ...datasets.readonly];
        datasetNames = datasetSelectionLocked
            ? datasetName
                ? [datasetName]
                : []
            : all;
    }

    async function getGraphNames(datasetName) {
        if (!datasetName) {
            graphNames = [];
            return;
        }
        if (
            !datasetSelectionLocked &&
            datasetNames.length &&
            !datasetNames.includes(datasetName)
        ) {
            graphNames = [{ suffix: "default" }];
            return;
        }
        return fetch(
            PUBLIC_BACKEND_URL + "/datasets/" + datasetName + "/graphs",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(response => response.text())
            .then(body => {
                graphNames = JSON.parse(body);
            });
    }
</script>

<Dialog bind:showDialog {onOpen}>
    <div class="mx-2 flex h-full flex-col">
        {#if datasetSelectionLocked}
            <p class="mb-1 font-semibold">Dataset</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {datasetName ?? "No dataset selected"}
            </div>
        {:else}
            <label for="datasetNameSHACLDelete" class="mb-1">Dataset:</label>
            {#key datasetNames}
                <select
                    class="border-border bg-window-background focus:border-orange ring-none h-9 w-full rounded border-2 p-2 outline-none"
                    id="datasetNameSHACLDelete"
                    bind:value={datasetName}
                    onchange={() => {
                        graphURI = "";
                        getGraphNames(datasetName);
                    }}
                >
                    {#each datasetNames as availableDataset}
                        <option value={availableDataset}>
                            {availableDataset}
                        </option>
                    {/each}
                </select>
            {/key}
        {/if}
        {#if isReadOnlySelected}
            <div class="mt-1 mb-1 h-6 text-sm">
                Cannot import into read-only dataset
            </div>
        {/if}
        {#if graphSelectionLocked}
            <p class="mt-2 mb-1 font-semibold">Graph:</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {graphURI ?? "No graph selected"}
            </div>
        {:else}
            <label for="graphUriSHACLDelete" class="mt-2 mb-1">Graph:</label>
            {#key graphNames}
                <select
                    class="border-border bg-window-background focus:border-orange ring-none h-9 w-full rounded border-2 p-2 outline-none"
                    id="graphUriSHACLDelete"
                    bind:value={graphURI}
                    disabled={graphNames.length === 0}
                >
                    {#each graphNames as graphName}
                        <option
                            value={(graphName.prefix == null
                                ? ""
                                : graphName.prefix) + graphName.suffix}
                        >
                            {graphName.suffix}
                        </option>
                    {/each}
                </select>
            {/key}
        {/if}
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
    <DialogLeaveButtons
        bind:showDialog
        submitLabel="Import"
        onSubmit={importGraph}
        {disableSubmit}
    />
</Dialog>
