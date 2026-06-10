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
    import { faRightLeft } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { BackendConnection } from "$lib/api/backend.js";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import FileSelectButton from "$lib/components/FileSelectButton.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
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
        STORED_TO_FILE: 3,
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
            label: "Stored schema → Stored schema",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_STORED,
            label: "Uploaded schema → Stored schema",
            disabled: false,
        },
        {
            value: CompareMode.STORED_TO_FILE,
            label: "Stored schema → Uploaded schema",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_FILE,
            label: "Uploaded schema → Uploaded schema",
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

        if (compareMode === CompareMode.STORED_TO_FILE) {
            return !datasetA || !graphA || !fileA;
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

    function swapSelections() {
        if (compareMode === CompareMode.STORED_TO_STORED) {
            [datasetA, datasetB] = [datasetB, datasetA];
            [graphA, graphB] = [graphB, graphA];
        } else if (compareMode === CompareMode.FILE_TO_FILE) {
            [fileA, fileB] = [fileB, fileA];
        } else if (compareMode === CompareMode.FILE_TO_STORED) {
            datasetA = datasetB;
            graphA = graphB;
            datasetB = null;
            graphB = null;
            compareMode = CompareMode.STORED_TO_FILE;
        } else if (compareMode === CompareMode.STORED_TO_FILE) {
            datasetB = datasetA;
            graphB = graphA;
            datasetA = null;
            graphA = null;
            compareMode = CompareMode.FILE_TO_STORED;
        }
    }

    function invertPropertyChange(change) {
        return { ...change, from: change.to, to: change.from };
    }

    function invertResourceChange(resource) {
        return {
            ...resource,
            changes: resource.changes?.map(invertPropertyChange) ?? null,
        };
    }

    function invertClassChange(cls) {
        return {
            ...invertResourceChange(cls),
            attributes: cls.attributes?.map(invertResourceChange) ?? null,
            associations: cls.associations?.map(invertResourceChange) ?? null,
            enumEntries: cls.enumEntries?.map(invertResourceChange) ?? null,
        };
    }

    function invertChangeList(changeList) {
        return changeList.map(pkg => ({
            ...invertResourceChange(pkg),
            classes: pkg.classes.map(invertClassChange),
        }));
    }

    async function runCompare() {
        let response;
        let invert = false;
        switch (compareMode) {
            case CompareMode.FILE_TO_FILE:
                response = await bec.compareSchemasFromFiles(fileA, fileB);
                break;
            case CompareMode.FILE_TO_STORED:
                response = await bec.compareSchemas(datasetB, graphB, fileA);
                break;
            case CompareMode.STORED_TO_FILE:
                response = await bec.compareSchemas(datasetA, graphA, fileA);
                invert = true;
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

        let changeList = await response.json();
        if (invert) {
            changeList = invertChangeList(changeList);
        }
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
    title="Compare Schemas"
>
    <div class="mx-2 flex h-full flex-col font-[350]">
        <div class="mb-3">
            <p class="text-text-subtle mt-1 text-sm">
                Select a source and a modified schema to see what changed
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
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Before
                    </span>
                    <DatasetAndGraphSelection
                        bind:dataset={datasetA}
                        bind:graph={graphA}
                        {lockedDatasetName}
                        {lockedGraphUri}
                    />
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <button
                        onclick={swapSelections}
                        class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
                        title="Swap Before and After"
                    >
                        <Fa icon={faRightLeft} />
                        Swap
                    </button>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        After
                    </span>
                    <DatasetAndGraphSelection
                        bind:dataset={datasetB}
                        bind:graph={graphB}
                    />
                </div>
            {/if}

            {#if compareMode === CompareMode.FILE_TO_STORED}
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Before
                    </span>
                    <div
                        class="border-border bg-background-subtle rounded border p-3"
                    >
                        <FileSelectButton bind:file={fileA} />
                    </div>
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <button
                        onclick={swapSelections}
                        class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
                        title="Swap Before and After"
                    >
                        <Fa icon={faRightLeft} />
                        Swap
                    </button>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        After
                    </span>
                    <DatasetAndGraphSelection
                        bind:dataset={datasetB}
                        bind:graph={graphB}
                        {lockedDatasetName}
                        {lockedGraphUri}
                    />
                </div>
            {/if}

            {#if compareMode === CompareMode.STORED_TO_FILE}
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Before
                    </span>
                    <DatasetAndGraphSelection
                        bind:dataset={datasetA}
                        bind:graph={graphA}
                        {lockedDatasetName}
                        {lockedGraphUri}
                    />
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <button
                        onclick={swapSelections}
                        class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
                        title="Swap Before and After"
                    >
                        <Fa icon={faRightLeft} />
                        Swap
                    </button>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        After
                    </span>
                    <div
                        class="border-border bg-background-subtle rounded border p-3"
                    >
                        <FileSelectButton bind:file={fileA} />
                    </div>
                </div>
            {/if}

            {#if compareMode === CompareMode.FILE_TO_FILE}
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Before
                    </span>
                    <div
                        class="border-border bg-background-subtle rounded border p-3"
                    >
                        <FileSelectButton bind:file={fileA} />
                    </div>
                </div>

                <div class="flex items-center gap-3">
                    <div class="bg-border h-px w-full"></div>
                    <button
                        onclick={swapSelections}
                        class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
                        title="Swap Before and After"
                    >
                        <Fa icon={faRightLeft} />
                        Swap
                    </button>
                    <div class="bg-border h-px w-full"></div>
                </div>

                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        After
                    </span>
                    <div
                        class="border-border bg-background-subtle rounded border p-3"
                    >
                        <FileSelectButton
                            bind:file={fileB}
                            label="Select second file"
                        />
                    </div>
                </div>
            {/if}
        </div>
    </div>
</ActionDialog>
