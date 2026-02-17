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

    import { PUBLIC_BACKEND_URL } from "$env/static/public";

    import { getDatasetNames } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedDatasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const uniqueId = uuidv4();
    const defaultGraphUriPrefix = "http://graph#";
    const uriSchemePattern = /^[a-zA-Z][a-zA-Z\d+.-]*:/;
    const datasetInputId = `datasetNameNewGraph-${uniqueId}`;
    const datasetListId = `datasetNamesNewGraph-${uniqueId}`;
    const graphInputId = `graphUriNewGraph-${uniqueId}`;

    let datasetNameUserInput = $state("");
    let graphUriUserInput = $state("");
    let readOnlyDatasets = $state([]);
    let modifiableDatasets = $state([]);
    let graphNames = $state([]);

    const datasetSelectionLocked = $derived(!!lockedDatasetName);
    const datasetIsReadOnly = $derived(
        datasetNameUserInput && readOnlyDatasets.includes(datasetNameUserInput),
    );

    const resolvedGraphUri = $derived(resolveGraphUri(graphUriUserInput));
    const graphExists = $derived(
        !!resolvedGraphUri &&
            graphNames.some(
                g =>
                    (g.prefix == null ? "" : g.prefix) + g.suffix ===
                    resolvedGraphUri,
            ),
    );
    const disableSubmit = $derived(
        !datasetNameUserInput ||
            !resolvedGraphUri ||
            datasetIsReadOnly ||
            graphExists,
    );
    function resolveGraphUri(graphInput) {
        const trimmedInput = graphInput.trim();
        if (!trimmedInput) {
            return "";
        }
        if (uriSchemePattern.test(trimmedInput)) {
            return trimmedInput;
        }
        return defaultGraphUriPrefix + trimmedInput;
    }

    async function onOpen() {
        datasetNameUserInput =
            lockedDatasetName ?? editorState.selectedDataset.getValue() ?? "";
        graphUriUserInput = "";

        const datasetNames = await getDatasetNames();
        modifiableDatasets = datasetNames.modifiable;
        readOnlyDatasets = datasetNames.readonly;

        await refreshGraphNames();
    }

    function onClose() {
        datasetNameUserInput = "";
        graphUriUserInput = "";
        graphNames = [];
    }

    async function refreshGraphNames() {
        if (!datasetNameUserInput) {
            graphNames = [];
            return;
        }

        if (!modifiableDatasets.includes(datasetNameUserInput)) {
            graphNames = [];
            return;
        }

        const res = await bec.getGraphNames(datasetNameUserInput);
        graphNames = await res.json();
    }

    async function addGraph() {
        const datasetNameLocal = datasetNameUserInput;
        const graphURILocal = resolvedGraphUri;

        const promise = fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetNameLocal) +
                "/graphs/" +
                encodeURIComponent(graphURILocal) +
                "/content",
            {
                method: "PUT",
                credentials: "include",
            },
        ).then(res => {
            if (res.ok) {
                editorState.selectedDataset.updateValue(datasetNameLocal);
                editorState.selectedGraph.updateValue(graphURILocal);
                editorState.selectedPackageUUID.updateValue("default");
                editorState.selectedClassUUID.updateValue(null);
            } else {
                console.log("failed to create graph");
            }
        });

        promise
            .catch(e => {
                console.log("failed to create graph:");
                console.log(e);
            })
            .finally(() => {
                forceReloadTrigger.trigger();
            });
    }
</script>

<Dialog bind:showDialog {onOpen} {onClose}>
    <div class="mx-2 flex h-full flex-col">
        {#if !datasetSelectionLocked}
            <label for={datasetInputId} class="mb-1">Dataset</label>
            <input
                class="border-border bg-window-background focus:border-orange ring-none h-9 w-full rounded border-2 p-2 outline-none"
                type="text"
                id={datasetInputId}
                list={datasetListId}
                placeholder="Dataset name"
                bind:value={datasetNameUserInput}
                onchange={() => refreshGraphNames()}
            />
            <datalist id={datasetListId}>
                {#each modifiableDatasets as datasetName}
                    <option value={datasetName}>{datasetName}</option>
                {/each}
            </datalist>

            {#if datasetIsReadOnly}
                <div class="mt-1 mb-1 h-6 text-sm">
                    Cannot add graphs to read-only dataset
                </div>
            {/if}
        {:else}
            <p class="mb-1 font-semibold">Dataset</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {lockedDatasetName}
            </div>
        {/if}

        <label for={graphInputId} class="mt-2 mb-1">Graph</label>
        <input
            class="border-border bg-window-background focus:border-orange ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={graphInputId}
            placeholder={defaultGraphUriPrefix}
            autocomplete="off"
            bind:value={graphUriUserInput}
        />

        {#if graphExists}
            <div class="mt-1 mb-1 h-6 text-sm">Graph already exists</div>
        {/if}
    </div>

    <DialogLeaveButtons
        bind:showDialog
        submitLabel="Add graph"
        onSubmit={addGraph}
        {disableSubmit}
    />
</Dialog>
