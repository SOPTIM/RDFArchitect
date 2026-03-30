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
    import { BackendConnection } from "$lib/api/backend.js";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import FileSelectButton from "$lib/components/FileSelectButton.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";
    import {
        editorState,
        compareState,
        migrationState,
    } from "$lib/sharedState.svelte.js";

    import { goto } from "$app/navigation";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const CompareMode = Object.freeze({
        STORED_TO_STORED: 0,
        FILE_TO_STORED: 1,
        FILE_TO_FILE: 2,
    });

    let compareMode = $state(CompareMode.STORED_TO_STORED);

    let datasetA = $state(null);
    let graphA = $state(null);

    let datasetB = $state(null);
    let graphB = $state(null);

    let fileA = $state(null);
    let fileB = $state(null);

    const compareModeOptions = $derived([
        {
            value: CompareMode.STORED_TO_STORED,
            label: "Stored graph → Stored graph",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_STORED,
            label: "Uploaded graph → Stored graph",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_FILE,
            label: "Uploaded graph → Uploaded graph",
            disabled: !!lockedDatasetName || !!lockedGraphUri,
        },
    ]);

    const disableSubmit = $derived.by(() => {
        if (compareMode === CompareMode.FILE_TO_FILE) {
            return !fileA || !fileB;
        }

        if (compareMode === CompareMode.FILE_TO_STORED) {
            return !datasetB || !graphB || !fileA;
        }

        if (compareMode === CompareMode.STORED_TO_STORED) {
            return !datasetA || !graphA || !datasetB || !graphB;
        }

        return true;
    });

    function onOpen() {
        datasetA = lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphA = lockedGraphUri ?? editorState.selectedGraph.getValue();

        datasetB = null;
        graphB = null;

        fileA = null;
        fileB = null;
    }

    function onClose() {
        compareMode = CompareMode.STORED_TO_STORED;

        datasetA = null;
        graphA = null;

        datasetB = null;
        graphB = null;

        fileA = null;
        fileB = null;
    }

    function onCompareModeChange(mode) {
        compareMode = mode;

        datasetB = null;
        graphB = null;

        fileA = null;
        fileB = null;
    }

    async function runCompare() {
        let response;
        switch (compareMode) {
            case CompareMode.FILE_TO_FILE:
                response = await bec.compareSchemasFromFiles(fileA, fileB);
                break;
            case CompareMode.FILE_TO_STORED:
                response = await bec.compareSchemas(datasetB, graphB, fileA);
                break;
            case CompareMode.STORED_TO_STORED:
                response = await bec.compareDatasetSchemas(
                    datasetA,
                    graphA,
                    datasetB,
                    graphB,
                );
                break;
            default:
                throw new Error(`Unknown compareMode: ${compareMode}`);
        }

        const changeList = await response.json();
        changeList.sort((a, b) => a.label.localeCompare(b.label));
        compareState.changeList.updateValue(changeList);
        migrationState.set({
            compareMode,
            datasetA,
            graphA,
            datasetB,
            graphB,
            fileA,
            fileB,
        });

        showDialog = false;
        await goto("/compare");
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Compare"
    onPrimary={runCompare}
    disablePrimary={disableSubmit}
    title="Compare Graphs"
>
    <div class="mx-2 flex h-full flex-col font-[350]">
        <div class="mb-3">
            <p class="text-text-subtle mt-1 text-sm">
                Select a source and a modified graph to see what changed
            </p>
        </div>

        <div class="mx-2 flex h-full flex-col space-y-4">
            <div class="border-border bg-background-subtle rounded border p-3">
                <label for="compareMode" class="mb-1 block text-sm">
                    Comparison type
                </label>
                <SelectEditControl
                    id="compareMode"
                    options={compareModeOptions}
                    bind:value={compareMode}
                    getOptionValue={o => o.value}
                    getOptionLabel={o => o.label}
                    onchange={value => onCompareModeChange(Number(value))}
                />
            </div>

            {#if compareMode === CompareMode.STORED_TO_STORED}
                <DatasetAndGraphSelection
                    bind:dataset={datasetA}
                    bind:graph={graphA}
                    {lockedDatasetName}
                    {lockedGraphUri}
                />

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <span
                        class="text-text-subtle text-xs font-light text-nowrap"
                    >
                        COMPARE TO
                    </span>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <DatasetAndGraphSelection
                    bind:dataset={datasetB}
                    bind:graph={graphB}
                />
            {/if}

            {#if compareMode === CompareMode.FILE_TO_STORED}
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton bind:file={fileA} />
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <span
                        class="text-text-subtle text-xs font-light text-nowrap"
                    >
                        COMPARE TO
                    </span>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <DatasetAndGraphSelection
                    bind:dataset={datasetB}
                    bind:graph={graphB}
                    {lockedDatasetName}
                    {lockedGraphUri}
                />
            {/if}

            {#if compareMode === CompareMode.FILE_TO_FILE}
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton bind:file={fileA} />
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <span
                        class="text-text-subtle text-xs font-light text-nowrap"
                    >
                        COMPARE TO
                    </span>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton
                        bind:file={fileB}
                        label="Select second file"
                    />
                </div>
            {/if}
        </div>
    </div>
</ActionDialog>
